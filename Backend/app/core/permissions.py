from enum import Enum
from typing import Callable, Iterable, Optional

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import decode_token
from app.models.entities import User, UserRole

bearer_scheme = HTTPBearer(auto_error=False)


ROLE_HIERARCHY_CREATION = {
    UserRole.SUPER_ADMIN: {
        UserRole.COLLEGE_ADMIN,
        UserRole.CENTER_ADMIN,
        UserRole.HEALTH_ORGANIZER,
    },
    UserRole.COLLEGE_ADMIN: {
        UserRole.CENTER_ADMIN,
        UserRole.HEALTH_ORGANIZER,
    },
    UserRole.CENTER_ADMIN: {
        UserRole.HEALTH_ORGANIZER,
    },
    UserRole.HEALTH_ORGANIZER: set(),
}


def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication credentials were not provided.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    payload = decode_token(credentials.credentials)
    if not payload or payload.get("type") != "access":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired access token.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    user_id_str = payload.get("sub")
    if not user_id_str:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload.",
        )

    user = db.query(User).filter(User.id == int(user_id_str), User.is_deleted.is_(False)).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User account not found.",
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User account is deactivated. Contact your administrator.",
        )

    return user


def require_roles(*allowed_roles: UserRole) -> Callable:
    allowed = set(allowed_roles)

    def _dependency(current_user: User = Depends(get_current_user)) -> User:
        # Never trust client-supplied roles: strictly enforce DB-persisted user role.
        if current_user.role not in allowed:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Insufficient role permissions for this operation.",
            )
        return current_user

    return _dependency


def assert_can_create_role(actor: User, target_role: UserRole) -> None:
    allowed_targets = ROLE_HIERARCHY_CREATION.get(actor.role, set())
    if target_role not in allowed_targets:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Role {actor.role.value} is not permitted to create {target_role.value} accounts.",
        )


def assert_college_access(user: User, college_id: int) -> None:
    if user.role == UserRole.SUPER_ADMIN:
        return
    if user.college_id != college_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: outside assigned college scope.",
        )


def assert_center_access(user: User, college_id: int, center_id: int) -> None:
    if user.role == UserRole.SUPER_ADMIN:
        return
    if user.role == UserRole.COLLEGE_ADMIN:
        if user.college_id != college_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: center belongs to another college.",
            )
        return
    if user.center_id != center_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: outside assigned center scope.",
        )


def assert_patient_access(user: User, college_id: int, center_id: int, organizer_user_id: Optional[int]) -> None:
    if user.role == UserRole.SUPER_ADMIN:
        return
    if user.role == UserRole.COLLEGE_ADMIN:
        if user.college_id != college_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: patient belongs to another college.",
            )
        return
    if user.role == UserRole.CENTER_ADMIN:
        if user.center_id != center_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: patient belongs to another center.",
            )
        return
    # Health Organizer Assistant: must belong to their assigned center AND either be assigned or created by them
    if user.center_id != center_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: patient belongs to another center.",
        )
    if organizer_user_id is not None and organizer_user_id != user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied: patient is assigned to another health organizer.",
        )


def get_client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    if request.client:
        return request.client.host
    return "unknown"
