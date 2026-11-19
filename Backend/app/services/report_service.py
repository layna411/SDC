import csv
from datetime import date, timedelta
import io
from typing import List, Optional

from openpyxl import Workbook
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
from sqlalchemy.orm import Session

from app.core.security import utc_now
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
