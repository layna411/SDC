package com.simats.sdc.presentation.screening

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.database.ScreeningDraftEntity
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.data.dto.AIAnalysisDto
import com.simats.sdc.data.dto.ScreeningDetailDto
import com.simats.sdc.presentation.components.*

private val STEP_TITLES = listOf(
    "Consent & Location",
    "Personal Details",
    "Personal History",
    "Habit History",
    "Medical & Dental History",
    "Clinical Findings",
    "Lesion Details",
    "Teeth Examination",
    "Lab Investigation",
    "Image Upload & AI",
    "Consultation Summary",
)

@Composable
fun ScreeningWizardScreen(
    viewModel: ScreeningWizardViewModel,
    onBack: () -> Unit,
    onCompletedNavigatePatients: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val drafts by viewModel.savedDrafts.collectAsState()
    var showConfirmSubmit by remember { mutableStateOf(false) }
    var showDraftsDialog by remember { mutableStateOf(false) }

    val result = state.submittedResult
    if (result != null) {
        ScreeningOutcomeResultScreen(
            result = result,
            aiAnalyses = state.aiAnalysisByImageId,
            onBack = {
                viewModel.startFreshScreening()
                onBack()
            },
            onRequestAi = { viewModel.requestAiAnalysisForUploadedImage(it) },
            onReviewAi = { analysisId, imageId, reviewStatus, notes ->
                viewModel.submitAiClinicianReview(analysisId, imageId, reviewStatus, notes)
            },
            onStartAnother = { viewModel.startFreshScreening() },
            onViewPatients = {
                viewModel.startFreshScreening()
                onCompletedNavigatePatients()
            },
        )
        return
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(state.currentStep) {
        scrollState.animateScrollTo(0)
    }
    LaunchedEffect(state.validationError) {
        if (!state.validationError.isNullOrBlank()) {
            scrollState.animateScrollTo(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        GlassHeader(
            title = "New Screening",
            subtitle = "Step ${state.currentStep} of 11 • ${STEP_TITLES[state.currentStep - 1]}",
            showBackButton = true,
            onBack = onBack,
            actions = {
                if (drafts.isNotEmpty()) {
                    TextButton(
                        onClick = { showDraftsDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Drafts (${drafts.size})", color = OralCareColors.AccentBlueStrong, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = { viewModel.saveDraftManually() }) {
                    Icon(Icons.Default.Save, contentDescription = "Save Draft", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassProgressIndicator(
                currentStep = state.currentStep,
                totalSteps = 11,
                stepTitle = STEP_TITLES[state.currentStep - 1],
            )

            if (!state.validationError.isNullOrBlank()) {
                GlassCard(
                    containerColor = OralCareColors.AbnormalRedBg,
                    borderColor = OralCareColors.AbnormalRed.copy(alpha = 0.25f),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = OralCareColors.AbnormalRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.validationError!!, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (!state.draftSavedNotice.isNullOrBlank()) {
                GlassCard(
                    containerColor = OralCareColors.NormalGreenBg,
                    borderColor = OralCareColors.NormalGreen.copy(alpha = 0.25f),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Text(state.draftSavedNotice!!, color = OralCareColors.NormalGreen, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!state.offlineQueuedMessage.isNullOrBlank()) {
                GlassCard(
                    containerColor = OralCareColors.WarningAmberBg,
                    borderColor = OralCareColors.WarningAmber.copy(alpha = 0.28f),
                ) {
                    Text("Queued for Offline Sync", fontWeight = FontWeight.Bold, color = OralCareColors.WarningAmber)
                    Spacer(Modifier.height(4.dp))
                    Text(state.offlineQueuedMessage!!, style = MaterialTheme.typography.bodySmall, color = OralCareColors.WarningAmber)
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Start Next Patient Screening", onClick = { viewModel.startFreshScreening() })
                }
            }

            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "wizardStep",
            ) { step ->
                when (step) {
                    1 -> Step1ConsentLocation(state, viewModel::updateState)
                    2 -> Step2PersonalDetails(state, viewModel::updateState)
                    3 -> Step3PersonalHistory(state, viewModel::updateState)
                    4 -> Step4HabitHistory(state, viewModel::addHabit, viewModel::removeHabit)
                    5 -> Step5MedicalDentalHistory(state, viewModel::updateState, viewModel::toggleFamilyCondition, viewModel::toggleMedicalCondition)
                    6 -> Step6ClinicalFindings(state, viewModel::updateClinicalFinding)
                    7 -> Step7LesionDetails(state, viewModel::updateState, viewModel::addLesion, viewModel::removeLesion)
                    8 -> Step8TeethExamination(state, viewModel::updateState, viewModel::updateToothExam)
                    9 -> Step9LabInvestigation(state, viewModel::updateState)
                    10 -> Step10ImageUploadAndAi(state, viewModel::addCapturedImage, viewModel::removeCapturedImage)
                    11 -> Step11ConsultationSummary(
                        state = state,
                        onJumpToStep = viewModel::goToStep,
                        onUpdateRecommendations = { v -> viewModel.updateState { it.copy(clinicalRecommendations = v) } },
                        onRequestSubmit = { showConfirmSubmit = true },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Clean Glass Step Action Bar (respects system nav bar insets when bottom nav is hidden)
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = Color(0xFFF8FCFF),
            border = BorderStroke(1.dp, Color(0xFFD5E9FA)),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.currentStep > 1) {
                    GlassButton(
                        text = "Previous",
                        isPrimary = false,
                        onClick = { viewModel.previousStep() },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GlassButton(
                        text = "Save Draft",
                        isPrimary = false,
                        onClick = { viewModel.saveDraftManually() },
                        icon = Icons.Default.Save,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.currentStep < 11) {
                    GlassButton(
                        text = "Next",
                        onClick = { viewModel.nextStep() },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GlassButton(
                        text = "Submit",
                        onClick = { showConfirmSubmit = true },
                        loading = state.isSubmitting,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (state.isSubmitting) {
        GlassDialog(
            title = "AI Screening Analysis",
            onDismiss = {},
            confirmButton = {},
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = OralCareColors.AccentBlueStrong,
                    trackColor = OralCareColors.LightBlue,
                    strokeWidth = 3.dp,
                )
                Text(
                    "Uploading clinical images & running AI oral health analysis...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Evaluating clinical findings, lesions, habits & mucosal images for NORMAL / ABNORMAL result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OralCareColors.TextSecondary,
                )
            }
        }
    }

    if (showConfirmSubmit) {
        ConfirmationDialog(
            title = "Confirm Clinical Submission",
            message = "Are you sure you want to submit this 11-step oral health screening? A unique Screening ID and clinical summary will be generated.",
            confirmText = "Submit Screening",
            onConfirm = {
                showConfirmSubmit = false
                viewModel.submitFinalScreening()
            },
            onDismiss = { showConfirmSubmit = false },
        )
    }

    if (showDraftsDialog) {
        DraftsPickerDialog(
            drafts = drafts,
            onResume = {
                viewModel.resumeDraft(it)
                showDraftsDialog = false
            },
            onDelete = { viewModel.deleteDraft(it.draftId) },
            onDismiss = { showDraftsDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreeningOutcomeResultScreen(
    result: ScreeningDetailDto,
    aiAnalyses: Map<Int, AIAnalysisDto>,
    onBack: () -> Unit,
    onRequestAi: (Int) -> Unit,
    onReviewAi: (Int, Int, String, String) -> Unit,
    onStartAnother: () -> Unit,
    onViewPatients: () -> Unit,
) {
    val isNormal = result.outcome.equals("NORMAL", ignoreCase = true)
    val isAbnormal = result.outcome.equals("ABNORMAL", ignoreCase = true)

    val outcomeAccent = when {
        isNormal -> Color(0xFF16A34A)   // Vivid Green for Normal
        isAbnormal -> Color(0xFFDC2626) // Vivid Red for Abnormal
        else -> OralCareColors.WarningAmber
    }
    val outcomeTextColor = when {
        isNormal -> OralCareColors.NormalGreen
        isAbnormal -> OralCareColors.AbnormalRed
        else -> OralCareColors.WarningAmber
    }
    val outcomeBg = when {
        isNormal -> OralCareColors.NormalGreenBg
        isAbnormal -> OralCareColors.AbnormalRedBg
        else -> OralCareColors.WarningAmberBg
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        GlassHeader(
            title = "AI Screening Result",
            subtitle = result.screeningUid,
            showBackButton = true,
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Vivid Green (NORMAL) or Vivid Red (ABNORMAL) Primary Outcome Banner
            GlassCard(
                containerColor = outcomeBg,
                borderColor = outcomeAccent,
                contentPadding = PaddingValues(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = outcomeAccent,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isNormal -> Icons.Default.CheckCircle
                                    isAbnormal -> Icons.Default.Warning
                                    else -> Icons.Default.PendingActions
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = OralCareTokens.BadgeShape,
                        color = outcomeAccent,
                    ) {
                        Text(
                            text = when {
                                isNormal -> "NORMAL CASE"
                                isAbnormal -> "ABNORMAL CASE"
                                else -> result.outcome.replace("_", " ")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isNormal) "Screening Result: NORMAL" else if (isAbnormal) "Screening Result: ABNORMAL" else result.outcome.replace("_", " "),
                        style = MaterialTheme.typography.headlineMedium,
                        color = outcomeTextColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Screening ID: ${result.screeningUid}", style = MaterialTheme.typography.titleSmall, color = OralCareColors.TextPrimary)
                    result.patient?.let {
                        Text("Patient: ${it.displayName} (${it.patientUid})", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextPrimary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = result.consultationSummary?.outcomeReason ?: "AI & clinical evaluation completed and persisted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = outcomeTextColor,
                    )
                }
            }

            if (result.patientImages.isNotEmpty()) {
                SectionHeader("AI Image Analysis Results (${result.patientImages.size} image(s))")
                result.patientImages.forEach { img ->
                    val analysis = aiAnalyses[img.id] ?: img.aiAnalyses.firstOrNull()
                    val imgClass = analysis?.modelOutput?.get("classification")?.toString()?.uppercase()
                    val imgIsNormal = imgClass == "NORMAL"
                    val imgIsAbnormal = imgClass == "ABNORMAL"
                    val confPct = analysis?.confidenceScore?.let { "${(it * 100).toInt()}%" }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = when {
                            imgIsAbnormal -> Color(0xFFDC2626).copy(alpha = 0.6f)
                            imgIsNormal -> Color(0xFF16A34A).copy(alpha = 0.6f)
                            else -> null
                        },
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${img.clinicalRegion} (${img.subRegion ?: "General"})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (imgClass != null) {
                                    StatusPill(
                                        text = if (confPct != null) "$imgClass ($confPct)" else imgClass,
                                        bg = if (imgIsAbnormal) OralCareColors.AbnormalRedBg else OralCareColors.NormalGreenBg,
                                        fg = if (imgIsAbnormal) OralCareColors.AbnormalRed else OralCareColors.NormalGreen,
                                    )
                                }
                            }
                            if (analysis == null) {
                                GlassButton(
                                    text = "Run AI Image Analysis",
                                    isPrimary = false,
                                    icon = Icons.Default.AutoAwesome,
                                    onClick = { onRequestAi(img.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    StatusPill(
                                        text = "Quality: ${analysis.imageQualityStatus}",
                                        bg = OralCareColors.LightBlue,
                                        fg = OralCareColors.AccentBlueStrong,
                                    )
                                    StatusPill(
                                        text = "Review: ${analysis.reviewStatus.replace("_", " ")}",
                                        bg = OralCareColors.WarningAmberBg,
                                        fg = OralCareColors.WarningAmber,
                                    )
                                }
                                analysis.modelOutput?.get("summary")?.toString()?.let { summary ->
                                    Text(summary, style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextPrimary)
                                }
                                var selectedReview by remember { mutableStateOf(analysis.reviewStatus) }
                                var notes by remember { mutableStateOf("") }
                                GlassDropdown("Clinician Review Status", selectedReview, ClinicalConstants.AI_REVIEW_STATUSES, { selectedReview = it })
                                GlassTextField(notes, { notes = it }, "Clinician Review Notes")
                                GlassButton(
                                    text = "Save Clinician Review",
                                    onClick = { onReviewAi(analysis.id, img.id, selectedReview, notes) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            GlassButton("View Patients List", onClick = onViewPatients, icon = Icons.Default.People, modifier = Modifier.fillMaxWidth())
            GlassButton("Start New Patient Screening", isPrimary = false, onClick = onStartAnother, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DraftsPickerDialog(
    drafts: List<ScreeningDraftEntity>,
    onResume: (ScreeningDraftEntity) -> Unit,
    onDelete: (ScreeningDraftEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        title = "Saved Offline Drafts",
        onDismiss = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            drafts.forEach { d ->
                GlassCard(contentPadding = PaddingValues(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.patientNamePreview, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Step ${d.currentStep} • ${d.syncStatus}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                        }
                        TextButton(onClick = { onResume(d) }) { Text("Resume") }
                        IconButton(onClick = { onDelete(d) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete draft", tint = OralCareColors.AbnormalRed)
                        }
                    }
                }
            }
        }
    }
}
