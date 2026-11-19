from typing import List

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.ai.analyzer import run_image_ai_pipeline
from app.ai.model_registry import model_registry
from app.core.database import SessionLocal, get_db
from app.core.permissions import assert_patient_access, get_client_ip, get_current_user
from app.models.entities import AIAnalysis, AIReviewRecord, PatientImage, ScreeningRecord, User
from app.models.enums import AIAnalysisStatus, AIReviewStatus
from app.schemas.patient_screening import (
    AIAnalysisResponse,
    AIReviewCreateRequest,
    AIReviewRecordResponse,
)
from app.services.activity_service import log_activity

router = APIRouter(prefix="/ai-analysis", tags=["Step 10 - AI Image Analysis & Clinician Review"])


def _bg_run_analysis(image_id: int, analysis_id: int) -> None:
    db = SessionLocal()
    try:
        img = db.query(PatientImage).filter(PatientImage.id == image_id).first()
        if img:
            run_image_ai_pipeline(db, img, existing_analysis_id=analysis_id)
    finally:
        db.close()


@router.get("/status")
def get_ai_service_status(current_user: User = Depends(get_current_user)):
    model = model_registry.get_active_model()
    return {
        "model_configured": model.is_ready(),
        "model_name": model.model_name,
        "model_version": model.model_version,
        "preprocessing_available": True,
        "notice": (
            "Clinically validated AI model is active for assistive analysis."
            if model.is_ready()
            else "No clinically validated oral-health model is currently configured. OpenCV image validation and quality checks are active; clinical diagnosis requires clinician review."
        ),
    }


@router.post("/analyze/{image_id}", response_model=AIAnalysisResponse, status_code=status.HTTP_201_CREATED)
def request_ai_analysis(
    image_id: int,
    request: Request,
    background_tasks: BackgroundTasks,
    async_mode: bool = False,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    img = db.query(PatientImage).filter(PatientImage.id == image_id).first()
    if not img:
        raise HTTPException(status_code=404, detail="Clinical image not found.")
    screening = img.screening
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)

    log_activity(
        db,
        action="AI_ANALYSIS_REQUESTED",
        entity_type="PATIENT_IMAGE",
        entity_id=img.image_uid,
        user=current_user,
        college_id=screening.college_id,
        center_id=screening.center_id,
        ip_address=get_client_ip(request),
        details={"async_mode": async_mode},
    )

    if async_mode:
        model = model_registry.get_active_model()
        pending = AIAnalysis(
            patient_image_id=img.id,
            screening_id=img.screening_id,
            status=AIAnalysisStatus.PENDING,
            model_name=model.model_name,
            model_version=model.model_version,
            review_status=AIReviewStatus.PENDING_REVIEW,
        )
        db.add(pending)
        db.commit()
        db.refresh(pending)
        background_tasks.add_task(_bg_run_analysis, img.id, pending.id)
        return AIAnalysisResponse.model_validate(pending)

    result = run_image_ai_pipeline(db, img)
    return AIAnalysisResponse.model_validate(result)


@router.get("/screening/{screening_id}", response_model=List[AIAnalysisResponse])
def list_screening_ai_analyses(
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
    rows = (
        db.query(AIAnalysis)
        .filter(AIAnalysis.screening_id == screening.id)
        .order_by(AIAnalysis.analyzed_at.desc())
        .all()
    )
    return [AIAnalysisResponse.model_validate(r) for r in rows]


@router.post("/{analysis_id}/review", response_model=AIReviewRecordResponse, status_code=status.HTTP_201_CREATED)
def review_ai_analysis(
    analysis_id: int,
    payload: AIReviewCreateRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    analysis = db.query(AIAnalysis).filter(AIAnalysis.id == analysis_id).first()
    if not analysis:
        raise HTTPException(status_code=404, detail="AI analysis record not found.")
    screening = db.query(ScreeningRecord).filter(ScreeningRecord.id == analysis.screening_id).first()
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)

    review = AIReviewRecord(
        ai_analysis_id=analysis.id,
        reviewer_user_id=current_user.id,
        review_status=payload.review_status,
        clinician_notes=payload.clinician_notes,
        corrected_findings=payload.corrected_findings,
    )
    analysis.review_status = payload.review_status
    db.add(review)
    db.commit()
    db.refresh(review)

    log_activity(
        db,
        action="AI_RESULT_REVIEWED",
        entity_type="AI_ANALYSIS",
        entity_id=str(analysis.id),
        user=current_user,
        college_id=screening.college_id,
        center_id=screening.center_id,
        ip_address=get_client_ip(request),
        details={"review_status": payload.review_status.value},
    )
    return AIReviewRecordResponse.model_validate(review)
