package com.simats.sdc.presentation.patient

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
import javax.inject.Inject

data class PatientsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
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

    fun onSexFilterChange(sex: String?) {
        _uiState.update { it.copy(sexFilter = sex) }
        loadPatients(1)
    }

    fun onOutcomeFilterChange(outcome: String?) {
        _uiState.update { it.copy(outcomeFilter = outcome) }
        loadPatients(1)
    }

    fun onLesionFilterChange(lesion: Boolean?) {
        _uiState.update { it.copy(lesionFilter = lesion) }
        loadPatients(1)
    }

    fun openPatientDetail(patient: PatientDto) {
        _uiState.update { it.copy(selectedPatient = patient, isLoadingDetail = true) }
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
        _uiState.update { it.copy(selectedPatient = null, patientScreenings = emptyList()) }
    }
}
