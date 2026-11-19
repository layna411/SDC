from pathlib import Path
import secrets
from typing import Set, Tuple

from fastapi import HTTPException, UploadFile, status

from app.core.config import settings

ALLOWED_IMAGE_MIME_TYPES: Set[str] = {
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
}

ALLOWED_IMAGE_EXTENSIONS: Set[str] = {".jpg", ".jpeg", ".png", ".webp"}

ALLOWED_DOC_MIME_TYPES: Set[str] = {
    "application/pdf",
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
}

ALLOWED_DOC_EXTENSIONS: Set[str] = {".pdf", ".jpg", ".jpeg", ".png", ".webp"}


class SecureFileStorage:
    """Stores clinical images and documents outside public web roots with strict validation."""

    def __init__(self, root: Path | None = None):
        self.root = (root or settings.storage_path).resolve()
        self.root.mkdir(parents=True, exist_ok=True)

    def _safe_subdir(self, category: str) -> Path:
        clean = "".join(c for c in category if c.isalnum() or c in ("_", "-"))
        target = (self.root / clean).resolve()
        if not str(target).startswith(str(self.root)):
            raise HTTPException(status_code=400, detail="Invalid storage category.")
        target.mkdir(parents=True, exist_ok=True)
        return target

    async def save_clinical_image(self, upload: UploadFile) -> Tuple[str, str, str, int]:
        return await self._save_file(
            upload=upload,
            subdir="clinical_images",
            allowed_mimes=ALLOWED_IMAGE_MIME_TYPES,
            allowed_exts=ALLOWED_IMAGE_EXTENSIONS,
            max_mb=settings.MAX_IMAGE_UPLOAD_MB,
        )

    async def save_document(self, upload: UploadFile, subdir: str = "documents") -> Tuple[str, str, str, int]:
        return await self._save_file(
            upload=upload,
            subdir=subdir,
            allowed_mimes=ALLOWED_DOC_MIME_TYPES,
            allowed_exts=ALLOWED_DOC_EXTENSIONS,
            max_mb=settings.MAX_DOCUMENT_UPLOAD_MB,
        )

    async def _save_file(
        self,
        upload: UploadFile,
        subdir: str,
        allowed_mimes: Set[str],
        allowed_exts: Set[str],
        max_mb: int,
    ) -> Tuple[str, str, str, int]:
        original_name = Path(upload.filename or "upload.bin").name
        ext = Path(original_name).suffix.lower()
        content_type = (upload.content_type or "").lower()

        if ext not in allowed_exts:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported file extension '{ext}'. Allowed: {sorted(allowed_exts)}",
            )

        if content_type and content_type not in allowed_mimes and content_type != "application/octet-stream":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported MIME type '{content_type}'.",
            )

        data = await upload.read()
        max_bytes = max_mb * 1024 * 1024
        if len(data) == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Uploaded file is empty.",
            )
        if len(data) > max_bytes:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail=f"File exceeds maximum allowed size of {max_mb} MB.",
            )

        # Magic-byte verification for common clinical file formats
        if ext in {".jpg", ".jpeg"} and not data.startswith(b"\xff\xd8"):
            raise HTTPException(status_code=400, detail="Invalid JPEG file signature.")
        if ext == ".png" and not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise HTTPException(status_code=400, detail="Invalid PNG file signature.")
        if ext == ".pdf" and not data.startswith(b"%PDF"):
            raise HTTPException(status_code=400, detail="Invalid PDF file signature.")

        folder = self._safe_subdir(subdir)
        stored_name = f"{secrets.token_hex(16)}{ext}"
        full_path = folder / stored_name
        full_path.write_bytes(data)

        relative_path = f"{subdir}/{stored_name}"
        resolved_mime = content_type if content_type in allowed_mimes else self._infer_mime(ext)
        return relative_path, original_name, resolved_mime, len(data)

    def resolve_path(self, relative_path: str) -> Path:
        full_path = (self.root / relative_path).resolve()
        if not str(full_path).startswith(str(self.root)):
            raise HTTPException(status_code=400, detail="Invalid file path.")
        if not full_path.exists() or not full_path.is_file():
            raise HTTPException(status_code=404, detail="Stored file not found.")
        return full_path

    @staticmethod
    def _infer_mime(ext: str) -> str:
        return {
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".png": "image/png",
            ".webp": "image/webp",
            ".pdf": "application/pdf",
        }.get(ext, "application/octet-stream")


file_storage = SecureFileStorage()
