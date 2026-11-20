from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.schemas.auth import UserResponse


class CollegeCreateRequest(BaseModel):
    college_code: str = Field(..., min_length=2, max_length=32)
    name: str = Field(..., min_length=2, max_length=200)
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    pincode: Optional[str] = None
    contact_email: Optional[EmailStr] = None
    contact_phone: Optional[str] = None
    # Optional immediate college admin credential provisioning
    create_admin_account: bool = False
    admin_username: Optional[str] = Field(default=None, min_length=3, max_length=100)
    admin_full_name: Optional[str] = None
    admin_email: Optional[EmailStr] = None
    admin_phone: Optional[str] = None


class CollegeUpdateRequest(BaseModel):
    name: Optional[str] = Field(default=None, min_length=2, max_length=200)
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    pincode: Optional[str] = None
    contact_email: Optional[EmailStr] = None
    contact_phone: Optional[str] = None
    is_active: Optional[bool] = None


class CollegeResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    college_code: str
    name: str
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    pincode: Optional[str] = None
    contact_email: Optional[str] = None
    contact_phone: Optional[str] = None
    is_active: bool
    created_at: datetime
    admin_username: Optional[str] = None
    admin_user_id: Optional[int] = None
    has_admin: bool = False


class CollegeCreateWithAdminResponse(BaseModel):
    college: CollegeResponse
    admin_user: Optional[UserResponse] = None
    temporary_password: Optional[str] = None


class CenterCreateRequest(BaseModel):
    college_id: Optional[int] = None  # Inferred for College Admin
    center_code: str = Field(..., min_length=2, max_length=32)
    name: str = Field(..., min_length=2, max_length=200)
    hub_name: Optional[str] = None
    health_partner: Optional[str] = None
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    contact_email: Optional[EmailStr] = None
    contact_phone: Optional[str] = None
    create_admin_account: bool = False
    admin_username: Optional[str] = Field(default=None, min_length=3, max_length=100)
    admin_full_name: Optional[str] = None
    admin_email: Optional[EmailStr] = None
    admin_phone: Optional[str] = None


class CenterUpdateRequest(BaseModel):
    name: Optional[str] = Field(default=None, min_length=2, max_length=200)
    hub_name: Optional[str] = None
    health_partner: Optional[str] = None
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    contact_email: Optional[EmailStr] = None
    contact_phone: Optional[str] = None
    is_active: Optional[bool] = None


class CenterResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    college_id: int
    center_code: str
    name: str
    hub_name: Optional[str] = None
    health_partner: Optional[str] = None
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    contact_email: Optional[str] = None
    contact_phone: Optional[str] = None
    is_active: bool
    created_at: datetime
    admin_username: Optional[str] = None
    admin_user_id: Optional[int] = None
    has_admin: bool = False


class CenterCreateWithAdminResponse(BaseModel):
    center: CenterResponse
    admin_user: Optional[UserResponse] = None
    temporary_password: Optional[str] = None


class OrganizerCreateRequest(BaseModel):
    center_id: Optional[int] = None  # Inferred for Center Admin
    username: str = Field(..., min_length=3, max_length=100)
    full_name: str = Field(..., min_length=2, max_length=150)
    email: Optional[EmailStr] = None
    phone: Optional[str] = None
    employee_code: str = Field(..., min_length=2, max_length=50)
    designation: str = "Health Organizer Assistant"
    qualification: Optional[str] = None
    assigned_area: Optional[str] = None
    temporary_password: Optional[str] = Field(default=None, min_length=8, max_length=128)


class OrganizerUpdateRequest(BaseModel):
    center_id: Optional[int] = None
    designation: Optional[str] = None
    qualification: Optional[str] = None
    assigned_area: Optional[str] = None
    is_active: Optional[bool] = None


class OrganizerResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    college_id: int
    center_id: int
    employee_code: str
    designation: str
    qualification: Optional[str] = None
    assigned_area: Optional[str] = None
    is_active: bool
    user: Optional[UserResponse] = None
    created_at: datetime


class OrganizerCreatedResponse(BaseModel):
    organizer: OrganizerResponse
    temporary_password: str
