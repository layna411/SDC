import csv
from datetime import date, timedelta
import io
import logging
from pathlib import Path
from typing import List, Optional

from openpyxl import Workbook
from PIL import Image as PILImage, ImageOps
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.platypus import (
    Image as RLImage,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import utc_now
from app.storage.file_storage import file_storage
from app.models.entities import (
    ActivityLog,
    Center,
    College,
    Feedback,
    FollowUpRecord,
    LabInvestigation,
    Organizer,
    Patient,
    ScreeningRecord,
    User,
)
from app.models.enums import FollowUpStatus, ScreeningOutcome, SexType, UserRole
from app.repositories.scope_repository import (
    scope_activities_query,
    scope_centers_query,
    scope_colleges_query,
    scope_feedback_query,
    scope_organizers_query,
    scope_patients_query,
    scope_screenings_query,
)
from app.schemas.operations import (
    ActivityLogResponse,
    BreakdownItem,
    DashboardStatsResponse,
    MonthlyOverview,
    ReportAnalyticsResponse,
    WeeklyPoint,
)


def _weekly_series(screenings: List[ScreeningRecord], end_day: Optional[date] = None) -> List[WeeklyPoint]:
    anchor = end_day or date.today()
    points: List[WeeklyPoint] = []
    for offset in range(6, -1, -1):
        d = anchor - timedelta(days=offset)
        cnt = sum(1 for s in screenings if s.screening_date == d)
        points.append(WeeklyPoint(date=d.isoformat(), label=d.strftime("%a"), count=cnt))
    return points


def _monthly_series(screenings: List[ScreeningRecord]) -> List[WeeklyPoint]:
    today = date.today()
    points: List[WeeklyPoint] = []
    for offset in range(5, -1, -1):
        year = today.year
        month = today.month - offset
        while month <= 0:
            month += 12
            year -= 1
        cnt = sum(
            1 for s in screenings if s.screening_date.year == year and s.screening_date.month == month
        )
        label = date(year, month, 1).strftime("%b %Y")
        points.append(WeeklyPoint(date=f"{year:04d}-{month:02d}", label=label, count=cnt))
    return points


def build_dashboard_stats(db: Session, user: User) -> DashboardStatsResponse:
    patients = scope_patients_query(db.query(Patient), user).all()
    screenings = scope_screenings_query(db.query(ScreeningRecord), user).all()
    feedback_rows = scope_feedback_query(db.query(Feedback), user).all()
    activities = (
        scope_activities_query(db.query(ActivityLog), user)
        .order_by(ActivityLog.created_at.desc())
        .limit(10)
        .all()
    )

    total_colleges = scope_colleges_query(db.query(College), user).count()
    total_centers = scope_centers_query(db.query(Center), user).count()
    total_organizers = scope_organizers_query(db.query(Organizer), user).count()

    today = date.today()
    month_start = today.replace(day=1)

    new_patients_month = sum(1 for p in patients if p.registration_date >= month_start)
    lesions_month = sum(1 for s in screenings if s.screening_date >= month_start and s.lesion_present)
    pending_reviews = sum(
        1 for s in screenings if s.outcome == ScreeningOutcome.PENDING_CLINICAL_REVIEW
    )
    reports_generated_month = (
        scope_activities_query(db.query(ActivityLog), user)
        .filter(ActivityLog.action == "REPORT_DOWNLOADED")
        .count()
    )

    avg_rating = (
        round(sum(f.rating for f in feedback_rows) / len(feedback_rows), 2)
        if feedback_rows
        else 0.0
    )

    org_label = "OralCare Organization"
    if user.center_id:
        center = db.query(Center).filter(Center.id == user.center_id).first()
        if center:
            org_label = center.name
    elif user.college_id:
        college = db.query(College).filter(College.id == user.college_id).first()
        if college:
            org_label = college.name

    return DashboardStatsResponse(
        role=user.role.value,
        user_full_name=user.full_name,
        organization_label=org_label,
        total_colleges=total_colleges,
        total_centers=total_centers,
        total_organizers=total_organizers,
        total_patients=len(patients),
        male_patients=sum(1 for p in patients if p.sex == SexType.MALE),
        female_patients=sum(1 for p in patients if p.sex == SexType.FEMALE),
        other_patients=sum(1 for p in patients if p.sex == SexType.OTHER),
        total_screenings=len(screenings),
        normal_screenings=sum(1 for s in screenings if s.outcome == ScreeningOutcome.NORMAL),
        abnormal_screenings=sum(1 for s in screenings if s.outcome == ScreeningOutcome.ABNORMAL),
        pending_review_screenings=pending_reviews,
        lesions_detected=sum(1 for s in screenings if s.lesion_present),
        total_feedback=len(feedback_rows),
        average_feedback_rating=avg_rating,
        weekly_screenings=_weekly_series(screenings),
        monthly_overview=MonthlyOverview(
            new_patients=new_patients_month,
            lesions_detected=lesions_month,
            reports_generated=reports_generated_month,
            pending_reviews=pending_reviews,
        ),
        recent_activities=[ActivityLogResponse.model_validate(a) for a in activities],
    )


def build_report_analytics(
    db: Session,
    user: User,
    *,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    organizer_user_id: Optional[int] = None,
    outcome: Optional[ScreeningOutcome] = None,
) -> ReportAnalyticsResponse:
    p_query = scope_patients_query(db.query(Patient), user)
    s_query = scope_screenings_query(db.query(ScreeningRecord), user)

    if start_date:
        p_query = p_query.filter(Patient.registration_date >= start_date)
        s_query = s_query.filter(ScreeningRecord.screening_date >= start_date)
    if end_date:
        p_query = p_query.filter(Patient.registration_date <= end_date)
        s_query = s_query.filter(ScreeningRecord.screening_date <= end_date)
    if college_id and user.role == UserRole.SUPER_ADMIN:
        p_query = p_query.filter(Patient.college_id == college_id)
        s_query = s_query.filter(ScreeningRecord.college_id == college_id)
    if center_id and user.role in (UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN):
        p_query = p_query.filter(Patient.center_id == center_id)
        s_query = s_query.filter(ScreeningRecord.center_id == center_id)
    if organizer_user_id and user.role != UserRole.HEALTH_ORGANIZER:
        p_query = p_query.filter(Patient.assigned_organizer_id == organizer_user_id)
        s_query = s_query.filter(ScreeningRecord.organizer_user_id == organizer_user_id)
    if outcome:
        s_query = s_query.filter(ScreeningRecord.outcome == outcome)

    patients = p_query.all()
    screenings = s_query.all()
    screening_ids = [s.id for s in screenings]

    lab_count = (
        db.query(LabInvestigation).filter(LabInvestigation.screening_id.in_(screening_ids)).count()
        if screening_ids
        else 0
    )
    follow_ups = (
        db.query(FollowUpRecord).filter(FollowUpRecord.screening_id.in_(screening_ids)).all()
        if screening_ids
        else []
    )

    colleges = scope_colleges_query(db.query(College), user).all()
    centers = scope_centers_query(db.query(Center), user).all()
    organizers = scope_organizers_query(db.query(Organizer), user).all()

    college_breakdown = [
        BreakdownItem(
            id=c.id,
            name=c.name,
            total_patients=sum(1 for p in patients if p.college_id == c.id),
            total_screenings=sum(1 for s in screenings if s.college_id == c.id),
            normal_screenings=sum(
                1 for s in screenings if s.college_id == c.id and s.outcome == ScreeningOutcome.NORMAL
            ),
            abnormal_screenings=sum(
                1 for s in screenings if s.college_id == c.id and s.outcome == ScreeningOutcome.ABNORMAL
            ),
            pending_reviews=sum(
                1
                for s in screenings
                if s.college_id == c.id and s.outcome == ScreeningOutcome.PENDING_CLINICAL_REVIEW
            ),
            lesions_detected=sum(1 for s in screenings if s.college_id == c.id and s.lesion_present),
        )
        for c in colleges
    ]

    center_breakdown = [
        BreakdownItem(
            id=ct.id,
            name=ct.name,
            total_patients=sum(1 for p in patients if p.center_id == ct.id),
            total_screenings=sum(1 for s in screenings if s.center_id == ct.id),
            normal_screenings=sum(
                1 for s in screenings if s.center_id == ct.id and s.outcome == ScreeningOutcome.NORMAL
            ),
            abnormal_screenings=sum(
                1 for s in screenings if s.center_id == ct.id and s.outcome == ScreeningOutcome.ABNORMAL
            ),
            pending_reviews=sum(
                1
                for s in screenings
                if s.center_id == ct.id and s.outcome == ScreeningOutcome.PENDING_CLINICAL_REVIEW
            ),
            lesions_detected=sum(1 for s in screenings if s.center_id == ct.id and s.lesion_present),
        )
        for ct in centers
    ]

    organizer_breakdown = [
        BreakdownItem(
            id=org.user_id,
            name=org.user.full_name if org.user else org.employee_code,
            total_patients=sum(1 for p in patients if p.assigned_organizer_id == org.user_id),
            total_screenings=sum(1 for s in screenings if s.organizer_user_id == org.user_id),
            normal_screenings=sum(
                1
                for s in screenings
                if s.organizer_user_id == org.user_id and s.outcome == ScreeningOutcome.NORMAL
            ),
            abnormal_screenings=sum(
                1
                for s in screenings
                if s.organizer_user_id == org.user_id and s.outcome == ScreeningOutcome.ABNORMAL
            ),
            pending_reviews=sum(
                1
                for s in screenings
                if s.organizer_user_id == org.user_id
                and s.outcome == ScreeningOutcome.PENDING_CLINICAL_REVIEW
            ),
            lesions_detected=sum(
                1 for s in screenings if s.organizer_user_id == org.user_id and s.lesion_present
            ),
        )
        for org in organizers
    ]

    return ReportAnalyticsResponse(
        generated_at=utc_now(),
        total_patients=len(patients),
        male_patients=sum(1 for p in patients if p.sex == SexType.MALE),
        female_patients=sum(1 for p in patients if p.sex == SexType.FEMALE),
        other_patients=sum(1 for p in patients if p.sex == SexType.OTHER),
        total_screenings=len(screenings),
        normal_screenings=sum(1 for s in screenings if s.outcome == ScreeningOutcome.NORMAL),
        abnormal_screenings=sum(1 for s in screenings if s.outcome == ScreeningOutcome.ABNORMAL),
        pending_reviews=sum(
            1 for s in screenings if s.outcome == ScreeningOutcome.PENDING_CLINICAL_REVIEW
        ),
        lesions_detected=sum(1 for s in screenings if s.lesion_present),
        lab_investigations_count=lab_count,
        follow_ups_scheduled=sum(1 for f in follow_ups if f.status == FollowUpStatus.SCHEDULED),
        follow_ups_completed=sum(1 for f in follow_ups if f.status == FollowUpStatus.COMPLETED),
        weekly_series=_weekly_series(screenings, end_date),
        monthly_series=_monthly_series(screenings),
        college_breakdown=college_breakdown,
        center_breakdown=center_breakdown,
        organizer_breakdown=organizer_breakdown,
    )


def export_report_csv(report: ReportAnalyticsResponse) -> bytes:
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(["ORALCARE CLINICAL SCREENING ANALYTICS REPORT"])
    writer.writerow(["Generated At", report.generated_at.isoformat()])
    writer.writerow([])
    writer.writerow(["Metric", "Value"])
    writer.writerow(["Total Patients", report.total_patients])
    writer.writerow(["Male Patients", report.male_patients])
    writer.writerow(["Female Patients", report.female_patients])
    writer.writerow(["Other Patients", report.other_patients])
    writer.writerow(["Total Screenings", report.total_screenings])
    writer.writerow(["Normal Screenings", report.normal_screenings])
    writer.writerow(["Abnormal Screenings", report.abnormal_screenings])
    writer.writerow(["Pending Clinical Reviews", report.pending_reviews])
    writer.writerow(["Lesions Detected", report.lesions_detected])
    writer.writerow(["Lab Investigations", report.lab_investigations_count])
    writer.writerow(["Follow-Ups Scheduled", report.follow_ups_scheduled])
    writer.writerow(["Follow-Ups Completed", report.follow_ups_completed])
    writer.writerow([])
    writer.writerow(["CENTER BREAKDOWN"])
    writer.writerow(
        ["Center ID", "Center Name", "Patients", "Screenings", "Normal", "Abnormal", "Pending", "Lesions"]
    )
    for item in report.center_breakdown:
        writer.writerow(
            [
                item.id,
                item.name,
                item.total_patients,
                item.total_screenings,
                item.normal_screenings,
                item.abnormal_screenings,
                item.pending_reviews,
                item.lesions_detected,
            ]
        )
    return buf.getvalue().encode("utf-8")


def export_report_excel(report: ReportAnalyticsResponse) -> bytes:
    wb = Workbook()
    ws = wb.active
    ws.title = "Summary"
    ws.append(["ORALCARE CLINICAL ANALYTICS REPORT"])
    ws.append(["Generated At", report.generated_at.isoformat()])
    ws.append([])
    ws.append(["Metric", "Count"])
    metrics = [
        ("Total Patients", report.total_patients),
        ("Male Patients", report.male_patients),
        ("Female Patients", report.female_patients),
        ("Total Screenings", report.total_screenings),
        ("Normal Screenings", report.normal_screenings),
        ("Abnormal Screenings", report.abnormal_screenings),
        ("Pending Reviews", report.pending_reviews),
        ("Lesions Detected", report.lesions_detected),
        ("Lab Investigations", report.lab_investigations_count),
        ("Follow-Ups Scheduled", report.follow_ups_scheduled),
        ("Follow-Ups Completed", report.follow_ups_completed),
    ]
    for k, v in metrics:
        ws.append([k, v])

    ws_centers = wb.create_sheet("Center Breakdown")
    ws_centers.append(
        ["Center ID", "Name", "Patients", "Screenings", "Normal", "Abnormal", "Pending", "Lesions"]
    )
    for item in report.center_breakdown:
        ws_centers.append(
            [
                item.id,
                item.name,
                item.total_patients,
                item.total_screenings,
                item.normal_screenings,
                item.abnormal_screenings,
                item.pending_reviews,
                item.lesions_detected,
            ]
        )

    ws_orgs = wb.create_sheet("Organizer Breakdown")
    ws_orgs.append(
        ["Organizer User ID", "Name", "Patients", "Screenings", "Normal", "Abnormal", "Pending", "Lesions"]
    )
    for item in report.organizer_breakdown:
        ws_orgs.append(
            [
                item.id,
                item.name,
                item.total_patients,
                item.total_screenings,
                item.normal_screenings,
                item.abnormal_screenings,
                item.pending_reviews,
                item.lesions_detected,
            ]
        )

    output = io.BytesIO()
    wb.save(output)
    return output.getvalue()


def export_report_pdf(report: ReportAnalyticsResponse) -> bytes:
    output = io.BytesIO()
    doc = SimpleDocTemplate(output, pagesize=A4)
    styles = getSampleStyleSheet()
    elements = [
        Paragraph("ORALCARE - Oral Health Screening Analytics Report", styles["Title"]),
        Paragraph(f"Generated: {report.generated_at.strftime('%Y-%m-%d %H:%M UTC')}", styles["Normal"]),
        Spacer(1, 12),
    ]

    summary_data = [
        ["Metric", "Value"],
        ["Total Patients", str(report.total_patients)],
        ["Male / Female / Other", f"{report.male_patients} / {report.female_patients} / {report.other_patients}"],
        ["Total Screenings", str(report.total_screenings)],
        ["Normal Screenings", str(report.normal_screenings)],
        ["Abnormal Screenings", str(report.abnormal_screenings)],
        ["Pending Clinical Reviews", str(report.pending_reviews)],
        ["Lesions Detected", str(report.lesions_detected)],
        ["Lab Investigations", str(report.lab_investigations_count)],
        ["Follow-Ups (Scheduled / Completed)", f"{report.follow_ups_scheduled} / {report.follow_ups_completed}"],
    ]
    table = Table(summary_data, colWidths=[240, 220])
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1565C0")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#EAF3FF")]),
                ("PADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    elements.append(table)
    elements.append(Spacer(1, 16))

    if report.center_breakdown:
        elements.append(Paragraph("Center-wise Breakdown", styles["Heading2"]))
        c_rows = [["Center", "Patients", "Screenings", "Normal", "Abnormal", "Lesions"]]
        for item in report.center_breakdown:
            c_rows.append(
                [
                    item.name,
                    str(item.total_patients),
                    str(item.total_screenings),
                    str(item.normal_screenings),
                    str(item.abnormal_screenings),
                    str(item.lesions_detected),
                ]
            )
        c_table = Table(c_rows, colWidths=[160, 60, 70, 55, 60, 55])
        c_table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0D2340")),
                    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                    ("PADDING", (0, 0), (-1, -1), 5),
                ]
            )
        )
        elements.append(c_table)

    doc.build(elements)
    return output.getvalue()


