from typing import List

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.permissions import get_current_user, require_roles
from app.models.entities import AppSetting, User
from app.models.enums import UserRole
from app.schemas.operations import AppSettingResponse, AppSettingUpsertRequest

router = APIRouter(prefix="/settings", tags=["Application Settings"])


@router.get("", response_model=List[AppSettingResponse])
def list_settings(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    rows = db.query(AppSetting).order_by(AppSetting.setting_key.asc()).all()
    return [AppSettingResponse.model_validate(r) for r in rows]


@router.put("", response_model=AppSettingResponse)
def upsert_setting(
    payload: AppSettingUpsertRequest,
    current_user: User = Depends(require_roles(UserRole.SUPER_ADMIN)),
    db: Session = Depends(get_db),
):
    row = db.query(AppSetting).filter(AppSetting.setting_key == payload.setting_key).first()
    if not row:
        row = AppSetting(setting_key=payload.setting_key)
        db.add(row)
    row.setting_value = payload.setting_value
    row.description = payload.description
    row.updated_by_user_id = current_user.id
    db.commit()
    db.refresh(row)
    return AppSettingResponse.model_validate(row)
