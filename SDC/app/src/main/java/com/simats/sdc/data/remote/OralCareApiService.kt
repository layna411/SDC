package com.simats.sdc.data.remote

import com.simats.sdc.data.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface OralCareApiService {
    // Authentication
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthTokenResponseDto>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body body: RefreshTokenRequestDto): Response<AuthTokenResponseDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<MessageResponseDto>

    @GET("auth/me")
    suspend fun getMe(): Response<UserDto>

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequestDto): Response<MessageResponseDto>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto): Response<MessageResponseDto>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto): Response<MessageResponseDto>

    // Users
    @GET("users")
    suspend fun listUsers(@Query("role") role: String? = null): Response<List<UserDto>>

    @PATCH("users/{id}/status")
    suspend fun updateUserStatus(@Path("id") id: Int, @Body body: UserStatusUpdateDto): Response<UserDto>

    // Colleges
    @GET("colleges")
    suspend fun listColleges(): Response<List<CollegeDto>>

    @POST("colleges")
    suspend fun createCollege(@Body body: CollegeCreateDto): Response<CollegeCreatedWithAdminDto>

    @PUT("colleges/{id}")
    suspend fun updateCollege(@Path("id") id: Int, @Body body: CollegeUpdateDto): Response<CollegeDto>

    @PATCH("colleges/{id}/status")
    suspend fun toggleCollegeStatus(@Path("id") id: Int, @Query("is_active") isActive: Boolean): Response<CollegeDto>

    // Centers
    @GET("centers")
    suspend fun listCenters(@Query("college_id") collegeId: Int? = null): Response<List<CenterDto>>

    @POST("centers")
    suspend fun createCenter(@Body body: CenterCreateDto): Response<CenterCreatedWithAdminDto>

    @PATCH("centers/{id}/status")
    suspend fun toggleCenterStatus(@Path("id") id: Int, @Query("is_active") isActive: Boolean): Response<CenterDto>

    // Organizers
    @GET("organizers")
    suspend fun listOrganizers(@Query("center_id") centerId: Int? = null): Response<List<OrganizerDto>>

    @POST("organizers")
    suspend fun createOrganizer(@Body body: OrganizerCreateDto): Response<OrganizerCreatedDto>

    @PUT("organizers/{id}")
    suspend fun updateOrganizer(@Path("id") id: Int, @Body body: OrganizerUpdateDto): Response<OrganizerDto>

    // Patients
    @GET("patients")
    suspend fun listPatients(
        @Query("search") search: String? = null,
        @Query("sex") sex: String? = null,
        @Query("center_id") centerId: Int? = null,
        @Query("screening_outcome") outcome: String? = null,
        @Query("lesion_present") lesionPresent: Boolean? = null,
        @Query("sort_by") sortBy: String = "created_at",
        @Query("sort_order") sortOrder: String = "desc",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
    ): Response<PaginatedPatientsDto>

    @GET("patients/{id}")
    suspend fun getPatient(@Path("id") id: Int): Response<PatientDto>

    @GET("patients/{id}/screenings")
    suspend fun getPatientScreenings(@Path("id") id: Int): Response<List<ScreeningDetailDto>>

    @Streaming
    @GET("patients/{id}/report")
    suspend fun downloadPatientReport(@Path("id") id: Int): Response<ResponseBody>

    // Screenings (11-Step Complete Bundle + Detail)
    @POST("screenings/complete-bundle")
    suspend fun submitCompleteScreening(@Body body: CompleteScreeningBundleDto): Response<ScreeningDetailDto>

    @GET("screenings")
    suspend fun listScreenings(@Query("outcome") outcome: String? = null): Response<List<ScreeningDetailDto>>

    @GET("screenings/{id}")
    suspend fun getScreeningDetail(@Path("id") id: Int): Response<ScreeningDetailDto>

    @POST("screenings/{id}/finalize-with-ai")
    suspend fun finalizeScreeningWithAi(@Path("id") id: Int): Response<ScreeningDetailDto>

    // Images & AI Analysis
    @Multipart
    @POST("images/upload")
    suspend fun uploadClinicalImage(
        @Part("screening_id") screeningId: RequestBody,
        @Part("clinical_region") clinicalRegion: RequestBody,
        @Part("sub_region") subRegion: RequestBody?,
        @Part file: MultipartBody.Part,
    ): Response<PatientImageDto>

    @GET("ai-analysis/status")
    suspend fun getAiStatus(): Response<AIServiceStatusDto>

    @POST("ai-analysis/analyze/{imageId}")
    suspend fun analyzeImage(@Path("imageId") imageId: Int): Response<AIAnalysisDto>

    @POST("ai-analysis/{analysisId}/review")
    suspend fun reviewAiAnalysis(
        @Path("analysisId") analysisId: Int,
        @Body body: AIReviewCreateDto,
    ): Response<Map<String, Any>>

    // Feedback
    @GET("feedback")
    suspend fun listFeedback(
        @Query("min_rating") minRating: Int? = null,
        @Query("search") search: String? = null,
    ): Response<FeedbackSummaryDto>

    @POST("feedback")
    suspend fun createFeedback(@Body body: FeedbackCreateDto): Response<FeedbackDto>

    @PUT("feedback/{id}")
    suspend fun updateFeedback(@Path("id") id: Int, @Body body: FeedbackCreateDto): Response<FeedbackDto>

    @DELETE("feedback/{id}")
    suspend fun deleteFeedback(@Path("id") id: Int): Response<MessageResponseDto>

    // Dashboard & Reports
    @GET("reports/dashboard")
    suspend fun getDashboard(): Response<DashboardStatsDto>

    @GET("reports/analytics")
    suspend fun getReportAnalytics(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("outcome") outcome: String? = null,
    ): Response<ReportAnalyticsDto>

    @Streaming
    @GET("reports/export")
    suspend fun exportReport(
        @Query("export_format") exportFormat: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("outcome") outcome: String? = null,
    ): Response<ResponseBody>

    // Educational Resources & Awareness
    @GET("education/resources")
    suspend fun listEducationalResources(
        @Query("search") search: String? = null,
        @Query("language") language: String? = null,
        @Query("category") category: String? = null,
    ): Response<List<EducationalResourceDto>>

    @POST("education/resources")
    suspend fun createEducationalResource(@Body body: EducationalResourceCreateDto): Response<EducationalResourceDto>

    @DELETE("education/resources/{id}")
    suspend fun deleteEducationalResource(@Path("id") id: Int): Response<MessageResponseDto>

    @GET("education/awareness")
    suspend fun listAwarenessContent(
        @Query("search") search: String? = null,
        @Query("language") language: String? = null,
    ): Response<List<AwarenessContentDto>>

    @POST("education/awareness")
    suspend fun createAwarenessContent(@Body body: AwarenessCreateDto): Response<AwarenessContentDto>

    @DELETE("education/awareness/{id}")
    suspend fun deleteAwarenessContent(@Path("id") id: Int): Response<MessageResponseDto>

    // Activity Logs
    @GET("activities")
    suspend fun listActivities(
        @Query("action") action: String? = null,
        @Query("limit") limit: Int = 80,
    ): Response<List<ActivityLogDto>>

    // Settings
    @GET("settings")
    suspend fun listSettings(): Response<List<AppSettingDto>>

    @PUT("settings")
    suspend fun upsertSetting(@Body body: AppSettingUpsertDto): Response<AppSettingDto>
}
