from typing import Dict, List, Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_current_user
from app.models.entities import ActivityLog, User
from app.repositories.scope_repository import scope_activities_query
from app.schemas.operations import ActivityLogResponse

router = APIRouter(prefix="/activities", tags=["Activity & Audit Logs"])


@router.get("", response_model=List[ActivityLogResponse])
def list_activity_logs(
    action: Optional[str] = None,
    entity_type: Optional[str] = None,
    user_id: Optional[int] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Activity history endpoint scoped to role hierarchy with optional entity filters."""
    query = scope_activities_query(db.query(ActivityLog), current_user)
    if action:
        query = query.filter(ActivityLog.action == action.upper())
    if entity_type:
        query = query.filter(ActivityLog.entity_type == entity_type.upper())
    if user_id:
        query = query.filter(ActivityLog.user_id == user_id)
    if college_id:
        query = query.filter(ActivityLog.college_id == college_id)
    if center_id:
        query = query.filter(ActivityLog.center_id == center_id)

    rows = query.order_by(ActivityLog.created_at.desc()).offset(offset).limit(limit).all()
    user_ids = {r.user_id for r in rows if r.user_id is not None}
    users_by_id: Dict[int, User] = {}
    if user_ids:
        users = db.query(User).filter(User.id.in_(user_ids)).all()
        users_by_id = {u.id: u for u in users}

    result: List[ActivityLogResponse] = []
    for r in rows:
        res = ActivityLogResponse.model_validate(r)
        u = users_by_id.get(r.user_id) if r.user_id else None
        if u:
            res.username = u.username
            res.user_full_name = u.full_name
        result.append(res)
    return result
