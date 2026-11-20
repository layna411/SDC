from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_client_ip, get_current_user, require_roles
from app.core.security import utc_now
from app.models.entities import Center, College, Feedback, User
from app.models.enums import UserRole
from app.repositories.scope_repository import scope_feedback_query
from app.schemas.operations import (
    FeedbackCreateRequest,
    FeedbackReplyRequest,
    FeedbackResponse,
    FeedbackSummaryResponse,
    FeedbackUpdateRequest,
)
from app.services.activity_service import log_activity

router = APIRouter(prefix="/feedback", tags=["Feedback Management"])


def _enrich_feedback_items(rows: List[Feedback], db: Session) -> List[FeedbackResponse]:
    if not rows:
        return []
    college_ids = {r.college_id for r in rows if r.college_id}
    center_ids = {r.center_id for r in rows if r.center_id}
    replier_ids = {r.replied_by_user_id for r in rows if r.replied_by_user_id}

    colleges_map = (
        {c.id: c.name for c in db.query(College).filter(College.id.in_(college_ids)).all()}
        if college_ids
        else {}
    )
    centers_map = (
        {c.id: c.name for c in db.query(Center).filter(Center.id.in_(center_ids)).all()}
        if center_ids
        else {}
    )
    repliers_map = (
        {
            u.id: (u.full_name or u.username, u.role.value if hasattr(u.role, "value") else str(u.role))
            for u in db.query(User).filter(User.id.in_(replier_ids)).all()
        }
        if replier_ids
        else {}
    )

    results = []
    for r in rows:
        res = FeedbackResponse.model_validate(r)
        res.college_name = colleges_map.get(r.college_id)
        res.center_name = centers_map.get(r.center_id)
        if r.replied_by_user_id and r.replied_by_user_id in repliers_map:
            res.replied_by_name, res.replied_by_role = repliers_map[r.replied_by_user_id]
        results.append(res)
    return results


def _enrich_single_feedback(row: Feedback, db: Session) -> FeedbackResponse:
    res = FeedbackResponse.model_validate(row)
    if row.college_id:
        c = db.query(College).filter(College.id == row.college_id).first()
        res.college_name = c.name if c else None
    if row.center_id:
        ct = db.query(Center).filter(Center.id == row.center_id).first()
        res.center_name = ct.name if ct else None
    if row.replied_by_user_id:
        u = db.query(User).filter(User.id == row.replied_by_user_id).first()
        if u:
            res.replied_by_name = u.full_name or u.username
            res.replied_by_role = u.role.value if hasattr(u.role, "value") else str(u.role)
    return res


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
        items=_enrich_feedback_items(rows, db),
        total_count=len(rows),
        average_rating=avg,
    )


@router.post("/{feedback_id}/reply", response_model=FeedbackResponse)
def reply_to_feedback(
    feedback_id: int,
    payload: FeedbackReplyRequest,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = db.query(Feedback).filter(Feedback.id == feedback_id, Feedback.is_deleted.is_(False)).first()
    if not row:
        raise HTTPException(status_code=404, detail="Feedback not found.")

    # Permissions:
    # Super Admin can reply to ANY feedback.
    # College Admin can reply ONLY to feedback belonging to their college.
    if current_user.role == UserRole.COLLEGE_ADMIN:
        if row.college_id != current_user.college_id:
            raise HTTPException(
                status_code=403,
                detail="Not permitted: You can only reply to feedback assigned to your dental college.",
            )

    row.admin_reply = payload.reply.strip()
    row.replied_by_user_id = current_user.id
    row.replied_at = utc_now()
    db.commit()
    db.refresh(row)

    log_activity(
        db,
        action="FEEDBACK_REPLIED",
        entity_type="FEEDBACK",
        entity_id=str(row.id),
        user=current_user,
        ip_address=get_client_ip(request),
        details={"college_id": row.college_id, "rating": row.rating},
    )
    return _enrich_single_feedback(row, db)


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
    return _enrich_single_feedback(row, db)


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
    return _enrich_single_feedback(row, db)


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
