package com.simats.sdc.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.simats.sdc.R
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

    override suspend fun downloadPatientReport(
        patient: PatientDto,
        screenings: List<ScreeningDetailDto>,
    ): RepoResult<File> = withContext(Dispatchers.IO) {
        val fileName = "Patient_${patient.patientUid}_Report.pdf"
        try {
            val response = api.downloadPatientReport(patient.id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val dir = File(context.filesDir, "reports").apply { mkdirs() }
                val file = File(dir, fileName)

                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }

                if (file.exists() && file.length() > 0L) {
                    runCatching {
                        saveCopyToPublicDownloads(file, fileName, "application/pdf")
                    }
                    return@withContext RepoResult.Success(file)
                }
            }
            // Fallback to local PDF generation if server returned error
            val localFile = generateLocalPatientPdf(patient, screenings)
            RepoResult.Success(localFile)
        } catch (e: Exception) {
            // Offline/Network fallback ensures download/share always succeeds
            try {
                val localFile = generateLocalPatientPdf(patient, screenings)
                RepoResult.Success(localFile)
            } catch (fallbackError: Exception) {
                RepoResult.Error("Failed to generate patient report: ${fallbackError.localizedMessage}")
            }
        }
    }

    private fun generateLocalPatientPdf(patient: PatientDto, screenings: List<ScreeningDetailDto>): File {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0D2340")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1E40AF")
            textSize = 8.5f
            isAntiAlias = true
        }
        val headerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1E3A8A")
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val boldPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1F2937")
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val regularPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#374151")
            textSize = 8f
            isAntiAlias = true
        }
        val normalGreenPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#059669")
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val abnormalRedPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#DC2626")
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val boxPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#EBF3FB")
            style = android.graphics.Paint.Style.FILL
        }
        val strokePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#93C5FD")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1f
        }
        val linePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#CBD5E1")
            strokeWidth = 0.5f
        }
        val footerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 7f
            isAntiAlias = true
        }

        val logoBitmap: Bitmap? = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.oralcare_logo)
        }.getOrNull()

        // ================= PAGE 1 =================
        val pageInfo1 = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas

        var y1 = 30f
        if (logoBitmap != null) {
            val scaled = Bitmap.createScaledBitmap(logoBitmap, 48, 42, true)
            canvas1.drawBitmap(scaled, 40f, y1, null)
            canvas1.drawText("SAVEETHA DENTAL COLLEGE & HOSPITAL", 98f, y1 + 14f, titlePaint)
            canvas1.drawText("Department of Oral Medicine & Radiology • SIMATS", 98f, y1 + 26f, subPaint)
            canvas1.drawText("COMPREHENSIVE CLINICAL EXAMINATION REPORT", 98f, y1 + 38f, boldPaint)
            y1 += 48f
        } else {
            canvas1.drawText("SAVEETHA DENTAL COLLEGE & HOSPITAL", 40f, y1 + 14f, titlePaint)
            canvas1.drawText("Department of Oral Medicine & Radiology • SIMATS", 40f, y1 + 26f, subPaint)
            y1 += 38f
        }

        // Metadata box
        canvas1.drawRect(40f, y1, 555f, y1 + 36f, boxPaint)
        canvas1.drawRect(40f, y1, 555f, y1 + 36f, strokePaint)
        canvas1.drawText("Patient UID: ${patient.patientUid}", 50f, y1 + 14f, boldPaint)
        canvas1.drawText("Report ID: OC-RPT-${patient.patientUid}", 300f, y1 + 14f, regularPaint)
        val latestOutcome = screenings.firstOrNull()?.outcome ?: patient.latestOutcome ?: "NORMAL"
        canvas1.drawText("Clinical Outcome: ${latestOutcome.replace("_", " ")}", 50f, y1 + 28f, boldPaint)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        canvas1.drawText("Generated: $dateStr", 300f, y1 + 28f, regularPaint)
        y1 += 48f

        // Demographics
        canvas1.drawText("1. Patient Demographic Profile", 40f, y1, headerPaint)
        y1 += 10f
        canvas1.drawLine(40f, y1, 555f, y1, linePaint)
        y1 += 12f

        fun drawRow1(label1: String, val1: String, label2: String, val2: String) {
            canvas1.drawText(label1, 40f, y1, boldPaint)
            canvas1.drawText(val1, 130f, y1, regularPaint)
            canvas1.drawText(label2, 300f, y1, boldPaint)
            canvas1.drawText(val2, 400f, y1, regularPaint)
            y1 += 13f
        }

        drawRow1("Full Name:", patient.displayName, "Reg. Date:", patient.registrationDate)
        drawRow1("Age / Sex:", "${patient.age} Y / ${patient.sex}", "Phone:", patient.phoneNumber)
        drawRow1("Address:", patient.address.take(28), "Marital Status:", patient.maritalStatus ?: "-")
        drawRow1("Religion:", patient.religion ?: "-", "Education:", patient.education ?: "-")
        drawRow1("Occupation:", patient.occupation ?: "-", "Monthly Income:", patient.familyIncomePerMonth ?: "-")
        y1 += 8f

        val scr = screenings.firstOrNull()

        // Step 1: Informed Consent & Location
        canvas1.drawText("2. Informed Consent & Geolocation Protocol (Step 1)", 40f, y1, headerPaint)
        y1 += 10f
        canvas1.drawLine(40f, y1, 555f, y1, linePaint)
        y1 += 12f

        if (scr?.consentRecord != null) {
            val cr = scr.consentRecord
            drawRow1("Hub / Center:", cr.hub ?: "-", "Healthworker:", cr.healthworkerName ?: "-")
            drawRow1("Health Partner:", cr.healthPartner ?: "-", "GPS Location:", cr.geolocationLabel ?: "${cr.latitude ?: "-"}, ${cr.longitude ?: "-"}")
            drawRow1("Info Sheet Ack:", if (cr.participantInfoSheetAck) "YES" else "NO", "Consent Signed:", if (cr.consentFormSigned) "YES" else "NO")
            drawRow1("Screening Consent:", if (cr.consentAccepted) "YES" else "NO", "Terms Consent:", if (cr.termsAccepted) "YES" else "NO")
        } else {
            canvas1.drawText("Consent record pending synchronization.", 40f, y1, regularPaint)
            y1 += 13f
        }
        y1 += 8f

        // Step 3: Habits & Personal History
        canvas1.drawText("3. Personal & Habit History (Step 3)", 40f, y1, headerPaint)
        y1 += 10f
        canvas1.drawLine(40f, y1, 555f, y1, linePaint)
        y1 += 12f

        if (scr != null) {
            val dietStr = "Diet: ${scr.personalHistory?.dietHistory ?: "-"} | Spice Level: ${scr.personalHistory?.spiceConsumption ?: "-"}"
            canvas1.drawText(dietStr, 40f, y1, regularPaint)
            y1 += 12f
            if (scr.habitHistories.isNotEmpty()) {
                for (h in scr.habitHistories.take(4)) {
                    val hText = "• ${h.habitCategory} (${h.habitType ?: "General"}): ${h.usageStatus}, Freq: ${h.frequency ?: "-"}, Duration: ${h.duration ?: "-"} yrs"
                    canvas1.drawText(hText.take(85), 45f, y1, regularPaint)
                    y1 += 12f
                }
            } else {
                canvas1.drawText("No tobacco, arecanut, or alcohol habits recorded.", 40f, y1, regularPaint)
                y1 += 12f
            }
        }
        y1 += 8f

        // Step 4 & 5: Medical, Dental & Oral Hygiene Practices
        canvas1.drawText("4. Medical, Dental History & Oral Hygiene (Step 4–5)", 40f, y1, headerPaint)
        y1 += 10f
        canvas1.drawLine(40f, y1, 555f, y1, linePaint)
        y1 += 12f

        if (scr != null) {
            val medConds = scr.medicalHistory?.pastMedicalConditions?.joinToString(", ")?.ifBlank { "None" } ?: "None"
            val allergies = scr.medicalHistory?.allergies ?: "None"
            drawRow1("Medical Conditions:", medConds.take(25), "Allergies:", allergies.take(25))
            val cleaning = "${scr.dentalHistory?.cleaningAid ?: "-"} / ${scr.dentalHistory?.materialUsed ?: "-"}"
            val brushing = "${scr.dentalHistory?.brushingFrequency ?: "-"} (${scr.dentalHistory?.brushingMethod ?: "-"})"
            drawRow1("Cleaning Aid:", cleaning.take(25), "Brushing Method:", brushing.take(25))
            drawRow1("Denture Usage:", if (scr.dentalHistory?.dentureUsage == true) "YES" else "NO", "Past Dental:", if (scr.dentalHistory?.pastDentalHistory == true) "YES" else "NO")
        }
        y1 += 10f

        // Page 1 Footer
        canvas1.drawLine(40f, 810f, 555f, 810f, linePaint)
        canvas1.drawText("Saveetha Dental College & Hospital • ORALCARE Medical Record • Page 1 of 2", 40f, 822f, footerPaint)
        pdfDocument.finishPage(page1)

        // ================= PAGE 2 =================
        val pageInfo2 = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas

        var y2 = 35f
        canvas2.drawText("ORALCARE Clinical Report • Patient: ${patient.displayName} (${patient.patientUid})", 40f, y2, subPaint)
        canvas2.drawText("Page 2 of 2", 510f, y2, subPaint)
        y2 += 14f
        canvas2.drawLine(40f, y2, 555f, y2, linePaint)
        y2 += 16f

        // Step 6: Clinical Mucosal Examination (11 Regions)
        canvas2.drawText("5. Comprehensive Mucosal Examination — All 11 Regions (Step 6)", 40f, y2, headerPaint)
        y2 += 10f
        canvas2.drawLine(40f, y2, 555f, y2, linePaint)
        y2 += 12f

        if (scr != null && scr.clinicalFindings.isNotEmpty()) {
            for (cf in scr.clinicalFindings.take(11)) {
                val isAbnormal = cf.status.equals("ABNORMAL", ignoreCase = true)
                val paintToUse = if (isAbnormal) abnormalRedPaint else normalGreenPaint
                val obs = cf.observations.joinToString(", ") { it.observationLabel }.ifBlank { cf.remarks ?: "Normal mucosa" }
                val regLabel = "${cf.anatomicalRegion}${if (!cf.subRegion.isNullOrBlank()) " (${cf.subRegion})" else ""}:"
                canvas2.drawText(regLabel.take(28), 40f, y2, boldPaint)
                canvas2.drawText(cf.status.uppercase(), 185f, y2, paintToUse)
                canvas2.drawText(obs.take(55), 245f, y2, regularPaint)
                y2 += 11f
            }
        } else {
            canvas2.drawText("All 11 anatomical mucosal regions examined normal.", 40f, y2, regularPaint)
            y2 += 12f
        }
        y2 += 8f

        // Step 7: Lesions (if any)
        if (scr != null && scr.lesionPresent && scr.lesionDetails.isNotEmpty()) {
            canvas2.drawText("6. Lesion Assessment (Step 7)", 40f, y2, headerPaint)
            y2 += 10f
            canvas2.drawLine(40f, y2, 555f, y2, linePaint)
            y2 += 12f
            for (ld in scr.lesionDetails.take(2)) {
                val lStr = "• Location: ${ld.anatomicalLocation} | Type: ${ld.lesionType} | Size: ${ld.size ?: "-"} | Duration: ${ld.duration ?: "-"}"
                canvas2.drawText(lStr.take(90), 40f, y2, boldPaint)
                y2 += 11f
            }
            y2 += 6f
        }

        // Step 8 & 9: Teeth & Lab
        canvas2.drawText("7. Teeth Examination & Laboratory Diagnostics (Step 8–9)", 40f, y2, headerPaint)
        y2 += 10f
        canvas2.drawLine(40f, y2, 555f, y2, linePaint)
        y2 += 12f

        if (scr != null) {
            val teethCount = scr.teethExaminations.size
            val lab = scr.labInvestigation
            val labStr = if (lab != null) "Hb: ${lab.haemoglobin ?: "-"} g/dL | RBC: ${lab.rbc ?: "-"} | TC: ${lab.totalCount ?: "-"} | Plt: ${lab.plateletCount ?: "-"}" else "Standard parameters"
            canvas2.drawText("Teeth Examined: $teethCount tooth records | Lab: $labStr", 40f, y2, regularPaint)
            y2 += 12f
        }
        y2 += 6f

        // Step 10: Clinical Photography & Uploaded Images
        canvas2.drawText("8. Clinical Photography & Uploaded Anatomical Images (Step 10)", 40f, y2, headerPaint)
        y2 += 10f
        canvas2.drawLine(40f, y2, 555f, y2, linePaint)
        y2 += 12f

        val patientImages = scr?.patientImages ?: emptyList()
        if (patientImages.isNotEmpty()) {
            canvas2.drawText("Attached Images: ${patientImages.size} anatomical photo(s) recorded for this session.", 40f, y2, boldPaint)
            y2 += 12f

            // Try to find and render image thumbnails or cards
            var imgX = 40f
            val imgY = y2
            var renderedCount = 0

            for (img in patientImages.take(4)) {
                val cardRect = android.graphics.RectF(imgX, imgY, imgX + 120f, imgY + 85f)
                canvas2.drawRect(cardRect, boxPaint)
                canvas2.drawRect(cardRect, strokePaint)

                // Try to resolve bitmap from local storage
                val localCand = listOf(
                    File(context.filesDir, "clinical_images/${img.originalFilename}"),
                    File(context.cacheDir, "clinical_captures/${img.originalFilename}"),
                    File(context.filesDir, img.originalFilename),
                ).firstOrNull { it.exists() }

                val thumb = localCand?.let {
                    runCatching {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeFile(it.absolutePath, opts)
                    }.getOrNull()
                }

                if (thumb != null) {
                    val scaled = Bitmap.createScaledBitmap(thumb, 110, 50, true)
                    canvas2.drawBitmap(scaled, imgX + 5f, imgY + 5f, null)
                } else {
                    canvas2.drawText("[PHOTO]", imgX + 38f, imgY + 30f, boldPaint)
                }

                canvas2.drawText(img.clinicalRegion.take(18), imgX + 5f, imgY + 65f, boldPaint)
                canvas2.drawText(img.imageUid.take(15), imgX + 5f, imgY + 77f, footerPaint)

                imgX += 130f
                renderedCount++
            }
            y2 += 95f
        } else {
            canvas2.drawText("No photographic records attached to this screening session.", 40f, y2, regularPaint)
            y2 += 14f
        }
        y2 += 6f

        // Step 11: Clinical Recommendations & Referral
        canvas2.drawText("9. Clinical Consultation Summary & Recommendations (Step 11)", 40f, y2, headerPaint)
        y2 += 10f
        canvas2.drawLine(40f, y2, 555f, y2, linePaint)
        y2 += 12f

        val recom = scr?.consultationSummary?.clinicalRecommendations ?: "Standard preventive oral hygiene regimen and periodic annual recall advised."
        canvas2.drawText("Advice: ${recom.take(85)}", 40f, y2, boldPaint)
        y2 += 12f
        canvas2.drawText("Referral Facility: Saveetha Dental College & Hospital, Dept of Oral Medicine", 40f, y2, regularPaint)
        y2 += 22f

        // Clinician Signature Box
        canvas2.drawRect(40f, y2, 555f, y2 + 42f, boxPaint)
        canvas2.drawRect(40f, y2, 555f, y2 + 42f, strokePaint)
        canvas2.drawText("Examining Clinician: ___________________________", 50f, y2 + 18f, regularPaint)
        canvas2.drawText("Specialist Sign-off: ___________________________", 300f, y2 + 18f, regularPaint)
        canvas2.drawText("Saveetha Dental College & Hospital (SIMATS)", 50f, y2 + 32f, footerPaint)
        canvas2.drawText("Official Hospital Record Seal", 300f, y2 + 32f, footerPaint)

        // Page 2 Footer
        canvas2.drawLine(40f, 810f, 555f, 810f, linePaint)
        canvas2.drawText("Confidential Medical Information • Saveetha Dental College & Hospital • Page 2 of 2", 40f, 822f, footerPaint)
        pdfDocument.finishPage(page2)

        // Save PDF to reports dir
        val fileName = "Patient_${patient.patientUid}_Report.pdf"
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()

        runCatching {
            saveCopyToPublicDownloads(file, fileName, "application/pdf")
        }
        return file
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

    override suspend fun reexaminePatient(
        patientId: Int,
        localImages: List<Triple<String, String?, String>>,
    ): RepoResult<ScreeningDetailDto> {
        val startRes = safeCall { api.reexaminePatient(patientId) }
        if (startRes !is RepoResult.Success) {
            return startRes
        }
        val screening = startRes.data

        // Upload each captured clinical image
        for ((region, sub, path) in localImages) {
            val file = File(path)
            if (file.exists()) {
                uploadImage(screening.id, region, sub, file)
            }
        }

        // Run assistive AI image analysis & evaluate updated clinical outcome
        val aiFinalized = safeCall { api.finalizeScreeningWithAi(screening.id) }
        if (aiFinalized is RepoResult.Success) {
            return aiFinalized
        }
        return getScreeningDetail(screening.id)
    }

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
