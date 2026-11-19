from datetime import timedelta, timezone
import secrets
from typing import Optional, Tuple

from fastapi import HTTPException, status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.permissions import assert_can_create_role
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    generate_temporary_password,
    hash_password,
    hash_token,
    utc_now,
    verify_password,
)
from app.models.entities import Center, College, PasswordResetToken, RefreshToken, User
from app.models.enums import UserRole
from app.services.activity_service import log_activity


def _normalize_dt(dt):
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


def authenticate_user(
    db: Session, username: str, password: str, ip_address: str = "unknown"
) -> Tuple[User, str, str]:
    user = (
        db.query(User)
        .filter(
            or_(User.username == username.strip(), User.email == username.strip(), User.user_uid == username.strip()),
            User.is_deleted.is_(False),
        )
        .first()
    )

    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password.",
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is deactivated. Contact your administrator.",
        )

    now = utc_now()
    locked_until = _normalize_dt(user.locked_until)
    if locked_until and locked_until > now:
        remaining = int((locked_until - now).total_seconds() // 60) + 1
        raise HTTPException(
            status_code=status.HTTP_423_LOCKED,
            detail=f"Account temporarily locked due to failed logins. Try again in {remaining} minute(s).",
        )

    if not verify_password(password, user.password_hash):
        user.failed_login_attempts += 1
        if user.failed_login_attempts >= settings.MAX_FAILED_LOGIN_ATTEMPTS:
            user.locked_until = now + timedelta(minutes=settings.ACCOUNT_LOCKOUT_MINUTES)
        db.commit()
        log_activity(
            db,
            action="LOGIN_FAILED",
            entity_type="USER",
            entity_id=str(user.id),
            user=user,
            ip_address=ip_address,
            details={"attempts": user.failed_login_attempts},
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password.",
        )

    # Reset lockout state on successful login
    user.failed_login_attempts = 0
    user.locked_until = None
    user.last_login_at = now

    access_token = create_access_token(
        subject=str(user.id),
        role=user.role.value,
        college_id=user.college_id,
        center_id=user.center_id,
    )
    refresh_token, family, expires_at = create_refresh_token(subject=str(user.id))

    db.add(
        RefreshToken(
            user_id=user.id,
            token_hash=hash_token(refresh_token),
            token_family=family,
            expires_at=expires_at,
        )
    )
    db.commit()
    db.refresh(user)

    log_activity(
        db,
        action="LOGIN",
        entity_type="USER",
        entity_id=str(user.id),
        user=user,
        ip_address=ip_address,
    )
    return user, access_token, refresh_token


def rotate_refresh_token(
    db: Session, refresh_token_raw: str, ip_address: str = "unknown"
) -> Tuple[User, str, str]:
    payload = decode_token(refresh_token_raw)
    if not payload or payload.get("type") != "refresh":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid refresh token.",
        )

    token_digest = hash_token(refresh_token_raw)
    stored = db.query(RefreshToken).filter(RefreshToken.token_hash == token_digest).first()
    if not stored:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token not recognized.",
        )

    now = utc_now()
    if stored.is_revoked:
        # Refresh token reuse detected — revoke entire family immediately
        db.query(RefreshToken).filter(
            RefreshToken.token_family == stored.token_family
        ).update({"is_revoked": True, "revoked_at": now})
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token reuse detected. Please log in again.",
        )

    if _normalize_dt(stored.expires_at) <= now:
        stored.is_revoked = True
        stored.revoked_at = now
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token expired. Please log in again.",
        )

    user = db.query(User).filter(User.id == stored.user_id, User.is_deleted.is_(False)).first()
    if not user or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User account is inactive.",
        )

    # Rotate token
    stored.is_revoked = True
    stored.revoked_at = now

    new_access = create_access_token(
        subject=str(user.id),
        role=user.role.value,
        college_id=user.college_id,
        center_id=user.center_id,
    )
    new_refresh, family, expires_at = create_refresh_token(
        subject=str(user.id), token_family=stored.token_family
    )
    db.add(
        RefreshToken(
            user_id=user.id,
            token_hash=hash_token(new_refresh),
            token_family=family,
            expires_at=expires_at,
        )
    )
    db.commit()

    return user, new_access, new_refresh


def logout_user(
    db: Session, user: User, refresh_token_raw: Optional[str], ip_address: str = "unknown"
) -> None:
    now = utc_now()
    if refresh_token_raw:
        digest = hash_token(refresh_token_raw)
        stored = (
            db.query(RefreshToken)
            .filter(RefreshToken.token_hash == digest, RefreshToken.user_id == user.id)
            .first()
        )
        if stored:
            db.query(RefreshToken).filter(
                RefreshToken.token_family == stored.token_family
            ).update({"is_revoked": True, "revoked_at": now})
    else:
        db.query(RefreshToken).filter(
            RefreshToken.user_id == user.id, RefreshToken.is_revoked.is_(False)
        ).update({"is_revoked": True, "revoked_at": now})

    db.commit()
    log_activity(
        db,
        action="LOGOUT",
        entity_type="USER",
        entity_id=str(user.id),
        user=user,
        ip_address=ip_address,
    )