logger = logging.getLogger("report_service")


def _find_hospital_logo() -> Optional[Path]:
    possible_paths = [
        Path(__file__).parent.parent / "assets" / "oralcare_logo.png",
        Path("Backend/app/assets/oralcare_logo.png"),
        Path("SDC/app/src/main/res/drawable/oralcare_logo.png"),
        Path(__file__).parent.parent.parent / "SDC" / "app" / "src" / "main" / "res" / "drawable" / "oralcare_logo.png",
    ]
    for p in possible_paths:
        try:
            if p.is_file():
                return p.resolve()
        except Exception:
            continue
    return None


def _resolve_image_file(rel_path: str) -> Optional[Path]:
    if not rel_path:
        return None
    candidates = [
        Path(rel_path),
        Path("Backend") / rel_path,
        Path(settings.STORAGE_ROOT) / rel_path,
        Path("Backend") / settings.STORAGE_ROOT / rel_path,
    ]
    try:
        resolved = file_storage.resolve_path(rel_path)
        if resolved.is_file():
            return resolved
    except Exception:
        pass
    for cand in candidates:
        try:
            if cand.is_file():
                return cand.resolve()
        except Exception:
            continue
    return None


def _create_thumbnail_image(img_path: Path, max_w: float = 230, max_h: float = 160) -> Optional[RLImage]:
    try:
        with PILImage.open(img_path) as im:
            im = ImageOps.exif_transpose(im)
            im_w, im_h = im.size
            if im_w <= 0 or im_h <= 0:
                return None
            aspect = im_h / float(im_w)
            target_w = max_w
            target_h = max_w * aspect
            if target_h > max_h:
                target_h = max_h
                target_w = max_h / aspect

            buf = io.BytesIO()
            if im.mode in ("RGBA", "P"):
                im = im.convert("RGB")
            im.save(buf, format="JPEG", quality=88)
            buf.seek(0)
            return RLImage(buf, width=target_w, height=target_h)
    except Exception as e:
        logger.warning("Could not process clinical image %s: %s", img_path, e)
        return None


