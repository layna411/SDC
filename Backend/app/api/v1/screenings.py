from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_client_ip, get_current_user
from app.core.security import utc_now
from app.models.entities import (
    ConsultationSummary,
    FollowUpRecord,
    Patient,
    ReferralRecord,
    ScreeningRecord,
    User,
)
from app.models.enums import ScreeningOutcome, ScreeningWorkflowStatus
from app.repositories.scope_repository import scope_screenings_query
from app.schemas.patient_screening import (
    ClinicianOutcomeReviewRequest,
    CompleteScreeningBundleRequest,
    FollowUpCreateRequest,
    FollowUpResponse,
    FollowUpUpdateRequest,
    ReferralCreateRequest,
    ReferralResponse,
    ScreeningDetailResponse,
    ScreeningStartRequest,
    ScreeningSubmitRequest,
)
from app.services.activity_service import log_activity
from app.services.screening_service import (
    create_complete_screening_bundle,
    generate_screening_uid,
    submit_screening,
)

router = APIRouter(prefix="/screenings", tags=["Step 11 - Screenings, Consultation & Follow-Up"])


@router.get("", response_model=List[ScreeningDetailResponse])
def list_screenings(
    outcome: Optional[ScreeningOutcome] = None,
    lesion_present: Optional[bool] = None,
    workflow_status: Optional[ScreeningWorkflowStatus] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = scope_screenings_query(db.query(ScreeningRecord), current_user)
    if outcome:
        query = query.filter(ScreeningRecord.outcome == outcome)
    if lesion_present is not None:
        query = query.filter(ScreeningRecord.lesion_present == lesion_present)
    if workflow_status:
        query = query.filter(ScreeningRecord.workflow_status == workflow_status)
    rows = query.order_by(ScreeningRecord.created_at.desc()).limit(100).all()
    return [ScreeningDetailResponse.model_validate(s) for s in rows]


@router.post("/start", response_model=ScreeningDetailResponse, status_code=status.HTTP_201_CREATED)
def start_screening(
    payload: ScreeningStartRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if payload.idempotency_key:
        existing = (
            db.query(ScreeningRecord)
            .filter(ScreeningRecord.idempotency_key == payload.idempotency_key)
            .first()
        )
        if existing:
            return ScreeningDetailResponse.model_validate(existing)

    patient = db.query(Patient).filter(Patient.id == payload.patient_id, Patient.is_deleted.is_(False)).first()
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
    assert_patient_access(current_user, patient.college_id, patient.center_id, patient.assigned_organizer_id)

    screening = ScreeningRecord(
        screening_uid=generate_screening_uid(),
        idempotency_key=payload.idempotency_key,
        patient_id=patient.id,
        college_id=patient.college_id,
        center_id=patient.center_id,
        organizer_user_id=current_user.id,
        screening_date=payload.screening_date,
        workflow_status=ScreeningWorkflowStatus.DRAFT,
        current_step=1,
    )
    db.add(screening)
    db.commit()
    db.refresh(screening)
    return ScreeningDetailResponse.model_validate(screening)


@router.post("/complete-bundle", response_model=ScreeningDetailResponse, status_code=status.HTTP_201_CREATED)
def submit_complete_screening_bundle(
    payload: CompleteScreeningBundleRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Atomic 11-step submission supporting offline draft synchronization with idempotency protection."""
    screening = create_complete_screening_bundle(
        db, current_user, payload, ip_address=get_client_ip(request)
    )
    return ScreeningDetailResponse.model_validate(screening)


@router.get("/{screening_id}", response_model=ScreeningDetailResponse)
def get_screening_detail(
    screening_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening:
        raise HTTPException(status_code=404, detail="Screening record not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)
    return ScreeningDetailResponse.model_validate(screening)


@router.post("/{screening_id}/submit", response_model=ScreeningDetailResponse)
def finalize_screening(
    screening_id: int,
    payload: ScreeningSubmitRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening:
        raise HTTPException(status_code=404, detail="Screening record not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)
    updated = submit_screening(
        db,
        screening,
        current_user,
        clinical_recommendations=payload.clinical_recommendations,
        ip_address=get_client_ip(request),
    )
    return ScreeningDetailResponse.model_validate(updated)


@router.post("/{screening_id}/finalize-with-ai", response_model=ScreeningDetailResponse)
def finalize_screening_with_ai(
    screening_id: int,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Runs the AI image analysis pipeline across all uploaded clinical images for this screening,
    then re-evaluates and persists the unified NORMAL / ABNORMAL clinical + AI screening result.
    """
    from app.ai.analyzer import run_image_ai_pipeline

    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening:
        raise HTTPException(status_code=404, detail="Screening record not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)

    for img in screening.patient_images:
        if not img.ai_analyses:
            run_image_ai_pipeline(db, img)

    db.refresh(screening)
    updated = submit_screening(
        db,
        screening,
        current_user,
        clinical_recommendations=(
            screening.consultation_summary.clinical_recommendations
            if screening.consultation_summary
            else None
        ),
        ip_address=get_client_ip(request),
    )
    return ScreeningDetailResponse.model_validate(updated)


@router.post("/{screening_id}/clinician-review", response_model=ScreeningDetailResponse)
def record_clinician_review(
    screening_id: int,
    payload: ClinicianOutcomeReviewRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening:
        raise HTTPException(status_code=404, detail="Screening record not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)

    screening.outcome = payload.outcome
    screening.clinician_review_status = payload.clinician_review_status
    screening.workflow_status = ScreeningWorkflowStatus.REVIEWED
    screening.reviewed_by_user_id = current_user.id
    screening.reviewed_at = utc_now()

    summary = (
        db.query(ConsultationSummary)
        .filter(ConsultationSummary.screening_id == screening.id)
        .first()
    )
    if summary:
        summary.outcome = payload.outcome
        if payload.outcome_reason:
            summary.outcome_reason = payload.outcome_reason
        if payload.clinical_recommendations:
            summary.clinical_recommendations = payload.clinical_recommendations

    db.commit()
    db.refresh(screening)

    log_activity(
        db,
        action="SCREENING_CLINICIAN_REVIEWED",
        entity_type="SCREENING",
        entity_id=screening.screening_uid,
        user=current_user,
        college_id=screening.college_id,
        center_id=screening.center_id,
        ip_address=get_client_ip(request),
        details={"outcome": payload.outcome.value},
    )
    return ScreeningDetailResponse.model_validate(screening)


@router.post("/{screening_id}/follow-ups", response_model=FollowUpResponse, status_code=status.HTTP_201_CREATED)
def add_follow_up(
    screening_id: int,
    payload: FollowUpCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = db.query(ScreeningRecord).filter(ScreeningRecord.id == screening_id).first()
    if not screening:
        raise HTTPException(status_code=404, detail="Screening not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)

    rec = FollowUpRecord(
        patient_id=screening.patient_id,
        screening_id=screening.id,
        scheduled_date=payload.scheduled_date,
        notes=payload.notes,
        created_by_user_id=current_user.id,
    )
    db.add(rec)
    db.commit()
    db.refresh(rec)
    return FollowUpResponse.model_validate(rec)


@router.patch("/follow-ups/{follow_up_id}", response_model=FollowUpResponse)
def update_follow_up(
    follow_up_id: int,
    payload: FollowUpUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    rec = db.query(FollowUpRecord).filter(FollowUpRecord.id == follow_up_id).first()
    if not rec:
        raise HTTPException(status_code=404, detail="Follow-up record not found.")
    screening = rec.screening
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)
    rec.status = payload.status
    if payload.notes is not None:
        rec.notes = payload.notes
    db.commit()
    db.refresh(rec)
    return FollowUpResponse.model_validate(rec)


@router.post("/{screening_id}/referrals", response_model=ReferralResponse, status_code=status.HTTP_201_CREATED)
def add_referral(
    screening_id: int,
    payload: ReferralCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = db.query(ScreeningRecord).filter(ScreeningRecord.id == screening_id).first()
    if not screening:
        raise HTTPException(status_code=404, detail="Screening not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)

    rec = ReferralRecord(
        patient_id=screening.patient_id,
        screening_id=screening.id,
        referred_to=payload.referred_to,
        department=payload.department,
        urgency=payload.urgency,
        reason=payload.reason,
        created_by_user_id=current_user.id,
    )
    db.add(rec)
    db.commit()
    db.refresh(rec)
    return ReferralResponse.model_validate(rec)
