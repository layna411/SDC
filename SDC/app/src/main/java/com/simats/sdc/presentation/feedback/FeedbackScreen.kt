package com.simats.sdc.presentation.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
                    _uiState.update { it.copy(isLoading = false, successMessage = "Feedback submitted successfully.") }
                    onDone()
                    loadFeedback()
                }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun replyFeedback(id: Int, replyText: String, onDone: () -> Unit = {}) {
        if (replyText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            when (val res = repository.replyFeedback(id, replyText.trim())) {
                is RepoResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Official reply recorded successfully.") }
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

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}

@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    currentRole: String = "",
    userCollegeId: Int? = null,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    var patientName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }
    var message by remember { mutableStateOf("") }

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "NEEDS_REPLY", "REPLIED", "5_STAR"
    var showSubmitForm by remember { mutableStateOf(currentRole != "SUPER_ADMIN" && currentRole != "COLLEGE_ADMIN") }
    var replyingFeedback by remember { mutableStateOf<FeedbackDto?>(null) }
    var replyInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadFeedback()
    }

    val filteredItems = remember(state.items, selectedFilter) {
        when (selectedFilter) {
            "NEEDS_REPLY" -> state.items.filter { it.adminReply.isNullOrBlank() }
            "REPLIED" -> state.items.filter { !it.adminReply.isNullOrBlank() }
            "5_STAR" -> state.items.filter { it.rating == 5 }
            else -> state.items
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .let { if (showBackButton) it.navigationBarsPadding() else it }
    ) {
        GlassHeader(
            title = "Patient Feedback & Replies",
            subtitle = if (currentRole == "SUPER_ADMIN") {
                "Manage all college & center feedback and official replies"
            } else if (currentRole == "COLLEGE_ADMIN") {
                "Manage feedback and reply for your dental college"
            } else {
                "5-Star clinical experience ratings & comments"
            },
            showBackButton = showBackButton,
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewModel.loadFeedback() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OralCareColors.AccentBlueStrong)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Status & Alerts
            if (!state.error.isNullOrBlank()) {
                item {
                    StatusPill("Error: ${state.error}", OralCareColors.AbnormalRedBg, OralCareColors.AbnormalRed)
                }
            }
            if (!state.successMessage.isNullOrBlank()) {
                item {
                    StatusPill("✓ ${state.successMessage}", OralCareColors.NormalGreenBg, OralCareColors.NormalGreen)
                }
            }

            // Summary Stat Card
            item {
                GlassStatCard(
                    title = "Average Patient Satisfaction Rating",
                    value = "${state.averageRating} / 5.0",
                    icon = Icons.Default.Star,
                    accentColor = OralCareColors.WarningAmber,
                    subtitle = "Based on ${state.totalCount} total feedback record(s)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All (${state.items.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "NEEDS_REPLY",
                        onClick = { selectedFilter = "NEEDS_REPLY" },
                        label = {
                            val count = state.items.count { it.adminReply.isNullOrBlank() }
                            Text("Awaiting Reply ($count)")
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == "REPLIED",
                        onClick = { selectedFilter = "REPLIED" },
                        label = {
                            val count = state.items.count { !it.adminReply.isNullOrBlank() }
                            Text("Replied ($count)")
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == "5_STAR",
                        onClick = { selectedFilter = "5_STAR" },
                        label = {
                            val count = state.items.count { it.rating == 5 }
                            Text("5★ Only ($count)")
                        }
                    )
                }
            }

            // Collapsible Submit Feedback Button & Form
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = if (showSubmitForm) "Hide Feedback Submission Form" else "+ Submit New Patient Feedback",
                        onClick = { showSubmitForm = !showSubmitForm },
                        isPrimary = false,
                        icon = if (showSubmitForm) Icons.Default.ExpandLess else Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (showSubmitForm) {
                        GlassCard(contentPadding = PaddingValues(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Submit Patient Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                GlassTextField(patientName, { patientName = it }, "Patient Name *")
                                GlassTextField(email, { email = it }, "Email Address *", keyboardType = KeyboardType.Email)

                                Text("5-Star Experience Rating", style = MaterialTheme.typography.labelMedium, color = OralCareColors.TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (1..5).forEach { star ->
                                        Icon(
                                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "$star stars",
                                            tint = OralCareColors.WarningAmber,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clickable { rating = star },
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
                                            patientName = ""
                                            email = ""
                                            rating = 5
                                            message = ""
                                            showSubmitForm = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // Feedback Cards List
            if (filteredItems.isEmpty() && !state.isLoading) {
                item {
                    EmptyStateView(
                        title = when (selectedFilter) {
                            "NEEDS_REPLY" -> "No pending feedback"
                            "REPLIED" -> "No answered feedback yet"
                            "5_STAR" -> "No 5-star feedback yet"
                            else -> "No feedback entries yet"
                        },
                        subtitle = "Feedback submitted by patients across centers will appear here.",
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { fb ->
                    val isAssignedToUserCollege = fb.collegeId == null || userCollegeId == null || fb.collegeId == userCollegeId
                    val canReply = currentRole == "SUPER_ADMIN" || (currentRole == "COLLEGE_ADMIN" && isAssignedToUserCollege)

                    GlassCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Header: Name, Stars, Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fb.patientName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    if (!fb.createdAt.isNullOrBlank()) {
                                        Text(
                                            text = fb.createdAt.replace("T", " ").take(16),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OralCareColors.TextSecondary,
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        (1..5).forEach { star ->
                                            Icon(
                                                imageVector = if (star <= fb.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = null,
                                                tint = OralCareColors.WarningAmber,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    if (currentRole == "SUPER_ADMIN") {
                                        IconButton(onClick = { viewModel.deleteFeedback(fb.id) }) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = OralCareColors.AbnormalRed,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // Email & Institutional Affiliations
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(14.dp))
                                Text(fb.email, style = MaterialTheme.typography.bodySmall, color = OralCareColors.AccentBlueStrong)
                            }

                            if (!fb.collegeName.isNullOrBlank() || !fb.centerName.isNullOrBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    if (!fb.collegeName.isNullOrBlank()) {
                                        StatusPill(
                                            text = "College: ${fb.collegeName}",
                                            bg = OralCareColors.LightBlue,
                                            fg = OralCareColors.AccentBlueStrong,
                                        )
                                    }
                                    if (!fb.centerName.isNullOrBlank()) {
                                        StatusPill(
                                            text = "Center: ${fb.centerName}",
                                            bg = OralCareColors.PastelBlue.copy(alpha = 0.5f),
                                            fg = OralCareColors.AccentBlueStrong,
                                        )
                                    }
                                }
                            }

                            // Patient Message
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PATIENT COMMENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OralCareColors.TextSecondary,
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.5f) else OralCareColors.LightBlue.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, if (isDark) OralCareColors.DarkBorder else Color(0xFFCCE4FF)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = fb.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }

                            // Official Admin Response Section
                            if (!fb.adminReply.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.85f) else Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF1E3A2B) else Color(0xFF86EFAC)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        // Header Row: Icon + "Official Response" on left, Edit action pill on right
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f, fill = false),
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(OralCareColors.NormalGreenBg),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.QuestionAnswer,
                                                        contentDescription = null,
                                                        tint = OralCareColors.NormalGreen,
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                }
                                                Text(
                                                    text = "Official Response",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = OralCareColors.NormalGreen,
                                                )
                                            }

                                            if (canReply) {
                                                Surface(
                                                    onClick = {
                                                        replyingFeedback = fb
                                                        replyInput = fb.adminReply
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isDark) OralCareColors.DarkCard else OralCareColors.LightBlue,
                                                    border = BorderStroke(1.dp, if (isDark) OralCareColors.DarkBorder else Color(0xFFB9D7F9)),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Reply",
                                                            tint = OralCareColors.AccentBlueStrong,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                        Text(
                                                            text = "Edit",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = OralCareColors.AccentBlueStrong,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Reply Body
                                        Text(
                                            text = fb.adminReply,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        // Divider
                                        HorizontalDivider(
                                            color = if (isDark) Color(0xFF1E3A2B) else Color(0xFFBBF7D0),
                                            thickness = 0.8.dp,
                                        )

                                        // Metadata Footer
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f, fill = false),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = OralCareColors.TextSecondary,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                                Text(
                                                    text = "${fb.repliedByName ?: "Administrator"} • ${fb.repliedByRole?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Admin"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = OralCareColors.TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }

                                            if (!fb.repliedAt.isNullOrBlank()) {
                                                Spacer(Modifier.width(8.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        tint = OralCareColors.TextSecondary,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = fb.repliedAt.replace("T", " ").take(16),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = OralCareColors.TextSecondary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Pending Reply State
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.5f) else OralCareColors.WarningAmberBg.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, if (isDark) OralCareColors.DarkBorder else OralCareColors.WarningAmber.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f, fill = false),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(OralCareColors.WarningAmber),
                                            )
                                            Text(
                                                text = "Awaiting Official Response",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = OralCareColors.WarningAmber,
                                            )
                                        }

                                        if (canReply) {
                                            Surface(
                                                onClick = {
                                                    replyingFeedback = fb
                                                    replyInput = ""
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = OralCareColors.AccentBlueStrong,
                                                modifier = Modifier.padding(start = 8.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Text(
                                                        text = "Reply",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                    )
                                                }
                                            }
                                        } else if (currentRole == "COLLEGE_ADMIN" && !isAssignedToUserCollege) {
                                            Text(
                                                text = "Assigned to other college",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OralCareColors.TextSecondary,
                                            )
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

    // Official Reply Dialog
    if (replyingFeedback != null) {
        val target = replyingFeedback!!
        GlassDialog(
            title = if (target.adminReply.isNullOrBlank()) "Reply to Patient Feedback" else "Edit Official Response",
            onDismiss = { replyingFeedback = null },
            confirmButton = {
                GlassButton(
                    text = "Submit Reply",
                    onClick = {
                        viewModel.replyFeedback(target.id, replyInput) {
                            replyingFeedback = null
                            replyInput = ""
                        }
                    },
                    loading = state.isLoading,
                    enabled = replyInput.isNotBlank(),
                )
            },
            dismissButton = {
                GlassButton(
                    text = "Cancel",
                    isPrimary = false,
                    onClick = { replyingFeedback = null },
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Info Summary Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) OralCareColors.DarkSurface else OralCareColors.LightBlue.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isDark) OralCareColors.DarkBorder else Color(0xFFCBE3FB)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(target.patientName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("★".repeat(target.rating), color = OralCareColors.WarningAmber, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(target.email, style = MaterialTheme.typography.bodySmall, color = OralCareColors.AccentBlueStrong)
                        if (!target.collegeName.isNullOrBlank()) {
                            Text("College: ${target.collegeName}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                        }
                    }
                }

                Text(
                    text = "PATIENT COMMENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = OralCareColors.TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "\"${target.message}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Spacer(Modifier.height(2.dp))
                GlassTextField(
                    value = replyInput,
                    onValueChange = { replyInput = it },
                    label = "Official Administrative Response *",
                    singleLine = false,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
