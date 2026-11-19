from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_client_ip, get_current_user
from app.core.security import utc_now
from app.models.entities import Feedback, User
from app.models.enums import UserRole
from app.repositories.scope_repository import scope_feedback_query
from app.schemas.operations import (
    FeedbackCreateRequest,
    FeedbackResponse,
    FeedbackSummaryResponse,
    FeedbackUpdateRequest,
)
from app.services.activity_service import log_activity

router = APIRouter(prefix="/feedback", tags=["Feedback Management"])


@router.get("", response_model=FeedbackSummaryResponse)
def list_feedback(
    min_rating: Optional[int] = Query(default=None, ge=1, le=5),
    max_rating: Optional[int] = Query(default=None, ge=1, le=5),
    search: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = scope_feedback_query(db.query(Feedback), current_user)
    if min_rating is not None:
        query = query.filter(Feedback.rating >= min_rating)
    if max_rating is not None:
        query = query.filter(Feedback.rating <= max_rating)
    if search:
        term = f"%{search.strip()}%"
        query = query.filter(
            Feedback.patient_name.ilike(term) | Feedback.message.ilike(term) | Feedback.email.ilike(term)
        )
    rows = query.order_by(Feedback.created_at.desc()).all()
    avg = round(sum(r.rating for r in rows) / len(rows), 2) if rows else 0.0
    return FeedbackSummaryResponse(
        items=[FeedbackResponse.model_validate(r) for r in rows],
        total_count=len(rows),
        average_rating=avg,
    )


@router.post("", response_model=FeedbackResponse, status_code=status.HTTP_201_CREATED)
def create_feedback(
    payload: FeedbackCreateRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    row = Feedback(
        college_id=current_user.college_id,
        center_id=current_user.center_id,
        patient_id=payload.patient_id,
        created_by_user_id=current_user.id,
        patient_name=payload.patient_name.strip(),
        email=payload.email,
        rating=payload.rating,
        message=payload.message.strip(),
    )
    db.add(row)
    db.commit()
    db.refresh(row)

    log_activity(
        db,
        action="FEEDBACK_SUBMITTED",
        entity_type="FEEDBACK",
        entity_id=str(row.id),
        user=current_user,
        ip_address=get_client_ip(request),
        details={"rating": row.rating},
    )
    return FeedbackResponse.model_validate(row)


@router.put("/{feedback_id}", response_model=FeedbackResponse)
def update_feedback(
    feedback_id: int,
    payload: FeedbackUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    row = db.query(Feedback).filter(Feedback.id == feedback_id, Feedback.is_deleted.is_(False)).first()
    if not row:
        raise HTTPException(status_code=404, detail="Feedback not found.")

    if current_user.role != UserRole.SUPER_ADMIN and row.created_by_user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not permitted to edit this feedback entry.")

    for k, v in payload.model_dump(exclude_unset=True).items():
        setattr(row, k, v)
    db.commit()
    db.refresh(row)
    return FeedbackResponse.model_validate(row)


@router.delete("/{feedback_id}")
def delete_feedback(
    feedback_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    row = db.query(Feedback).filter(Feedback.id == feedback_id, Feedback.is_deleted.is_(False)).first()
    if not row:
        raise HTTPException(status_code=404, detail="Feedback not found.")

    allowed = (
        current_user.role == UserRole.SUPER_ADMIN
        or row.created_by_user_id == current_user.id
        or (current_user.role == UserRole.COLLEGE_ADMIN and row.college_id == current_user.college_id)
        or (current_user.role == UserRole.CENTER_ADMIN and row.center_id == current_user.center_id)
    )
    if not allowed:
        raise HTTPException(status_code=403, detail="Not permitted to delete this feedback entry.")

    row.is_deleted = True
    row.deleted_at = utc_now()
    db.commit()
    return {"message": "Feedback deleted."}
