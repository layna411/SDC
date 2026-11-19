from functools import lru_cache
from pathlib import Path
from typing import List
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    APP_NAME: str = "OralCare Clinical API"
    APP_VERSION: str = "1.0.0"
    ENVIRONMENT: str = "development"
    DEBUG: bool = False

    # MySQL 8+ connection string (override via .env; sqlite supported for isolated tests)
    DATABASE_URL: str = Field(
        default="mysql+pymysql://oralcare_user:change_me@127.0.0.1:3306/oralcare_db?charset=utf8mb4"
    )

    # JWT Authentication
    JWT_SECRET_KEY: str = Field(
        default="dev-only-change-in-production-use-64-char-random-secret-key"
    )
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 14

    # Account lockout protection
    MAX_FAILED_LOGIN_ATTEMPTS: int = 5
    ACCOUNT_LOCKOUT_MINUTES: int = 15

    # Secure file storage (stored outside public static roots)
    STORAGE_ROOT: str = "./storage_data"
    MAX_IMAGE_UPLOAD_MB: int = 10
    MAX_DOCUMENT_UPLOAD_MB: int = 20

    # CORS
    CORS_ORIGINS: str = "http://localhost:3000,http://127.0.0.1:8000"

    # AI Service
    AI_SERVICE_MODE: str = "embedded"  # "embedded" or "remote"
    AI_SERVICE_URL: str = "http://127.0.0.1:8001"
    AI_MODEL_NAME: str = "unconfigured"
    AI_MODEL_VERSION: str = "0.0.0"
    AI_MODEL_PATH: str = ""
    AI_MIN_IMAGE_WIDTH: int = 320
    AI_MIN_IMAGE_HEIGHT: int = 240

    # Initial bootstrap Super Admin (used only when users table is empty)
    INITIAL_SUPER_ADMIN_USERNAME: str = "superadmin"
    INITIAL_SUPER_ADMIN_EMAIL: str = "superadmin@oralcare.local"
    INITIAL_SUPER_ADMIN_PASSWORD: str = "OralCare@Admin2026!"

    @property
    def cors_origin_list(self) -> List[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]

    @property
    def storage_path(self) -> Path:
        path = Path(self.STORAGE_ROOT).resolve()
        path.mkdir(parents=True, exist_ok=True)
        return path


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
