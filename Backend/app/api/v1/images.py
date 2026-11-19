import json
import secrets
from typing import List, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_client_ip, get_current_user
from app.core.security import utc_now
from app.models.entities import PatientImage, ScreeningRecord, User
from app.schemas.patient_screening import PatientImageResponse
from app.services.activity_service import log_activity
from app.storage.file_storage import file_storage

router = APIRouter(prefix="/images", tags=["Step 10 - Clinical Images"])


@router.post("/upload", response_model=PatientImageResponse, status_code=status.HTTP_201_CREATED)
async def upload_clinical_image(
    request: Request,
    screening_id: int = Form(...),
    clinical_region: str = Form(...),
    sub_region: Optional[str] = Form(default=None),
    metadata_json: Optional[str] = Form(default=None),
    file: UploadFile = File(...),
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

    rel_path, orig_name, mime_type, size_bytes = await file_storage.save_clinical_image(file)

    extra_meta = {}
    if metadata_json:
        try:
            extra_meta = json.loads(metadata_json)
        except json.JSONDecodeError:
            extra_meta = {"raw_note": metadata_json}

    image_rec = PatientImage(
        image_uid=f"OC-IMG-{secrets.token_hex(5).upper()}",
        patient_id=screening.patient_id,
        screening_id=screening.id,
        clinical_region=clinical_region.strip(),
        sub_region=sub_region.strip() if sub_region else None,
        storage_path=rel_path,
        original_filename=orig_name,
        mime_type=mime_type,
        file_size_bytes=size_bytes,
        capture_date=utc_now(),
        uploaded_by_user_id=current_user.id,
        image_metadata=extra_meta,
    )
    screening.current_step = max(screening.current_step, 10)
    db.add(image_rec)
    db.commit()
    db.refresh(image_rec)

    log_activity(
        db,
        action="IMAGE_UPLOADED",
        entity_type="PATIENT_IMAGE",
        entity_id=image_rec.image_uid,
        user=current_user,
        college_id=screening.college_id,
        center_id=screening.center_id,
        ip_address=get_client_ip(request),
        details={"clinical_region": image_rec.clinical_region, "screening_id": screening.id},
    )
    return PatientImageResponse.model_validate(image_rec)


@router.get("/screening/{screening_id}", response_model=List[PatientImageResponse])
def list_screening_images(
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
    return [PatientImageResponse.model_validate(img) for img in screening.patient_images]


@router.get("/{image_id}/content")
def get_secure_image_content(
    image_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Authorized-only image retrieval. Never exposes patient images via public URLs."""
    image_rec = db.query(PatientImage).filter(PatientImage.id == image_id).first()
    if not image_rec:
        raise HTTPException(status_code=404, detail="Clinical image not found.")
    screening = image_rec.screening
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)
    path = file_storage.resolve_path(image_rec.storage_path)
    return FileResponse(path=str(path), media_type=image_rec.mime_type, filename=image_rec.original_filename)
