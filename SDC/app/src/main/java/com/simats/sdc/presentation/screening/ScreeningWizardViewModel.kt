package com.simats.sdc.presentation.screening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.common.ValidationUtils
import com.simats.sdc.core.database.ScreeningDraftEntity
import com.simats.sdc.core.datastore.SessionManager
import com.simats.sdc.data.dto.*
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class LocalCapturedImage(
    val id: String = UUID.randomUUID().toString(),
    val clinicalRegion: String,
    val subRegion: String? = null,
    val localPath: String,
)

data class ScreeningWizardState(
    val draftId: String = UUID.randomUUID().toString(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val currentStep: Int = 1,
    val validationError: String? = null,
    val isSubmitting: Boolean = false,
    val draftSavedNotice: String? = null,

    // Step 1 - Consent & Location
    val hub: String = "",
    val healthPartner: String = "",
    val healthworkerName: String = "",
    val participantInfoSheetAck: Boolean = false,
    val consentFormSigned: Boolean = false,
    val consentAccepted: Boolean = false,
    val termsAccepted: Boolean = false,
    val geolocationLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationPermissionGranted: Boolean = false,

    // Step 2 - Personal Details
    val registrationDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val prefix: String = "Mr.",
    val firstName: String = "",
    val lastName: String = "",
    val age: String = "",
    val sex: String = "MALE",
    val religion: String = "Hindu",
    val maritalStatus: String = "Married",
    val education: String = "Secondary",
    val occupation: String = "",
    val familyIncomePerMonth: String = "₹10,000 – ₹25,000",
    val phoneNumber: String = "",
    val address: String = "",

    // Step 3 - Personal History
    val dietHistory: String = "Mixed",
    val spiceConsumption: String = "Moderate",
    val hasHabitHistory: Boolean = false,
    val personalHistoryNotes: String = "",

    // Step 4 - Habit History
    val habits: List<HabitItemDto> = emptyList(),

    // Step 5 - Medical & Dental History
    val cleaningAid: String = "Tooth Brush",
    val materialUsed: String = "Tooth Paste",
    val materialOther: String = "",
    val brushingMethod: String = "Horizontal",
    val brushingFrequency: String = "Twice daily",
    val brushingDuration: String = "2–3 minutes",
    val toothbrushChangeFrequency: String = "Every 3 months",
    val otherHygieneAids: String = "None",
    val familyHistoryConditions: Set<String> = setOf("No Relevant History"),
    val familyHistoryOther: String = "",
    val pastMedicalConditions: Set<String> = setOf("No Relevant History"),
    val pastMedicalOther: String = "",
    val pastDentalHistory: Boolean = false,
    val pastDentalDetails: String = "",
    val dentureUsage: Boolean = false,
    val dentureDetails: String = "",

    // Step 6 - Clinical Findings (keyed by "Region|SubRegion")
    val clinicalFindings: Map<String, ClinicalFindingDto> = emptyMap(),

    // Step 7 - Lesion Details
    val lesionPresent: Boolean = false,
    val lesions: List<LesionDetailDto> = emptyList(),

    // Step 8 - Teeth Examination (keyed by FDI tooth number)
    val dentitionType: String = "PERMANENT",
    val teethRecords: Map<String, ToothExaminationDto> = emptyMap(),

    // Step 9 - Lab Investigation
    val labTestDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val haemoglobin: String = "",
    val rbc: String = "",
    val totalCount: String = "",
    val plateletCount: String = "",
    val percentage: String = "",
    val biopsyReportSummary: String = "",
    val labRemarks: String = "",

    // Step 10 - Images & AI
    val localImages: List<LocalCapturedImage> = emptyList(),
    val aiStatus: AIServiceStatusDto? = null,

    // Step 11 - Final Result
    val clinicalRecommendations: String = "",
    val submittedResult: ScreeningDetailDto? = null,
    val offlineQueuedMessage: String? = null,
    val aiAnalysisByImageId: Map<Int, AIAnalysisDto> = emptyMap(),
)

@HiltViewModel
class ScreeningWizardViewModel @Inject constructor(
    private val repository: OralCareRepository,
    private val sessionManager: SessionManager,
    private val gson: Gson,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreeningWizardState())
    val state: StateFlow<ScreeningWizardState> = _state.asStateFlow()

    val savedDrafts: StateFlow<List<ScreeningDraftEntity>> = repository.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val session = sessionManager.getSessionSnapshot()
            _state.update {
                it.copy(
                    healthworkerName = session.fullName.ifBlank { "Health Organizer Assistant" },
                    hub = it.hub.ifBlank { "Regional Oral Health Hub" },
                    healthPartner = it.healthPartner.ifBlank { "OralCare Clinical Partner" },
                )
            }
            when (val aiRes = repository.getAiStatus()) {
                is RepoResult.Success -> _state.update { it.copy(aiStatus = aiRes.data) }
                else -> {}
            }
        }
    }

    fun updateState(transform: (ScreeningWizardState) -> ScreeningWizardState) {
        _state.update { transform(it).copy(validationError = null, draftSavedNotice = null) }
    }

    fun goToStep(step: Int) {
        val clamped = step.coerceIn(1, 11)
        _state.update { it.copy(currentStep = clamped, validationError = null) }
        autoSaveDraft()
    }

    fun nextStep() {
        val current = _state.value
        val error = validateStep(current, current.currentStep)
        if (error != null) {
            _state.update { it.copy(validationError = error) }
            return
        }
        val next = if (current.currentStep == 3 && !current.hasHabitHistory) {
            // Allow graceful skip of Step 4 when Habit History is NO, while still reachable via step tabs
            5
        } else {
            (current.currentStep + 1).coerceAtMost(11)
        }
        _state.update { it.copy(currentStep = next, validationError = null) }
        autoSaveDraft()
    }

    fun previousStep() {
        val current = _state.value
        val prev = if (current.currentStep == 5 && !current.hasHabitHistory) 3 else (current.currentStep - 1).coerceAtLeast(1)
        _state.update { it.copy(currentStep = prev, validationError = null) }
    }

    fun validateStep(s: ScreeningWizardState, step: Int): String? = when (step) {
        1 -> when {
            s.hub.isBlank() -> "Hub name is required."
            s.healthPartner.isBlank() -> "Health Partner is required."
            s.healthworkerName.isBlank() -> "Healthworker name is required."
            !s.participantInfoSheetAck -> "Participant Information Sheet acknowledgment is required."
            !s.consentFormSigned -> "Signed Consent Form confirmation is required."
            !s.consentAccepted -> "Participant consent acceptance is required."
            !s.termsAccepted -> "Terms and Conditions acceptance is required."
            else -> null
        }
        2 -> when {
            s.firstName.isBlank() -> "Patient First Name is required."
            s.lastName.isBlank() -> "Patient Last Name is required."
            !ValidationUtils.isValidAge(s.age) -> "Please enter a valid patient age (0–130)."
            !ValidationUtils.isValidPhone(s.phoneNumber) -> "Please enter a valid phone number (7–15 digits)."
            s.address.isBlank() -> "Patient address is required."
            else -> null
        }
        7 -> if (s.lesionPresent && s.lesions.isEmpty()) "Add at least one lesion entry or select Lesion Absent." else null
        else -> null
    }

    fun toggleFamilyCondition(condition: String) {
        updateState { s ->
            val next = s.familyHistoryConditions.toMutableSet()
            if (condition == "No Relevant History") {
                next.clear()
                next.add(condition)
            } else {
                next.remove("No Relevant History")
                if (!next.add(condition)) next.remove(condition)
                if (next.isEmpty()) next.add("No Relevant History")
            }
            s.copy(familyHistoryConditions = next)
        }
    }

    fun toggleMedicalCondition(condition: String) {
        updateState { s ->
            val next = s.pastMedicalConditions.toMutableSet()
            if (condition == "No Relevant History") {
                next.clear()
                next.add(condition)
            } else {
                next.remove("No Relevant History")
                if (!next.add(condition)) next.remove(condition)
                if (next.isEmpty()) next.add("No Relevant History")
            }
            s.copy(pastMedicalConditions = next)
        }
    }

    fun updateClinicalFinding(
        regionName: String,
        subRegion: String,
        status: String,
        observations: List<ClinicalObservationDto>,
        remarks: String,
    ) {
        val key = "$regionName|$subRegion"
        updateState { s ->
            val updated = s.clinicalFindings.toMutableMap()
            updated[key] = ClinicalFindingDto(
                anatomicalRegion = regionName,
                subRegion = subRegion,
                status = status,
                remarks = remarks.ifBlank { null },
                observations = observations,
            )
            s.copy(clinicalFindings = updated)
        }
    }

    fun addHabit(habit: HabitItemDto) = updateState { it.copy(habits = it.habits + habit) }
    fun removeHabit(index: Int) = updateState { it.copy(habits = it.habits.filterIndexed { i, _ -> i != index }) }

    fun addLesion(lesion: LesionDetailDto) = updateState { it.copy(lesionPresent = true, lesions = it.lesions + lesion) }
    fun removeLesion(index: Int) = updateState { it.copy(lesions = it.lesions.filterIndexed { i, _ -> i != index }) }

    fun updateToothExam(exam: ToothExaminationDto) {
        updateState { s ->
            val updated = s.teethRecords.toMutableMap()
            updated[exam.toothNumber] = exam
            s.copy(teethRecords = updated)
        }
    }

    fun addCapturedImage(region: String, subRegion: String?, localPath: String) {
        updateState {
            it.copy(localImages = it.localImages + LocalCapturedImage(clinicalRegion = region, subRegion = subRegion, localPath = localPath))
        }
    }

    fun removeCapturedImage(id: String) {
        updateState { it.copy(localImages = it.localImages.filterNot { img -> img.id == id }) }
    }

    fun buildBundle(submitImmediately: Boolean): CompleteScreeningBundleDto {
        val s = _state.value
        val hasAnyLab = listOf(s.haemoglobin, s.rbc, s.totalCount, s.plateletCount, s.percentage, s.biopsyReportSummary, s.labRemarks).any { it.isNotBlank() }

        return CompleteScreeningBundleDto(
            idempotencyKey = s.idempotencyKey,
            patient = PatientCreateDto(
                idempotencyKey = "pat-${s.idempotencyKey}",
                registrationDate = s.registrationDate,
                prefix = s.prefix,
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                age = s.age.toIntOrNull() ?: 0,
                sex = s.sex,
                religion = s.religion,
                maritalStatus = s.maritalStatus,
                education = s.education,
                occupation = s.occupation.ifBlank { null },
                familyIncomePerMonth = s.familyIncomePerMonth,
                phoneNumber = s.phoneNumber.trim(),
                address = s.address.trim(),
            ),
            screeningDate = s.registrationDate,
            consent = ConsentStepDto(
                hub = s.hub,
                healthPartner = s.healthPartner,
                healthworkerName = s.healthworkerName,
                participantInfoSheetAck = s.participantInfoSheetAck,
                consentFormSigned = s.consentFormSigned,
                consentAccepted = s.consentAccepted,
                termsAccepted = s.termsAccepted,
                geolocationLabel = s.geolocationLabel.ifBlank { null },
                latitude = if (s.locationPermissionGranted) s.latitude else null,
                longitude = if (s.locationPermissionGranted) s.longitude else null,
                locationPermissionGranted = s.locationPermissionGranted,
            ),
            personalHistory = PersonalHistoryDto(
                dietHistory = s.dietHistory,
                spiceConsumption = s.spiceConsumption,
                hasHabitHistory = s.hasHabitHistory,
                notes = s.personalHistoryNotes.ifBlank { null },
            ),
            habits = if (s.hasHabitHistory) s.habits else emptyList(),
            medicalDentalHistory = MedicalDentalHistoryDto(
                cleaningAid = s.cleaningAid,
                materialUsed = s.materialUsed,
                materialOther = s.materialOther.ifBlank { null },
                brushingMethod = s.brushingMethod,
                brushingFrequency = s.brushingFrequency,
                brushingDuration = s.brushingDuration,
                toothbrushChangeFrequency = s.toothbrushChangeFrequency,
                otherHygieneAids = s.otherHygieneAids,
                familyHistoryConditions = s.familyHistoryConditions.toList(),
                familyHistoryOther = s.familyHistoryOther.ifBlank { null },
                pastMedicalConditions = s.pastMedicalConditions.toList(),
                pastMedicalOther = s.pastMedicalOther.ifBlank { null },
                pastDentalHistory = s.pastDentalHistory,
                pastDentalDetails = s.pastDentalDetails.ifBlank { null },
                dentureUsage = s.dentureUsage,
                dentureDetails = s.dentureDetails.ifBlank { null },
            ),
            clinicalFindings = s.clinicalFindings.values.toList(),
            lesionPresent = s.lesionPresent,
            lesions = if (s.lesionPresent) s.lesions else emptyList(),
            teethExaminations = s.teethRecords.values.toList(),
            labInvestigation = if (hasAnyLab) {
                LabInvestigationDto(
                    testDate = s.labTestDate.ifBlank { null },
                    haemoglobin = s.haemoglobin.toDoubleOrNull(),
                    rbc = s.rbc.toDoubleOrNull(),
                    totalCount = s.totalCount.toDoubleOrNull(),
                    plateletCount = s.plateletCount.toDoubleOrNull(),
                    percentage = s.percentage.toDoubleOrNull(),
                    biopsyReportSummary = s.biopsyReportSummary.ifBlank { null },
                    laboratoryRemarks = s.labRemarks.ifBlank { null },
                )
            } else null,
            clinicalRecommendations = s.clinicalRecommendations.ifBlank { null },
            submitImmediately = submitImmediately,
        )
    }

    fun saveDraftManually() {
        autoSaveDraft(showBanner = true)
    }

    private fun autoSaveDraft(showBanner: Boolean = false) {
        val s = _state.value
        if (s.submittedResult != null) return
        viewModelScope.launch {
            val preview = listOf(s.firstName, s.lastName).filter { it.isNotBlank() }.joinToString(" ")
            repository.saveDraftLocally(
                draftId = s.draftId,
                idempotencyKey = s.idempotencyKey,
                patientNamePreview = preview.ifBlank { "Draft Step ${s.currentStep}" },
                currentStep = s.currentStep,
                bundle = buildBundle(submitImmediately = false),
            )
            if (showBanner) {
                _state.update { it.copy(draftSavedNotice = "Draft saved locally on device.") }
            }
        }
    }

    fun resumeDraft(draft: ScreeningDraftEntity) {
        try {
            val b = gson.fromJson(draft.payloadJson, CompleteScreeningBundleDto::class.java)
            val findingsMap = b.clinicalFindings.associateBy { "${it.anatomicalRegion}|${it.subRegion ?: "General"}" }
            val teethMap = b.teethExaminations.associateBy { it.toothNumber }
            _state.update { s ->
                s.copy(
                    draftId = draft.draftId,
                    idempotencyKey = draft.idempotencyKey,
                    currentStep = draft.currentStep.coerceIn(1, 11),
                    hub = b.consent.hub,
                    healthPartner = b.consent.healthPartner,
                    healthworkerName = b.consent.healthworkerName,
                    participantInfoSheetAck = b.consent.participantInfoSheetAck,
                    consentFormSigned = b.consent.consentFormSigned,
                    consentAccepted = b.consent.consentAccepted,
                    termsAccepted = b.consent.termsAccepted,
                    geolocationLabel = b.consent.geolocationLabel ?: "",
                    latitude = b.consent.latitude,
                    longitude = b.consent.longitude,
                    locationPermissionGranted = b.consent.locationPermissionGranted,
                    prefix = b.patient?.prefix ?: "Mr.",
                    firstName = b.patient?.firstName ?: "",
                    lastName = b.patient?.lastName ?: "",
                    age = b.patient?.age?.toString() ?: "",
                    sex = b.patient?.sex ?: "MALE",
                    phoneNumber = b.patient?.phoneNumber ?: "",
                    address = b.patient?.address ?: "",
                    dietHistory = b.personalHistory.dietHistory,
                    spiceConsumption = b.personalHistory.spiceConsumption,
                    hasHabitHistory = b.personalHistory.hasHabitHistory,
                    habits = b.habits,
                    cleaningAid = b.medicalDentalHistory.cleaningAid,
                    materialUsed = b.medicalDentalHistory.materialUsed,
                    familyHistoryConditions = b.medicalDentalHistory.familyHistoryConditions.toSet().ifEmpty { setOf("No Relevant History") },
                    pastMedicalConditions = b.medicalDentalHistory.pastMedicalConditions.toSet().ifEmpty { setOf("No Relevant History") },
                    pastDentalHistory = b.medicalDentalHistory.pastDentalHistory,
                    pastDentalDetails = b.medicalDentalHistory.pastDentalDetails ?: "",
                    dentureUsage = b.medicalDentalHistory.dentureUsage,
                    dentureDetails = b.medicalDentalHistory.dentureDetails ?: "",
                    clinicalFindings = findingsMap,
                    lesionPresent = b.lesionPresent,
                    lesions = b.lesions,
                    teethRecords = teethMap,
                    haemoglobin = b.labInvestigation?.haemoglobin?.toString() ?: "",
                    rbc = b.labInvestigation?.rbc?.toString() ?: "",
                    totalCount = b.labInvestigation?.totalCount?.toString() ?: "",
                    plateletCount = b.labInvestigation?.plateletCount?.toString() ?: "",
                    percentage = b.labInvestigation?.percentage?.toString() ?: "",
                    biopsyReportSummary = b.labInvestigation?.biopsyReportSummary ?: "",
                    submittedResult = null,
                    offlineQueuedMessage = null,
                )
            }
        } catch (_: Exception) {
            _state.update { it.copy(validationError = "Unable to restore draft.") }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch { repository.deleteDraft(draftId) }
    }

    fun submitFinalScreening() {
        val s = _state.value
        val step1Err = validateStep(s, 1)
        if (step1Err != null) {
            _state.update { it.copy(currentStep = 1, validationError = step1Err) }
            return
        }
        val step2Err = validateStep(s, 2)
        if (step2Err != null) {
            _state.update { it.copy(currentStep = 2, validationError = step2Err) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, validationError = null, offlineQueuedMessage = null) }
            val bundle = buildBundle(submitImmediately = true)
            val imgs = s.localImages.map { Triple(it.clinicalRegion, it.subRegion, it.localPath) }
            when (val res = repository.submitCompleteScreening(s.draftId, bundle, imgs)) {
                is RepoResult.Success -> {
                    _state.update { it.copy(isSubmitting = false, submittedResult = res.data) }
                }
                is RepoResult.Error -> {
                    if (res.message.startsWith("Offline mode:")) {
                        _state.update { it.copy(isSubmitting = false, offlineQueuedMessage = res.message) }
                    } else {
                        _state.update { it.copy(isSubmitting = false, validationError = res.message) }
                    }
                }
            }
        }
    }

    fun requestAiAnalysisForUploadedImage(imageId: Int) {
        viewModelScope.launch {
            when (val res = repository.analyzeImage(imageId)) {
                is RepoResult.Success -> {
                    _state.update {
                        it.copy(aiAnalysisByImageId = it.aiAnalysisByImageId + (imageId to res.data))
                    }
                }
                is RepoResult.Error -> _state.update { it.copy(validationError = res.message) }
            }
        }
    }

    fun submitAiClinicianReview(analysisId: Int, imageId: Int, status: String, notes: String) {
        viewModelScope.launch {
            when (val res = repository.reviewAiAnalysis(analysisId, status, notes.ifBlank { null })) {
                is RepoResult.Success -> {
                    _state.update { s ->
                        val existing = s.aiAnalysisByImageId[imageId]
                        if (existing != null) {
                            s.copy(aiAnalysisByImageId = s.aiAnalysisByImageId + (imageId to existing.copy(reviewStatus = status)))
                        } else s
                    }
                }
                is RepoResult.Error -> _state.update { it.copy(validationError = res.message) }
            }
        }
    }

    fun startFreshScreening() {
        val currentWorker = _state.value.healthworkerName
        val currentAi = _state.value.aiStatus
        _state.value = ScreeningWizardState(
            healthworkerName = currentWorker,
            hub = "Regional Oral Health Hub",
            healthPartner = "OralCare Clinical Partner",
            aiStatus = currentAi,
        )
    }
}
