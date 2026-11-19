from datetime import date, datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response, status
from sqlalchemy import asc, desc, or_
from sqlalchemy.orm import Session, joinedload

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_client_ip, get_current_user
from app.models.entities import (
    ClinicalFinding,
    ConsentRecord,
    DentalHistory,
    HabitHistory,
    LesionDetail,
    MedicalHistory,
    Patient,
    PersonalHistory,
    ScreeningRecord,
    TeethExamination,
    User,
)
from app.models.enums import ScreeningOutcome, ScreeningWorkflowStatus, SexType, UserRole
from app.repositories.scope_repository import scope_patients_query
from app.schemas.patient_screening import (
    PaginatedPatientsResponse,
    PatientCreateRequest,
    PatientResponse,
    PatientUpdateRequest,
    ScreeningDetailResponse,
)
from app.services.activity_service import log_activity
from app.services.report_service import export_patient_report_pdf
from app.services.screening_service import create_patient_record, generate_screening_uid

router = APIRouter(prefix="/patients", tags=["Patient Management"])


def _enrich_patient(patient: Patient) -> PatientResponse:
    resp = PatientResponse.model_validate(patient)
    screenings = [s for s in (patient.screenings or []) if not s.is_deleted]
    resp.total_screenings = len(screenings)
    if screenings:
        # Guarantee strictly sorted by created_at desc, then id desc
        screenings.sort(
            key=lambda s: (s.created_at or datetime.min, s.id or 0),
            reverse=True,
        )
        latest = screenings[0]
        resp.latest_outcome = latest.outcome
        resp.latest_lesion_present = latest.lesion_present
        resp.latest_review_status = latest.clinician_review_status
    return resp


