from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_current_user
from app.models.entities import ScreeningRecord, User
from app.schemas.patient_screening import ToothExaminationInput, ToothExaminationResponse
from app.services.screening_service import replace_teeth_examinations
from app.utils.clinical_constants import FDI_PERMANENT_TEETH, FDI_PRIMARY_TEETH

router = APIRouter(prefix="/teeth", tags=["Step 8 - Teeth Examination"])


@router.get("/chart-schema")
def get_teeth_chart_schema(current_user: User = Depends(get_current_user)):
    return {
        "numbering_system": "FDI World Dental Federation Notation",
        "permanent": FDI_PERMANENT_TEETH,
        "primary": FDI_PRIMARY_TEETH,
        "surfaces": ["M", "D", "O", "B", "L", "I"],
        "statuses": [
            "HEALTHY",
            "CARIES",
            "FILLED",
            "MISSING",
            "CROWN",
            "ROOT_STUMP",
            "FRACTURED",
            "MOBILE",
            "IMPACTED",
            "PROSTHESIS",
        ],
    }


@router.put("/{screening_id}", response_model=List[ToothExaminationResponse])
def save_teeth_examination(
    screening_id: int,
    payload: List[ToothExaminationInput],
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
    rows = replace_teeth_examinations(db, screening, payload)
    return [ToothExaminationResponse.model_validate(r) for r in rows]


@router.get("/{screening_id}", response_model=List[ToothExaminationResponse])
def get_teeth_examination(
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
    return [ToothExaminationResponse.model_validate(r) for r in screening.teeth_examinations]
