from typing import List, Optional

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
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Read-only activity history endpoint scoped strictly to the authenticated user only."""
    query = scope_activities_query(db.query(ActivityLog), current_user)
    if action:
        query = query.filter(ActivityLog.action == action.upper())
    if entity_type:
        query = query.filter(ActivityLog.entity_type == entity_type.upper())

    rows = query.order_by(ActivityLog.created_at.desc()).offset(offset).limit(limit).all()
    return [ActivityLogResponse.model_validate(r) for r in rows]
