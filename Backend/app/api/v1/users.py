from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_client_ip, get_current_user, require_roles
from app.models.entities import User
from app.models.enums import UserRole
from app.schemas.auth import (
    UserCreateRequest,
    UserCreatedWithCredentialsResponse,
    UserResponse,
    UserStatusUpdateRequest,
)
from app.services.activity_service import log_activity
from app.services.auth_service import create_hierarchical_user

router = APIRouter(prefix="/users", tags=["Users & Account Management"])


@router.get("", response_model=List[UserResponse])
def list_users(
    role: Optional[UserRole] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    current_user: User = Depends(
        require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN, UserRole.CENTER_ADMIN)
    ),
    db: Session = Depends(get_db),
):
    query = db.query(User).filter(User.is_deleted.is_(False))
    if current_user.role == UserRole.COLLEGE_ADMIN:
        query = query.filter(User.college_id == current_user.college_id)
    elif current_user.role == UserRole.CENTER_ADMIN:
        query = query.filter(User.center_id == current_user.center_id)
    else:
        if college_id:
            query = query.filter(User.college_id == college_id)
        if center_id:
            query = query.filter(User.center_id == center_id)

    if role:
        query = query.filter(User.role == role)

    return [UserResponse.model_validate(u) for u in query.order_by(User.created_at.desc()).all()]


@router.post("", response_model=UserCreatedWithCredentialsResponse, status_code=status.HTTP_201_CREATED)
def create_user(
    payload: UserCreateRequest,
    request: Request,
    current_user: User = Depends(
        require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN, UserRole.CENTER_ADMIN)
    ),
    db: Session = Depends(get_db),
):
    user, temp_pwd = create_hierarchical_user(
        db,
        actor=current_user,
        username=payload.username,
        full_name=payload.full_name,
        role=payload.role,
        email=payload.email,
        phone=payload.phone,
        college_id=payload.college_id,
        center_id=payload.center_id,
        temporary_password=payload.temporary_password,
        ip_address=get_client_ip(request),
    )
    return UserCreatedWithCredentialsResponse(
        user=UserResponse.model_validate(user),
        temporary_password=temp_pwd,
    )


@router.patch("/{user_id}/status", response_model=UserResponse)
def update_user_status(
    user_id: int,
    payload: UserStatusUpdateRequest,
    request: Request,
    current_user: User = Depends(
        require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN, UserRole.CENTER_ADMIN)
    ),
    db: Session = Depends(get_db),
):
    target = db.query(User).filter(User.id == user_id, User.is_deleted.is_(False)).first()
    if not target:
        raise HTTPException(status_code=404, detail="User not found.")
    if target.id == current_user.id:
        raise HTTPException(status_code=400, detail="You cannot deactivate your own account.")

    # Hierarchy & organization scope check
    if current_user.role == UserRole.COLLEGE_ADMIN:
        if target.college_id != current_user.college_id or target.role in (
            UserRole.SUPER_ADMIN,
            UserRole.COLLEGE_ADMIN,
        ):
            raise HTTPException(status_code=403, detail="Not permitted to modify this user account.")
    elif current_user.role == UserRole.CENTER_ADMIN:
        if target.center_id != current_user.center_id or target.role != UserRole.HEALTH_ORGANIZER:
            raise HTTPException(status_code=403, detail="Not permitted to modify this user account.")

    target.is_active = payload.is_active
    db.commit()
    db.refresh(target)

    log_activity(
        db,
        action="ACCOUNT_ACTIVATED" if payload.is_active else "ACCOUNT_DEACTIVATED",
        entity_type="USER",
        entity_id=str(target.id),
        user=current_user,
        college_id=target.college_id,
        center_id=target.center_id,
        ip_address=get_client_ip(request),
    )
    return UserResponse.model_validate(target)
