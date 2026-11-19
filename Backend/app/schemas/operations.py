from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.models.enums import AwarenessContentType, EducationResourceType


# Feedback
class FeedbackCreateRequest(BaseModel):
    patient_id: Optional[int] = None
    patient_name: str = Field(..., min_length=2, max_length=150)
    email: EmailStr
    rating: int = Field(..., ge=1, le=5)
    message: str = Field(..., min_length=2, max_length=2000)


class FeedbackUpdateRequest(BaseModel):
    patient_name: Optional[str] = Field(default=None, min_length=2, max_length=150)
    email: Optional[EmailStr] = None
    rating: Optional[int] = Field(default=None, ge=1, le=5)
    message: Optional[str] = Field(default=None, min_length=2, max_length=2000)


class FeedbackResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    college_id: Optional[int] = None
    center_id: Optional[int] = None
    patient_id: Optional[int] = None
    created_by_user_id: int
    patient_name: str
    email: str
    rating: int
    message: str
    created_at: datetime


class FeedbackSummaryResponse(BaseModel):
    items: List[FeedbackResponse]
    total_count: int
    average_rating: float


# Educational Resources & Awareness
class EducationalResourceCreateRequest(BaseModel):
    title: str = Field(..., min_length=2, max_length=200)
    description: Optional[str] = None
    category: str = Field(default="USER_MANUAL", max_length=80)
    language: str = Field(default="English", max_length=30)
    resource_type: EducationResourceType
    external_url: Optional[str] = None
    is_published: bool = True


class EducationalResourceUpdateRequest(BaseModel):
    title: Optional[str] = Field(default=None, min_length=2, max_length=200)
    description: Optional[str] = None
    category: Optional[str] = None
    language: Optional[str] = None
    external_url: Optional[str] = None
    is_published: Optional[bool] = None


class EducationalResourceResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    title: str
    description: Optional[str] = None
    category: str
    language: str
    resource_type: EducationResourceType
    external_url: Optional[str] = None
    file_name: Optional[str] = None
    mime_type: Optional[str] = None
    college_id: Optional[int] = None
    is_published: bool
    created_at: datetime


class AwarenessCreateRequest(BaseModel):
    title: str = Field(..., min_length=2, max_length=200)
    summary: Optional[str] = Field(default=None, max_length=500)
    body: str = Field(..., min_length=5)
    category: str = Field(default="ORAL_HYGIENE", max_length=80)
    language: str = Field(default="English", max_length=30)
    content_type: AwarenessContentType = AwarenessContentType.ARTICLE
    is_published: bool = True


class AwarenessUpdateRequest(BaseModel):
    title: Optional[str] = Field(default=None, min_length=2, max_length=200)
    summary: Optional[str] = None
    body: Optional[str] = None
    category: Optional[str] = None
    language: Optional[str] = None
    content_type: Optional[AwarenessContentType] = None
    is_published: Optional[bool] = None


class AwarenessContentResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    title: str
    summary: Optional[str] = None
    body: str
    category: str
    language: str
    content_type: AwarenessContentType
    poster_file_name: Optional[str] = None
    college_id: Optional[int] = None
    is_published: bool
    created_at: datetime


# Activity Logs
class ActivityLogResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: Optional[int] = None
    username: Optional[str] = None
    user_full_name: Optional[str] = None
    role: str
    college_id: Optional[int] = None
    center_id: Optional[int] = None
    action: str
    entity_type: str
    entity_id: Optional[str] = None
    ip_address: Optional[str] = None
    details: Optional[Dict[str, Any]] = None
    created_at: datetime


# Dashboard & Reports
class WeeklyPoint(BaseModel):
    date: str
    label: str
    count: int


class MonthlyOverview(BaseModel):
    new_patients: int
    lesions_detected: int
    reports_generated: int
    pending_reviews: int


class DashboardStatsResponse(BaseModel):
    role: str
    user_full_name: str
    organization_label: str
    total_colleges: int = 0
    total_centers: int = 0
    total_organizers: int = 0
    total_patients: int
    male_patients: int
    female_patients: int
    other_patients: int
    total_screenings: int
    normal_screenings: int
    abnormal_screenings: int
    pending_review_screenings: int
    lesions_detected: int
    total_feedback: int
    average_feedback_rating: float
    weekly_screenings: List[WeeklyPoint]
    monthly_overview: MonthlyOverview
    recent_activities: List[ActivityLogResponse]


class BreakdownItem(BaseModel):
    id: int
    name: str
    total_patients: int
    total_screenings: int
    normal_screenings: int
    abnormal_screenings: int
    pending_reviews: int
    lesions_detected: int


class ReportAnalyticsResponse(BaseModel):
    generated_at: datetime
    total_patients: int
    male_patients: int
    female_patients: int
    other_patients: int
    total_screenings: int
    normal_screenings: int
    abnormal_screenings: int
    pending_reviews: int
    lesions_detected: int
    lab_investigations_count: int
    follow_ups_scheduled: int
    follow_ups_completed: int
    weekly_series: List[WeeklyPoint]
    monthly_series: List[WeeklyPoint]
    college_breakdown: List[BreakdownItem]
    center_breakdown: List[BreakdownItem]
    organizer_breakdown: List[BreakdownItem]


# App Settings
class AppSettingUpsertRequest(BaseModel):
    setting_key: str = Field(..., min_length=2, max_length=100)
    setting_value: str
    description: Optional[str] = None


class AppSettingResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    setting_key: str
    setting_value: str
    description: Optional[str] = None
    updated_at: datetime
