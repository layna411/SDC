package com.simats.sdc.presentation.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.ActivityLogDto
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import com.simats.sdc.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionFilter: String? = null,
    val logs: List<ActivityLogDto> = emptyList(),
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: OralCareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = ActivityUiState()
    }

    fun loadLogs(action: String? = _uiState.value.actionFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, actionFilter = action) }
            when (val res = repository.listActivities(action)) {
                is RepoResult.Success -> _uiState.update { it.copy(isLoading = false, logs = res.data) }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }
}

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadLogs() }

    val filters = listOf(
        null to "All",
        "LOGIN" to "Login",
        "PATIENT_CREATED" to "Patients",
        "SCREENING_SUBMITTED" to "Screenings",
        "IMAGE_UPLOADED" to "Images",
        "AI_ANALYSIS_REQUESTED" to "AI",
        "REPORT_DOWNLOADED" to "Reports",
        "FEEDBACK_SUBMITTED" to "Feedback",
    )

    Column(modifier = Modifier.fillMaxSize().let { if (showBackButton) it.navigationBarsPadding() else it }) {
        GlassHeader(
            title = "Activity History",
            subtitle = "Your personal clinical and account activity",
            showBackButton = showBackButton,
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewModel.loadLogs() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { (code, label) ->
                    FilterChip(
                        selected = state.actionFilter == code,
                        onClick = { viewModel.loadLogs(code) },
                        label = { Text(label) },
                    )
                }
            }

            when {
                state.isLoading -> LoadingView("Loading activity history...")
                state.error != null -> ErrorStateView(state.error!!, onRetry = { viewModel.loadLogs() })
                state.logs.isEmpty() -> EmptyStateView("No activity yet", "Your screening and account actions will appear here.")
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(state.logs, key = { it.id }) { log ->
                            GlassCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(38.dp).clip(CircleShape).background(OralCareColors.LightBlue),
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(19.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(log.action.replace("_", " "), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Text("${log.entityType} ${log.entityId ?: ""}".trim(), style = MaterialTheme.typography.bodySmall)
                                        Text(log.createdAt.take(19).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
