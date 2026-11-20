package com.simats.sdc.domain.repository

import com.simats.sdc.core.database.ScreeningDraftEntity
import com.simats.sdc.data.dto.*
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed interface RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>
    data class Error(val message: String, val unauthorized: Boolean = false) : RepoResult<Nothing>
}

interface OralCareRepository {
    // Auth
    suspend fun login(username: String, password: String): RepoResult<AuthTokenResponseDto>
    suspend fun logout(): RepoResult<Unit>
    suspend fun changePassword(currentPassword: String, newPassword: String): RepoResult<String>
    suspend fun forgotPassword(usernameOrEmail: String): RepoResult<MessageResponseDto>
    suspend fun resetPassword(resetToken: String, newPassword: String): RepoResult<String>

    // Dashboard & Analytics
    suspend fun getDashboard(): RepoResult<DashboardStatsDto>
    suspend fun getReportAnalytics(startDate: String?, endDate: String?, outcome: String?): RepoResult<ReportAnalyticsDto>
    suspend fun downloadReport(format: String, startDate: String?, endDate: String?, outcome: String?): RepoResult<File>
    suspend fun downloadPatientReport(patient: PatientDto, screenings: List<ScreeningDetailDto>): RepoResult<File>

    // Organization Management
    suspend fun listUsers(role: String? = null): RepoResult<List<UserDto>>
    suspend fun toggleUserStatus(id: Int, isActive: Boolean): RepoResult<UserDto>

    suspend fun listColleges(): RepoResult<List<CollegeDto>>
    suspend fun createCollege(req: CollegeCreateDto): RepoResult<CollegeCreatedWithAdminDto>
    suspend fun toggleCollegeStatus(id: Int, isActive: Boolean): RepoResult<CollegeDto>

    suspend fun listCenters(collegeId: Int? = null): RepoResult<List<CenterDto>>
    suspend fun createCenter(req: CenterCreateDto): RepoResult<CenterCreatedWithAdminDto>
    suspend fun generateOrResetCenterAdmin(id: Int): RepoResult<CenterCreatedWithAdminDto>
    suspend fun toggleCenterStatus(id: Int, isActive: Boolean): RepoResult<CenterDto>

    suspend fun listOrganizers(centerId: Int? = null): RepoResult<List<OrganizerDto>>
    suspend fun createOrganizer(req: OrganizerCreateDto): RepoResult<OrganizerCreatedDto>
    suspend fun toggleOrganizerStatus(id: Int, isActive: Boolean): RepoResult<OrganizerDto>

    // Patients & Screenings
    suspend fun listPatients(
        search: String? = null,
        sex: String? = null,
        outcome: String? = null,
        lesionPresent: Boolean? = null,
        page: Int = 1,
    ): RepoResult<PaginatedPatientsDto>

    suspend fun getPatient(id: Int): RepoResult<PatientDto>
    suspend fun getPatientScreenings(id: Int): RepoResult<List<ScreeningDetailDto>>
    suspend fun reexaminePatient(
        patientId: Int,
        localImages: List<Triple<String, String?, String>>,
    ): RepoResult<ScreeningDetailDto>
    suspend fun listScreenings(outcome: String? = null): RepoResult<List<ScreeningDetailDto>>
    suspend fun getScreeningDetail(id: Int): RepoResult<ScreeningDetailDto>

    suspend fun submitCompleteScreening(
        draftId: String,
        bundle: CompleteScreeningBundleDto,
        localImages: List<Triple<String, String?, String>>, // (region, subRegion, localFilePath)
    ): RepoResult<ScreeningDetailDto>

    suspend fun saveDraftLocally(
        draftId: String,
        idempotencyKey: String,
        patientNamePreview: String,
        currentStep: Int,
        bundle: CompleteScreeningBundleDto,
        syncStatus: String = "DRAFT",
    )

    fun observeDrafts(): Flow<List<ScreeningDraftEntity>>
    suspend fun loadDraft(draftId: String): ScreeningDraftEntity?
    suspend fun deleteDraft(draftId: String)

    // AI & Images
    suspend fun getAiStatus(): RepoResult<AIServiceStatusDto>
    suspend fun uploadImage(screeningId: Int, region: String, subRegion: String?, file: File): RepoResult<PatientImageDto>
    suspend fun analyzeImage(imageId: Int): RepoResult<AIAnalysisDto>
    suspend fun reviewAiAnalysis(analysisId: Int, status: String, notes: String?): RepoResult<Unit>

    // Feedback
    suspend fun listFeedback(minRating: Int? = null, search: String? = null): RepoResult<FeedbackSummaryDto>
    suspend fun createFeedback(req: FeedbackCreateDto): RepoResult<FeedbackDto>
    suspend fun replyFeedback(id: Int, reply: String): RepoResult<FeedbackDto>
    suspend fun deleteFeedback(id: Int): RepoResult<Unit>

    // Education & Awareness
    suspend fun listEducationalResources(search: String? = null, language: String? = null): RepoResult<List<EducationalResourceDto>>
    suspend fun createEducationalResource(req: EducationalResourceCreateDto): RepoResult<EducationalResourceDto>
    suspend fun deleteEducationalResource(id: Int): RepoResult<Unit>

    suspend fun listAwarenessContent(search: String? = null, language: String? = null): RepoResult<List<AwarenessContentDto>>
    suspend fun createAwarenessContent(req: AwarenessCreateDto): RepoResult<AwarenessContentDto>
    suspend fun deleteAwarenessContent(id: Int): RepoResult<Unit>

    // Activities & Settings
    suspend fun listActivities(
        action: String? = null,
        userId: Int? = null,
        collegeId: Int? = null,
        centerId: Int? = null,
        limit: Int = 80,
    ): RepoResult<List<ActivityLogDto>>
    suspend fun listSettings(): RepoResult<List<AppSettingDto>>
    suspend fun upsertSetting(req: AppSettingUpsertDto): RepoResult<AppSettingDto>
}
