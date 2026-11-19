package com.simats.sdc.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.simats.sdc.core.database.PendingImageDao
import com.simats.sdc.core.database.PendingImageUploadEntity
import com.simats.sdc.core.database.ScreeningDraftDao
import com.simats.sdc.core.database.ScreeningDraftEntity
import com.simats.sdc.core.datastore.SessionManager
import com.simats.sdc.core.network.ApiErrorParser
import com.simats.sdc.core.network.NetworkMonitor
import com.simats.sdc.core.sync.OfflineSyncWorker
import com.simats.sdc.data.dto.*
import com.simats.sdc.data.remote.OralCareApiService
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OralCareRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: OralCareApiService,
    private val sessionManager: SessionManager,
    private val networkMonitor: NetworkMonitor,
    private val draftDao: ScreeningDraftDao,
    private val pendingImageDao: PendingImageDao,
    private val gson: Gson,
) : OralCareRepository {

    private suspend inline fun <T> safeCall(crossinline call: suspend () -> Response<T>): RepoResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                RepoResult.Success(response.body()!!)
            } else {
                RepoResult.Error(
                    message = ApiErrorParser.parseError(response),
                    unauthorized = response.code() == 401,
                )
            }
        } catch (e: Exception) {
            val msg = if (!networkMonitor.isOnlineNow()) {
                "No internet connection. Please check your network."
            } else {
                e.localizedMessage ?: "Network communication error."
            }
            RepoResult.Error(msg)
        }
    }

    override suspend fun login(username: String, password: String): RepoResult<AuthTokenResponseDto> {
        val res = safeCall { api.login(LoginRequestDto(username.trim(), password)) }
        if (res is RepoResult.Success) {
            val data = res.data
            sessionManager.saveSession(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                userId = data.user.id,
                userUid = data.user.userUid,
                username = data.user.username,
                fullName = data.user.fullName,
                role = data.user.role,
                collegeId = data.user.collegeId,
                centerId = data.user.centerId,
                mustChangePassword = data.mustChangePassword,
            )
        }
        return res
    }

    override suspend fun logout(): RepoResult<Unit> {
        val snap = sessionManager.getSessionSnapshot()
        try {
            if (snap.accessToken.isNotBlank()) {
                api.logout(LogoutRequestDto(snap.refreshToken.ifBlank { null }))
            }
        } catch (_: Exception) {
            // Always clear local session even if server unreachable
        }
        sessionManager.clearSession()
        draftDao.clearAll()
        pendingImageDao.clearAll()
        return RepoResult.Success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): RepoResult<String> {
        val res = safeCall { api.changePassword(ChangePasswordRequestDto(currentPassword, newPassword)) }
        return when (res) {
            is RepoResult.Success -> {
                sessionManager.clearPasswordChangeFlag()
                RepoResult.Success(res.data.message)
            }
            is RepoResult.Error -> res
        }
    }

    override suspend fun forgotPassword(usernameOrEmail: String): RepoResult<MessageResponseDto> =
        safeCall { api.forgotPassword(ForgotPasswordRequestDto(usernameOrEmail.trim())) }

    override suspend fun resetPassword(resetToken: String, newPassword: String): RepoResult<String> =
        when (val res = safeCall { api.resetPassword(ResetPasswordRequestDto(resetToken.trim(), newPassword)) }) {
            is RepoResult.Success -> RepoResult.Success(res.data.message)
            is RepoResult.Error -> res
        }

    override suspend fun getDashboard(): RepoResult<DashboardStatsDto> = safeCall { api.getDashboard() }

    override suspend fun getReportAnalytics(
        startDate: String?,
        endDate: String?,
        outcome: String?,
    ): RepoResult<ReportAnalyticsDto> = safeCall {
        api.getReportAnalytics(startDate, endDate, outcome)
    }

    override suspend fun downloadReport(
        format: String,
        startDate: String?,
        endDate: String?,
        outcome: String?,
    ): RepoResult<File> = withContext(Dispatchers.IO) {
        try {
            val normalizedFormat = format.lowercase(Locale.US)
            val response = api.exportReport(normalizedFormat, startDate, endDate, outcome)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val (ext, mimeType) = when (normalizedFormat) {
                    "excel" -> "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "csv" -> "csv" to "text/csv"
                    else -> "pdf" to "application/pdf"
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "OralCare_Report_$stamp.$ext"
                val dir = File(context.filesDir, "reports").apply { mkdirs() }
                val file = File(dir, fileName)

                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }

                if (!file.exists() || file.length() == 0L) {
                    return@withContext RepoResult.Error("Downloaded report file was empty.")
                }

                // Also publish a copy into the device's public Downloads/OralCare folder
                runCatching {
                    saveCopyToPublicDownloads(file, fileName, mimeType)
                }

                RepoResult.Success(file)
            } else {
                RepoResult.Error(ApiErrorParser.parseError(response), response.code() == 401)
            }
        } catch (e: Exception) {
            val msg = if (!networkMonitor.isOnlineNow()) {
                "No internet connection. Please check your network."
            } else {
                e.localizedMessage ?: "Failed to download report. Please try again."
            }
            RepoResult.Error(msg)
        }
    }

    private fun saveCopyToPublicDownloads(sourceFile: File, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/OralCare")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "OralCare",
            ).apply { mkdirs() }
            val destFile = File(downloadsDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
        }
    }

    override suspend fun listUsers(role: String?): RepoResult<List<UserDto>> =
        safeCall { api.listUsers(role) }

    override suspend fun toggleUserStatus(id: Int, isActive: Boolean): RepoResult<UserDto> =
        safeCall { api.updateUserStatus(id, UserStatusUpdateDto(isActive)) }

    override suspend fun listColleges(): RepoResult<List<CollegeDto>> = safeCall { api.listColleges() }

    override suspend fun createCollege(req: CollegeCreateDto): RepoResult<CollegeCreatedWithAdminDto> =
        safeCall { api.createCollege(req) }

    override suspend fun toggleCollegeStatus(id: Int, isActive: Boolean): RepoResult<CollegeDto> =
        safeCall { api.toggleCollegeStatus(id, isActive) }

    override suspend fun listCenters(collegeId: Int?): RepoResult<List<CenterDto>> =
        safeCall { api.listCenters(collegeId) }

    override suspend fun createCenter(req: CenterCreateDto): RepoResult<CenterCreatedWithAdminDto> =
        safeCall { api.createCenter(req) }

    override suspend fun toggleCenterStatus(id: Int, isActive: Boolean): RepoResult<CenterDto> =
        safeCall { api.toggleCenterStatus(id, isActive) }

    override suspend fun listOrganizers(centerId: Int?): RepoResult<List<OrganizerDto>> =
        safeCall { api.listOrganizers(centerId) }

    override suspend fun createOrganizer(req: OrganizerCreateDto): RepoResult<OrganizerCreatedDto> =
        safeCall { api.createOrganizer(req) }

    override suspend fun toggleOrganizerStatus(id: Int, isActive: Boolean): RepoResult<OrganizerDto> =
        safeCall { api.updateOrganizer(id, OrganizerUpdateDto(isActive = isActive)) }

    override suspend fun listPatients(
        search: String?,
        sex: String?,
        outcome: String?,
        lesionPresent: Boolean?,
        page: Int,
    ): RepoResult<PaginatedPatientsDto> = safeCall {
        api.listPatients(
            search = search?.takeIf { it.isNotBlank() },
            sex = sex?.takeIf { it.isNotBlank() },
            outcome = outcome?.takeIf { it.isNotBlank() },
            lesionPresent = lesionPresent,
            page = page,
        )
    }

    override suspend fun getPatient(id: Int): RepoResult<PatientDto> = safeCall { api.getPatient(id) }

    override suspend fun getPatientScreenings(id: Int): RepoResult<List<ScreeningDetailDto>> =
        safeCall { api.getPatientScreenings(id) }

    override suspend fun listScreenings(outcome: String?): RepoResult<List<ScreeningDetailDto>> =
        safeCall { api.listScreenings(outcome) }

    override suspend fun getScreeningDetail(id: Int): RepoResult<ScreeningDetailDto> =
        safeCall { api.getScreeningDetail(id) }

    override suspend fun submitCompleteScreening(
        draftId: String,
        bundle: CompleteScreeningBundleDto,
        localImages: List<Triple<String, String?, String>>,
    ): RepoResult<ScreeningDetailDto> {
        val previewName = bundle.patient?.let { "${it.firstName} ${it.lastName}" } ?: "Patient"

        if (!networkMonitor.isOnlineNow()) {
            // Queue locally in Room + enqueue WorkManager background sync
            saveDraftLocally(draftId, bundle.idempotencyKey, previewName, 11, bundle, "PENDING_SYNC")
            pendingImageDao.deleteForDraft(draftId)
            localImages.forEach { (region, sub, path) ->
                pendingImageDao.insert(
                    PendingImageUploadEntity(
                        draftId = draftId,
                        clinicalRegion = region,
                        subRegion = sub,
                        localFilePath = path,
                    )
                )
            }
            OfflineSyncWorker.enqueue(context)
            return RepoResult.Error(
                "Offline mode: Screening saved locally and queued for automatic background synchronization."
            )
        }

        val submitResult = safeCall { api.submitCompleteScreening(bundle) }
        if (submitResult is RepoResult.Success) {
            val screening = submitResult.data
            // Upload any attached clinical images immediately
            for ((region, sub, path) in localImages) {
                val file = File(path)
                if (file.exists()) {
                    val up = uploadImage(screening.id, region, sub, file)
                    if (up is RepoResult.Error) {
                        pendingImageDao.insert(
                            PendingImageUploadEntity(
                                draftId = draftId,
                                remoteScreeningId = screening.id,
                                clinicalRegion = region,
                                subRegion = sub,
                                localFilePath = path,
                            )
                        )
                        OfflineSyncWorker.enqueue(context)
                    }
                }
            }
            draftDao.deleteById(draftId)
            // Automatically run AI image analysis & unified NORMAL / ABNORMAL clinical evaluation
            val aiFinalized = safeCall { api.finalizeScreeningWithAi(screening.id) }
            if (aiFinalized is RepoResult.Success) {
                return aiFinalized
            }
            // Fallback to latest screening detail if AI finalize call failed transiently
            return getScreeningDetail(screening.id).let {
                if (it is RepoResult.Success) it else submitResult
            }
        }
        return submitResult
    }

    override suspend fun saveDraftLocally(
        draftId: String,
        idempotencyKey: String,
        patientNamePreview: String,
        currentStep: Int,
        bundle: CompleteScreeningBundleDto,
        syncStatus: String,
    ) {
        draftDao.upsert(
            ScreeningDraftEntity(
                draftId = draftId,
                idempotencyKey = idempotencyKey,
                patientNamePreview = patientNamePreview.ifBlank { "Untitled Draft" },
                currentStep = currentStep,
                payloadJson = gson.toJson(bundle),
                syncStatus = syncStatus,
                updatedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    override fun observeDrafts(): Flow<List<ScreeningDraftEntity>> = draftDao.observeAllDrafts()

    override suspend fun loadDraft(draftId: String): ScreeningDraftEntity? = draftDao.getById(draftId)

    override suspend fun deleteDraft(draftId: String) {
        draftDao.deleteById(draftId)
        pendingImageDao.deleteForDraft(draftId)
    }

    override suspend fun getAiStatus(): RepoResult<AIServiceStatusDto> = safeCall { api.getAiStatus() }

    override suspend fun uploadImage(
        screeningId: Int,
        region: String,
        subRegion: String?,
        file: File,
    ): RepoResult<PatientImageDto> = safeCall {
        val mime = if (file.extension.lowercase() == "png") "image/png" else "image/jpeg"
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mime.toMediaType()))
        api.uploadClinicalImage(
            screeningId = screeningId.toString().toRequestBody("text/plain".toMediaType()),
            clinicalRegion = region.toRequestBody("text/plain".toMediaType()),
            subRegion = subRegion?.toRequestBody("text/plain".toMediaType()),
            file = part,
        )
    }

    override suspend fun analyzeImage(imageId: Int): RepoResult<AIAnalysisDto> =
        safeCall { api.analyzeImage(imageId) }

    override suspend fun reviewAiAnalysis(analysisId: Int, status: String, notes: String?): RepoResult<Unit> =
        when (val res = safeCall { api.reviewAiAnalysis(analysisId, AIReviewCreateDto(status, notes)) }) {
            is RepoResult.Success -> RepoResult.Success(Unit)
            is RepoResult.Error -> res
        }

    override suspend fun listFeedback(minRating: Int?, search: String?): RepoResult<FeedbackSummaryDto> =
        safeCall { api.listFeedback(minRating, search?.takeIf { it.isNotBlank() }) }

    override suspend fun createFeedback(req: FeedbackCreateDto): RepoResult<FeedbackDto> =
        safeCall { api.createFeedback(req) }

    override suspend fun deleteFeedback(id: Int): RepoResult<Unit> =
        when (val res = safeCall { api.deleteFeedback(id) }) {
            is RepoResult.Success -> RepoResult.Success(Unit)
            is RepoResult.Error -> res
        }

    override suspend fun listEducationalResources(
        search: String?,
        language: String?,
    ): RepoResult<List<EducationalResourceDto>> = safeCall {
        api.listEducationalResources(search?.takeIf { it.isNotBlank() }, language?.takeIf { it.isNotBlank() })
    }

    override suspend fun createEducationalResource(req: EducationalResourceCreateDto): RepoResult<EducationalResourceDto> =
        safeCall { api.createEducationalResource(req) }

    override suspend fun deleteEducationalResource(id: Int): RepoResult<Unit> =
        when (val res = safeCall { api.deleteEducationalResource(id) }) {
            is RepoResult.Success -> RepoResult.Success(Unit)
            is RepoResult.Error -> res
        }

    override suspend fun listAwarenessContent(
        search: String?,
        language: String?,
    ): RepoResult<List<AwarenessContentDto>> = safeCall {
        api.listAwarenessContent(search?.takeIf { it.isNotBlank() }, language?.takeIf { it.isNotBlank() })
    }

    override suspend fun createAwarenessContent(req: AwarenessCreateDto): RepoResult<AwarenessContentDto> =
        safeCall { api.createAwarenessContent(req) }

    override suspend fun deleteAwarenessContent(id: Int): RepoResult<Unit> =
        when (val res = safeCall { api.deleteAwarenessContent(id) }) {
            is RepoResult.Success -> RepoResult.Success(Unit)
            is RepoResult.Error -> res
        }

    override suspend fun listActivities(action: String?): RepoResult<List<ActivityLogDto>> =
        safeCall { api.listActivities(action?.takeIf { it.isNotBlank() }) }

    override suspend fun listSettings(): RepoResult<List<AppSettingDto>> = safeCall { api.listSettings() }

    override suspend fun upsertSetting(req: AppSettingUpsertDto): RepoResult<AppSettingDto> =
        safeCall { api.upsertSetting(req) }
}
