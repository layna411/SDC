from typing import Any, Dict, Optional

from sqlalchemy.orm import Session

from app.models.entities import ActivityLog, User


def log_activity(
    db: Session,
    *,
    action: str,
    entity_type: str,
    entity_id: Optional[str] = None,
    user: Optional[User] = None,
    role: Optional[str] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    ip_address: Optional[str] = None,
    details: Optional[Dict[str, Any]] = None,
) -> ActivityLog:
    """Record an immutable audit event without logging patient PII."""
    entry = ActivityLog(
        user_id=user.id if user else None,
        role=(user.role.value if user else (role or "SYSTEM")),
        college_id=college_id if college_id is not None else (user.college_id if user else None),
        center_id=center_id if center_id is not None else (user.center_id if user else None),
        action=action,
        entity_type=entity_type,
        entity_id=str(entity_id) if entity_id is not None else None,
        ip_address=ip_address,
        details=details or {},
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)
    return entry
