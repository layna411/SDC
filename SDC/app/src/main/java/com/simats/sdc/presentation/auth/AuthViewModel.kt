package com.simats.sdc.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.core.datastore.SessionManager
import com.simats.sdc.core.datastore.UserSession
import com.simats.sdc.core.network.NetworkMonitor
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val devResetToken: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: OralCareRepository,
    private val sessionManager: SessionManager,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val session: StateFlow<UserSession> = sessionManager.sessionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSession(),
    )

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your Username/ID and Password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val res = repository.login(username, password)) {
                is RepoResult.Success -> _uiState.update { it.copy(isLoading = false) }
                is RepoResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun changePassword(currentPwd: String, newPwd: String, confirmPwd: String, onSuccess: () -> Unit) {
        if (newPwd.length < 8) {
            _uiState.update { it.copy(errorMessage = "New password must be at least 8 characters.") }
            return
        }
        if (newPwd != confirmPwd) {
            _uiState.update { it.copy(errorMessage = "New password and confirmation do not match.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val res = repository.changePassword(currentPwd, newPwd)) {
                is RepoResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, infoMessage = res.data) }
                    onSuccess()
                }
                is RepoResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun forgotPassword(usernameOrEmail: String) {
        if (usernameOrEmail.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your username or registered email.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val res = repository.forgotPassword(usernameOrEmail)) {
                is RepoResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = res.data.message,
                        devResetToken = res.data.devResetToken,
                    )
                }
                is RepoResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun resetPassword(token: String, newPwd: String, onSuccess: () -> Unit) {
        if (token.isBlank() || newPwd.length < 8) {
            _uiState.update { it.copy(errorMessage = "Provide a valid reset token and a new password (min 8 chars).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.resetPassword(token, newPwd)) {
                is RepoResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, infoMessage = res.data, devResetToken = null) }
                    onSuccess()
                }
                is RepoResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setDarkMode(enabled) }
    }

    fun updateServerUrl(url: String) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            viewModelScope.launch {
                sessionManager.setBaseUrl(url.trim())
                _uiState.update { it.copy(infoMessage = "API Server URL updated.") }
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Server URL must start with http:// or https://") }
        }
    }
}
