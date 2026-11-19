from datetime import date, datetime
from typing import Optional

from sqlalchemy import (
    Boolean,
    Date,
    DateTime,
    Enum as SAEnum,
    Float,
    ForeignKey,
    Index,
    Integer,
    JSON,
    String,
    Text,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base
from app.models.enums import (
    AIAnalysisStatus,
    AIReviewStatus,
    DentitionType,
    FindingStatus,
    FollowUpStatus,
    ImageQualityStatus,
    ReferralUrgency,
    ScreeningOutcome,
)


class ClinicalFinding(Base):
    __tablename__ = "clinical_findings"
    __table_args__ = (
        Index("ix_clinical_findings_region", "screening_id", "anatomical_region", "sub_region"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    anatomical_region: Mapped[str] = mapped_column(String(100), nullable=False)
    sub_region: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    status: Mapped[FindingStatus] = mapped_column(
        SAEnum(FindingStatus), default=FindingStatus.NOT_EXAMINED, nullable=False
    )
    remarks: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    reference_image_key: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)

    screening = relationship("ScreeningRecord", back_populates="clinical_findings")
    observations = relationship(
        "ClinicalObservation", back_populates="clinical_finding", cascade="all, delete-orphan"
    )


class ClinicalObservation(Base):
    __tablename__ = "clinical_observations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    clinical_finding_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("clinical_findings.id", ondelete="CASCADE"), nullable=False, index=True
    )
    observation_code: Mapped[str] = mapped_column(String(80), nullable=False)
    observation_label: Mapped[str] = mapped_column(String(200), nullable=False)
    details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    clinical_finding = relationship("ClinicalFinding", back_populates="observations")


class LesionDetail(Base):
    __tablename__ = "lesion_details"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    anatomical_location: Mapped[str] = mapped_column(String(150), nullable=False)
    lesion_type: Mapped[str] = mapped_column(String(150), nullable=False)
    size: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    appearance: Mapped[Optional[str]] = mapped_column(String(200), nullable=True)
    duration: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    symptoms: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    clinical_observations: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    remarks: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    referral_recommendation: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)

    screening = relationship("ScreeningRecord", back_populates="lesion_details")


