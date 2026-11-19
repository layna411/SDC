from typing import List

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_current_user, require_roles
from app.models.entities import LabInvestigation, LabReferenceRange, ScreeningRecord, User
from app.models.enums import UserRole
from app.schemas.patient_screening import (
    LabInvestigationInput,
    LabInvestigationResponse,
    LabReferenceRangeInput,
    LabReferenceRangeResponse,
)
from app.services.screening_service import upsert_lab_investigation
from app.storage.file_storage import file_storage

router = APIRouter(prefix="/laboratory", tags=["Step 9 - Lab Investigation"])


@router.get("/reference-ranges", response_model=List[LabReferenceRangeResponse])
def list_reference_ranges(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    rows = db.query(LabReferenceRange).order_by(LabReferenceRange.parameter_key.asc()).all()
    return [LabReferenceRangeResponse.model_validate(r) for r in rows]


@router.put("/reference-ranges", response_model=LabReferenceRangeResponse)
def upsert_reference_range(
    payload: LabReferenceRangeInput,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = (
        db.query(LabReferenceRange)
        .filter(LabReferenceRange.parameter_key == payload.parameter_key)
        .first()
    )
    if not row:
        row = LabReferenceRange(parameter_key=payload.parameter_key)
        db.add(row)
    row.display_name = payload.display_name
    row.unit = payload.unit
    row.min_value = payload.min_value
    row.max_value = payload.max_value
    row.standard_source = payload.standard_source
    row.updated_by_user_id = current_user.id
    db.commit()
    db.refresh(row)
    return LabReferenceRangeResponse.model_validate(row)


@router.put("/{screening_id}", response_model=LabInvestigationResponse)
def save_lab_investigation(
    screening_id: int,
    payload: LabInvestigationInput,
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
    record = upsert_lab_investigation(db, screening, payload)
    return LabInvestigationResponse.model_validate(record)


@router.post("/{screening_id}/biopsy-attachment", response_model=LabInvestigationResponse)
async def upload_biopsy_attachment(
    screening_id: int,
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

    rel_path, orig_name, _mime, _size = await file_storage.save_document(file, subdir="biopsy_reports")
    lab = db.query(LabInvestigation).filter(LabInvestigation.screening_id == screening.id).first()
    if not lab:
        lab = LabInvestigation(screening_id=screening.id)
        db.add(lab)
    lab.biopsy_attachment_path = rel_path
    lab.biopsy_attachment_name = orig_name
    db.commit()
    db.refresh(lab)
    return LabInvestigationResponse.model_validate(lab)


@router.get("/{screening_id}/biopsy-attachment")
def download_biopsy_attachment(
    screening_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening or not screening.lab_investigation or not screening.lab_investigation.biopsy_attachment_path:
        raise HTTPException(status_code=404, detail="Biopsy attachment not found.")
    assert_patient_access(current_user, screening.college_id, screening.center_id, screening.organizer_user_id)
    path = file_storage.resolve_path(screening.lab_investigation.biopsy_attachment_path)
    return FileResponse(path=str(path), filename=screening.lab_investigation.biopsy_attachment_name)
