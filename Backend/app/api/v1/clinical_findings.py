from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_patient_access, get_current_user
from app.models.entities import ScreeningRecord, User
from app.schemas.patient_screening import ClinicalFindingInput, ClinicalFindingResponse
from app.services.screening_service import replace_clinical_findings
from app.utils.clinical_constants import ANATOMICAL_REGIONS, APPROVED_CLINICAL_OBSERVATIONS

router = APIRouter(prefix="/clinical-findings", tags=["Step 6 - Clinical Findings"])


@router.get("/taxonomy")
def get_clinical_taxonomy(current_user: User = Depends(get_current_user)):
    """Return approved 11 anatomical regions, sub-regions, and configurable clinical observation options."""
    return {
        "anatomical_regions": ANATOMICAL_REGIONS,
        "approved_observations": APPROVED_CLINICAL_OBSERVATIONS,
    }


@router.put("/{screening_id}", response_model=List[ClinicalFindingResponse])
def save_clinical_findings(
    screening_id: int,
    payload: List[ClinicalFindingInput],
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

    rows = replace_clinical_findings(db, screening, payload)
    return [ClinicalFindingResponse.model_validate(r) for r in rows]


@router.get("/{screening_id}", response_model=List[ClinicalFindingResponse])
def get_clinical_findings(
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
    return [ClinicalFindingResponse.model_validate(r) for r in screening.clinical_findings]
