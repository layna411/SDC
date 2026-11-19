package com.simats.sdc.presentation.patient

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.core.datastore.SessionManager
import com.simats.sdc.data.dto.PatientDto
import com.simats.sdc.data.dto.ScreeningDetailDto
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class PatientFilterType {
    ALL,
    NORMAL,
    ABNORMAL,
    PENDING_REVIEW,
    LESION_PRESENT,
    MALE,
    FEMALE,
}

data class PatientsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val activeFilter: PatientFilterType = PatientFilterType.ALL,
    val sexFilter: String? = null,
    val outcomeFilter: String? = null,
    val lesionFilter: Boolean? = null,
    val patients: List<PatientDto> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val selectedPatient: PatientDto? = null,
    val patientScreenings: List<ScreeningDetailDto> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val accessToken: String = "",
    val baseUrl: String = "",
    val isDownloadingReport: Boolean = false,
    val reportDownloadedFile: File? = null,
    val reportError: String? = null,
    val reportMessage: String? = null,
)

@HiltViewModel
class PatientsViewModel @Inject constructor(
    private val repository: OralCareRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientsUiState())
    val uiState: StateFlow<PatientsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.sessionFlow.collect { session ->
                _uiState.update {
                    it.copy(
                        accessToken = session.accessToken,
                        baseUrl = session.baseUrl,
                    )
                }
            }
        }
    }

    fun loadPatients(page: Int = 1) {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, page = page) }
            when (
                val res = repository.listPatients(
                    search = s.searchQuery,
                    sex = s.sexFilter,
                    outcome = s.outcomeFilter,
                    lesionPresent = s.lesionFilter,
                    page = page,
                )
            ) {
                is RepoResult.Success -> _uiState.update {
                    it.copy(isLoading = false, patients = res.data.items, totalCount = res.data.total)
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun onSearchChange(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
        loadPatients(1)
    }

    fun onFilterSelect(filter: PatientFilterType) {
        val newFilter = if (_uiState.value.activeFilter == filter && filter != PatientFilterType.ALL) {
            PatientFilterType.ALL
        } else {
            filter
        }
        _uiState.update {
            it.copy(
                activeFilter = newFilter,
                sexFilter = when (newFilter) {
                    PatientFilterType.MALE -> "MALE"
                    PatientFilterType.FEMALE -> "FEMALE"
                    else -> null
                },
                outcomeFilter = when (newFilter) {
                    PatientFilterType.NORMAL -> "NORMAL"
                    PatientFilterType.ABNORMAL -> "ABNORMAL"
                    PatientFilterType.PENDING_REVIEW -> "PENDING_CLINICAL_REVIEW"
                    else -> null
                },
                lesionFilter = when (newFilter) {
                    PatientFilterType.LESION_PRESENT -> true
                    else -> null
                },
            )
        }
        loadPatients(1)
    }

    fun onSexFilterChange(sex: String?) {
        when (sex) {
            "MALE" -> onFilterSelect(PatientFilterType.MALE)
            "FEMALE" -> onFilterSelect(PatientFilterType.FEMALE)
            else -> onFilterSelect(PatientFilterType.ALL)
        }
    }

    fun onOutcomeFilterChange(outcome: String?) {
        when (outcome) {
            "NORMAL" -> onFilterSelect(PatientFilterType.NORMAL)
            "ABNORMAL" -> onFilterSelect(PatientFilterType.ABNORMAL)
            "PENDING_CLINICAL_REVIEW" -> onFilterSelect(PatientFilterType.PENDING_REVIEW)
            else -> onFilterSelect(PatientFilterType.ALL)
        }
    }

    fun onLesionFilterChange(lesion: Boolean?) {
        if (lesion == true) {
            onFilterSelect(PatientFilterType.LESION_PRESENT)
        } else {
            onFilterSelect(PatientFilterType.ALL)
        }
    }

    fun openPatientDetail(patient: PatientDto) {
        _uiState.update {
            it.copy(
                selectedPatient = patient,
                isLoadingDetail = true,
                reportDownloadedFile = null,
                reportMessage = null,
                reportError = null,
            )
        }
        viewModelScope.launch {
            val freshPatient = (repository.getPatient(patient.id) as? RepoResult.Success)?.data ?: patient
            val scrRes = repository.getPatientScreenings(patient.id)
            _uiState.update {
                it.copy(
                    selectedPatient = freshPatient,
                    isLoadingDetail = false,
                    patientScreenings = (scrRes as? RepoResult.Success)?.data ?: emptyList(),
                )
            }
        }
    }

    fun refreshSelectedPatient() {
        val p = _uiState.value.selectedPatient ?: return
        openPatientDetail(p)
    }

    fun closePatientDetail() {
        _uiState.update {
            it.copy(
                selectedPatient = null,
                patientScreenings = emptyList(),
                reportDownloadedFile = null,
                reportMessage = null,
                reportError = null,
            )
        }
    }

    fun downloadPatientReport(patient: PatientDto, screenings: List<ScreeningDetailDto>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingReport = true, reportError = null, reportMessage = null) }
            when (val res = repository.downloadPatientReport(patient, screenings)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isDownloadingReport = false,
                            reportDownloadedFile = res.data,
                            reportMessage = "Saved to Downloads/OralCare/${res.data.name}",
                        )
                    }
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isDownloadingReport = false,
                            reportError = res.message,
                        )
                    }
                }
            }
        }
    }

    fun sharePatientReport(context: Context, patient: PatientDto, screenings: List<ScreeningDetailDto>) {
        viewModelScope.launch {
            val existing = _uiState.value.reportDownloadedFile
            val fileToShare = if (existing != null && existing.exists()) {
                existing
            } else {
                _uiState.update { it.copy(isDownloadingReport = true, reportError = null) }
                when (val res = repository.downloadPatientReport(patient, screenings)) {
                    is RepoResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingReport = false,
                                reportDownloadedFile = res.data,
                                reportMessage = "Saved to Downloads/OralCare/${res.data.name}",
                            )
                        }
                        res.data
                    }
                    is RepoResult.Error -> {
                        _uiState.update { it.copy(isDownloadingReport = false, reportError = res.message) }
                        null
                    }
                }
            }

            if (fileToShare != null) {
                runCatching {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        fileToShare,
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "OralCare Report - ${patient.displayName} (${patient.patientUid})")
                        putExtra(Intent.EXTRA_TEXT, "Detailed Clinical Screening Report for ${patient.displayName} (${patient.patientUid}) from Saveetha Dental College & Hospital.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share OralCare Report"))
                }.onFailure { err ->
                    _uiState.update { it.copy(reportError = "Unable to share report: ${err.localizedMessage}") }
                }
            }
        }
    }

    fun clearReportMessage() {
        _uiState.update { it.copy(reportMessage = null, reportError = null) }
    }
}