class TeethExamination(Base):
    __tablename__ = "teeth_examinations"
    __table_args__ = (
        Index("ix_teeth_exam_screening_tooth", "screening_id", "tooth_number"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    dentition_type: Mapped[DentitionType] = mapped_column(
        SAEnum(DentitionType), default=DentitionType.PERMANENT, nullable=False
    )
    tooth_number: Mapped[str] = mapped_column(String(10), nullable=False)  # FDI notation e.g. 11..48, 51..85
    status: Mapped[str] = mapped_column(String(60), default="HEALTHY", nullable=False)
    clinical_finding: Mapped[Optional[str]] = mapped_column(String(200), nullable=True)
    remarks: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    screening = relationship("ScreeningRecord", back_populates="teeth_examinations")
    surfaces = relationship(
        "ToothSurface", back_populates="tooth_examination", cascade="all, delete-orphan"
    )


class ToothSurface(Base):
    __tablename__ = "tooth_surfaces"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    teeth_examination_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("teeth_examinations.id", ondelete="CASCADE"), nullable=False, index=True
    )
    surface_code: Mapped[str] = mapped_column(String(20), nullable=False)  # M, D, O, B, L, I
    condition: Mapped[str] = mapped_column(String(100), nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    tooth_examination = relationship("TeethExamination", back_populates="surfaces")


class LabReferenceRange(Base):
    __tablename__ = "lab_reference_ranges"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    parameter_key: Mapped[str] = mapped_column(String(60), unique=True, nullable=False, index=True)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    unit: Mapped[str] = mapped_column(String(40), nullable=False)
    min_value: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    max_value: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    standard_source: Mapped[Optional[str]] = mapped_column(String(200), nullable=True)
    updated_by_user_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class LabInvestigation(Base):
    __tablename__ = "lab_investigations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    test_date: Mapped[Optional[date]] = mapped_column(Date, nullable=True)
    haemoglobin: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    haemoglobin_unit: Mapped[Optional[str]] = mapped_column(String(30), default="g/dL")
    rbc: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    rbc_unit: Mapped[Optional[str]] = mapped_column(String(30), default="million/uL")
    total_count: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    total_count_unit: Mapped[Optional[str]] = mapped_column(String(30), default="cells/uL")
    platelet_count: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    platelet_count_unit: Mapped[Optional[str]] = mapped_column(String(30), default="lakh/uL")
    percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    percentage_label: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    biopsy_report_summary: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    biopsy_attachment_path: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    biopsy_attachment_name: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    reference_ranges_snapshot: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    laboratory_remarks: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    screening = relationship("ScreeningRecord", back_populates="lab_investigation")


class PatientImage(Base):
    __tablename__ = "patient_images"
    __table_args__ = (
        Index("ix_patient_images_patient_screening", "patient_id", "screening_id"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    image_uid: Mapped[str] = mapped_column(String(40), unique=True, nullable=False, index=True)
    patient_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    clinical_region: Mapped[str] = mapped_column(String(120), nullable=False)
    sub_region: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    storage_path: Mapped[str] = mapped_column(String(500), nullable=False)
    original_filename: Mapped[str] = mapped_column(String(255), nullable=False)
    mime_type: Mapped[str] = mapped_column(String(80), nullable=False)
    file_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    width_px: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    height_px: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    capture_date: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    uploaded_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    image_metadata: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    screening = relationship("ScreeningRecord", back_populates="patient_images")
    ai_analyses = relationship(
        "AIAnalysis", back_populates="patient_image", order_by="desc(AIAnalysis.analyzed_at)"
    )


class AIAnalysis(Base):
    __tablename__ = "ai_analysis"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    patient_image_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("patient_images.id", ondelete="CASCADE"), nullable=False, index=True
    )
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    status: Mapped[AIAnalysisStatus] = mapped_column(
        SAEnum(AIAnalysisStatus), default=AIAnalysisStatus.PENDING, nullable=False
    )
    model_name: Mapped[str] = mapped_column(String(120), nullable=False)
    model_version: Mapped[str] = mapped_column(String(60), nullable=False)
    image_quality_status: Mapped[ImageQualityStatus] = mapped_column(
        SAEnum(ImageQualityStatus), default=ImageQualityStatus.ACCEPTABLE, nullable=False
    )
    quality_metrics: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    model_output: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    confidence_score: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    review_status: Mapped[AIReviewStatus] = mapped_column(
        SAEnum(AIReviewStatus), default=AIReviewStatus.PENDING_REVIEW, nullable=False, index=True
    )
    assistive_disclaimer: Mapped[str] = mapped_column(
        String(300),
        default="AI output is assistive only and is not a standalone clinical diagnosis. Final clinical interpretation must be made by a qualified clinician.",
        nullable=False,
    )
    analyzed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    patient_image = relationship("PatientImage", back_populates="ai_analyses")
    review_records = relationship(
        "AIReviewRecord", back_populates="ai_analysis", order_by="desc(AIReviewRecord.reviewed_at)"
    )


class AIReviewRecord(Base):
    __tablename__ = "ai_review_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    ai_analysis_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("ai_analysis.id", ondelete="CASCADE"), nullable=False, index=True
    )
    reviewer_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    review_status: Mapped[AIReviewStatus] = mapped_column(SAEnum(AIReviewStatus), nullable=False)
    clinician_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    corrected_findings: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    reviewed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    ai_analysis = relationship("AIAnalysis", back_populates="review_records")


class ConsultationSummary(Base):
    __tablename__ = "consultation_summaries"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    outcome: Mapped[ScreeningOutcome] = mapped_column(SAEnum(ScreeningOutcome), nullable=False)
    outcome_reason: Mapped[str] = mapped_column(Text, nullable=False)
    clinical_recommendations: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    summary_payload: Mapped[dict] = mapped_column(JSON, nullable=False)
    generated_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    generated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    screening = relationship("ScreeningRecord", back_populates="consultation_summary")


class FollowUpRecord(Base):
    __tablename__ = "follow_up_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    patient_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    scheduled_date: Mapped[date] = mapped_column(Date, nullable=False)
    status: Mapped[FollowUpStatus] = mapped_column(
        SAEnum(FollowUpStatus), default=FollowUpStatus.SCHEDULED, nullable=False
    )
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    screening = relationship("ScreeningRecord", back_populates="follow_ups")


class ReferralRecord(Base):
    __tablename__ = "referral_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    patient_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    referred_to: Mapped[str] = mapped_column(String(200), nullable=False)
    department: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    urgency: Mapped[ReferralUrgency] = mapped_column(
        SAEnum(ReferralUrgency), default=ReferralUrgency.ROUTINE, nullable=False
    )
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(40), default="OPEN", nullable=False)
    created_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    screening = relationship("ScreeningRecord", back_populates="referrals")
