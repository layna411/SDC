package com.simats.sdc.presentation.education

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.*
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import com.simats.sdc.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EducationUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val search: String = "",
    val languageFilter: String? = null,
    val resources: List<EducationalResourceDto> = emptyList(),
    val awareness: List<AwarenessContentDto> = emptyList(),
)

@HiltViewModel
class EducationViewModel @Inject constructor(
    private val repository: OralCareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EducationUiState())
    val uiState: StateFlow<EducationUiState> = _uiState.asStateFlow()

    fun load(search: String = _uiState.value.search, lang: String? = _uiState.value.languageFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, search = search, languageFilter = lang) }
            val res = repository.listEducationalResources(search, lang)
            val aw = repository.listAwarenessContent(search, lang)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    resources = (res as? RepoResult.Success)?.data ?: emptyList(),
                    awareness = (aw as? RepoResult.Success)?.data ?: emptyList(),
                    error = (res as? RepoResult.Error)?.message,
                )
            }
        }
    }

    fun addResource(req: EducationalResourceCreateDto) {
        viewModelScope.launch {
            repository.createEducationalResource(req)
            load()
        }
    }

    fun deleteResource(id: Int) {
        viewModelScope.launch {
            repository.deleteEducationalResource(id)
            load()
        }
    }
}

@Composable
fun EducationScreen(
    viewModel: EducationViewModel,
    canManage: Boolean,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            GlassHeader(
                title = "Education & Oral Health Aids",
                subtitle = "English & Tamil manuals, videos & awareness",
                showBackButton = true,
                onBack = onBack,
                actions = {
                    if (canManage) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Content", tint = OralCareColors.AccentBlueStrong)
                        }
                    }
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GlassSearchBar(state.search, { viewModel.load(search = it) }, "Search manuals, videos, articles...")
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.languageFilter == null, onClick = { viewModel.load(lang = null) }, label = { Text("All Languages") })
                        FilterChip(selected = state.languageFilter == "English", onClick = { viewModel.load(lang = "English") }, label = { Text("English") })
                        FilterChip(selected = state.languageFilter == "Tamil", onClick = { viewModel.load(lang = "Tamil") }, label = { Text("தமிழ் (Tamil)") })
                    }
                }

                item { SectionHeader("Manuals & YouTube Oral Health Aids (${state.resources.size})") }
                items(state.resources, key = { "res_${it.id}" }) { r ->
                    GlassCard(
                        contentPadding = PaddingValues(14.dp),
                        onClick = {
                            r.externalUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(OralCareColors.LightBlue),
                            ) {
                                Icon(
                                    imageVector = if (r.resourceType == "YOUTUBE_VIDEO") Icons.Default.PlayCircleFilled else Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = if (r.resourceType == "YOUTUBE_VIDEO") OralCareColors.AbnormalRed else OralCareColors.AccentBlueStrong,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("${r.language} • ${r.resourceType}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.AccentBlueStrong)
                                r.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (canManage) {
                                IconButton(onClick = { viewModel.deleteResource(r.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = OralCareColors.AbnormalRed)
                                }
                            }
                        }
                    }
                }

                item { SectionHeader("Oral Health Awareness Articles (${state.awareness.size})") }
                items(state.awareness, key = { "aw_${it.id}" }) { a ->
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(a.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                StatusPill(a.language, OralCareColors.LightBlue, OralCareColors.AccentBlueStrong)
                            }
                            Text("${a.category} • ${a.contentType}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.AccentBlueStrong)
                            Text(a.body, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var lang by remember { mutableStateOf("English") }
        var type by remember { mutableStateOf("MANUAL_PDF") }
        var url by remember { mutableStateOf("") }

        GlassDialog(
            title = "Publish Educational Resource",
            onDismiss = { showAddDialog = false },
            confirmButton = {
                GlassButton("Publish", enabled = title.isNotBlank(), onClick = {
                    viewModel.addResource(
                        EducationalResourceCreateDto(
                            title = title,
                            description = desc.ifBlank { null },
                            category = if (type == "YOUTUBE_VIDEO") "ORAL_HEALTH_AIDS" else "USER_MANUAL",
                            language = lang,
                            resourceType = type,
                            externalUrl = url.ifBlank { null },
                        )
                    )
                    showAddDialog = false
                })
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(title, { title = it }, "Title")
                GlassTextField(desc, { desc = it }, "Description", singleLine = false)
                GlassDropdown("Language", lang, listOf("English", "Tamil"), { lang = it })
                GlassDropdown("Resource Type", type, listOf("MANUAL_PDF", "YOUTUBE_VIDEO", "DOCUMENT", "IMAGE"), { type = it })
                GlassTextField(url, { url = it }, "PDF / YouTube URL")
            }
        }
    }
}