@router.get("", response_model=PaginatedPatientsResponse)
def list_patients(
    search: Optional[str] = None,
    sex: Optional[SexType] = None,
    center_id: Optional[int] = None,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
    screening_outcome: Optional[ScreeningOutcome] = None,
    lesion_present: Optional[bool] = None,
    review_status: Optional[str] = None,
    sort_by: str = Query(default="created_at", pattern="^(created_at|first_name|age|registration_date)$"),
    sort_order: str = Query(default="desc", pattern="^(asc|desc)$"),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = scope_patients_query(
        db.query(Patient).options(joinedload(Patient.screenings)), current_user
    )

    if search:
        term = f"%{search.strip()}%"
        query = query.filter(
            or_(
                Patient.patient_uid.ilike(term),
                Patient.first_name.ilike(term),
                Patient.last_name.ilike(term),
                Patient.phone_number.ilike(term),
            )
        )
    if sex:
        query = query.filter(Patient.sex == sex)
    if center_id and current_user.role in (UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN):
        query = query.filter(Patient.center_id == center_id)
    if start_date:
        query = query.filter(Patient.registration_date >= start_date)
    if end_date:
        query = query.filter(Patient.registration_date <= end_date)

    sort_col = getattr(Patient, sort_by, Patient.created_at)
    query = query.order_by(asc(sort_col) if sort_order == "asc" else desc(sort_col))

    all_matching = query.all()
    enriched = [_enrich_patient(p) for p in all_matching]

    # Filter on latest screening clinical metadata if requested
    if screening_outcome is not None:
        target_outcome_str = screening_outcome.value if hasattr(screening_outcome, "value") else str(screening_outcome)
        enriched = [
            p for p in enriched
            if p.latest_outcome is not None and (
                p.latest_outcome == screening_outcome
                or (hasattr(p.latest_outcome, "value") and p.latest_outcome.value == target_outcome_str)
                or str(p.latest_outcome).upper() == str(target_outcome_str).upper()
            )
        ]
    if lesion_present is not None:
        enriched = [p for p in enriched if p.latest_lesion_present == lesion_present]
    if review_status:
        enriched = [
            p for p in enriched if (p.latest_review_status or "").upper() == review_status.upper()
        ]

    total = len(enriched)
    start = (page - 1) * page_size
    items = enriched[start : start + page_size]

    return PaginatedPatientsResponse(items=items, total=total, page=page, page_size=page_size)


@router.post("", response_model=PatientResponse, status_code=status.HTTP_201_CREATED)
def create_patient(
    payload: PatientCreateRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    patient = create_patient_record(db, current_user, payload, ip_address=get_client_ip(request))
    return _enrich_patient(patient)


@router.get("/{patient_id}", response_model=PatientResponse)
def get_patient(
    patient_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    patient = (
        db.query(Patient)
        .options(joinedload(Patient.screenings))
        .filter(Patient.id == patient_id, Patient.is_deleted.is_(False))
        .first()
    )
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
    assert_patient_access(current_user, patient.college_id, patient.center_id, patient.assigned_organizer_id)
    return _enrich_patient(patient)


@router.put("/{patient_id}", response_model=PatientResponse)
def update_patient(
    patient_id: int,
    payload: PatientUpdateRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    patient = db.query(Patient).filter(Patient.id == patient_id, Patient.is_deleted.is_(False)).first()
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
    assert_patient_access(current_user, patient.college_id, patient.center_id, patient.assigned_organizer_id)

    for k, v in payload.model_dump(exclude_unset=True).items():
        setattr(patient, k, v)
    db.commit()
    db.refresh(patient)

    log_activity(
        db,
        action="PATIENT_UPDATED",
        entity_type="PATIENT",
        entity_id=patient.patient_uid,
        user=current_user,
        college_id=patient.college_id,
        center_id=patient.center_id,
        ip_address=get_client_ip(request),
    )
    return _enrich_patient(patient)


@router.get("/{patient_id}/screenings", response_model=List[ScreeningDetailResponse])
def get_patient_screenings(
    patient_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    patient = db.query(Patient).filter(Patient.id == patient_id, Patient.is_deleted.is_(False)).first()
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
    assert_patient_access(current_user, patient.college_id, patient.center_id, patient.assigned_organizer_id)

    screenings = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.patient_id == patient.id, ScreeningRecord.is_deleted.is_(False))
        .order_by(ScreeningRecord.created_at.desc())
        .all()
    )
    return [ScreeningDetailResponse.model_validate(s) for s in screenings]


@router.get("/{patient_id}/report")
def download_patient_report(
    patient_id: int,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    patient = (
        db.query(Patient)
        .options(
            joinedload(Patient.screenings).joinedload(ScreeningRecord.consent_record),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.personal_history),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.habit_histories),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.medical_history),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.dental_history),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.clinical_findings),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.lesion_details),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.teeth_examinations),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.lab_investigation),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.patient_images),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.consultation_summary),
        )
        .filter(Patient.id == patient_id, Patient.is_deleted.is_(False))
        .first()
    )
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
    assert_patient_access(current_user, patient.college_id, patient.center_id, patient.assigned_organizer_id)

    screenings = [s for s in (patient.screenings or []) if not s.is_deleted]
    pdf_bytes = export_patient_report_pdf(patient, screenings)

    log_activity(
        db,
        action="PATIENT_REPORT_DOWNLOADED",
        entity_type="PATIENT",
        entity_id=patient.patient_uid,
        user=current_user,
        college_id=patient.college_id,
        center_id=patient.center_id,
        ip_address=get_client_ip(request),
        details={"screenings_count": len(screenings)},
    )

    filename = f"Patient_{patient.patient_uid}_Report.pdf"
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename={filename}"},
    )


