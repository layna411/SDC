from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_current_user
from app.models.entities import ScreeningRecord, User
from app.schemas.patient_screening import ConsentRecordResponse, ConsentStepInput
from app.services.screening_service import upsert_consent

router = APIRouter(prefix="/consent", tags=["Step 1 - Consent & Location"])


def _get_authorized_screening(db: Session, screening_id: int, user: User) -> ScreeningRecord:
    screening = (
        db.query(ScreeningRecord)
        .filter(ScreeningRecord.id == screening_id, ScreeningRecord.is_deleted.is_(False))
        .first()
    )
    if not screening:
        raise HTTPException(status_code=404, detail="Screening record not found.")
    assert_patient_access(user, screening.college_id, screening.center_id, screening.organizer_user_id)
    return screening


@router.put("/{screening_id}", response_model=ConsentRecordResponse)
def save_consent(
    screening_id: int,
    payload: ConsentStepInput,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = _get_authorized_screening(db, screening_id, current_user)
    record = upsert_consent(db, screening, payload)
    return ConsentRecordResponse.model_validate(record)


@router.get("/{screening_id}", response_model=ConsentRecordResponse)
def get_consent(
    screening_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    screening = _get_authorized_screening(db, screening_id, current_user)
    if not screening.consent_record:
        raise HTTPException(status_code=404, detail="Consent record not yet recorded.")
    return ConsentRecordResponse.model_validate(screening.consent_record)
