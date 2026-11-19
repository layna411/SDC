from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_current_user
from app.models.entities import ScreeningRecord, User
from app.schemas.patient_screening import LesionBatchInput, LesionDetailResponse
from app.services.screening_service import replace_lesion_details

router = APIRouter(prefix="/lesions", tags=["Step 7 - Lesion Details"])


@router.put("/{screening_id}", response_model=List[LesionDetailResponse])
def save_lesions(
    screening_id: int,
    payload: LesionBatchInput,
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
    rows = replace_lesion_details(db, screening, payload)
    return [LesionDetailResponse.model_validate(r) for r in rows]


@router.get("/{screening_id}", response_model=List[LesionDetailResponse])
def get_lesions(
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
    return [LesionDetailResponse.model_validate(r) for r in screening.lesion_details]
