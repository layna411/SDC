from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session, joinedload

from app.core.database import get_db
from app.core.permissions import assert_center_access, get_client_ip, get_current_user, require_roles
from app.models.entities import Center, Organizer, User
from app.models.enums import UserRole
from app.repositories.scope_repository import scope_organizers_query
from app.schemas.organization import (
    OrganizerCreatedResponse,
    OrganizerCreateRequest,
    OrganizerResponse,
    OrganizerUpdateRequest,
)
from app.services.activity_service import log_activity
from app.services.auth_service import create_hierarchical_user

router = APIRouter(prefix="/organizers", tags=["Health Organizer Assistants"])


@router.get("", response_model=List[OrganizerResponse])
def list_organizers(
    center_id: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = scope_organizers_query(
        db.query(Organizer).options(joinedload(Organizer.user)), current_user
    )
    if center_id and current_user.role in (UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN):
        query = query.filter(Organizer.center_id == center_id)
    rows = query.order_by(Organizer.created_at.desc()).all()
    return [OrganizerResponse.model_validate(o) for o in rows]


@router.post("", response_model=OrganizerCreatedResponse, status_code=status.HTTP_201_CREATED)
def create_organizer(
    payload: OrganizerCreateRequest,
    request: Request,
    current_user: User = Depends(
        require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN, UserRole.CENTER_ADMIN)
    ),
    db: Session = Depends(get_db),
):
    resolved_center_id = (
        current_user.center_id if current_user.role == UserRole.CENTER_ADMIN else payload.center_id
    )
    if not resolved_center_id:
        raise HTTPException(status_code=400, detail="center_id is required.")

    center = db.query(Center).filter(Center.id == resolved_center_id, Center.is_deleted.is_(False)).first()
    if not center:
        raise HTTPException(status_code=404, detail="Assigned center not found.")
    assert_center_access(current_user, center.college_id, center.id)

    if db.query(Organizer).filter(Organizer.employee_code == payload.employee_code.strip().upper()).first():
        raise HTTPException(status_code=409, detail="Employee code already exists.")

    user, temp_pwd = create_hierarchical_user(
        db,
        actor=current_user,
        username=payload.username,
        full_name=payload.full_name,
        role=UserRole.HEALTH_ORGANIZER,
        email=payload.email,
        phone=payload.phone,
        college_id=center.college_id,
        center_id=center.id,
        temporary_password=payload.temporary_password,
        ip_address=get_client_ip(request),
    )

    organizer = Organizer(
        user_id=user.id,
        college_id=center.college_id,
        center_id=center.id,
        employee_code=payload.employee_code.strip().upper(),
        designation=payload.designation,
        qualification=payload.qualification,
        assigned_area=payload.assigned_area,
        is_active=True,
    )
    db.add(organizer)
    db.commit()
    db.refresh(organizer)
    organizer.user = user

    return OrganizerCreatedResponse(
        organizer=OrganizerResponse.model_validate(organizer),
        temporary_password=temp_pwd,
    )


@router.put("/{organizer_id}", response_model=OrganizerResponse)
def update_organizer(
    organizer_id: int,
    payload: OrganizerUpdateRequest,
    request: Request,
    current_user: User = Depends(
        require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN, UserRole.CENTER_ADMIN)
    ),
    db: Session = Depends(get_db),
):
    organizer = (
        db.query(Organizer)
        .options(joinedload(Organizer.user))
        .filter(Organizer.id == organizer_id, Organizer.is_deleted.is_(False))
        .first()
    )
    if not organizer:
        raise HTTPException(status_code=404, detail="Organizer not found.")
    assert_center_access(current_user, organizer.college_id, organizer.center_id)

    data = payload.model_dump(exclude_unset=True)
    if "center_id" in data and data["center_id"] is not None:
        new_center = (
            db.query(Center)
            .filter(Center.id == data["center_id"], Center.is_deleted.is_(False))
            .first()
        )
        if not new_center:
            raise HTTPException(status_code=404, detail="Target center not found.")
        assert_center_access(current_user, new_center.college_id, new_center.id)
        organizer.center_id = new_center.id
        organizer.college_id = new_center.college_id
        if organizer.user:
            organizer.user.center_id = new_center.id
            organizer.user.college_id = new_center.college_id

    for k in ("designation", "qualification", "assigned_area", "is_active"):
        if k in data:
            setattr(organizer, k, data[k])
            if k == "is_active" and organizer.user:
                organizer.user.is_active = data[k]

    db.commit()
    db.refresh(organizer)

    log_activity(
        db,
        action="ORGANIZER_UPDATED",
        entity_type="ORGANIZER",
        entity_id=str(organizer.id),
        user=current_user,
        college_id=organizer.college_id,
        center_id=organizer.center_id,
        ip_address=get_client_ip(request),
    )
    return OrganizerResponse.model_validate(organizer)