@router.post("/{patient_id}/re-examine", response_model=ScreeningDetailResponse, status_code=status.HTTP_201_CREATED)
def reexamine_patient(
    patient_id: int,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Initializes a new re-examination screening session for an existing patient.
    Carries forward previous demographic, habit, medical, dental and finding baselines
    so the health worker or clinician can upload new clinical region images only
    and trigger immediate assistive AI evaluation.
    """
    patient = (
        db.query(Patient)
        .options(
            joinedload(Patient.screenings).joinedload(ScreeningRecord.consent_record),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.personal_history),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.habit_histories),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.medical_history),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.dental_history),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.clinical_findings),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.lesion_details),
            joinedload(Patient.screenings).joinedload(ScreeningRecord.teeth_examinations),
        )
        .filter(Patient.id == patient_id, Patient.is_deleted.is_(False))
        .first()
    )
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
    assert_patient_access(current_user, patient.college_id, patient.center_id, patient.assigned_organizer_id)

    screenings = [s for s in (patient.screenings or []) if not s.is_deleted]
    screenings.sort(key=lambda s: (s.created_at or datetime.min, s.id or 0), reverse=True)
    prev = screenings[0] if screenings else None

    screening_uid = generate_screening_uid()
    new_screening = ScreeningRecord(
        screening_uid=screening_uid,
        patient_id=patient.id,
        college_id=patient.college_id,
        center_id=patient.center_id,
        organizer_user_id=current_user.id,
        screening_date=date.today(),
        workflow_status=ScreeningWorkflowStatus.SUBMITTED,
        current_step=11,
        outcome=ScreeningOutcome.PENDING_CLINICAL_REVIEW,
        lesion_present=prev.lesion_present if prev else False,
        clinician_review_status="PENDING_REVIEW",
    )
    db.add(new_screening)
    db.flush()

    if prev:
        if prev.consent_record:
            cr = ConsentRecord(
                screening_id=new_screening.id,
                consent_given=prev.consent_record.consent_given,
                consent_form_signed=prev.consent_record.consent_form_signed,
                patient_signature_name=prev.consent_record.patient_signature_name,
                signed_date=date.today(),
            )
            db.add(cr)
        if prev.personal_history:
            ph = PersonalHistory(
                screening_id=new_screening.id,
                dietary_pattern=prev.personal_history.dietary_pattern,
                brushing_frequency=prev.personal_history.brushing_frequency,
                cleaning_aid=prev.personal_history.cleaning_aid,
                last_dental_visit=prev.personal_history.last_dental_visit,
                symptoms=prev.personal_history.symptoms,
            )
            db.add(ph)
        for h in (prev.habit_histories or []):
            hh = HabitHistory(
                screening_id=new_screening.id,
                habit_category=h.habit_category,
                habit_name=h.habit_name,
                frequency=h.frequency,
                duration_years=h.duration_years,
                notes=h.notes,
            )
            db.add(hh)
        if prev.medical_history:
            mh = MedicalHistory(
                screening_id=new_screening.id,
                systemic_conditions=prev.medical_history.systemic_conditions,
                current_medications=prev.medical_history.current_medications,
                known_allergies=prev.medical_history.known_allergies,
                bleeding_disorders=prev.medical_history.bleeding_disorders,
            )
            db.add(mh)
        if prev.dental_history:
            dh = DentalHistory(
                screening_id=new_screening.id,
                previous_dental_treatment=prev.dental_history.previous_dental_treatment,
                treatment_type=prev.dental_history.treatment_type,
                pain_or_discomfort=prev.dental_history.pain_or_discomfort,
                aesthetic_complaints=prev.dental_history.aesthetic_complaints,
            )
            db.add(dh)
        for cf in (prev.clinical_findings or []):
            ncf = ClinicalFinding(
                screening_id=new_screening.id,
                anatomical_region=cf.anatomical_region,
                status=cf.status,
                color=cf.color,
                texture=cf.texture,
                lesion_suspected=cf.lesion_suspected,
                notes=cf.notes,
            )
            db.add(ncf)
        for ld in (prev.lesion_details or []):
            nld = LesionDetail(
                screening_id=new_screening.id,
                site=ld.site,
                lesion_type=ld.lesion_type,
                size_mm=ld.size_mm,
                color=ld.color,
                duration=ld.duration,
                pain_associated=ld.pain_associated,
                bleeding_on_touch=ld.bleeding_on_touch,
                induration_present=ld.induration_present,
            )
            db.add(nld)
        for te in (prev.teeth_examinations or []):
            nte = TeethExamination(
                screening_id=new_screening.id,
                tooth_number=te.tooth_number,
                condition=te.condition,
                mobility_grade=te.mobility_grade,
                notes=te.notes,
            )
            db.add(nte)
    else:
        cr = ConsentRecord(
            screening_id=new_screening.id,
            consent_given=True,
            consent_form_signed=True,
            patient_signature_name=f"{patient.first_name} {patient.last_name}",
            signed_date=date.today(),
        )
        db.add(cr)

    db.commit()
    db.refresh(new_screening)

    log_activity(
        db,
        action="PATIENT_REEXAMINED",
        entity_type="SCREENING",
        entity_id=new_screening.screening_uid,
        user=current_user,
        college_id=patient.college_id,
        center_id=patient.center_id,
        ip_address=get_client_ip(request),
        details={"patient_uid": patient.patient_uid, "reexamination": True},
    )

    return ScreeningDetailResponse.model_validate(new_screening)
