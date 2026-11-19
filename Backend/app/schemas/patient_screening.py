from datetime import date, datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.enums import (
    AIAnalysisStatus,
    AIReviewStatus,
    DentitionType,
    FindingStatus,
    FollowUpStatus,
    ImageQualityStatus,
    ReferralUrgency,
    ScreeningOutcome,
    ScreeningWorkflowStatus,
    SexType,
)


# Step 1 - Consent & Location
class ConsentStepInput(BaseModel):
    hub: str = Field(..., min_length=1, max_length=150)
    health_partner: str = Field(..., min_length=1, max_length=150)
    healthworker_name: str = Field(..., min_length=1, max_length=150)
    participant_info_sheet_ack: bool = False
    consent_form_signed: bool = False
    consent_accepted: bool = False
    terms_accepted: bool = False
    geolocation_label: Optional[str] = None
    latitude: Optional[float] = Field(default=None, ge=-90.0, le=90.0)
    longitude: Optional[float] = Field(default=None, ge=-180.0, le=180.0)
    location_permission_granted: bool = False


class ConsentRecordResponse(ConsentStepInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    patient_id: int
    recorded_at: datetime


# Step 2 - Personal Details (Patient)
class PatientCreateRequest(BaseModel):
    idempotency_key: Optional[str] = Field(default=None, max_length=100)
    center_id: Optional[int] = None
    registration_date: date = Field(default_factory=date.today)
    prefix: Optional[str] = Field(default=None, max_length=20)
    first_name: str = Field(..., min_length=1, max_length=100)
    last_name: str = Field(..., min_length=1, max_length=100)
    age: int = Field(..., ge=0, le=130)
    sex: SexType
    religion: Optional[str] = None
    marital_status: Optional[str] = None
    education: Optional[str] = None
    occupation: Optional[str] = None
    family_income_per_month: Optional[str] = None
    phone_number: str = Field(..., min_length=7, max_length=25)
    address: str = Field(..., min_length=2)

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, v: str) -> str:
        digits = [c for c in v if c.isdigit()]
        if len(digits) < 7 or len(digits) > 15:
            raise ValueError("Phone number must contain between 7 and 15 digits.")
        return v.strip()


class PatientUpdateRequest(BaseModel):
    prefix: Optional[str] = None
    first_name: Optional[str] = Field(default=None, min_length=1, max_length=100)
    last_name: Optional[str] = Field(default=None, min_length=1, max_length=100)
    age: Optional[int] = Field(default=None, ge=0, le=130)
    sex: Optional[SexType] = None
    religion: Optional[str] = None
    marital_status: Optional[str] = None
    education: Optional[str] = None
    occupation: Optional[str] = None
    family_income_per_month: Optional[str] = None
    phone_number: Optional[str] = None
    address: Optional[str] = None


class PatientResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    patient_uid: str
    college_id: int
    center_id: int
    assigned_organizer_id: Optional[int] = None
    registration_date: date
    prefix: Optional[str] = None
    first_name: str
    last_name: str
    age: int
    sex: SexType
    religion: Optional[str] = None
    marital_status: Optional[str] = None
    education: Optional[str] = None
    occupation: Optional[str] = None
    family_income_per_month: Optional[str] = None
    phone_number: str
    address: str
    created_at: datetime
    latest_outcome: Optional[ScreeningOutcome] = None
    latest_lesion_present: Optional[bool] = None
    latest_review_status: Optional[str] = None
    total_screenings: int = 0


class PaginatedPatientsResponse(BaseModel):
    items: List[PatientResponse]
    total: int
    page: int
    page_size: int


# Step 3 - Personal History
class PersonalHistoryInput(BaseModel):
    diet_history: str = Field(..., min_length=1, max_length=100)
    spice_consumption: str = Field(..., min_length=1, max_length=100)
    has_habit_history: bool = False
    notes: Optional[str] = None


