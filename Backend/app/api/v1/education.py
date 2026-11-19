from typing import List, Optional

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_client_ip, get_current_user, require_roles
from app.core.security import utc_now
from app.models.entities import AwarenessContent, EducationalResource, User
from app.models.enums import EducationResourceType, UserRole
from app.schemas.operations import (
    AwarenessContentResponse,
    AwarenessCreateRequest,
    AwarenessUpdateRequest,
    EducationalResourceCreateRequest,
    EducationalResourceResponse,
    EducationalResourceUpdateRequest,
)
from app.services.activity_service import log_activity
from app.storage.file_storage import file_storage

router = APIRouter(prefix="/education", tags=["Educational Resources & Awareness"])


@router.get("/resources", response_model=List[EducationalResourceResponse])
def list_educational_resources(
    search: Optional[str] = None,
    category: Optional[str] = None,
    language: Optional[str] = None,
    resource_type: Optional[EducationResourceType] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = db.query(EducationalResource).filter(EducationalResource.is_deleted.is_(False))
    if current_user.role != UserRole.SUPER_ADMIN:
        query = query.filter(
            EducationalResource.is_published.is_(True),
            or_(
                EducationalResource.college_id.is_(None),
                EducationalResource.college_id == current_user.college_id,
            ),
        )
    if search:
        term = f"%{search.strip()}%"
        query = query.filter(
            EducationalResource.title.ilike(term) | EducationalResource.description.ilike(term)
        )
    if category:
        query = query.filter(EducationalResource.category == category)
    if language:
        query = query.filter(EducationalResource.language.ilike(language))
    if resource_type:
        query = query.filter(EducationalResource.resource_type == resource_type)

    rows = query.order_by(EducationalResource.created_at.desc()).all()
    return [EducationalResourceResponse.model_validate(r) for r in rows]


@router.post(
    "/resources",
    response_model=EducationalResourceResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_educational_resource(
    payload: EducationalResourceCreateRequest,
    request: Request,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = EducationalResource(
        title=payload.title.strip(),
        description=payload.description,
        category=payload.category,
        language=payload.language,
        resource_type=payload.resource_type,
        external_url=payload.external_url,
        college_id=None if current_user.role == UserRole.SUPER_ADMIN else current_user.college_id,
        is_published=payload.is_published,
        created_by_user_id=current_user.id,
    )
    db.add(row)
    db.commit()
    db.refresh(row)

    log_activity(
        db,
        action="EDUCATION_RESOURCE_CREATED",
        entity_type="EDUCATIONAL_RESOURCE",
        entity_id=str(row.id),
        user=current_user,
        ip_address=get_client_ip(request),
    )
    return EducationalResourceResponse.model_validate(row)


@router.post("/resources/{resource_id}/file", response_model=EducationalResourceResponse)
async def upload_resource_file(
    resource_id: int,
    file: UploadFile = File(...),
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = (
        db.query(EducationalResource)
        .filter(EducationalResource.id == resource_id, EducationalResource.is_deleted.is_(False))
        .first()
    )
    if not row:
        raise HTTPException(status_code=404, detail="Educational resource not found.")
    if current_user.role == UserRole.COLLEGE_ADMIN and row.college_id != current_user.college_id:
        raise HTTPException(status_code=403, detail="Not permitted to update global or other college resource.")

    rel_path, orig_name, mime_type, _size = await file_storage.save_document(
        file, subdir="educational_manuals"
    )
    row.file_storage_path = rel_path
    row.file_name = orig_name
    row.mime_type = mime_type
    db.commit()
    db.refresh(row)
    return EducationalResourceResponse.model_validate(row)


@router.get("/resources/{resource_id}/download")
def download_resource_file(
    resource_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    row = (
        db.query(EducationalResource)
        .filter(EducationalResource.id == resource_id, EducationalResource.is_deleted.is_(False))
        .first()
    )
    if not row or not row.file_storage_path:
        raise HTTPException(status_code=404, detail="Downloadable file not found for this resource.")
    if (
        current_user.role != UserRole.SUPER_ADMIN
        and row.college_id is not None
        and row.college_id != current_user.college_id
    ):
        raise HTTPException(status_code=403, detail="Access denied.")
    path = file_storage.resolve_path(row.file_storage_path)
    return FileResponse(path=str(path), media_type=row.mime_type, filename=row.file_name)


@router.put("/resources/{resource_id}", response_model=EducationalResourceResponse)
def update_educational_resource(
    resource_id: int,
    payload: EducationalResourceUpdateRequest,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = (
        db.query(EducationalResource)
        .filter(EducationalResource.id == resource_id, EducationalResource.is_deleted.is_(False))
        .first()
    )
    if not row:
        raise HTTPException(status_code=404, detail="Resource not found.")
    if current_user.role == UserRole.COLLEGE_ADMIN and row.college_id != current_user.college_id:
        raise HTTPException(status_code=403, detail="Not permitted to modify this resource.")
    for k, v in payload.model_dump(exclude_unset=True).items():
        setattr(row, k, v)
    db.commit()
    db.refresh(row)
    return EducationalResourceResponse.model_validate(row)


@router.delete("/resources/{resource_id}")
def delete_educational_resource(
    resource_id: int,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = (
        db.query(EducationalResource)
        .filter(EducationalResource.id == resource_id, EducationalResource.is_deleted.is_(False))
        .first()
    )
    if not row:
        raise HTTPException(status_code=404, detail="Resource not found.")
    if current_user.role == UserRole.COLLEGE_ADMIN and row.college_id != current_user.college_id:
        raise HTTPException(status_code=403, detail="Not permitted to delete this resource.")
    row.is_deleted = True
    row.deleted_at = utc_now()
    db.commit()
    return {"message": "Educational resource deleted."}


@router.get("/awareness", response_model=List[AwarenessContentResponse])
def list_awareness_content(
    search: Optional[str] = None,
    category: Optional[str] = None,
    language: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = db.query(AwarenessContent).filter(AwarenessContent.is_deleted.is_(False))
    if current_user.role != UserRole.SUPER_ADMIN:
        query = query.filter(
            AwarenessContent.is_published.is_(True),
            or_(
                AwarenessContent.college_id.is_(None),
                AwarenessContent.college_id == current_user.college_id,
            ),
        )
    if search:
        term = f"%{search.strip()}%"
        query = query.filter(
            AwarenessContent.title.ilike(term) | AwarenessContent.body.ilike(term)
        )
    if category:
        query = query.filter(AwarenessContent.category == category)
    if language:
        query = query.filter(AwarenessContent.language.ilike(language))

    rows = query.order_by(AwarenessContent.created_at.desc()).all()
    return [AwarenessContentResponse.model_validate(r) for r in rows]


@router.post(
    "/awareness",
    response_model=AwarenessContentResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_awareness_content(
    payload: AwarenessCreateRequest,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = AwarenessContent(
        title=payload.title.strip(),
        summary=payload.summary,
        body=payload.body,
        category=payload.category,
        language=payload.language,
        content_type=payload.content_type,
        college_id=None if current_user.role == UserRole.SUPER_ADMIN else current_user.college_id,
        is_published=payload.is_published,
        created_by_user_id=current_user.id,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return AwarenessContentResponse.model_validate(row)


@router.put("/awareness/{content_id}", response_model=AwarenessContentResponse)
def update_awareness_content(
    content_id: int,
    payload: AwarenessUpdateRequest,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = (
        db.query(AwarenessContent)
        .filter(AwarenessContent.id == content_id, AwarenessContent.is_deleted.is_(False))
        .first()
    )
    if not row:
        raise HTTPException(status_code=404, detail="Awareness content not found.")
    if current_user.role == UserRole.COLLEGE_ADMIN and row.college_id != current_user.college_id:
        raise HTTPException(status_code=403, detail="Not permitted to edit this awareness content.")
    for k, v in payload.model_dump(exclude_unset=True).items():
        setattr(row, k, v)
    db.commit()
    db.refresh(row)
    return AwarenessContentResponse.model_validate(row)


@router.delete("/awareness/{content_id}")
def delete_awareness_content(
    content_id: int,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN, UserRole.COLLEGE_ADMIN)),
    db: Session = Depends(get_db),
):
    row = (
        db.query(AwarenessContent)
        .filter(AwarenessContent.id == content_id, AwarenessContent.is_deleted.is_(False))
        .first()
    )
    if not row:
        raise HTTPException(status_code=404, detail="Awareness content not found.")
    if current_user.role == UserRole.COLLEGE_ADMIN and row.college_id != current_user.college_id:
        raise HTTPException(status_code=403, detail="Not permitted to delete this awareness content.")
    row.is_deleted = True
    row.deleted_at = utc_now()
    db.commit()
    return {"message": "Awareness content deleted."}
