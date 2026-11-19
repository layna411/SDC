from datetime import date
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy import asc, desc, or_
from sqlalchemy.orm import Session, joinedload

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_client_ip, get_current_user
from app.models.entities import Patient, ScreeningRecord, User
from app.models.enums import ScreeningOutcome, SexType, UserRole
from app.repositories.scope_repository import scope_patients_query
from app.schemas.patient_screening import (
    PaginatedPatientsResponse,
    PatientCreateRequest,
    PatientResponse,
    PatientUpdateRequest,
    ScreeningDetailResponse,
)
from app.services.activity_service import log_activity
from app.services.screening_service import create_patient_record

router = APIRouter(prefix="/patients", tags=["Patient Management"])


def _enrich_patient(patient: Patient) -> PatientResponse:
    resp = PatientResponse.model_validate(patient)
    screenings = [s for s in (patient.screenings or []) if not s.is_deleted]
    resp.total_screenings = len(screenings)
    if screenings:
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
        enriched = [p for p in enriched if p.latest_outcome == screening_outcome]
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
