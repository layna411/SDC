package com.simats.sdc.presentation.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.core.common.ValidationUtils
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.FeedbackCreateDto
import com.simats.sdc.data.dto.FeedbackDto
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import com.simats.sdc.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedbackUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val items: List<FeedbackDto> = emptyList(),
    val averageRating: Double = 0.0,
    val totalCount: Int = 0,
    val minRatingFilter: Int? = null,
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val repository: OralCareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun loadFeedback(minRating: Int? = _uiState.value.minRatingFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, minRatingFilter = minRating) }
            when (val res = repository.listFeedback(minRating = minRating)) {
                is RepoResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = res.data.items,
                        averageRating = res.data.averageRating,
                        totalCount = res.data.totalCount,
                    )
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun submitFeedback(patientName: String, email: String, rating: Int, message: String, onDone: () -> Unit) {
        if (patientName.isBlank() || !ValidationUtils.isValidEmail(email) || message.isBlank()) {
            _uiState.update { it.copy(error = "Enter patient name, a valid email address, and feedback message.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            when (val res = repository.createFeedback(FeedbackCreateDto(patientName = patientName, email = email, rating = rating, message = message))) {
                is RepoResult.Success -> {
                    _uiState.update { it.copy(successMessage = "Feedback submitted successfully.") }
                    onDone()
                    loadFeedback()
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun deleteFeedback(id: Int) {
        viewModelScope.launch {
            repository.deleteFeedback(id)
            loadFeedback()
        }
    }
}

@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    var patientName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadFeedback() }

    Column(modifier = Modifier.fillMaxSize().imePadding().let { if (showBackButton) it.navigationBarsPadding() else it }) {
        GlassHeader(
            title = "Patient Feedback",
            subtitle = "5-Star clinical experience ratings & comments",
            showBackButton = showBackButton,
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GlassStatCard(
                    title = "Average Patient Satisfaction Rating",
                    value = "${state.averageRating} / 5.0",
                    icon = Icons.Default.Star,
                    accentColor = OralCareColors.WarningAmber,
                    highlighted = true,
                    subtitle = "Based on ${state.totalCount} feedback record(s)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                GlassCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Submit Patient Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!state.error.isNullOrBlank()) {
                            Text(state.error!!, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!state.successMessage.isNullOrBlank()) {
                            Text(state.successMessage!!, color = OralCareColors.NormalGreen, style = MaterialTheme.typography.bodySmall)
                        }
                        GlassTextField(patientName, { patientName = it }, "Patient Name *")
                        GlassTextField(email, { email = it }, "Email Address *", keyboardType = KeyboardType.Email)

                        Text("5-Star Rating", style = MaterialTheme.typography.labelMedium, color = OralCareColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$star stars",
                                    tint = OralCareColors.WarningAmber,
                                    modifier = Modifier.size(34.dp).clickable { rating = star },
                                )
                            }
                        }

                        GlassTextField(message, { message = it }, "Feedback Message *", singleLine = false, maxLines = 4)
                        GlassButton(
                            text = "Submit Feedback",
                            loading = state.isLoading,
                            icon = Icons.AutoMirrored.Filled.Send,
                            onClick = {
                                viewModel.submitFeedback(patientName, email, rating, message) {
                                    patientName = ""; email = ""; rating = 5; message = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.minRatingFilter == null, onClick = { viewModel.loadFeedback(null) }, label = { Text("All") })
                    FilterChip(selected = state.minRatingFilter == 4, onClick = { viewModel.loadFeedback(4) }, label = { Text("4★ & Up") })
                    FilterChip(selected = state.minRatingFilter == 5, onClick = { viewModel.loadFeedback(5) }, label = { Text("5★ Only") })
                }
            }

            if (state.items.isEmpty() && !state.isLoading) {
                item { EmptyStateView("No feedback entries yet", "Collect patient experience feedback using the form above.") }
            } else {
                items(state.items, key = { it.id }) { fb ->
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(fb.patientName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("★".repeat(fb.rating), color = OralCareColors.WarningAmber)
                                    IconButton(onClick = { viewModel.deleteFeedback(fb.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = OralCareColors.AbnormalRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text(fb.email, style = MaterialTheme.typography.labelSmall, color = OralCareColors.AccentBlueStrong)
                            Text(fb.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
