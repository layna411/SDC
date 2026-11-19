from pathlib import Path
from typing import Optional

from sqlalchemy.orm import Session

from app.ai.model_registry import model_registry
from app.ai.preprocessor import oral_image_preprocessor
from app.core.security import utc_now
from app.models.entities import AIAnalysis, PatientImage
from app.models.enums import AIAnalysisStatus, AIReviewStatus
from app.storage.file_storage import file_storage


def run_image_ai_pipeline(
    db: Session,
    patient_image: PatientImage,
    existing_analysis_id: Optional[int] = None,
) -> AIAnalysis:
    """
    Executes the clinical image AI pipeline:
    1. Locate and validate authorized clinical image
    2. Run OpenCV image quality assessment and preprocessing
    3. Invoke configured & validated model (if available) without ever fabricating results
    4. Persist model version, quality status, and assistive output for clinician review
    """
    image_path: Path = file_storage.resolve_path(patient_image.storage_path)
    prep = oral_image_preprocessor.process(image_path)

    if prep.valid:
        patient_image.width_px = prep.width
        patient_image.height_px = prep.height

    active_model = model_registry.get_active_model()

    if existing_analysis_id:
        analysis = db.query(AIAnalysis).filter(AIAnalysis.id == existing_analysis_id).first()
    else:
        analysis = None

    if analysis is None:
        analysis = AIAnalysis(
            patient_image_id=patient_image.id,
            screening_id=patient_image.screening_id,
            model_name=active_model.model_name,
            model_version=active_model.model_version,
            review_status=AIReviewStatus.PENDING_REVIEW,
        )
        db.add(analysis)

    analysis.model_name = active_model.model_name
    analysis.model_version = active_model.model_version
    analysis.image_quality_status = prep.quality_status
    analysis.quality_metrics = prep.quality_metrics
    analysis.analyzed_at = utc_now()

    if not prep.valid or prep.normalized_tensor is None:
        analysis.status = AIAnalysisStatus.FAILED
        analysis.model_output = {
            "service_status": "FAILED",
            "error": prep.error_message or "Image validation failed.",
        }
        analysis.confidence_score = None
    else:
        inference = active_model.predict(
            normalized_image=prep.normalized_tensor,
            clinical_region=patient_image.clinical_region,
        )
        analysis.status = (
            AIAnalysisStatus.COMPLETED
            if inference.available
            else AIAnalysisStatus.MODEL_UNAVAILABLE
        )
        analysis.model_output = inference.output
        analysis.confidence_score = inference.confidence_score

    db.commit()
    db.refresh(analysis)
    return analysis
