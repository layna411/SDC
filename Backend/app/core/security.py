from datetime import datetime, timedelta, timezone
import hashlib
import secrets
import string
from typing import Any, Dict, Optional

import jwt
from jwt import InvalidTokenError

from app.core.config import settings

try:
    from argon2 import PasswordHasher
    from argon2.exceptions import VerifyMismatchError, InvalidHashError

    _argon2_hasher: Optional[PasswordHasher] = PasswordHasher(
        time_cost=3,
        memory_cost=65536,
        parallelism=4,
    )
except ImportError:  # Fallback only if argon2-cffi is not yet installed in local dev env
    _argon2_hasher = None
    VerifyMismatchError = Exception  # type: ignore
    InvalidHashError = Exception  # type: ignore

from passlib.hash import pbkdf2_sha256


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def hash_password(plain_password: str) -> str:
    """Hash password using Argon2id (preferred) with PBKDF2-SHA256 fallback."""
    if _argon2_hasher is not None:
        return _argon2_hasher.hash(plain_password)
    return pbkdf2_sha256.hash(plain_password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    if not hashed_password:
        return False
    if hashed_password.startswith("$argon2"):
        if _argon2_hasher is None:
            return False
        try:
            return _argon2_hasher.verify(hashed_password, plain_password)
        except (VerifyMismatchError, InvalidHashError):
            return False
    try:
        return pbkdf2_sha256.verify(plain_password, hashed_password)
    except Exception:
        return False


def generate_temporary_password(length: int = 12) -> str:
    """Generate a cryptographically random temporary password meeting complexity rules."""
    alphabet_upper = string.ascii_uppercase
    alphabet_lower = string.ascii_lowercase
    alphabet_digits = string.digits
    alphabet_special = "@#$%&*!"
    all_chars = alphabet_upper + alphabet_lower + alphabet_digits + alphabet_special

    chars = [
        secrets.choice(alphabet_upper),
        secrets.choice(alphabet_lower),
        secrets.choice(alphabet_digits),
        secrets.choice(alphabet_special),
    ] + [secrets.choice(all_chars) for _ in range(max(length - 4, 8))]
    secrets.SystemRandom().shuffle(chars)
    return "".join(chars)


def hash_token(token: str) -> str:
    """One-way SHA-256 hash for storing refresh tokens and reset tokens safely."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def create_access_token(
    subject: str,
    role: str,
    college_id: Optional[int] = None,
    center_id: Optional[int] = None,
    expires_delta: Optional[timedelta] = None,
) -> str:
    now = utc_now()
    expire = now + (expires_delta or timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))
    payload: Dict[str, Any] = {
        "sub": subject,
        "role": role,
        "college_id": college_id,
        "center_id": center_id,
        "type": "access",
        "iat": int(now.timestamp()),
        "exp": int(expire.timestamp()),
        "jti": secrets.token_hex(16),
    }
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def create_refresh_token(subject: str, token_family: Optional[str] = None) -> tuple[str, str, datetime]:
    now = utc_now()
    expire = now + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    family = token_family or secrets.token_hex(16)
    jti = secrets.token_hex(24)
    payload: Dict[str, Any] = {
        "sub": subject,
        "type": "refresh",
        "family": family,
        "jti": jti,
        "iat": int(now.timestamp()),
        "exp": int(expire.timestamp()),
    }
    token = jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)
    return token, family, expire


def decode_token(token: str) -> Optional[Dict[str, Any]]:
    try:
        return jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM],
        )
    except InvalidTokenError:
        return None
