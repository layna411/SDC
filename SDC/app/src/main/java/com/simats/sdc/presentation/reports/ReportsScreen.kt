package com.simats.sdc.presentation.reports

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.BreakdownItemDto
import com.simats.sdc.data.dto.ReportAnalyticsDto
import com.simats.sdc.domain.repository.OralCareRepository
import com.simats.sdc.domain.repository.RepoResult
import com.simats.sdc.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReportsUiState(
    val isLoading: Boolean = false,
    val exportingFormat: String? = null,
    val error: String? = null,
    val exportError: String? = null,
    val exportMessage: String? = null,
    val exportedFile: File? = null,
    val exportedMimeType: String = "application/pdf",
    val outcomeFilter: String? = null,
    val report: ReportAnalyticsDto? = null,
) {
    val isExporting: Boolean get() = exportingFormat != null
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: OralCareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = ReportsUiState()
    }

    fun loadReport(outcome: String? = _uiState.value.outcomeFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, outcomeFilter = outcome) }
            when (val res = repository.getReportAnalytics(null, null, outcome)) {
                is RepoResult.Success -> _uiState.update { it.copy(isLoading = false, report = res.data) }
                is RepoResult.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun export(format: String) {
        viewModelScope.launch {
            val mimeType = when (format.lowercase()) {
                "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "csv" -> "text/csv"
                else -> "application/pdf"
            }
            _uiState.update {
                it.copy(
                    exportingFormat = format.lowercase(),
                    exportError = null,
                    exportMessage = null,
                )
            }
            when (val res = repository.downloadReport(format, null, null, _uiState.value.outcomeFilter)) {
                is RepoResult.Success -> _uiState.update {
                    it.copy(
                        exportingFormat = null,
                        exportedFile = res.data,
                        exportedMimeType = mimeType,
                        exportMessage = "Saved to Downloads/OralCare/${res.data.name}",
                    )
                }
                is RepoResult.Error -> _uiState.update {
                    it.copy(
                        exportingFormat = null,
                        exportError = res.message,
                    )
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadReport() }
    LaunchedEffect(state.exportedFile) {
        state.exportedFile?.let { file ->
            Toast.makeText(context, "Saved to Downloads/OralCare/${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Reports & Analytics",
            subtitle = "Clinical statistics & downloadable reports",
            actions = {
                IconButton(onClick = { viewModel.loadReport() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = state.outcomeFilter == null, onClick = { viewModel.loadReport(null) }, label = { Text("All") })
                    FilterChip(selected = state.outcomeFilter == "NORMAL", onClick = { viewModel.loadReport("NORMAL") }, label = { Text("Normal") })
                    FilterChip(selected = state.outcomeFilter == "ABNORMAL", onClick = { viewModel.loadReport("ABNORMAL") }, label = { Text("Abnormal") })
                    FilterChip(selected = state.outcomeFilter == "PENDING_CLINICAL_REVIEW", onClick = { viewModel.loadReport("PENDING_CLINICAL_REVIEW") }, label = { Text("Pending") })
                }
            }

            item {
                GlassCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Download Authorized Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            GlassButton(
                                text = "PDF",
                                onClick = { viewModel.export("pdf") },
                                enabled = !state.isExporting,
                                loading = state.exportingFormat == "pdf",
                                icon = Icons.Default.PictureAsPdf,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "Excel",
                                onClick = { viewModel.export("excel") },
                                isPrimary = false,
                                enabled = !state.isExporting,
                                loading = state.exportingFormat == "excel",
                                icon = Icons.Default.TableChart,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "CSV",
                                onClick = { viewModel.export("csv") },
                                isPrimary = false,
                                enabled = !state.isExporting,
                                loading = state.exportingFormat == "csv",
                                icon = Icons.Default.Download,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!state.exportError.isNullOrBlank()) {
                            Text(state.exportError!!, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!state.exportMessage.isNullOrBlank()) {
                            Text(state.exportMessage!!, color = OralCareColors.NormalGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            state.exportedFile?.let { file ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        runCatching {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, state.exportedMimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            val shareFallback = Intent(Intent.ACTION_SEND).apply {
                                                type = state.exportedMimeType
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching {
                                                context.startActivity(viewIntent)
                                            }.onFailure {
                                                context.startActivity(Intent.createChooser(shareFallback, "Open OralCare Report"))
                                            }
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Cannot open file: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Open File", color = OralCareColors.AccentBlueStrong, fontWeight = FontWeight.SemiBold)
                                    }
                                    TextButton(onClick = {
                                        runCatching {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = state.exportedMimeType
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share OralCare Report"))
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Cannot share file: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Share", color = OralCareColors.AccentBlueStrong, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading && state.report == null) {
                item { LoadingView("Generating clinical analytics...") }
            } else if (state.error != null) {
                item { ErrorStateView(state.error!!, onRetry = { viewModel.loadReport() }) }
            } else {
                val rep = state.report
                if (rep != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GlassStatCard("Total Patients", rep.totalPatients.toString(), Icons.Default.People, Modifier.weight(1f).fillMaxHeight(), OralCareColors.AccentBlueStrong)
                                GlassStatCard("Male / Female", "${rep.malePatients} / ${rep.femalePatients}", Icons.Default.Wc, Modifier.weight(1f).fillMaxHeight(), OralCareColors.AccentBlue)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GlassStatCard("Normal", rep.normalScreenings.toString(), Icons.Default.CheckCircle, Modifier.weight(1f).fillMaxHeight(), OralCareColors.NormalGreen)
                                GlassStatCard("Abnormal", rep.abnormalScreenings.toString(), Icons.Default.Warning, Modifier.weight(1f).fillMaxHeight(), OralCareColors.AbnormalRed)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GlassStatCard("Lesions", rep.lesionsDetected.toString(), Icons.Default.LocalHospital, Modifier.weight(1f).fillMaxHeight(), OralCareColors.AbnormalRed)
                                GlassStatCard("Pending Reviews", rep.pendingReviews.toString(), Icons.Default.PendingActions, Modifier.weight(1f).fillMaxHeight(), OralCareColors.WarningAmber)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GlassStatCard("Lab Reports", rep.labInvestigationsCount.toString(), Icons.Default.Biotech, Modifier.weight(1f).fillMaxHeight(), OralCareColors.TextPrimary, subtitle = "Investigations")
                                GlassStatCard("Follow-Ups", "${rep.followUpsScheduled} / ${rep.followUpsCompleted}", Icons.Default.EventAvailable, Modifier.weight(1f).fillMaxHeight(), OralCareColors.AccentBlueStrong, subtitle = "Scheduled / Done")
                            }
                        }
                    }

                    item { WeeklyScreeningChart(points = rep.weeklySeries, title = "Weekly Screening Report") }
                    item { WeeklyScreeningChart(points = rep.monthlySeries, title = "Monthly Screening Trend") }

                    if (rep.collegeBreakdown.isNotEmpty()) {
                        item { BreakdownSection("College-wise Report", rep.collegeBreakdown) }
                    }
                    if (rep.centerBreakdown.isNotEmpty()) {
                        item { BreakdownSection("Center-wise Report", rep.centerBreakdown) }
                    }
                    if (rep.organizerBreakdown.isNotEmpty()) {
                        item { BreakdownSection("Organizer-wise Report", rep.organizerBreakdown) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownSection(title: String, items: List<BreakdownItemDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        items.forEach { row ->
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Patients: ${row.totalPatients} • Screenings: ${row.totalScreenings} • Normal: ${row.normalScreenings} • Abnormal: ${row.abnormalScreenings} • Lesions: ${row.lesionsDetected}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
