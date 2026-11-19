"""Standalone OralCare AI Microservice (FastAPI + OpenCV + Modular Model Serving)."""

from pathlib import Path
import tempfile

from fastapi import FastAPI, File, Form, UploadFile

from app.ai.model_registry import model_registry
from app.ai.preprocessor import oral_image_preprocessor

ai_app = FastAPI(
    title="OralCare AI Image Analysis Microservice",
    version="1.0.0",
    description="Dedicated OpenCV preprocessing and clinically validated oral-health model serving service.",
)


@ai_app.get("/health")
def ai_health():
    model = model_registry.get_active_model()
    return {
        "status": "healthy",
        "model_configured": model.is_ready(),
        "model_name": model.model_name,
        "model_version": model.model_version,
    }


@ai_app.post("/analyze")
async def analyze_image(
    clinical_region: str = Form(...),
    file: UploadFile = File(...),
):
    data = await file.read()
    suffix = Path(file.filename or "image.jpg").suffix or ".jpg"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(data)
        tmp_path = Path(tmp.name)

    try:
        prep = oral_image_preprocessor.process(tmp_path)
        model = model_registry.get_active_model()
        if not prep.valid or prep.normalized_tensor is None:
            return {
                "status": "FAILED",
                "model_name": model.model_name,
                "model_version": model.model_version,
                "image_quality_status": prep.quality_status.value,
                "quality_metrics": prep.quality_metrics,
                "model_output": {"error": prep.error_message},
                "confidence_score": None,
            }

        inf = model.predict(prep.normalized_tensor, clinical_region)
        return {
            "status": "COMPLETED" if inf.available else "MODEL_UNAVAILABLE",
            "model_name": inf.model_name,
            "model_version": inf.model_version,
            "image_quality_status": prep.quality_status.value,
            "quality_metrics": prep.quality_metrics,
            "model_output": inf.output,
            "confidence_score": inf.confidence_score,
            "message": inf.message,
        }
    finally:
        tmp_path.unlink(missing_ok=True)
