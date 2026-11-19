package com.simats.sdc.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.simats.sdc.core.database.PendingImageDao
import com.simats.sdc.core.database.ScreeningDraftDao
import com.simats.sdc.data.dto.CompleteScreeningBundleDto
import com.simats.sdc.data.remote.OralCareApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val draftDao: ScreeningDraftDao,
    private val imageDao: PendingImageDao,
    private val api: OralCareApiService,
    private val gson: Gson,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        var anyRetryableFailure = false

        val pendingDrafts = draftDao.getPendingSyncDrafts()
        for (draft in pendingDrafts) {
            try {
                val bundle = gson.fromJson(draft.payloadJson, CompleteScreeningBundleDto::class.java)
                val response = api.submitCompleteScreening(bundle)
                if (response.isSuccessful && response.body() != null) {
                    val remoteId = response.body()!!.id
                    imageDao.attachRemoteScreeningId(draft.draftId, remoteId)
                    draftDao.upsert(
                        draft.copy(
                            syncStatus = "SYNCED",
                            lastError = null,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    )
                } else {
                    val err = "HTTP ${response.code()}"
                    draftDao.upsert(draft.copy(syncStatus = "FAILED", lastError = err))
                    if (response.code() >= 500) anyRetryableFailure = true
                }
            } catch (e: Exception) {
                draftDao.upsert(draft.copy(syncStatus = "FAILED", lastError = e.message))
                anyRetryableFailure = true
            }
        }

        val readyImages = imageDao.getReadyForUpload()
        for (img in readyImages) {
            val screeningId = img.remoteScreeningId ?: continue
            val file = File(img.localFilePath)
            if (!file.exists()) {
                imageDao.updateStatus(img.id, "FAILED", "Local image file missing")
                continue
            }
            try {
                val mime = if (file.extension.lowercase() == "png") "image/png" else "image/jpeg"
                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    file.asRequestBody(mime.toMediaType()),
                )
                val resp = api.uploadClinicalImage(
                    screeningId = screeningId.toString().toRequestBody("text/plain".toMediaType()),
                    clinicalRegion = img.clinicalRegion.toRequestBody("text/plain".toMediaType()),
                    subRegion = img.subRegion?.toRequestBody("text/plain".toMediaType()),
                    file = filePart,
                )
                if (resp.isSuccessful) {
                    imageDao.updateStatus(img.id, "UPLOADED", null)
                } else {
                    imageDao.updateStatus(img.id, "FAILED", "HTTP ${resp.code()}")
                    if (resp.code() >= 500) anyRetryableFailure = true
                }
            } catch (e: Exception) {
                imageDao.updateStatus(img.id, "FAILED", e.message)
                anyRetryableFailure = true
            }
        }

        return if (anyRetryableFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "oralcare_offline_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
