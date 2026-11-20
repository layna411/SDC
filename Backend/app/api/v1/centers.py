from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_center_access, get_client_ip, get_current_user, require_roles
from app.core.security import generate_temporary_password, hash_password
from app.models.entities import Center, College, User
from app.models.enums import UserRole
from app.repositories.scope_repository import scope_centers_query
from app.schemas.auth import UserResponse
from app.schemas.organization import (
    CenterCreateRequest,
    CenterCreateWithAdminResponse,
    CenterResponse,
    CenterUpdateRequest,
)
from app.services.activity_service import log_activity
from app.services.auth_service import create_hierarchical_user

router = APIRouter(prefix="/centers", tags=["Screening Centers"])


def _enrich_center_response(center: Center, db: Session) -> CenterResponse:
    admin = (
        db.query(User)
        .filter(
            User.center_id == center.id,
            User.role == UserRole.CENTER_ADMIN,
            User.is_deleted.is_(False),
        )
        .first()
    )
    res = CenterResponse.model_validate(center)
    res.admin_username = admin.username if admin else None
    res.admin_user_id = admin.id if admin else None
    res.has_admin = admin is not None
    return res


@router.get("", response_model=List[CenterResponse])
def list_centers(
    college_id: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = scope_centers_query(db.query(Center), current_user)
    if college_id and current_user.role == UserRole.SUPER_ADMIN:
        query = query.filter(Center.college_id == college_id)
    rows = query.order_by(Center.created_at.desc()).all()
    return [_enrich_center_response(c, db) for c in rows]


@router.get("/{center_id}", response_model=CenterResponse)
def get_center(
    center_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    center = db.query(Center).filter(Center.id == center_id, Center.is_deleted.is_(False)).first()
    if not center:
        raise HTTPException(status_code=404, detail="Center not found.")
    assert_center_access(current_user, center.college_id, center.id)
    return _enrich_center_response(center, db)


@router.post("", response_model=CenterCreateWithAdminResponse, status_code=status.HTTP_201_CREATED)
def create_center(
    payload: CenterCreateRequest,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    resolved_college_id = (
        current_user.college_id if current_user.role == UserRole.COLLEGE_ADMIN else payload.college_id
    )
    if not resolved_college_id:
        raise HTTPException(status_code=400, detail="college_id is required.")

    college = db.query(College).filter(College.id == resolved_college_id, College.is_deleted.is_(False)).first()
    if not college:
        raise HTTPException(status_code=404, detail="Parent dental college not found.")

    clean_code = payload.center_code.strip().upper()
    existing_center = db.query(Center).filter(Center.center_code == clean_code).first()
    if existing_center:
        raise HTTPException(status_code=409, detail=f"Center code '{clean_code}' already exists.")

    # Determine admin username and pre-validate uniqueness before touching DB
    target_admin_username = None
    if payload.create_admin_account or payload.admin_username:
        raw_user = payload.admin_username.strip() if payload.admin_username else f"{clean_code.lower()}_admin"
        if db.query(User).filter(User.username == raw_user).first():
            if payload.admin_username:
                raise HTTPException(
                    status_code=409,
                    detail=f"Admin username '{raw_user}' is already taken. Please enter a different username.",
                )
            # Auto-generated collision: find free suffix
            suffix = 1
            while db.query(User).filter(User.username == f"{raw_user}_{suffix}").first():
                suffix += 1
            raw_user = f"{raw_user}_{suffix}"
        target_admin_username = raw_user

    # Atomic creation: center and admin are created together or rolled back completely
    try:
        center = Center(
            college_id=college.id,
            center_code=clean_code,
            name=payload.name.strip(),
            hub_name=payload.hub_name,
            health_partner=payload.health_partner,
            address=payload.address,
            city=payload.city,
            state=payload.state,
            contact_email=payload.contact_email,
            contact_phone=payload.contact_phone,
            is_active=True,
            created_by_user_id=current_user.id,
        )
        db.add(center)
        db.flush()  # Assign center.id without committing yet

        admin_user = None
        temp_password = None
        if target_admin_username:
            admin_user, temp_password = create_hierarchical_user(
                db,
                actor=current_user,
                username=target_admin_username,
                full_name=payload.admin_full_name or f"{center.name} Admin",
                role=UserRole.CENTER_ADMIN,
                email=payload.admin_email or payload.contact_email,
                phone=payload.admin_phone or payload.contact_phone,
                college_id=college.id,
                center_id=center.id,
                ip_address=get_client_ip(request),
            )

        log_activity(
            db,
            action="CENTER_CREATED",
            entity_type="CENTER",
            entity_id=str(center.id),
            user=current_user,
            college_id=college.id,
            center_id=center.id,
            ip_address=get_client_ip(request),
            details={"center_code": center.center_code},
        )

        db.commit()
        db.refresh(center)
    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"Failed to create center: {exc}")

    c_resp = _enrich_center_response(center, db)
    return CenterCreateWithAdminResponse(
        center=c_resp,
        admin_user=UserResponse.model_validate(admin_user) if admin_user else None,
        temporary_password=temp_password,
    )


@router.post("/{center_id}/admin", response_model=CenterCreateWithAdminResponse)
def generate_or_reset_center_admin(
    center_id: int,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    center = db.query(Center).filter(Center.id == center_id, Center.is_deleted.is_(False)).first()
    if not center:
        raise HTTPException(status_code=404, detail="Center not found.")
    assert_center_access(current_user, center.college_id, center.id)

    existing_admin = (
        db.query(User)
        .filter(
            User.center_id == center.id,
            User.role == UserRole.CENTER_ADMIN,
            User.is_deleted.is_(False),
        )
        .first()
    )

    temp_password = generate_temporary_password()

    if existing_admin:
        existing_admin.password_hash = hash_password(temp_password)
        existing_admin.must_change_password = True
        existing_admin.failed_login_attempts = 0
        existing_admin.is_active = True
        admin_user = existing_admin
        action_name = "USER_CREDENTIALS_RESET"
    else:
        base_username = f"{center.center_code.lower()}_admin"
        final_username = base_username
        suffix = 1
        while db.query(User).filter(User.username == final_username).first():
            final_username = f"{base_username}_{suffix}"
            suffix += 1

        admin_user, temp_password = create_hierarchical_user(
            db,
            actor=current_user,
            username=final_username,
            full_name=f"{center.name} Admin",
            role=UserRole.CENTER_ADMIN,
            email=center.contact_email,
            phone=center.contact_phone,
            college_id=center.college_id,
            center_id=center.id,
            temporary_password=temp_password,
            ip_address=get_client_ip(request),
        )
        action_name = "USER_CREATED"

    log_activity(
        db,
        action=action_name,
        entity_type="USER",
        entity_id=str(admin_user.id),
        user=current_user,
        college_id=center.college_id,
        center_id=center.id,
        ip_address=get_client_ip(request),
        details={"center_code": center.center_code, "admin_username": admin_user.username},
    )

    db.commit()
    db.refresh(admin_user)
    db.refresh(center)

    c_resp = _enrich_center_response(center, db)
    return CenterCreateWithAdminResponse(
        center=c_resp,
        admin_user=UserResponse.model_validate(admin_user),
        temporary_password=temp_password,
    )


@router.put("/{center_id}", response_model=CenterResponse)
def update_center(
    center_id: int,
    payload: CenterUpdateRequest,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    center = db.query(Center).filter(Center.id == center_id, Center.is_deleted.is_(False)).first()
    if not center:
        raise HTTPException(status_code=404, detail="Center not found.")
    assert_center_access(current_user, center.college_id, center.id)

    for k, v in payload.model_dump(exclude_unset=True).items():
        setattr(center, k, v)
    db.commit()
    db.refresh(center)

    log_activity(
        db,
        action="CENTER_UPDATED",
        entity_type="CENTER",
        entity_id=str(center.id),
        user=current_user,
        college_id=center.college_id,
        center_id=center.id,
        ip_address=get_client_ip(request),
    )
    return CenterResponse.model_validate(center)


@router.patch("/{center_id}/status", response_model=CenterResponse)
def toggle_center_status(
    center_id: int,
    is_active: bool,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    center = db.query(Center).filter(Center.id == center_id, Center.is_deleted.is_(False)).first()
    if not center:
        raise HTTPException(status_code=404, detail="Center not found.")
    assert_center_access(current_user, center.college_id, center.id)
    center.is_active = is_active
    db.commit()
    db.refresh(center)

    log_activity(
        db,
        action="CENTER_ACTIVATED" if is_active else "CENTER_DEACTIVATED",
        entity_type="CENTER",
        entity_id=str(center.id),
        user=current_user,
        college_id=center.college_id,
        center_id=center.id,
        ip_address=get_client_ip(request),
    )
    return CenterResponse.model_validate(center)
