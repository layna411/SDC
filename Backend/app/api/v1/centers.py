from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_center_access, get_client_ip, get_current_user, require_roles
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
    return [CenterResponse.model_validate(c) for c in rows]


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
    return CenterResponse.model_validate(center)


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

    if db.query(Center).filter(Center.center_code == payload.center_code.strip().upper()).first():
        raise HTTPException(status_code=409, detail="Center code already exists.")

    center = Center(
        college_id=college.id,
        center_code=payload.center_code.strip().upper(),
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
    db.commit()
    db.refresh(center)

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

    admin_user = None
    temp_password = None
    if payload.create_admin_account:
        if not payload.admin_username:
            raise HTTPException(status_code=400, detail="admin_username is required when creating center admin.")
        admin_user, temp_password = create_hierarchical_user(
            db,
            actor=current_user,
            username=payload.admin_username,
            full_name=payload.admin_full_name or f"{center.name} Admin",
            role=UserRole.CENTER_ADMIN,
            email=payload.admin_email or payload.contact_email,
            phone=payload.admin_phone or payload.contact_phone,
            college_id=college.id,
            center_id=center.id,
            ip_address=get_client_ip(request),
        )

    return CenterCreateWithAdminResponse(
        center=CenterResponse.model_validate(center),
        admin_user=UserResponse.model_validate(admin_user) if admin_user else None,
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
