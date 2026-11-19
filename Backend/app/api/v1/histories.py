from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_current_user
from app.models.entities import ScreeningRecord, User
from app.schemas.patient_screening import (
    DentalHistoryResponse,
    HabitHistoryResponse,
    HabitItemInput,
    MedicalDentalHistoryInput,
    MedicalHistoryResponse,
    PersonalHistoryInput,
    PersonalHistoryResponse,
)
from app.services.screening_service import (
    replace_habit_histories,
    upsert_medical_dental_history,
    upsert_personal_history,
)

router = APIRouter(prefix="/histories", tags=["Steps 3-5 - Personal, Habit, Medical & Dental History"])


def _get_screening(db: Session, screening_id: int, user: User) -> ScreeningRecord:
    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening:
        raise HTTPException(status_code=404, detail="Screening record not found.")
    assert_patient_access(user, screening.college_id, screening.center_id, screening.organizer_user_id)
    return screening


@router.put("/{screening_id}/personal", response_model=PersonalHistoryResponse)
def save_personal_history(
    screening_id: int,
    payload: PersonalHistoryInput,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = _get_screening(db, screening_id, current_user)
    record = upsert_personal_history(db, screening, payload)
    return PersonalHistoryResponse.model_validate(record)


@router.put("/{screening_id}/habits", response_model=List[HabitHistoryResponse])
def save_habit_history(
    screening_id: int,
    payload: List[HabitItemInput],
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = _get_screening(db, screening_id, current_user)
    rows = replace_habit_histories(db, screening, payload)
    return [HabitHistoryResponse.model_validate(r) for r in rows]


@router.put("/{screening_id}/medical-dental")
def save_medical_dental_history(
    screening_id: int,
    payload: MedicalDentalHistoryInput,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = _get_screening(db, screening_id, current_user)
    med, dent = upsert_medical_dental_history(db, screening, payload)
    return {
        "medical_history": MedicalHistoryResponse.model_validate(med),
        "dental_history": DentalHistoryResponse.model_validate(dent),
    }
