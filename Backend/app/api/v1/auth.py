from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.database import get_db
from app.core.permissions import get_client_ip, get_current_user
from app.models.entities import User
from app.schemas.auth import (
    AuthTokenResponse,
    ChangePasswordRequest,
    ForgotPasswordRequest,
    LoginRequest,
    LogoutRequest,
    RefreshTokenRequest,
    ResetPasswordRequest,
    UserResponse,
)
from app.services.auth_service import (
    authenticate_user,
    change_user_password,
    complete_password_reset,
    initiate_forgot_password,
    logout_user,
    rotate_refresh_token,
)

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/login", response_model=AuthTokenResponse)
def login(payload: LoginRequest, request: Request, db: Session = Depends(get_db)):
    user, access_token, refresh_token = authenticate_user(
        db, payload.username, payload.password, ip_address=get_client_ip(request)
    )
    return AuthTokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_in_seconds=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        must_change_password=user.must_change_password,
        user=UserResponse.model_validate(user),
    )


@router.post("/refresh", response_model=AuthTokenResponse)
def refresh_tokens(payload: RefreshTokenRequest, request: Request, db: Session = Depends(get_db)):
    user, access_token, refresh_token = rotate_refresh_token(
        db, payload.refresh_token, ip_address=get_client_ip(request)
    )
    return AuthTokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_in_seconds=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        must_change_password=user.must_change_password,
        user=UserResponse.model_validate(user),
    )


@router.post("/logout")
def logout(
    payload: LogoutRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    logout_user(db, current_user, payload.refresh_token, ip_address=get_client_ip(request))
    return {"message": "Logged out successfully."}


@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    return UserResponse.model_validate(current_user)


@router.post("/change-password")
def change_password(
    payload: ChangePasswordRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    change_user_password(
        db,
        current_user,
        payload.current_password,
        payload.new_password,
        ip_address=get_client_ip(request),
    )
    return {"message": "Password changed successfully. Please sign in with your new password."}


@router.post("/forgot-password")
def forgot_password(payload: ForgotPasswordRequest, request: Request, db: Session = Depends(get_db)):
    token = initiate_forgot_password(db, payload.username_or_email, ip_address=get_client_ip(request))
    response = {
        "message": "If an active matching account exists, a password reset token has been generated."
    }
    if settings.ENVIRONMENT == "development" and token:
        response["dev_reset_token"] = token
    return response


@router.post("/reset-password")
def reset_password(payload: ResetPasswordRequest, request: Request, db: Session = Depends(get_db)):
    complete_password_reset(
        db, payload.reset_token, payload.new_password, ip_address=get_client_ip(request)
    )
    return {"message": "Password has been reset successfully."}