def _clean_pdf_text(text: Optional[str]) -> str:
    if not text:
        return "-"
    return (
        str(text)
        .replace("₹", "Rs. ")
        .replace("–", "-")
        .replace("—", "-")
        .replace("•", "*")
        .strip()
    )


def export_patient_report_pdf(patient: Patient, screenings: List[ScreeningRecord]) -> bytes:
    output = io.BytesIO()
    doc = SimpleDocTemplate(
        output,
        pagesize=A4,
        leftMargin=32,
        rightMargin=32,
        topMargin=28,
        bottomMargin=28,
    )
    styles = getSampleStyleSheet()

    title_style = ParagraphStyle(
        "ReportTitle",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=14,
        leading=17,
        textColor=colors.HexColor("#0D2340"),
    )
    sub_style = ParagraphStyle(
        "ReportSub",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8.5,
        leading=11,
        textColor=colors.HexColor("#1E40AF"),
    )
    badge_title = ParagraphStyle(
        "BadgeTitle",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=9,
        leading=12,
        textColor=colors.HexColor("#047857"),
    )
    h2_style = ParagraphStyle(
        "ReportH2",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=11,
        leading=14,
        textColor=colors.HexColor("#0D2340"),
        spaceBefore=8,
        spaceAfter=4,
    )
    h3_style = ParagraphStyle(
        "ReportH3",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=9.5,
        leading=12,
        textColor=colors.HexColor("#1E3A8A"),
        spaceBefore=6,
        spaceAfter=3,
    )
    normal_style = ParagraphStyle(
        "ReportNormal",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8.5,
        leading=11,
        textColor=colors.HexColor("#334155"),
    )
    bold_label = ParagraphStyle(
        "ReportBoldLabel",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=8,
        leading=10,
        textColor=colors.HexColor("#1E293B"),
    )
    table_cell = ParagraphStyle(
        "ReportCell",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8,
        leading=10,
        textColor=colors.HexColor("#334155"),
    )
    th_label_white = ParagraphStyle(
        "ReportThWhite",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=8,
        leading=10,
        textColor=colors.white,
    )
    photo_caption = ParagraphStyle(
        "PhotoCaption",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=7.5,
        leading=9.5,
        textColor=colors.HexColor("#1E293B"),
    )

    elements = []

    # 1. Header with Official Saveetha Logo & Title
    logo_path = _find_hospital_logo()
    header_content = [
        Paragraph("SAVEETHA DENTAL COLLEGE &amp; HOSPITAL", title_style),
        Spacer(1, 2),
        Paragraph("Department of Oral Medicine &amp; Radiology • Saveetha Institute of Medical and Technical Sciences (SIMATS)", sub_style),
        Spacer(1, 2),
        Paragraph("<b>COMPREHENSIVE ORAL HEALTH SCREENING &amp; DIAGNOSTIC CLINICAL REPORT</b>", badge_title),
    ]

    if logo_path:
        try:
            logo_img = RLImage(str(logo_path), width=65, height=57)
            header_table = Table([[logo_img, header_content]], colWidths=[72, 459])
            header_table.setStyle(
                TableStyle(
                    [
                        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                        ("PADDING", (0, 0), (-1, -1), 0),
                    ]
                )
            )
            elements.append(header_table)
        except Exception:
            elements.extend(header_content)
    else:
        elements.extend(header_content)

    elements.append(Spacer(1, 8))

    # 2. Metadata Banner
    latest_outcome = screenings[0].outcome.value if screenings else "NOT_SCREENED"
    first_scr = screenings[0] if screenings else None
    hub_name = first_scr.consent_record.hub if (first_scr and first_scr.consent_record and first_scr.consent_record.hub) else "Saveetha Dental Hub"
    worker_name = first_scr.consent_record.healthworker_name if (first_scr and first_scr.consent_record and first_scr.consent_record.healthworker_name) else "Clinical Examiner"

    banner_data = [
        [
            Paragraph("<b>Report UID:</b>", bold_label),
            Paragraph(f"OC-RPT-{patient.patient_uid}", table_cell),
            Paragraph("<b>Generated On:</b>", bold_label),
            Paragraph(utc_now().strftime("%Y-%m-%d %H:%M UTC"), table_cell),
        ],
        [
            Paragraph("<b>Patient UID:</b>", bold_label),
            Paragraph(patient.patient_uid, table_cell),
            Paragraph("<b>Clinical Outcome:</b>", bold_label),
            Paragraph(f"<b>{latest_outcome.replace('_', ' ')}</b>", bold_label),
        ],
        [
            Paragraph("<b>Screening Hub:</b>", bold_label),
            Paragraph(hub_name, table_cell),
            Paragraph("<b>Lead Examiner:</b>", bold_label),
            Paragraph(worker_name, table_cell),
        ],
    ]
    banner_table = Table(banner_data, colWidths=[95, 170, 95, 171])
    banner_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#EBF3FB")),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#93C5FD")),
                ("PADDING", (0, 0), (-1, -1), 4),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ]
        )
    )
    elements.append(banner_table)
    elements.append(Spacer(1, 10))

    # 3. Patient Demographic Profile (Full Demographics)
    elements.append(Paragraph("1. Patient Demographic Profile", h2_style))
    demo_data = [
        [
            Paragraph("<b>Parameter</b>", th_label_white),
            Paragraph("<b>Record Details</b>", th_label_white),
            Paragraph("<b>Parameter</b>", th_label_white),
            Paragraph("<b>Record Details</b>", th_label_white),
        ],
        [
            Paragraph("Full Legal Name", bold_label),
            Paragraph(f"{patient.prefix or ''} {patient.first_name} {patient.last_name}".strip(), table_cell),
            Paragraph("Registration Date", bold_label),
            Paragraph(str(patient.registration_date), table_cell),
        ],
        [
            Paragraph("Age / Sex", bold_label),
            Paragraph(f"{patient.age} Years / {getattr(patient.sex, 'value', patient.sex)}", table_cell),
            Paragraph("Phone Number", bold_label),
            Paragraph(patient.phone_number, table_cell),
        ],
        [
            Paragraph("Residential Address", bold_label),
            Paragraph(patient.address or "-", table_cell),
            Paragraph("Marital Status", bold_label),
            Paragraph(patient.marital_status or "-", table_cell),
        ],
        [
            Paragraph("Religion / Community", bold_label),
            Paragraph(patient.religion or "-", table_cell),
            Paragraph("Education Level", bold_label),
            Paragraph(patient.education or "-", table_cell),
        ],
        [
            Paragraph("Occupation", bold_label),
            Paragraph(patient.occupation or "-", table_cell),
            Paragraph("Monthly Income", bold_label),
            Paragraph(_clean_pdf_text(patient.family_income_per_month), table_cell),
        ],
    ]
    demo_table = Table(demo_data, colWidths=[110, 155, 110, 156])
    demo_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1E3A8A")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
                ("PADDING", (0, 0), (-1, -1), 4),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ]
        )
    )
    elements.append(demo_table)
    elements.append(Spacer(1, 10))

    # 4. Clinical Screening Records
    if not screenings:
        elements.append(Paragraph("2. Clinical Screenings", h2_style))
        elements.append(Paragraph("No clinical screening sessions recorded for this patient yet.", normal_style))
    else:
        for s_idx, scr in enumerate(screenings, start=1):
            elements.append(Paragraph(f"2.{s_idx} Clinical Screening Session: {scr.screening_uid}", h2_style))

            # Screening Overview Table
            scr_info = [
                ["Screening Date", str(scr.screening_date), "Workflow Status", scr.workflow_status.value],
                ["Clinical Outcome", scr.outcome.value.replace("_", " "), "Lesion Present", "YES" if scr.lesion_present else "NO"],
                ["Review Status", scr.clinician_review_status, "Center / Hub", (scr.consent_record.hub if scr.consent_record else "-")],
                ["Healthworker", (scr.consent_record.healthworker_name if scr.consent_record else "-"), "Partner", (scr.consent_record.health_partner if scr.consent_record else "-")],
            ]
            scr_table = Table(scr_info, colWidths=[105, 160, 105, 161])
            scr_table.setStyle(
                TableStyle(
                    [
                        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#F1F5F9")),
                        ("BACKGROUND", (2, 0), (2, -1), colors.HexColor("#F1F5F9")),
                        ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
                        ("FONTNAME", (2, 0), (2, -1), "Helvetica-Bold"),
                        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                        ("PADDING", (0, 0), (-1, -1), 3.5),
                        ("FONTSIZE", (0, 0), (-1, -1), 8),
                    ]
                )
            )
            elements.append(scr_table)
            elements.append(Spacer(1, 6))

            # Step 1: Informed Consent & Approvals
            if scr.consent_record:
                cr = scr.consent_record
                geo = f"{cr.geolocation_label or 'Site'} ({cr.latitude or '-'}, {cr.longitude or '-'})" if (cr.latitude or cr.longitude) else (cr.geolocation_label or "Not recorded")
                consent_rows = [
                    ["Informed Consent Parameter", "Status / Details", "Informed Consent Parameter", "Status / Details"],
                    ["Participant Info Sheet", "Explained & Acknowledged" if cr.participant_info_sheet_ack else "Not Confirmed", "Signed Consent Form", "Obtained / Thumb Impression" if cr.consent_form_signed else "Not Confirmed"],
                    ["Oral Screening Consent", "Accepted" if cr.consent_accepted else "Declined", "Diagnostic AI Terms", "Accepted" if cr.terms_accepted else "Declined"],
                    ["Screening Site / GPS", geo, "Location Permission", "Granted" if cr.location_permission_granted else "Not Granted"],
                ]
                c_table = Table(consent_rows, colWidths=[120, 145, 120, 146])
                c_table.setStyle(
                    TableStyle(
                        [
                            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#047857")),
                            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                            ("FONTNAME", (0, 1), (0, -1), "Helvetica-Bold"),
                            ("FONTNAME", (2, 1), (2, -1), "Helvetica-Bold"),
                            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                            ("PADDING", (0, 0), (-1, -1), 3.5),
                            ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                        ]
                    )
                )
                elements.append(Paragraph("Step 1: Informed Consent & Location Protocol", h3_style))
                elements.append(c_table)
                elements.append(Spacer(1, 6))

            # Step 3: Habits & Personal History
            elements.append(Paragraph("Step 3: Personal & Habit History", h3_style))
            diet_text = f"<b>Diet:</b> {scr.personal_history.diet_history if scr.personal_history else '-'} | <b>Spice Consumption:</b> {scr.personal_history.spice_consumption if scr.personal_history else '-'}"
            elements.append(Paragraph(diet_text, normal_style))
            elements.append(Spacer(1, 3))

            if scr.habit_histories:
                habit_rows = [["Habit Category", "Type / Brand", "Usage Status", "Frequency", "Duration", "Quantity", "Remarks"]]
                for h in scr.habit_histories:
                    habit_rows.append([
                        h.habit_category,
                        h.habit_type or "-",
                        h.usage_status,
                        h.frequency or "-",
                        f"{h.duration} yrs" if h.duration else "-",
                        h.quantity or "-",
                        h.remarks or "-",
                    ])
                h_table = Table(habit_rows, colWidths=[80, 80, 70, 75, 55, 75, 96])
                h_table.setStyle(
                    TableStyle(
                        [
                            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#334155")),
                            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                            ("PADDING", (0, 0), (-1, -1), 3.5),
                            ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                        ]
                    )
                )
                elements.append(h_table)
            else:
                elements.append(Paragraph("Habit History: No tobacco, arecanut, or alcohol habits reported.", normal_style))
            elements.append(Spacer(1, 6))

            # Step 4 & 5: Medical, Dental & Oral Hygiene History
            elements.append(Paragraph("Step 4–5: Medical, Dental History & Oral Hygiene Practices", h3_style))
            med_c = ", ".join(scr.medical_history.past_medical_conditions) if (scr.medical_history and scr.medical_history.past_medical_conditions) else "None Recorded"
            allergies = scr.medical_history.allergies if (scr.medical_history and scr.medical_history.allergies) else "No Known Drug Allergies"
            meds = scr.medical_history.current_medications if (scr.medical_history and scr.medical_history.current_medications) else "None"
            fam_c = ", ".join(scr.medical_history.family_history_conditions) if (scr.medical_history and scr.medical_history.family_history_conditions) else "None Recorded"

            cleaning = f"{scr.dental_history.cleaning_aid if scr.dental_history else '-'} / {scr.dental_history.material_used if scr.dental_history else '-'}"
            brushing = f"{scr.dental_history.brushing_frequency if scr.dental_history else '-'} ({scr.dental_history.brushing_method if scr.dental_history else '-'})"
            denture = "Yes" if (scr.dental_history and scr.dental_history.denture_usage) else "No"
            past_dent = "Yes" if (scr.dental_history and scr.dental_history.past_dental_history) else "No"

            med_data = [
                ["Past Medical Illnesses", med_c, "Allergies / Current Meds", f"{allergies} / {meds}"],
                ["Family History", fam_c, "Past Dental Visits", past_dent],
                ["Cleaning Aid / Material", cleaning, "Brushing Regimen", brushing],
                ["Denture Usage", denture, "Toothbrush Change Frequency", scr.dental_history.toothbrush_change_frequency if scr.dental_history else "-"],
            ]
            med_table = Table(med_data, colWidths=[120, 145, 120, 146])
            med_table.setStyle(
                TableStyle(
                    [
                        ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
                        ("FONTNAME", (2, 0), (2, -1), "Helvetica-Bold"),
                        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                        ("PADDING", (0, 0), (-1, -1), 3.5),
                        ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                    ]
                )
            )
            elements.append(med_table)
            elements.append(Spacer(1, 6))

            # Step 6: Clinical Examination (11 Anatomical Regions)
            elements.append(Paragraph("Step 6: Clinical Mucosal Examination (11 Anatomical Regions)", h3_style))
            if scr.clinical_findings:
                cf_rows = [["Anatomical Region", "Sub-Region", "Status", "Clinical Observations / Remarks"]]
                for cf in scr.clinical_findings:
                    obs_text = ", ".join(o.observation_name for o in (cf.observations or []))
                    if cf.remarks:
                        obs_text = f"{obs_text} ({cf.remarks})" if obs_text else cf.remarks
                    status_str = cf.status.value if hasattr(cf.status, "value") else str(cf.status)
                    cf_rows.append([
                        cf.anatomical_region,
                        cf.sub_region or "-",
                        status_str,
                        obs_text or "No abnormality detected",
                    ])
                cf_table = Table(cf_rows, colWidths=[125, 100, 65, 241])
                t_styles = [
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1E3A8A")),
                    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                    ("PADDING", (0, 0), (-1, -1), 3.5),
                    ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                ]
                for r_idx, cf in enumerate(scr.clinical_findings, start=1):
                    s_str = cf.status.value if hasattr(cf.status, "value") else str(cf.status)
                    if s_str.upper() == "ABNORMAL":
                        t_styles.append(("TEXTCOLOR", (2, r_idx), (2, r_idx), colors.HexColor("#DC2626")))
                        t_styles.append(("FONTNAME", (2, r_idx), (2, r_idx), "Helvetica-Bold"))
                    else:
                        t_styles.append(("TEXTCOLOR", (2, r_idx), (2, r_idx), colors.HexColor("#059669")))
                cf_table.setStyle(TableStyle(t_styles))
                elements.append(cf_table)
            else:
                elements.append(Paragraph("No mucosal examination records recorded.", normal_style))
            elements.append(Spacer(1, 6))

            # Step 7: Lesion Assessment Details (if lesion present)
            if scr.lesion_present and scr.lesion_details:
                elements.append(Paragraph(f"Step 7: Lesion Details ({len(scr.lesion_details)} lesion(s) documented)", h3_style))
                l_rows = [["Location", "Type", "Size (mm)", "Duration", "Symptoms", "Provisional Diagnosis"]]
                for ld in scr.lesion_details:
                    l_rows.append([
                        ld.anatomical_location,
                        ld.lesion_type,
                        ld.size or "-",
                        ld.duration or "-",
                        ld.symptoms or "-",
                        ld.clinical_observations or ld.remarks or "-",
                    ])
                l_table = Table(l_rows, colWidths=[90, 95, 60, 65, 105, 116])
                l_table.setStyle(
                    TableStyle(
                        [
                            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#DC2626")),
                            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                            ("PADDING", (0, 0), (-1, -1), 3.5),
                            ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                        ]
                    )
                )
                elements.append(l_table)
                elements.append(Spacer(1, 6))

            # Step 8: Teeth Examination
            if scr.teeth_examinations:
                elements.append(Paragraph(f"Step 8: Teeth & Periodontal Examination ({len(scr.teeth_examinations)} tooth record(s))", h3_style))
                t_rows = [["Tooth #", "Status", "Surfaces", "Clinical Finding", "Remarks"]]
                for te in scr.teeth_examinations[:16]:
                    surfaces = ", ".join(s.surface_name for s in (te.surfaces or [])) if te.surfaces else "-"
                    status_val = te.status.value if hasattr(te.status, "value") else str(te.status)
                    t_rows.append([
                        te.tooth_number,
                        status_val,
                        surfaces,
                        te.clinical_finding or "-",
                        te.remarks or "-",
                    ])
                t_table = Table(t_rows, colWidths=[55, 90, 100, 140, 146])
                t_table.setStyle(
                    TableStyle(
                        [
                            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#475569")),
                            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                            ("PADDING", (0, 0), (-1, -1), 3.5),
                            ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                        ]
                    )
                )
                elements.append(t_table)
                elements.append(Spacer(1, 6))

            # Step 9: Laboratory Investigations
            if scr.lab_investigation:
                elements.append(Paragraph("Step 9: Laboratory & Diagnostic Investigations", h3_style))
                lab = scr.lab_investigation
                lab_rows = [
                    ["Haemoglobin (Hb)", f"{lab.haemoglobin or '-'} g/dL", "Red Blood Cells (RBC)", f"{lab.rbc or '-'} million/uL"],
                    ["Total Count (TC)", f"{lab.total_count or '-'} cells/uL", "Platelets", f"{lab.platelet_count or '-'} lakh/uL"],
                    ["Biopsy Summary", lab.biopsy_report_summary or "None recorded", "Remarks", lab.remarks or "-"],
                ]
                lab_table = Table(lab_rows, colWidths=[120, 145, 120, 146])
                lab_table.setStyle(
                    TableStyle(
                        [
                            ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
                            ("FONTNAME", (2, 0), (2, -1), "Helvetica-Bold"),
                            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                            ("PADDING", (0, 0), (-1, -1), 3.5),
                            ("FONTSIZE", (0, 0), (-1, -1), 7.5),
                        ]
                    )
                )
                elements.append(lab_table)
                elements.append(Spacer(1, 6))

            # Step 10: Clinical Photography (UPLOADED IMAGES EMBEDDED)
            elements.append(Paragraph("Step 10: Clinical Photography & Uploaded Anatomical Images", h3_style))
            patient_images = scr.patient_images or []
            if patient_images:
                elements.append(
                    Paragraph(
                        f"<b>Attached Photographic Records:</b> {len(patient_images)} anatomical photograph(s) captured and archived.",
                        normal_style,
                    )
                )
                elements.append(Spacer(1, 4))

                photo_cards = []
                for img in patient_images:
                    resolved_file = _resolve_image_file(img.storage_path)
                    rl_thumb = None
                    if resolved_file:
                        rl_thumb = _create_thumbnail_image(resolved_file, max_w=240, max_h=165)

                    ai_status = "Pending AI Review"
                    ai_finding = "Awaiting model evaluation"
                    quality = "Acceptable"
                    if img.ai_analyses:
                        latest_ai = img.ai_analyses[0]
                        ai_status = latest_ai.status.value if hasattr(latest_ai.status, "value") else str(latest_ai.status)
                        quality = latest_ai.image_quality_status.value if hasattr(latest_ai.image_quality_status, "value") else str(latest_ai.image_quality_status)
                        ai_finding = f"Quality: {quality}"

                    sub_text = f" ({img.sub_region})" if img.sub_region else ""
                    date_str = img.capture_date.strftime("%Y-%m-%d %H:%M") if img.capture_date else "-"
                    caption_html = (
                        f"<b>Region:</b> {img.clinical_region}{sub_text}<br/>"
                        f"<b>UID:</b> {img.image_uid} • <b>Date:</b> {date_str}<br/>"
                        f"<b>AI State:</b> {ai_status} ({ai_finding})"
                    )
                    caption_para = Paragraph(caption_html, photo_caption)

                    card_items = []
                    if rl_thumb:
                        card_items.append(rl_thumb)
                    else:
                        missing_placeholder = Paragraph(
                            f"<br/><br/><b>[Photo Attached: {img.original_filename}]</b><br/>"
                            f"Region: {img.clinical_region}{sub_text}<br/><br/>",
                            ParagraphStyle("Ph", parent=photo_caption, alignment=1, textColor=colors.HexColor("#64748B")),
                        )
                        card_items.append(missing_placeholder)
                    card_items.append(Spacer(1, 2))
                    card_items.append(caption_para)

                    # Wrap in single cell card
                    card_table = Table([[card_items]], colWidths=[250])
                    card_table.setStyle(
                        TableStyle(
                            [
                                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F8FAFC")),
                                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                                ("PADDING", (0, 0), (-1, -1), 4),
                                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                            ]
                        )
                    )
                    photo_cards.append(card_table)

                # Arrange photo cards 2 per row
                grid_rows = []
                for i in range(0, len(photo_cards), 2):
                    row = [photo_cards[i]]
                    if i + 1 < len(photo_cards):
                        row.append(photo_cards[i + 1])
                    else:
                        row.append("")  # empty right column
                    grid_rows.append(row)

                outer_grid = Table(grid_rows, colWidths=[260, 260])
                outer_grid.setStyle(
                    TableStyle(
                        [
                            ("VALIGN", (0, 0), (-1, -1), "TOP"),
                            ("PADDING", (0, 0), (-1, -1), 2),
                        ]
                    )
                )
                elements.append(outer_grid)
            else:
                elements.append(
                    Paragraph("No clinical photographic records uploaded for this screening session.", normal_style)
                )
            elements.append(Spacer(1, 8))

            # Step 11: Clinical Consultation Summary, AI Risk Triage & Recommendations
            elements.append(Paragraph("Step 11: Consultation Summary, Clinical Triage & Recommendations", h3_style))
            recom = scr.consultation_summary.clinical_recommendations if (scr.consultation_summary and scr.consultation_summary.clinical_recommendations) else "Standard preventive oral hygiene regimen and periodic annual recall advised."
            reason = scr.consultation_summary.outcome_reason if (scr.consultation_summary and scr.consultation_summary.outcome_reason) else "Routine comprehensive oral mucosal evaluation completed."

            outcome_val = scr.outcome.value.replace("_", " ")
            outcome_color = colors.HexColor("#DC2626") if scr.outcome.value.upper() == "ABNORMAL" else colors.HexColor("#059669")

            final_rows = [
                [
                    Paragraph("<b>Clinical Outcome</b>", bold_label),
                    Paragraph(f"<b>{outcome_val}</b>", ParagraphStyle("OutcomeVal", parent=bold_label, textColor=outcome_color)),
                    Paragraph("<b>Review Status</b>", bold_label),
                    Paragraph(scr.clinician_review_status, table_cell),
                ],
                [
                    Paragraph("<b>Evaluation Findings</b>", bold_label),
                    Paragraph(_clean_pdf_text(reason), table_cell),
                    "",
                    "",
                ],
                [
                    Paragraph("<b>Clinical Advice / Plan</b>", bold_label),
                    Paragraph(_clean_pdf_text(recom), table_cell),
                    "",
                    "",
                ],
                [
                    Paragraph("<b>Referral Center</b>", bold_label),
                    Paragraph("Saveetha Dental College &amp; Hospital, Dept of Oral Medicine", table_cell),
                    Paragraph("<b>Sign-off Examiner</b>", bold_label),
                    Paragraph(worker_name, table_cell),
                ],
            ]
            final_table = Table(final_rows, colWidths=[120, 140, 110, 150])
            final_table.setStyle(
                TableStyle(
                    [
                        ("SPAN", (1, 1), (3, 1)),
                        ("SPAN", (1, 2), (3, 2)),
                        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#F8FAFC")),
                        ("BACKGROUND", (2, 0), (2, 0), colors.HexColor("#F8FAFC")),
                        ("BACKGROUND", (2, 3), (2, 3), colors.HexColor("#F8FAFC")),
                        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                        ("PADDING", (0, 0), (-1, -1), 4),
                        ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ]
                )
            )
            elements.append(final_table)
            elements.append(Spacer(1, 10))

    # 5. Clinician Signature Verification Box & Legal Disclaimer
    sign_data = [
        [
            Paragraph("<b>Examining Clinician:</b> ___________________________<br/>Saveetha Dental College &amp; Hospital", table_cell),
            Paragraph("<b>Verified By / Oral Medicine Specialist:</b> ___________________________<br/>Department of Oral Medicine &amp; Radiology", table_cell),
        ]
    ]
    sign_table = Table(sign_data, colWidths=[260, 271])
    sign_table.setStyle(
        TableStyle(
            [
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#94A3B8")),
                ("PADDING", (0, 0), (-1, -1), 6),
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F8FAFC")),
            ]
        )
    )
    elements.append(sign_table)
    elements.append(Spacer(1, 8))

    elements.append(
        Paragraph(
            "Notice: This clinical document is a confidential medical record issued by Saveetha Dental College &amp; Hospital (SIMATS). "
            "It is intended solely for the designated patient, clinical review, diagnostic referral, and authorized healthcare management.",
            ParagraphStyle("Disclaimer", parent=styles["Normal"], fontName="Helvetica-Oblique", fontSize=7, leading=9, textColor=colors.HexColor("#64748B")),
        )
    )

    doc.build(elements)
    return output.getvalue()
