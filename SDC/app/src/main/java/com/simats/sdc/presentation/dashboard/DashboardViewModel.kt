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
    val issuedCredentials: IssuedCredentialsDto? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: OralCareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = DashboardUiState(isLoading = true)
    }

    suspend fun getActivitiesForUser(userId: Int): List<ActivityLogDto> {
        return (repository.listActivities(userId = userId, limit = 40) as? RepoResult.Success)?.data ?: emptyList()
    }

    suspend fun getActivitiesForCollege(collegeId: Int): List<ActivityLogDto> {
        return (repository.listActivities(collegeId = collegeId, limit = 40) as? RepoResult.Success)?.data ?: emptyList()
    }

    suspend fun getActivitiesForCenter(centerId: Int): List<ActivityLogDto> {
        return (repository.listActivities(centerId = centerId, limit = 40) as? RepoResult.Success)?.data ?: emptyList()
    }

    fun loadDashboardForRole(role: String) {
        viewModelScope.launch {
            // Immediately clear any old session or role state to prevent cross-login data leak
            _uiState.value = DashboardUiState(isLoading = true)
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
        _uiState.update { it.copy(issuedCredentialBanner = null, issuedCredentials = null) }
    }

    fun createCollege(req: CollegeCreateDto, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.createCollege(req)) {
                is RepoResult.Success -> {
                    val admin = res.data.adminUser
                    val creds = admin?.let {
                        IssuedCredentialsDto(
                            title = "College Admin Account Created",
                            entityName = res.data.college.name,
                            username = it.username,
                            userUid = it.userUid,
                            temporaryPassword = res.data.temporaryPassword ?: "",
                            role = "College Admin",
                        )
                    }
                    val banner = admin?.let {
                        "College Created!\nAdmin Username: ${it.username}\nUser ID: ${it.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    } ?: "Dental College '${res.data.college.name}' created successfully."
                    _uiState.update { it.copy(issuedCredentialBanner = banner, issuedCredentials = creds) }
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
                    val admin = res.data.adminUser
                    val creds = admin?.let {
                        IssuedCredentialsDto(
                            title = "Center Admin Account Created",
                            entityName = res.data.center.name,
                            username = it.username,
                            userUid = it.userUid,
                            temporaryPassword = res.data.temporaryPassword ?: "",
                            role = "Center Admin",
                        )
                    }
                    val banner = admin?.let {
                        "Center Created!\nAdmin Username: ${it.username}\nUser ID: ${it.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    } ?: "Screening Center '${res.data.center.name}' created successfully."
                    _uiState.update { it.copy(issuedCredentialBanner = banner, issuedCredentials = creds) }
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

    fun resetOrGenerateCenterAdmin(centerId: Int, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.generateOrResetCenterAdmin(centerId)) {
                is RepoResult.Success -> {
                    val admin = res.data.adminUser
                    val creds = admin?.let {
                        IssuedCredentialsDto(
                            title = "Center Admin Credentials Reset",
                            entityName = res.data.center.name,
                            username = it.username,
                            userUid = it.userUid,
                            temporaryPassword = res.data.temporaryPassword ?: "",
                            role = "Center Admin",
                        )
                    }
                    val banner = if (admin != null) {
                        "Center Admin Credentials Issued!\nCenter: ${res.data.center.name}\nAdmin Username: ${admin.username}\nUser ID: ${admin.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    } else {
                        "Center credentials updated successfully."
                    }
                    _uiState.update { it.copy(isLoading = false, issuedCredentialBanner = banner, issuedCredentials = creds) }
                    loadDashboardForRole(role)
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun createOrganizer(req: OrganizerCreateDto, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.createOrganizer(req)) {
                is RepoResult.Success -> {
                    val u = res.data.organizer.user
                    val creds = IssuedCredentialsDto(
                        title = "Health Assistant Account Created",
                        entityName = res.data.organizer.employeeCode,
                        username = u?.username ?: req.username,
                        userUid = u?.userUid,
                        temporaryPassword = res.data.temporaryPassword,
                        role = "Health Organizer Assistant",
                    )
                    val banner = "Health Organizer Assistant Created!\nUsername: ${u?.username ?: req.username}\nUser ID: ${u?.userUid}\nTemporary Password: ${res.data.temporaryPassword}"
                    _uiState.update { it.copy(issuedCredentialBanner = banner, issuedCredentials = creds) }
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