def change_user_password(
    db: Session,
    user: User,
    current_password: str,
    new_password: str,
    ip_address: str = "unknown",
) -> None:
    if not verify_password(current_password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Current password is incorrect.",
        )
    if current_password == new_password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="New password must be different from the current password.",
        )

    user.password_hash = hash_password(new_password)
    user.must_change_password = False
    # Revoke active refresh tokens on password change
    now = utc_now()
    db.query(RefreshToken).filter(
        RefreshToken.user_id == user.id, RefreshToken.is_revoked.is_(False)
    ).update({"is_revoked": True, "revoked_at": now})
    db.commit()

    log_activity(
        db,
        action="PASSWORD_CHANGED",
        entity_type="USER",
        entity_id=str(user.id),
        user=user,
        ip_address=ip_address,
    )


def initiate_forgot_password(
    db: Session, username_or_email: str, ip_address: str = "unknown"
) -> Optional[str]:
    user = (
        db.query(User)
        .filter(
            or_(User.username == username_or_email.strip(), User.email == username_or_email.strip()),
            User.is_deleted.is_(False),
            User.is_active.is_(True),
        )
        .first()
    )
    if not user:
        return None

    raw_token = secrets.token_urlsafe(32)
    db.add(
        PasswordResetToken(
            user_id=user.id,
            token_hash=hash_token(raw_token),
            expires_at=utc_now() + timedelta(minutes=30),
        )
    )
    db.commit()
    log_activity(
        db,
        action="PASSWORD_RESET_REQUESTED",
        entity_type="USER",
        entity_id=str(user.id),
        user=user,
        ip_address=ip_address,
    )
    return raw_token


def complete_password_reset(
    db: Session, reset_token: str, new_password: str, ip_address: str = "unknown"
) -> None:
    record = (
        db.query(PasswordResetToken)
        .filter(
            PasswordResetToken.token_hash == hash_token(reset_token),
            PasswordResetToken.is_used.is_(False),
        )
        .first()
    )
    if not record or _normalize_dt(record.expires_at) <= utc_now():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired password reset token.",
        )

    user = db.query(User).filter(User.id == record.user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found.")

    user.password_hash = hash_password(new_password)
    user.must_change_password = False
    user.failed_login_attempts = 0
    user.locked_until = None
    record.is_used = True
    db.commit()

    log_activity(
        db,
        action="PASSWORD_RESET_COMPLETED",
        entity_type="USER",
        entity_id=str(user.id),
        user=user,
        ip_address=ip_address,
    )


def create_hierarchical_user(
    db: Session,
    *,
    actor: User,
    username: str,
    full_name: str,
    role: UserRole,
    email: Optional[str] = None,
    phone: Optional[str] = None,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    temporary_password: Optional[str] = None,
    ip_address: str = "unknown",
) -> Tuple[User, str]:
    """Create a child user account enforcing strict parent-child hierarchy & org isolation."""
    assert_can_create_role(actor, role)

    if db.query(User).filter(User.username == username.strip()).first():
        raise HTTPException(status_code=409, detail="Username is already taken.")
    if email and db.query(User).filter(User.email == email.strip()).first():
        raise HTTPException(status_code=409, detail="Email address is already registered.")

    # Resolve organizational bindings securely from actor scope
    resolved_college_id = college_id
    resolved_center_id = center_id

    if actor.role == UserRole.COLLEGE_ADMIN:
        resolved_college_id = actor.college_id
    elif actor.role == UserRole.CENTER_ADMIN:
        resolved_college_id = actor.college_id
        resolved_center_id = actor.center_id

    if role == UserRole.COLLEGE_ADMIN:
        if not resolved_college_id:
            raise HTTPException(status_code=400, detail="college_id is required for College Admin.")
        college = db.query(College).filter(College.id == resolved_college_id, College.is_deleted.is_(False)).first()
        if not college:
            raise HTTPException(status_code=404, detail="Assigned dental college not found.")
        resolved_center_id = None

    elif role in (UserRole.CENTER_ADMIN, UserRole.HEALTH_ORGANIZER):
        if not resolved_center_id:
            raise HTTPException(status_code=400, detail=f"center_id is required for {role.value}.")
        center = db.query(Center).filter(Center.id == resolved_center_id, Center.is_deleted.is_(False)).first()
        if not center:
            raise HTTPException(status_code=404, detail="Assigned center not found.")
        if actor.role == UserRole.COLLEGE_ADMIN and center.college_id != actor.college_id:
            raise HTTPException(status_code=403, detail="Cannot create user for a center outside your college.")
        resolved_college_id = center.college_id

    temp_pwd = temporary_password or generate_temporary_password()
    uid_prefix = {
        UserRole.SUPER_ADMIN: "SA",
        UserRole.COLLEGE_ADMIN: "CA",
        UserRole.CENTER_ADMIN: "CT",
        UserRole.HEALTH_ORGANIZER: "HO",
    }[role]
    user_uid = f"OC-{uid_prefix}-{secrets.token_hex(4).upper()}"

    user = User(
        user_uid=user_uid,
        username=username.strip(),
        email=email.strip() if email else None,
        full_name=full_name.strip(),
        phone=phone.strip() if phone else None,
        password_hash=hash_password(temp_pwd),
        role=role,
        college_id=resolved_college_id,
        center_id=resolved_center_id,
        created_by_user_id=actor.id,
        is_active=True,
        must_change_password=True,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    log_activity(
        db,
        action="USER_CREATED",
        entity_type="USER",
        entity_id=str(user.id),
        user=actor,
        college_id=resolved_college_id,
        center_id=resolved_center_id,
        ip_address=ip_address,
        details={"created_role": role.value, "user_uid": user.user_uid},
    )
    return user, temp_pwd
