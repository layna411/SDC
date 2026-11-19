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
from app.models.enums import ScreeningOutcome, ScreeningWorkflowStatus, SexType
from app.models.organization import TimestampSoftDeleteMixin


class Patient(Base, TimestampSoftDeleteMixin):
    __tablename__ = "patients"
    __table_args__ = (
        Index("ix_patients_scope", "college_id", "center_id", "assigned_organizer_id"),
        Index("ix_patients_name", "first_name", "last_name"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    patient_uid: Mapped[str] = mapped_column(String(40), unique=True, nullable=False, index=True)
    idempotency_key: Mapped[Optional[str]] = mapped_column(
        String(100), unique=True, nullable=True, index=True
    )

    college_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("colleges.id", ondelete="RESTRICT"), nullable=False, index=True
    )
    center_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("centers.id", ondelete="RESTRICT"), nullable=False, index=True
    )
    assigned_organizer_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    created_by_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )

    registration_date: Mapped[date] = mapped_column(Date, nullable=False)
    prefix: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    first_name: Mapped[str] = mapped_column(String(100), nullable=False)
    last_name: Mapped[str] = mapped_column(String(100), nullable=False)
    age: Mapped[int] = mapped_column(Integer, nullable=False)
    sex: Mapped[SexType] = mapped_column(SAEnum(SexType), nullable=False, index=True)
    religion: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    marital_status: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    education: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    occupation: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    family_income_per_month: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    phone_number: Mapped[str] = mapped_column(String(25), nullable=False, index=True)
    address: Mapped[str] = mapped_column(Text, nullable=False)

    screenings = relationship(
        "ScreeningRecord", back_populates="patient", order_by="desc(ScreeningRecord.created_at)"
    )


class ScreeningRecord(Base, TimestampSoftDeleteMixin):
    __tablename__ = "screening_records"
    __table_args__ = (
        Index("ix_screenings_scope_outcome", "college_id", "center_id", "outcome"),
        Index("ix_screenings_date", "screening_date"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_uid: Mapped[str] = mapped_column(String(40), unique=True, nullable=False, index=True)
    idempotency_key: Mapped[Optional[str]] = mapped_column(
        String(100), unique=True, nullable=True, index=True
    )

    patient_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    college_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("colleges.id", ondelete="RESTRICT"), nullable=False, index=True
    )
    center_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("centers.id", ondelete="RESTRICT"), nullable=False, index=True
    )
    organizer_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False, index=True
    )

    screening_date: Mapped[date] = mapped_column(Date, nullable=False)
    workflow_status: Mapped[ScreeningWorkflowStatus] = mapped_column(
        SAEnum(ScreeningWorkflowStatus),
        default=ScreeningWorkflowStatus.DRAFT,
        nullable=False,
        index=True,
    )
    current_step: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    outcome: Mapped[ScreeningOutcome] = mapped_column(
        SAEnum(ScreeningOutcome),
        default=ScreeningOutcome.PENDING_CLINICAL_REVIEW,
        nullable=False,
        index=True,
    )
    lesion_present: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False, index=True)
    clinician_review_status: Mapped[str] = mapped_column(
        String(40), default="PENDING_REVIEW", nullable=False
    )
    reviewed_by_user_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    submitted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    patient = relationship("Patient", back_populates="screenings")
    consent_record = relationship("ConsentRecord", back_populates="screening", uselist=False)
    personal_history = relationship("PersonalHistory", back_populates="screening", uselist=False)
    habit_histories = relationship("HabitHistory", back_populates="screening")
    medical_history = relationship("MedicalHistory", back_populates="screening", uselist=False)
    dental_history = relationship("DentalHistory", back_populates="screening", uselist=False)
    clinical_findings = relationship("ClinicalFinding", back_populates="screening")
    lesion_details = relationship("LesionDetail", back_populates="screening")
    teeth_examinations = relationship("TeethExamination", back_populates="screening")
    lab_investigation = relationship("LabInvestigation", back_populates="screening", uselist=False)
    patient_images = relationship("PatientImage", back_populates="screening")
    consultation_summary = relationship(
        "ConsultationSummary", back_populates="screening", uselist=False
    )
    follow_ups = relationship("FollowUpRecord", back_populates="screening")
    referrals = relationship("ReferralRecord", back_populates="screening")


class ConsentRecord(Base):
    __tablename__ = "consent_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    patient_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    hub: Mapped[str] = mapped_column(String(150), nullable=False)
    health_partner: Mapped[str] = mapped_column(String(150), nullable=False)
    healthworker_name: Mapped[str] = mapped_column(String(150), nullable=False)
    participant_info_sheet_ack: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    consent_form_signed: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    consent_accepted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    terms_accepted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    geolocation_label: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    latitude: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    longitude: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    location_permission_granted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    screening = relationship("ScreeningRecord", back_populates="consent_record")


class PersonalHistory(Base):
    __tablename__ = "personal_histories"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    diet_history: Mapped[str] = mapped_column(String(100), nullable=False)
    spice_consumption: Mapped[str] = mapped_column(String(100), nullable=False)
    has_habit_history: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    screening = relationship("ScreeningRecord", back_populates="personal_history")


class HabitHistory(Base):
    __tablename__ = "habit_histories"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), nullable=False, index=True
    )
    habit_category: Mapped[str] = mapped_column(
        String(50), nullable=False
    )  # TOBACCO, ARECANUT, ALCOHOL, OTHERS
    habit_type: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    frequency: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    duration: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    quantity: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    usage_status: Mapped[str] = mapped_column(
        String(40), default="CURRENT", nullable=False
    )  # CURRENT / PAST
    remarks: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    screening = relationship("ScreeningRecord", back_populates="habit_histories")


class MedicalHistory(Base):
    __tablename__ = "medical_histories"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    family_history_conditions: Mapped[list] = mapped_column(JSON, default=list, nullable=False)
    family_history_other: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    past_medical_conditions: Mapped[list] = mapped_column(JSON, default=list, nullable=False)
    past_medical_other: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    current_medications: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    allergies: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    screening = relationship("ScreeningRecord", back_populates="medical_history")


class DentalHistory(Base):
    __tablename__ = "dental_histories"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    screening_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("screening_records.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    cleaning_aid: Mapped[str] = mapped_column(String(80), nullable=False)
    material_used: Mapped[str] = mapped_column(String(80), nullable=False)
    material_other: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    brushing_method: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    brushing_frequency: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    brushing_duration: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    toothbrush_change_frequency: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    other_hygiene_aids: Mapped[Optional[str]] = mapped_column(String(150), nullable=True)
    past_dental_history: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    past_dental_details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    denture_usage: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    denture_details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    screening = relationship("ScreeningRecord", back_populates="dental_history")