class PersonalHistoryResponse(PersonalHistoryInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int


# Step 4 - Habit History
class HabitItemInput(BaseModel):
    habit_category: str = Field(..., min_length=2, max_length=50)  # TOBACCO, ARECANUT, ALCOHOL, OTHERS
    habit_type: Optional[str] = None
    frequency: Optional[str] = None
    duration: Optional[str] = None
    quantity: Optional[str] = None
    usage_status: str = Field(default="CURRENT")
    remarks: Optional[str] = None


class HabitHistoryResponse(HabitItemInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int


# Step 5 - Medical & Dental History
class MedicalDentalHistoryInput(BaseModel):
    cleaning_aid: str = Field(..., min_length=1, max_length=80)
    material_used: str = Field(..., min_length=1, max_length=80)
    material_other: Optional[str] = None
    brushing_method: Optional[str] = None
    brushing_frequency: Optional[str] = None
    brushing_duration: Optional[str] = None
    toothbrush_change_frequency: Optional[str] = None
    other_hygiene_aids: Optional[str] = None
    family_history_conditions: List[str] = Field(default_factory=list)
    family_history_other: Optional[str] = None
    past_medical_conditions: List[str] = Field(default_factory=list)
    past_medical_other: Optional[str] = None
    current_medications: Optional[str] = None
    allergies: Optional[str] = None
    past_dental_history: bool = False
    past_dental_details: Optional[str] = None
    denture_usage: bool = False
    denture_details: Optional[str] = None


class MedicalHistoryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    family_history_conditions: List[str]
    family_history_other: Optional[str] = None
    past_medical_conditions: List[str]
    past_medical_other: Optional[str] = None
    current_medications: Optional[str] = None
    allergies: Optional[str] = None


class DentalHistoryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    cleaning_aid: str
    material_used: str
    material_other: Optional[str] = None
    brushing_method: Optional[str] = None
    brushing_frequency: Optional[str] = None
    brushing_duration: Optional[str] = None
    toothbrush_change_frequency: Optional[str] = None
    other_hygiene_aids: Optional[str] = None
    past_dental_history: bool
    past_dental_details: Optional[str] = None
    denture_usage: bool
    denture_details: Optional[str] = None


# Step 6 - Clinical Findings
class ClinicalObservationInput(BaseModel):
    observation_code: str = Field(..., min_length=1, max_length=80)
    observation_label: str = Field(..., min_length=1, max_length=200)
    details: Optional[str] = None


class ClinicalObservationResponse(ClinicalObservationInput):
    model_config = ConfigDict(from_attributes=True)
    id: int


class ClinicalFindingInput(BaseModel):
    anatomical_region: str = Field(..., min_length=1, max_length=100)
    sub_region: Optional[str] = None
    status: FindingStatus = FindingStatus.NOT_EXAMINED
    remarks: Optional[str] = None
    reference_image_key: Optional[str] = None
    observations: List[ClinicalObservationInput] = Field(default_factory=list)


class ClinicalFindingResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    anatomical_region: str
    sub_region: Optional[str] = None
    status: FindingStatus
    remarks: Optional[str] = None
    reference_image_key: Optional[str] = None
    observations: List[ClinicalObservationResponse] = Field(default_factory=list)


# Step 7 - Lesion Details
class LesionDetailInput(BaseModel):
    anatomical_location: str = Field(..., min_length=1, max_length=150)
    lesion_type: str = Field(..., min_length=1, max_length=150)
    size: Optional[str] = None
    appearance: Optional[str] = None
    duration: Optional[str] = None
    symptoms: Optional[str] = None
    clinical_observations: Optional[str] = None
    remarks: Optional[str] = None
    referral_recommendation: Optional[str] = None


class LesionBatchInput(BaseModel):
    lesion_present: bool
    lesions: List[LesionDetailInput] = Field(default_factory=list)


class LesionDetailResponse(LesionDetailInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int


# Step 8 - Teeth Examination
class ToothSurfaceInput(BaseModel):
    surface_code: str = Field(..., min_length=1, max_length=20)
    condition: str = Field(..., min_length=1, max_length=100)
    notes: Optional[str] = None


class ToothSurfaceResponse(ToothSurfaceInput):
    model_config = ConfigDict(from_attributes=True)
    id: int


class ToothExaminationInput(BaseModel):
    dentition_type: DentitionType = DentitionType.PERMANENT
    tooth_number: str = Field(..., min_length=1, max_length=10)
    status: str = Field(default="HEALTHY", max_length=60)
    clinical_finding: Optional[str] = None
    remarks: Optional[str] = None
    surfaces: List[ToothSurfaceInput] = Field(default_factory=list)


class ToothExaminationResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    dentition_type: DentitionType
    tooth_number: str
    status: str
    clinical_finding: Optional[str] = None
    remarks: Optional[str] = None
    surfaces: List[ToothSurfaceResponse] = Field(default_factory=list)


# Step 9 - Lab Investigation
class LabReferenceRangeInput(BaseModel):
    parameter_key: str = Field(..., min_length=2, max_length=60)
    display_name: str = Field(..., min_length=2, max_length=120)
    unit: str = Field(..., min_length=1, max_length=40)
    min_value: Optional[float] = None
    max_value: Optional[float] = None
    standard_source: Optional[str] = None


class LabReferenceRangeResponse(LabReferenceRangeInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    updated_at: datetime


class LabInvestigationInput(BaseModel):
    test_date: Optional[date] = None
    haemoglobin: Optional[float] = None
    haemoglobin_unit: Optional[str] = "g/dL"
    rbc: Optional[float] = None
    rbc_unit: Optional[str] = "million/uL"
    total_count: Optional[float] = None
    total_count_unit: Optional[str] = "cells/uL"
    platelet_count: Optional[float] = None
    platelet_count_unit: Optional[str] = "lakh/uL"
    percentage: Optional[float] = None
    percentage_label: Optional[str] = None
    biopsy_report_summary: Optional[str] = None
    laboratory_remarks: Optional[str] = None


class LabInvestigationResponse(LabInvestigationInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    biopsy_attachment_name: Optional[str] = None
    reference_ranges_snapshot: Optional[Dict[str, Any]] = None


# Step 10 - Images & AI Analysis
class AIReviewCreateRequest(BaseModel):
    review_status: AIReviewStatus
    clinician_notes: Optional[str] = None
    corrected_findings: Optional[Dict[str, Any]] = None


class AIReviewRecordResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    ai_analysis_id: int
    reviewer_user_id: int
    review_status: AIReviewStatus
    clinician_notes: Optional[str] = None
    corrected_findings: Optional[Dict[str, Any]] = None
    reviewed_at: datetime


class AIAnalysisResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    patient_image_id: int
    screening_id: int
    status: AIAnalysisStatus
    model_name: str
    model_version: str
    image_quality_status: ImageQualityStatus
    quality_metrics: Optional[Dict[str, Any]] = None
    model_output: Optional[Dict[str, Any]] = None
    confidence_score: Optional[float] = None
    review_status: AIReviewStatus
    assistive_disclaimer: str
    analyzed_at: datetime
    review_records: List[AIReviewRecordResponse] = Field(default_factory=list)


class PatientImageResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    image_uid: str
    patient_id: int
    screening_id: int
    clinical_region: str
    sub_region: Optional[str] = None
    original_filename: str
    mime_type: str
    file_size_bytes: int
    width_px: Optional[int] = None
    height_px: Optional[int] = None
    capture_date: datetime
    uploaded_by_user_id: int
    image_metadata: Optional[Dict[str, Any]] = None
    ai_analyses: List[AIAnalysisResponse] = Field(default_factory=list)


# Step 11 - Screening Creation / Submission / Review / Summary
class ScreeningStartRequest(BaseModel):
    patient_id: int
    idempotency_key: Optional[str] = Field(default=None, max_length=100)
    screening_date: date = Field(default_factory=date.today)


class CompleteScreeningBundleRequest(BaseModel):
    """Atomic 11-step submission payload used for online submission and offline sync."""

    idempotency_key: Optional[str] = Field(default=None, max_length=100)
    existing_patient_id: Optional[int] = None
    patient: Optional[PatientCreateRequest] = None
    screening_date: date = Field(default_factory=date.today)
    consent: ConsentStepInput
    personal_history: PersonalHistoryInput
    habits: List[HabitItemInput] = Field(default_factory=list)
    medical_dental_history: MedicalDentalHistoryInput
    clinical_findings: List[ClinicalFindingInput] = Field(default_factory=list)
    lesion_present: bool = False
    lesions: List[LesionDetailInput] = Field(default_factory=list)
    teeth_examinations: List[ToothExaminationInput] = Field(default_factory=list)
    lab_investigation: Optional[LabInvestigationInput] = None
    clinical_recommendations: Optional[str] = None
    submit_immediately: bool = True


class ScreeningSubmitRequest(BaseModel):
    clinical_recommendations: Optional[str] = None


class ClinicianOutcomeReviewRequest(BaseModel):
    outcome: ScreeningOutcome
    clinician_review_status: str = Field(default="REVIEWED")
    clinical_recommendations: Optional[str] = None
    outcome_reason: Optional[str] = None


class ConsultationSummaryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_id: int
    outcome: ScreeningOutcome
    outcome_reason: str
    clinical_recommendations: Optional[str] = None
    summary_payload: Dict[str, Any]
    generated_at: datetime


class FollowUpCreateRequest(BaseModel):
    scheduled_date: date
    notes: Optional[str] = None


class FollowUpUpdateRequest(BaseModel):
    status: FollowUpStatus
    notes: Optional[str] = None


class FollowUpResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    patient_id: int
    screening_id: int
    scheduled_date: date
    status: FollowUpStatus
    notes: Optional[str] = None
    created_at: datetime


class ReferralCreateRequest(BaseModel):
    referred_to: str = Field(..., min_length=2, max_length=200)
    department: Optional[str] = None
    urgency: ReferralUrgency = ReferralUrgency.ROUTINE
    reason: str = Field(..., min_length=2)


class ReferralResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    patient_id: int
    screening_id: int
    referred_to: str
    department: Optional[str] = None
    urgency: ReferralUrgency
    reason: str
    status: str
    created_at: datetime


class ScreeningDetailResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    screening_uid: str
    patient_id: int
    college_id: int
    center_id: int
    organizer_user_id: int
    screening_date: date
    workflow_status: ScreeningWorkflowStatus
    current_step: int
    outcome: ScreeningOutcome
    lesion_present: bool
    clinician_review_status: str
    submitted_at: Optional[datetime] = None
    reviewed_at: Optional[datetime] = None
    created_at: datetime
    patient: Optional[PatientResponse] = None
    consent_record: Optional[ConsentRecordResponse] = None
    personal_history: Optional[PersonalHistoryResponse] = None
    habit_histories: List[HabitHistoryResponse] = Field(default_factory=list)
    medical_history: Optional[MedicalHistoryResponse] = None
    dental_history: Optional[DentalHistoryResponse] = None
    clinical_findings: List[ClinicalFindingResponse] = Field(default_factory=list)
    lesion_details: List[LesionDetailResponse] = Field(default_factory=list)
    teeth_examinations: List[ToothExaminationResponse] = Field(default_factory=list)
    lab_investigation: Optional[LabInvestigationResponse] = None
    patient_images: List[PatientImageResponse] = Field(default_factory=list)
    consultation_summary: Optional[ConsultationSummaryResponse] = None
    follow_ups: List[FollowUpResponse] = Field(default_factory=list)
    referrals: List[ReferralResponse] = Field(default_factory=list)
