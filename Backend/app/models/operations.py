from datetime import datetime
from typing import Optional

from sqlalchemy import (
    Boolean,
    DateTime,
    Enum as SAEnum,
    ForeignKey,
    Index,
    Integer,
    JSON,
    String,
    Text,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.models.enums import AwarenessContentType, EducationResourceType
from app.models.organization import TimestampSoftDeleteMixin


class Feedback(Base, TimestampSoftDeleteMixin):
    __tablename__ = "feedback"
    __table_args__ = (
        Index("ix_feedback_scope", "college_id", "center_id", "rating"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    college_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("colleges.id", ondelete="RESTRICT"), nullable=True, index=True
    )
    center_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("centers.id", ondelete="RESTRICT"), nullable=True, index=True
    )
    patient_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("patients.id", ondelete="SET NULL"), nullable=True, index=True
    )
    created_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False, index=True
    )
    patient_name: Mapped[str] = mapped_column(String(150), nullable=False)
    email: Mapped[str] = mapped_column(String(150), nullable=False)
    rating: Mapped[int] = mapped_column(Integer, nullable=False)  # 1..5
    message: Mapped[str] = mapped_column(Text, nullable=False)
    admin_reply: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    replied_by_user_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    replied_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class EducationalResource(Base, TimestampSoftDeleteMixin):
    __tablename__ = "educational_resources"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    category: Mapped[str] = mapped_column(String(80), default="USER_MANUAL", nullable=False, index=True)
    language: Mapped[str] = mapped_column(String(30), default="English", nullable=False, index=True)
    resource_type: Mapped[EducationResourceType] = mapped_column(
        SAEnum(EducationResourceType), nullable=False, index=True
    )
    external_url: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    file_storage_path: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    file_name: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    mime_type: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    college_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("colleges.id", ondelete="SET NULL"), nullable=True, index=True
    )
    is_published: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )


class AwarenessContent(Base, TimestampSoftDeleteMixin):
    __tablename__ = "awareness_content"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    summary: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    category: Mapped[str] = mapped_column(String(80), default="ORAL_HYGIENE", nullable=False, index=True)
    language: Mapped[str] = mapped_column(String(30), default="English", nullable=False)
    content_type: Mapped[AwarenessContentType] = mapped_column(
        SAEnum(AwarenessContentType), default=AwarenessContentType.ARTICLE, nullable=False
    )
    poster_storage_path: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    poster_file_name: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    college_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("colleges.id", ondelete="SET NULL"), nullable=True, index=True
    )
    is_published: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )


class ActivityLog(Base):
    """Immutable audit log — never exposed for edit or deletion by regular users."""

    __tablename__ = "activity_logs"
    __table_args__ = (
        Index("ix_activity_logs_scope_time", "college_id", "center_id", "created_at"),
        Index("ix_activity_logs_user_action", "user_id", "action"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    role: Mapped[str] = mapped_column(String(40), nullable=False)
    college_id: Mapped[Optional[int]] = mapped_column(Integer, nullable=True, index=True)
    center_id: Mapped[Optional[int]] = mapped_column(Integer, nullable=True, index=True)
    action: Mapped[str] = mapped_column(String(80), nullable=False, index=True)
    entity_type: Mapped[str] = mapped_column(String(80), nullable=False)
    entity_id: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    ip_address: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    details: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False, index=True
    )
