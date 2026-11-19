import enum


class UserRole(str, enum.Enum):
    SUPER_ADMIN = "SUPER_ADMIN"
    COLLEGE_ADMIN = "COLLEGE_ADMIN"
    CENTER_ADMIN = "CENTER_ADMIN"
    HEALTH_ORGANIZER = "HEALTH_ORGANIZER"


class SexType(str, enum.Enum):
    MALE = "MALE"
    FEMALE = "FEMALE"
    OTHER = "OTHER"


class ScreeningOutcome(str, enum.Enum):
    NORMAL = "NORMAL"
    ABNORMAL = "ABNORMAL"
    PENDING_CLINICAL_REVIEW = "PENDING_CLINICAL_REVIEW"


class ScreeningWorkflowStatus(str, enum.Enum):
    DRAFT = "DRAFT"
    SUBMITTED = "SUBMITTED"
    REVIEWED = "REVIEWED"


class FindingStatus(str, enum.Enum):
    NOT_EXAMINED = "NOT_EXAMINED"
    NORMAL = "NORMAL"
    ABNORMAL = "ABNORMAL"


class DentitionType(str, enum.Enum):
    PERMANENT = "PERMANENT"
    PRIMARY = "PRIMARY"


class AIAnalysisStatus(str, enum.Enum):
    PENDING = "PENDING"
    COMPLETED = "COMPLETED"
    MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE"
    FAILED = "FAILED"


class AIReviewStatus(str, enum.Enum):
    PENDING_REVIEW = "PENDING_REVIEW"
    REVIEWED = "REVIEWED"
    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"
    NEEDS_FURTHER_EXAMINATION = "NEEDS_FURTHER_EXAMINATION"


class ImageQualityStatus(str, enum.Enum):
    ACCEPTABLE = "ACCEPTABLE"
    LOW_RESOLUTION = "LOW_RESOLUTION"
    BLURRY = "BLURRY"
    POOR_LIGHTING = "POOR_LIGHTING"
    INVALID = "INVALID"


class EducationResourceType(str, enum.Enum):
    MANUAL_PDF = "MANUAL_PDF"
    YOUTUBE_VIDEO = "YOUTUBE_VIDEO"
    DOCUMENT = "DOCUMENT"
    IMAGE = "IMAGE"


class AwarenessContentType(str, enum.Enum):
    ARTICLE = "ARTICLE"
    POSTER = "POSTER"
    INFOGRAPHIC = "INFOGRAPHIC"


class FollowUpStatus(str, enum.Enum):
    SCHEDULED = "SCHEDULED"
    COMPLETED = "COMPLETED"
    MISSED = "MISSED"
    CANCELLED = "CANCELLED"


class ReferralUrgency(str, enum.Enum):
    ROUTINE = "ROUTINE"
    PRIORITY = "PRIORITY"
    URGENT = "URGENT"
