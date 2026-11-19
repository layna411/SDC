package com.simats.sdc.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.data.dto.*
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val stats: DashboardStatsDto? = null,
    val colleges: List<CollegeDto> = emptyList(),
    val centers: List<CenterDto> = emptyList(),
    val organizers: List<OrganizerDto> = emptyList(),
    val users: List<UserDto> = emptyList(),
    val screenings: List<ScreeningDetailDto> = emptyList(),
    val resources: List<EducationalResourceDto> = emptyList(),
    val awareness: List<AwarenessContentDto> = emptyList(),
    val settings: List<AppSettingDto> = emptyList(),
    val issuedCredentialBanner: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: OralCareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun loadDashboardForRole(role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val dashRes = repository.getDashboard()
            if (dashRes is RepoResult.Error) {
                _uiState.update { it.copy(isLoading = false, error = dashRes.message) }
                return@launch
            }
            val stats = (dashRes as RepoResult.Success).data
            val resources = (repository.listEducationalResources() as? RepoResult.Success)?.data ?: emptyList()
            val awareness = (repository.listAwarenessContent() as? RepoResult.Success)?.data ?: emptyList()
            val colleges = if (role == "SUPER_ADMIN" || role == "COLLEGE_ADMIN") {
                (repository.listColleges() as? RepoResult.Success)?.data ?: emptyList()
            } else emptyList()
            val centers = if (role != "HEALTH_ORGANIZER") {
                (repository.listCenters() as? RepoResult.Success)?.data ?: emptyList()
            } else emptyList()
            val organizers = if (role != "HEALTH_ORGANIZER") {
                (repository.listOrganizers() as? RepoResult.Success)?.data ?: emptyList()
            } else emptyList()
            val users = if (role != "HEALTH_ORGANIZER") {
                (repository.listUsers() as? RepoResult.Success)?.data ?: emptyList()
            } else emptyList()
            val screenings = if (role == "CENTER_ADMIN" || role == "COLLEGE_ADMIN" || role == "SUPER_ADMIN") {
                (repository.listScreenings() as? RepoResult.Success)?.data ?: emptyList()
            } else emptyList()
            val appSettings = if (role == "SUPER_ADMIN") {
                (repository.listSettings() as? RepoResult.Success)?.data ?: emptyList()
            } else emptyList()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    stats = stats,
                    colleges = colleges,
                    centers = centers,
                    organizers = organizers,
                    users = users,
                    screenings = screenings,
                    resources = resources,
                    awareness = awareness,
                    settings = appSettings,
                )
            }
        }
    }

    fun clearCredentialBanner() {
        _uiState.update { it.copy(issuedCredentialBanner = null) }
    }

    fun createCollege(req: CollegeCreateDto, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.createCollege(req)) {
                is RepoResult.Success -> {
                    val banner = res.data.adminUser?.let { admin ->
                        "College Created!\nAdmin Username: ${admin.username}\nUser ID: ${admin.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    } ?: "Dental College '${res.data.college.name}' created successfully."
                    _uiState.update { it.copy(issuedCredentialBanner = banner) }
                    loadDashboardForRole(role)
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun toggleCollege(id: Int, active: Boolean, role: String) {
        viewModelScope.launch {
            repository.toggleCollegeStatus(id, active)
            loadDashboardForRole(role)
        }
    }

    fun createCenter(req: CenterCreateDto, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.createCenter(req)) {
                is RepoResult.Success -> {
                    val banner = res.data.adminUser?.let { admin ->
                        "Center Created!\nAdmin Username: ${admin.username}\nUser ID: ${admin.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    } ?: "Screening Center '${res.data.center.name}' created successfully."
                    _uiState.update { it.copy(issuedCredentialBanner = banner) }
                    loadDashboardForRole(role)
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun toggleCenter(id: Int, active: Boolean, role: String) {
        viewModelScope.launch {
            repository.toggleCenterStatus(id, active)
            loadDashboardForRole(role)
        }
    }

    fun createOrganizer(req: OrganizerCreateDto, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.createOrganizer(req)) {
                is RepoResult.Success -> {
                    val u = res.data.organizer.user
                    val banner = "Health Organizer Assistant Created!\nUsername: ${u?.username}\nUser ID: ${u?.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    _uiState.update { it.copy(issuedCredentialBanner = banner) }
                    loadDashboardForRole(role)
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun toggleOrganizer(id: Int, active: Boolean, role: String) {
        viewModelScope.launch {
            repository.toggleOrganizerStatus(id, active)
            loadDashboardForRole(role)
        }
    }

    fun toggleUserStatus(id: Int, active: Boolean, role: String) {
        viewModelScope.launch {
            repository.toggleUserStatus(id, active)
            loadDashboardForRole(role)
        }
    }

    fun saveSetting(key: String, value: String, description: String) {
        viewModelScope.launch {
            repository.upsertSetting(AppSettingUpsertDto(key, value, description))
            loadDashboardForRole("SUPER_ADMIN")
        }
    }
}
