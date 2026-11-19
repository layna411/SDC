from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.models.enums import UserRole


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=2, max_length=150)
    password: str = Field(..., min_length=4, max_length=128)


class RefreshTokenRequest(BaseModel):
    refresh_token: str = Field(..., min_length=20)


class LogoutRequest(BaseModel):
    refresh_token: Optional[str] = None


class ChangePasswordRequest(BaseModel):
    current_password: str = Field(..., min_length=4, max_length=128)
    new_password: str = Field(..., min_length=8, max_length=128)


class ForgotPasswordRequest(BaseModel):
    username_or_email: str = Field(..., min_length=2, max_length=150)


class ResetPasswordRequest(BaseModel):
    reset_token: str = Field(..., min_length=16)
    new_password: str = Field(..., min_length=8, max_length=128)


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    user_uid: str
    username: str
    email: Optional[str] = None
    full_name: str
    phone: Optional[str] = None
    role: UserRole
    college_id: Optional[int] = None
    center_id: Optional[int] = None
    is_active: bool
    must_change_password: bool
    last_login_at: Optional[datetime] = None
    created_at: datetime


class AuthTokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in_seconds: int
    must_change_password: bool
    user: UserResponse


class UserCreateRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=100)
    email: Optional[EmailStr] = None
    full_name: str = Field(..., min_length=2, max_length=150)
    phone: Optional[str] = Field(default=None, max_length=30)
    role: UserRole
    college_id: Optional[int] = None
    center_id: Optional[int] = None
    temporary_password: Optional[str] = Field(default=None, min_length=8, max_length=128)


class UserCreatedWithCredentialsResponse(BaseModel):
    user: UserResponse
    temporary_password: str
    note: str = "Share this temporary password securely. User must change password on first login."


class UserStatusUpdateRequest(BaseModel):
    is_active: bool
