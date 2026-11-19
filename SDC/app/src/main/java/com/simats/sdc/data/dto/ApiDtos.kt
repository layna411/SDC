package com.simats.sdc.data.dto

import com.google.gson.annotations.SerializedName

// --- Auth & Users ---
data class LoginRequestDto(
    val username: String,
    val password: String,
)

data class RefreshTokenRequestDto(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class LogoutRequestDto(
    @SerializedName("refresh_token") val refreshToken: String? = null,
)

data class ChangePasswordRequestDto(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String,
)

data class ForgotPasswordRequestDto(
    @SerializedName("username_or_email") val usernameOrEmail: String,
)

data class ResetPasswordRequestDto(
    @SerializedName("reset_token") val resetToken: String,
    @SerializedName("new_password") val newPassword: String,
)

data class MessageResponseDto(
    val message: String,
    @SerializedName("dev_reset_token") val devResetToken: String? = null,
)

data class UserDto(
    val id: Int,
    @SerializedName("user_uid") val userUid: String,
    val username: String,
    val email: String? = null,
    @SerializedName("full_name") val fullName: String,
    val phone: String? = null,
    val role: String,
    @SerializedName("college_id") val collegeId: Int? = null,
    @SerializedName("center_id") val centerId: Int? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("must_change_password") val mustChangePassword: Boolean,
    @SerializedName("last_login_at") val lastLoginAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class AuthTokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int,
    @SerializedName("must_change_password") val mustChangePassword: Boolean,
    val user: UserDto,
)

data class UserStatusUpdateDto(
    @SerializedName("is_active") val isActive: Boolean,
)

data class IssuedCredentialsDto(
    val title: String,
    @SerializedName("entity_name") val entityName: String? = null,
    val username: String,
    @SerializedName("user_uid") val userUid: String? = null,
    @SerializedName("temporary_password") val temporaryPassword: String,
    val role: String? = null,
)

// --- Organization Hierarchy ---
data class CollegeCreateDto(
    @SerializedName("college_code") val collegeCode: String,
    val name: String,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    @SerializedName("contact_email") val contactEmail: String? = null,
    @SerializedName("contact_phone") val contactPhone: String? = null,
    @SerializedName("create_admin_account") val createAdminAccount: Boolean = false,
    @SerializedName("admin_username") val adminUsername: String? = null,
    @SerializedName("admin_full_name") val adminFullName: String? = null,
    @SerializedName("admin_email") val adminEmail: String? = null,
    @SerializedName("admin_phone") val adminPhone: String? = null,
)

data class CollegeUpdateDto(
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    @SerializedName("contact_email") val contactEmail: String? = null,
    @SerializedName("contact_phone") val contactPhone: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
)

data class CollegeDto(
    val id: Int,
    @SerializedName("college_code") val collegeCode: String,
    val name: String,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    @SerializedName("contact_email") val contactEmail: String? = null,
    @SerializedName("contact_phone") val contactPhone: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("admin_username") val adminUsername: String? = null,
    @SerializedName("admin_user_id") val adminUserId: Int? = null,
    @SerializedName("has_admin") val hasAdmin: Boolean = false,
)

data class CollegeCreatedWithAdminDto(
    val college: CollegeDto,
    @SerializedName("admin_user") val adminUser: UserDto? = null,
    @SerializedName("temporary_password") val temporaryPassword: String? = null,
)

data class CenterCreateDto(
    @SerializedName("college_id") val collegeId: Int? = null,
    @SerializedName("center_code") val centerCode: String,
    val name: String,
    @SerializedName("hub_name") val hubName: String? = null,
    @SerializedName("health_partner") val healthPartner: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerializedName("contact_email") val contactEmail: String? = null,
    @SerializedName("contact_phone") val contactPhone: String? = null,
    @SerializedName("create_admin_account") val createAdminAccount: Boolean = false,
    @SerializedName("admin_username") val adminUsername: String? = null,
    @SerializedName("admin_full_name") val adminFullName: String? = null,
    @SerializedName("admin_email") val adminEmail: String? = null,
    @SerializedName("admin_phone") val adminPhone: String? = null,
)

data class CenterDto(
    val id: Int,
    @SerializedName("college_id") val collegeId: Int,
    @SerializedName("center_code") val centerCode: String,
    val name: String,
    @SerializedName("hub_name") val hubName: String? = null,
    @SerializedName("health_partner") val healthPartner: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerializedName("contact_email") val contactEmail: String? = null,
    @SerializedName("contact_phone") val contactPhone: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("admin_username") val adminUsername: String? = null,
    @SerializedName("admin_user_id") val adminUserId: Int? = null,
    @SerializedName("has_admin") val hasAdmin: Boolean = false,
)

data class CenterCreatedWithAdminDto(
    val center: CenterDto,
    @SerializedName("admin_user") val adminUser: UserDto? = null,
    @SerializedName("temporary_password") val temporaryPassword: String? = null,
)

data class OrganizerCreateDto(
    @SerializedName("center_id") val centerId: Int? = null,
    val username: String,
    @SerializedName("full_name") val fullName: String,
    val email: String? = null,
    val phone: String? = null,
    @SerializedName("employee_code") val employeeCode: String,
    val designation: String = "Health Organizer Assistant",
    val qualification: String? = null,
    @SerializedName("assigned_area") val assignedArea: String? = null,
)

data class OrganizerUpdateDto(
    @SerializedName("center_id") val centerId: Int? = null,
    val designation: String? = null,
    val qualification: String? = null,
    @SerializedName("assigned_area") val assignedArea: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
)

data class OrganizerDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("college_id") val collegeId: Int,
    @SerializedName("center_id") val centerId: Int,
    @SerializedName("employee_code") val employeeCode: String,
    val designation: String,
    val qualification: String? = null,
    @SerializedName("assigned_area") val assignedArea: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
    val user: UserDto? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class OrganizerCreatedDto(
    val organizer: OrganizerDto,
    @SerializedName("temporary_password") val temporaryPassword: String,
)

// --- Patients & 11-Step Screening ---
data class ConsentStepDto(
    val hub: String,
    @SerializedName("health_partner") val healthPartner: String,
    @SerializedName("healthworker_name") val healthworkerName: String,
    @SerializedName("participant_info_sheet_ack") val participantInfoSheetAck: Boolean,
    @SerializedName("consent_form_signed") val consentFormSigned: Boolean,
    @SerializedName("consent_accepted") val consentAccepted: Boolean,
    @SerializedName("terms_accepted") val termsAccepted: Boolean,
    @SerializedName("geolocation_label") val geolocationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("location_permission_granted") val locationPermissionGranted: Boolean = false,
)

data class PatientCreateDto(
    @SerializedName("idempotency_key") val idempotencyKey: String? = null,
    @SerializedName("center_id") val centerId: Int? = null,
    @SerializedName("registration_date") val registrationDate: String,
    val prefix: String? = null,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val age: Int,
    val sex: String,
    val religion: String? = null,
    @SerializedName("marital_status") val maritalStatus: String? = null,
    val education: String? = null,
    val occupation: String? = null,
    @SerializedName("family_income_per_month") val familyIncomePerMonth: String? = null,
    @SerializedName("phone_number") val phoneNumber: String,
    val address: String,
)

data class PatientDto(
    val id: Int,
    @SerializedName("patient_uid") val patientUid: String,
    @SerializedName("college_id") val collegeId: Int,
    @SerializedName("center_id") val centerId: Int,
    @SerializedName("assigned_organizer_id") val assignedOrganizerId: Int? = null,
    @SerializedName("registration_date") val registrationDate: String,
    val prefix: String? = null,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val age: Int,
    val sex: String,
    val religion: String? = null,
    @SerializedName("marital_status") val maritalStatus: String? = null,
    val education: String? = null,
    val occupation: String? = null,
    @SerializedName("family_income_per_month") val familyIncomePerMonth: String? = null,
    @SerializedName("phone_number") val phoneNumber: String,
    val address: String,
    @SerializedName("latest_outcome") val latestOutcome: String? = null,
    @SerializedName("latest_lesion_present") val latestLesionPresent: Boolean? = null,
    @SerializedName("latest_review_status") val latestReviewStatus: String? = null,
    @SerializedName("total_screenings") val totalScreenings: Int = 0,
) {
    val displayName: String get() = listOfNotNull(prefix, firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class PaginatedPatientsDto(
    val items: List<PatientDto>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int,
)

data class PersonalHistoryDto(
    @SerializedName("diet_history") val dietHistory: String,
    @SerializedName("spice_consumption") val spiceConsumption: String,
    @SerializedName("has_habit_history") val hasHabitHistory: Boolean,
    val notes: String? = null,
)

data class HabitItemDto(
    @SerializedName("habit_category") val habitCategory: String,
    @SerializedName("habit_type") val habitType: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val quantity: String? = null,
    @SerializedName("usage_status") val usageStatus: String = "CURRENT",
    val remarks: String? = null,
)

data class MedicalDentalHistoryDto(
    @SerializedName("cleaning_aid") val cleaningAid: String,
    @SerializedName("material_used") val materialUsed: String,
    @SerializedName("material_other") val materialOther: String? = null,
    @SerializedName("brushing_method") val brushingMethod: String? = null,
    @SerializedName("brushing_frequency") val brushingFrequency: String? = null,
    @SerializedName("brushing_duration") val brushingDuration: String? = null,
    @SerializedName("toothbrush_change_frequency") val toothbrushChangeFrequency: String? = null,
    @SerializedName("other_hygiene_aids") val otherHygieneAids: String? = null,
    @SerializedName("family_history_conditions") val familyHistoryConditions: List<String> = emptyList(),
    @SerializedName("family_history_other") val familyHistoryOther: String? = null,
    @SerializedName("past_medical_conditions") val pastMedicalConditions: List<String> = emptyList(),
    @SerializedName("past_medical_other") val pastMedicalOther: String? = null,
    @SerializedName("current_medications") val currentMedications: String? = null,
    val allergies: String? = null,
    @SerializedName("past_dental_history") val pastDentalHistory: Boolean = false,
    @SerializedName("past_dental_details") val pastDentalDetails: String? = null,
    @SerializedName("denture_usage") val dentureUsage: Boolean = false,
    @SerializedName("denture_details") val dentureDetails: String? = null,
)

data class MedicalHistoryRespDto(
    @SerializedName("family_history_conditions") val familyHistoryConditions: List<String> = emptyList(),
    @SerializedName("family_history_other") val familyHistoryOther: String? = null,
    @SerializedName("past_medical_conditions") val pastMedicalConditions: List<String> = emptyList(),
    @SerializedName("past_medical_other") val pastMedicalOther: String? = null,
    @SerializedName("current_medications") val currentMedications: String? = null,
    val allergies: String? = null,
)

data class DentalHistoryRespDto(
    @SerializedName("cleaning_aid") val cleaningAid: String = "",
    @SerializedName("material_used") val materialUsed: String = "",
    @SerializedName("material_other") val materialOther: String? = null,
    @SerializedName("brushing_method") val brushingMethod: String? = null,
    @SerializedName("brushing_frequency") val brushingFrequency: String? = null,
    @SerializedName("brushing_duration") val brushingDuration: String? = null,
    @SerializedName("toothbrush_change_frequency") val toothbrushChangeFrequency: String? = null,
    @SerializedName("other_hygiene_aids") val otherHygieneAids: String? = null,
    @SerializedName("past_dental_history") val pastDentalHistory: Boolean = false,
    @SerializedName("past_dental_details") val pastDentalDetails: String? = null,
    @SerializedName("denture_usage") val dentureUsage: Boolean = false,
    @SerializedName("denture_details") val dentureDetails: String? = null,
)

data class ClinicalObservationDto(
    @SerializedName("observation_code") val observationCode: String,
    @SerializedName("observation_label") val observationLabel: String,
    val details: String? = null,
)

data class ClinicalFindingDto(
    @SerializedName("anatomical_region") val anatomicalRegion: String,
    @SerializedName("sub_region") val subRegion: String? = null,
    val status: String = "NOT_EXAMINED",
    val remarks: String? = null,
    @SerializedName("reference_image_key") val referenceImageKey: String? = null,
    val observations: List<ClinicalObservationDto> = emptyList(),
)

data class LesionDetailDto(
    @SerializedName("anatomical_location") val anatomicalLocation: String,
    @SerializedName("lesion_type") val lesionType: String,
    val size: String? = null,
    val appearance: String? = null,
    val duration: String? = null,
    val symptoms: String? = null,
    @SerializedName("clinical_observations") val clinicalObservations: String? = null,
    val remarks: String? = null,
    @SerializedName("referral_recommendation") val referralRecommendation: String? = null,
)

data class ToothSurfaceDto(
    @SerializedName("surface_code") val surfaceCode: String,
    val condition: String,
    val notes: String? = null,
)

data class ToothExaminationDto(
    @SerializedName("dentition_type") val dentitionType: String = "PERMANENT",
    @SerializedName("tooth_number") val toothNumber: String,
    val status: String = "HEALTHY",
    @SerializedName("clinical_finding") val clinicalFinding: String? = null,
    val remarks: String? = null,
    val surfaces: List<ToothSurfaceDto> = emptyList(),
)

data class LabInvestigationDto(
    @SerializedName("test_date") val testDate: String? = null,
    val haemoglobin: Double? = null,
    @SerializedName("haemoglobin_unit") val haemoglobinUnit: String? = "g/dL",
    val rbc: Double? = null,
    @SerializedName("rbc_unit") val rbcUnit: String? = "million/uL",
    @SerializedName("total_count") val totalCount: Double? = null,
    @SerializedName("total_count_unit") val totalCountUnit: String? = "cells/uL",
    @SerializedName("platelet_count") val plateletCount: Double? = null,
    @SerializedName("platelet_count_unit") val plateletCountUnit: String? = "lakh/uL",
    val percentage: Double? = null,
    @SerializedName("percentage_label") val percentageLabel: String? = null,
    @SerializedName("biopsy_report_summary") val biopsyReportSummary: String? = null,
    @SerializedName("laboratory_remarks") val laboratoryRemarks: String? = null,
    @SerializedName("biopsy_attachment_name") val biopsyAttachmentName: String? = null,
)

data class AIReviewCreateDto(
    @SerializedName("review_status") val reviewStatus: String,
    @SerializedName("clinician_notes") val clinicianNotes: String? = null,
)

data class AIAnalysisDto(
    val id: Int,
    @SerializedName("patient_image_id") val patientImageId: Int,
    @SerializedName("screening_id") val screeningId: Int,
    val status: String,
    @SerializedName("model_name") val modelName: String,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("image_quality_status") val imageQualityStatus: String,
    @SerializedName("quality_metrics") val qualityMetrics: Map<String, Any>? = null,
    @SerializedName("model_output") val modelOutput: Map<String, Any>? = null,
    @SerializedName("confidence_score") val confidenceScore: Double? = null,
    @SerializedName("review_status") val reviewStatus: String,
    @SerializedName("assistive_disclaimer") val assistiveDisclaimer: String = "",
    @SerializedName("analyzed_at") val analyzedAt: String? = null,
)

data class PatientImageDto(
    val id: Int,
    @SerializedName("image_uid") val imageUid: String,
    @SerializedName("patient_id") val patientId: Int,
    @SerializedName("screening_id") val screeningId: Int,
    @SerializedName("clinical_region") val clinicalRegion: String,
    @SerializedName("sub_region") val subRegion: String? = null,
    @SerializedName("original_filename") val originalFilename: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("file_size_bytes") val fileSizeBytes: Int,
    @SerializedName("capture_date") val captureDate: String? = null,
    @SerializedName("ai_analyses") val aiAnalyses: List<AIAnalysisDto> = emptyList(),
)

data class ConsultationSummaryDto(
    val id: Int,
    @SerializedName("screening_id") val screeningId: Int,
    val outcome: String,
    @SerializedName("outcome_reason") val outcomeReason: String,
    @SerializedName("clinical_recommendations") val clinicalRecommendations: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
)

data class FollowUpDto(
    val id: Int,
    @SerializedName("patient_id") val patientId: Int,
    @SerializedName("screening_id") val screeningId: Int,
    @SerializedName("scheduled_date") val scheduledDate: String,
    val status: String,
    val notes: String? = null,
)

data class ReferralDto(
    val id: Int,
    @SerializedName("patient_id") val patientId: Int,
    @SerializedName("screening_id") val screeningId: Int,
    @SerializedName("referred_to") val referredTo: String,
    val department: String? = null,
    val urgency: String,
    val reason: String,
    val status: String,
)

data class CompleteScreeningBundleDto(
    @SerializedName("idempotency_key") val idempotencyKey: String,
    @SerializedName("existing_patient_id") val existingPatientId: Int? = null,
    val patient: PatientCreateDto? = null,
    @SerializedName("screening_date") val screeningDate: String,
    val consent: ConsentStepDto,
    @SerializedName("personal_history") val personalHistory: PersonalHistoryDto,
    val habits: List<HabitItemDto> = emptyList(),
    @SerializedName("medical_dental_history") val medicalDentalHistory: MedicalDentalHistoryDto,
    @SerializedName("clinical_findings") val clinicalFindings: List<ClinicalFindingDto> = emptyList(),
    @SerializedName("lesion_present") val lesionPresent: Boolean = false,
    val lesions: List<LesionDetailDto> = emptyList(),
    @SerializedName("teeth_examinations") val teethExaminations: List<ToothExaminationDto> = emptyList(),
    @SerializedName("lab_investigation") val labInvestigation: LabInvestigationDto? = null,
    @SerializedName("clinical_recommendations") val clinicalRecommendations: String? = null,
    @SerializedName("submit_immediately") val submitImmediately: Boolean = true,
)

data class ScreeningDetailDto(
    val id: Int,
    @SerializedName("screening_uid") val screeningUid: String,
    @SerializedName("patient_id") val patientId: Int,
    @SerializedName("college_id") val collegeId: Int,
    @SerializedName("center_id") val centerId: Int,
    @SerializedName("screening_date") val screeningDate: String,
    @SerializedName("workflow_status") val workflowStatus: String,
    @SerializedName("current_step") val currentStep: Int,
    val outcome: String,
    @SerializedName("lesion_present") val lesionPresent: Boolean,
    @SerializedName("clinician_review_status") val clinicianReviewStatus: String,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    val patient: PatientDto? = null,
    @SerializedName("consent_record") val consentRecord: ConsentStepDto? = null,
    @SerializedName("personal_history") val personalHistory: PersonalHistoryDto? = null,
    @SerializedName("habit_histories") val habitHistories: List<HabitItemDto> = emptyList(),
    @SerializedName("medical_history") val medicalHistory: MedicalHistoryRespDto? = null,
    @SerializedName("dental_history") val dentalHistory: DentalHistoryRespDto? = null,
    @SerializedName("clinical_findings") val clinicalFindings: List<ClinicalFindingDto> = emptyList(),
    @SerializedName("lesion_details") val lesionDetails: List<LesionDetailDto> = emptyList(),
    @SerializedName("teeth_examinations") val teethExaminations: List<ToothExaminationDto> = emptyList(),
    @SerializedName("lab_investigation") val labInvestigation: LabInvestigationDto? = null,
    @SerializedName("patient_images") val patientImages: List<PatientImageDto> = emptyList(),
    @SerializedName("consultation_summary") val consultationSummary: ConsultationSummaryDto? = null,
    @SerializedName("follow_ups") val followUps: List<FollowUpDto> = emptyList(),
    val referrals: List<ReferralDto> = emptyList(),
)

data class AIServiceStatusDto(
    @SerializedName("model_configured") val modelConfigured: Boolean,
    @SerializedName("model_name") val modelName: String,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("preprocessing_available") val preprocessingAvailable: Boolean,
    val notice: String,
)

// --- Feedback, Dashboard, Reports, Education, Activities ---
data class FeedbackCreateDto(
    @SerializedName("patient_id") val patientId: Int? = null,
    @SerializedName("patient_name") val patientName: String,
    val email: String,
    val rating: Int,
    val message: String,
)

data class FeedbackDto(
    val id: Int,
    @SerializedName("patient_name") val patientName: String,
    val email: String,
    val rating: Int,
    val message: String,
    @SerializedName("created_at") val createdAt: String,
)

data class FeedbackSummaryDto(
    val items: List<FeedbackDto>,
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("average_rating") val averageRating: Double,
)

data class WeeklyPointDto(
    val date: String,
    val label: String,
    val count: Int,
)

data class MonthlyOverviewDto(
    @SerializedName("new_patients") val newPatients: Int,
    @SerializedName("lesions_detected") val lesionsDetected: Int,
    @SerializedName("reports_generated") val reportsGenerated: Int,
    @SerializedName("pending_reviews") val pendingReviews: Int,
)

data class ActivityLogDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int? = null,
    val username: String? = null,
    @SerializedName("user_full_name") val userFullName: String? = null,
    val role: String,
    val action: String,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: String? = null,
    @SerializedName("ip_address") val ipAddress: String? = null,
    val details: Any? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class DashboardStatsDto(
    val role: String,
    @SerializedName("user_full_name") val userFullName: String,
    @SerializedName("organization_label") val organizationLabel: String,
    @SerializedName("total_colleges") val totalColleges: Int = 0,
    @SerializedName("total_centers") val totalCenters: Int = 0,
    @SerializedName("total_organizers") val totalOrganizers: Int = 0,
    @SerializedName("total_patients") val totalPatients: Int = 0,
    @SerializedName("male_patients") val malePatients: Int = 0,
    @SerializedName("female_patients") val femalePatients: Int = 0,
    @SerializedName("other_patients") val otherPatients: Int = 0,
    @SerializedName("total_screenings") val totalScreenings: Int = 0,
    @SerializedName("normal_screenings") val normalScreenings: Int = 0,
    @SerializedName("abnormal_screenings") val abnormalScreenings: Int = 0,
    @SerializedName("pending_review_screenings") val pendingReviewScreenings: Int = 0,
    @SerializedName("lesions_detected") val lesionsDetected: Int = 0,
    @SerializedName("total_feedback") val totalFeedback: Int = 0,
    @SerializedName("average_feedback_rating") val averageFeedbackRating: Double = 0.0,
    @SerializedName("weekly_screenings") val weeklyScreenings: List<WeeklyPointDto> = emptyList(),
    @SerializedName("monthly_overview") val monthlyOverview: MonthlyOverviewDto = MonthlyOverviewDto(0, 0, 0, 0),
    @SerializedName("recent_activities") val recentActivities: List<ActivityLogDto> = emptyList(),
)

data class BreakdownItemDto(
    val id: Int,
    val name: String,
    @SerializedName("total_patients") val totalPatients: Int,
    @SerializedName("total_screenings") val totalScreenings: Int,
    @SerializedName("normal_screenings") val normalScreenings: Int,
    @SerializedName("abnormal_screenings") val abnormalScreenings: Int,
    @SerializedName("pending_reviews") val pendingReviews: Int,
    @SerializedName("lesions_detected") val lesionsDetected: Int,
)

data class ReportAnalyticsDto(
    @SerializedName("generated_at") val generatedAt: String,
    @SerializedName("total_patients") val totalPatients: Int,
    @SerializedName("male_patients") val malePatients: Int,
    @SerializedName("female_patients") val femalePatients: Int,
    @SerializedName("other_patients") val otherPatients: Int,
    @SerializedName("total_screenings") val totalScreenings: Int,
    @SerializedName("normal_screenings") val normalScreenings: Int,
    @SerializedName("abnormal_screenings") val abnormalScreenings: Int,
    @SerializedName("pending_reviews") val pendingReviews: Int,
    @SerializedName("lesions_detected") val lesionsDetected: Int,
    @SerializedName("lab_investigations_count") val labInvestigationsCount: Int,
    @SerializedName("follow_ups_scheduled") val followUpsScheduled: Int,
    @SerializedName("follow_ups_completed") val followUpsCompleted: Int,
    @SerializedName("weekly_series") val weeklySeries: List<WeeklyPointDto> = emptyList(),
    @SerializedName("monthly_series") val monthlySeries: List<WeeklyPointDto> = emptyList(),
    @SerializedName("college_breakdown") val collegeBreakdown: List<BreakdownItemDto> = emptyList(),
    @SerializedName("center_breakdown") val centerBreakdown: List<BreakdownItemDto> = emptyList(),
    @SerializedName("organizer_breakdown") val organizerBreakdown: List<BreakdownItemDto> = emptyList(),
)

data class EducationalResourceCreateDto(
    val title: String,
    val description: String? = null,
    val category: String = "USER_MANUAL",
    val language: String = "English",
    @SerializedName("resource_type") val resourceType: String,
    @SerializedName("external_url") val externalUrl: String? = null,
)

data class EducationalResourceDto(
    val id: Int,
    val title: String,
    val description: String? = null,
    val category: String,
    val language: String,
    @SerializedName("resource_type") val resourceType: String,
    @SerializedName("external_url") val externalUrl: String? = null,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class AwarenessCreateDto(
    val title: String,
    val summary: String? = null,
    val body: String,
    val category: String = "ORAL_HYGIENE",
    val language: String = "English",
    @SerializedName("content_type") val contentType: String = "ARTICLE",
)

data class AwarenessContentDto(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val body: String,
    val category: String,
    val language: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class AppSettingDto(
    val id: Int,
    @SerializedName("setting_key") val settingKey: String,
    @SerializedName("setting_value") val settingValue: String,
    val description: String? = null,
)

data class AppSettingUpsertDto(
    @SerializedName("setting_key") val settingKey: String,
    @SerializedName("setting_value") val settingValue: String,
    val description: String? = null,
)
