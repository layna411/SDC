from typing import List

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import assert_college_access, get_client_ip, get_current_user, require_roles
from app.models.entities import College, User
from app.models.enums import UserRole
from app.repositories.scope_repository import scope_colleges_query
from app.schemas.auth import UserResponse
from app.schemas.organization import (
    CollegeCreateRequest,
    CollegeCreateWithAdminResponse,
    CollegeResponse,
    CollegeUpdateRequest,
)
from app.services.activity_service import log_activity
from app.services.auth_service import create_hierarchical_user

router = APIRouter(prefix="/colleges", tags=["Dental Colleges"])


@router.get("", response_model=List[CollegeResponse])
def list_colleges(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    rows = scope_colleges_query(db.query(College), current_user).order_by(College.created_at.desc()).all()
    return [CollegeResponse.model_validate(c) for c in rows]


@router.get("/{college_id}", response_model=CollegeResponse)
def get_college(
    college_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    assert_college_access(current_user, college_id)
    college = db.query(College).filter(College.id == college_id, College.is_deleted.is_(False)).first()
    if not college:
        raise HTTPException(status_code=404, detail="College not found.")
    return CollegeResponse.model_validate(college)


@router.post("", response_model=CollegeCreateWithAdminResponse, status_code=status.HTTP_201_CREATED)
def create_college(
    payload: CollegeCreateRequest,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN)),
    db: Session = Depends(get_db),
):
    clean_code = payload.college_code.strip().upper()
    if db.query(College).filter(College.college_code == clean_code).first():
        raise HTTPException(status_code=409, detail="College code already exists.")

    target_admin_username = None
    if payload.create_admin_account or payload.admin_username:
        raw_user = payload.admin_username.strip() if payload.admin_username else f"{clean_code.lower()}_admin"
        if db.query(User).filter(User.username == raw_user).first():
            if payload.admin_username:
                raise HTTPException(
                    status_code=409,
                    detail=f"Admin username '{raw_user}' is already taken. Please choose a different username.",
                )
            suffix = 1
            while db.query(User).filter(User.username == f"{raw_user}_{suffix}").first():
                suffix += 1
            raw_user = f"{raw_user}_{suffix}"
        target_admin_username = raw_user

    try:
        college = College(
            college_code=clean_code,
            name=payload.name.strip(),
            address=payload.address,
            city=payload.city,
            state=payload.state,
            pincode=payload.pincode,
            contact_email=payload.contact_email,
            contact_phone=payload.contact_phone,
            is_active=True,
            created_by_user_id=current_user.id,
        )
        db.add(college)
        db.flush()

        admin_user = None
        temp_password = None
        if target_admin_username:
            admin_user, temp_password = create_hierarchical_user(
                db,
                actor=current_user,
                username=target_admin_username,
                full_name=payload.admin_full_name or f"{college.name} Admin",
                role=UserRole.COLLEGE_ADMIN,
                email=payload.admin_email or payload.contact_email,
                phone=payload.admin_phone or payload.contact_phone,
                college_id=college.id,
                ip_address=get_client_ip(request),
            )

        log_activity(
            db,
            action="COLLEGE_CREATED",
            entity_type="COLLEGE",
            entity_id=str(college.id),
            user=current_user,
            college_id=college.id,
            ip_address=get_client_ip(request),
            details={"college_code": college.college_code},
        )

        db.commit()
        db.refresh(college)
    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"Failed to create college: {exc}")

    return CollegeCreateWithAdminResponse(
        college=CollegeResponse.model_validate(college),
        admin_user=UserResponse.model_validate(admin_user) if admin_user else None,
        temporary_password=temp_password,
    )


@router.put("/{college_id}", response_model=CollegeResponse)
def update_college(
    college_id: int,
    payload: CollegeUpdateRequest,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN)),
    db: Session = Depends(get_db),
):
    college = db.query(College).filter(College.id == college_id, College.is_deleted.is_(False)).first()
    if not college:
        raise HTTPException(status_code=404, detail="College not found.")

    update_data = payload.model_dump(exclude_unset=True)
    for k, v in update_data.items():
        setattr(college, k, v)
    db.commit()
    db.refresh(college)

    log_activity(
        db,
        action="COLLEGE_UPDATED",
        entity_type="COLLEGE",
        entity_id=str(college.id),
        user=current_user,
        college_id=college.id,
        ip_address=get_client_ip(request),
    )
    return CollegeResponse.model_validate(college)


@router.patch("/{college_id}/status", response_model=CollegeResponse)
def toggle_college_status(
    college_id: int,
    is_active: bool,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN)),
    db: Session = Depends(get_db),
):
    college = db.query(College).filter(College.id == college_id, College.is_deleted.is_(False)).first()
    if not college:
        raise HTTPException(status_code=404, detail="College not found.")
    college.is_active = is_active
    db.commit()
    db.refresh(college)

    log_activity(
        db,
        action="COLLEGE_ACTIVATED" if is_active else "COLLEGE_DEACTIVATED",
        entity_type="COLLEGE",
        entity_id=str(college.id),
        user=current_user,
        college_id=college.id,
        ip_address=get_client_ip(request),
    )
    return CollegeResponse.model_validate(college)
