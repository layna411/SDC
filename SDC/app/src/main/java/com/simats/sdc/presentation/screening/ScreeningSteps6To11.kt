package com.simats.sdc.presentation.screening

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.common.ValidationUtils
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.*
import com.simats.sdc.presentation.components.*
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step6ClinicalFindings(
    state: ScreeningWizardState,
    onSaveFinding: (String, String, String, List<ClinicalObservationDto>, String) -> Unit,
) {
    var selectedRegionIdx by remember { mutableStateOf(0) }
    val region = ClinicalConstants.ANATOMICAL_REGIONS[selectedRegionIdx]

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(
            title = "Step 6: Clinical Findings",
            subtitle = "Image-guided examination across all 11 anatomical oral regions",
        )

        GlassCard(contentPadding = PaddingValues(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClinicalConstants.ANATOMICAL_REGIONS.forEachIndexed { idx, spec ->
                    val hasAbnormal = spec.subRegions.any { sub ->
                        state.clinicalFindings["${spec.name}|$sub"]?.status == "ABNORMAL"
                    }
                    val hasNormal = spec.subRegions.any { sub ->
                        state.clinicalFindings["${spec.name}|$sub"]?.status == "NORMAL"
                    }
                    FilterChip(
                        selected = selectedRegionIdx == idx,
                        onClick = { selectedRegionIdx = idx },
                        label = {
                            Text(
                                text = "${idx + 1}. ${spec.name}" + when {
                                    hasAbnormal -> " ⚠"
                                    hasNormal -> " ✓"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        AnatomicalReferenceDiagram(
            regionCode = region.code,
            regionName = region.name,
            guideText = region.referenceGuide,
        )

        region.subRegions.forEach { sub ->
            val key = "${region.name}|$sub"
            val existing = state.clinicalFindings[key]
            var status by remember(key, existing) { mutableStateOf(existing?.status ?: "NOT_EXAMINED") }
            var remarks by remember(key, existing) { mutableStateOf(existing?.remarks ?: "") }
            var selectedObs by remember(key, existing) {
                mutableStateOf(existing?.observations?.map { it.observationCode }?.toSet() ?: emptySet())
            }

            fun persist(newStatus: String, newObs: Set<String>, newRemarks: String) {
                val obsList = ClinicalConstants.CLINICAL_OBSERVATION_OPTIONS
                    .filter { newObs.contains(it.first) }
                    .map { ClinicalObservationDto(it.first, it.second) }
                onSaveFinding(region.name, sub, newStatus, obsList, newRemarks)
            }

            GlassCard(
                borderColor = when (status) {
                    "ABNORMAL" -> OralCareColors.AbnormalRed.copy(alpha = 0.45f)
                    "NORMAL" -> OralCareColors.NormalGreen.copy(alpha = 0.45f)
                    else -> null
                },
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${region.name} – $sub", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ScreeningStatusBadge(status)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = status == "NORMAL",
                            onClick = { status = "NORMAL"; persist(status, selectedObs, remarks) },
                            label = { Text("Normal") },
                        )
                        FilterChip(
                            selected = status == "ABNORMAL",
                            onClick = { status = "ABNORMAL"; persist(status, selectedObs, remarks) },
                            label = { Text("Abnormal") },
                        )
                        FilterChip(
                            selected = status == "NOT_EXAMINED",
                            onClick = { status = "NOT_EXAMINED"; persist(status, selectedObs, remarks) },
                            label = { Text("Not Examined") },
                        )
                    }
                    if (status == "ABNORMAL") {
                        Text("Clinical Observations", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ClinicalConstants.CLINICAL_OBSERVATION_OPTIONS.forEach { (code, label) ->
                                FilterChip(
                                    selected = selectedObs.contains(code),
                                    onClick = {
                                        val next = selectedObs.toMutableSet()
                                        if (!next.add(code)) next.remove(code)
                                        selectedObs = next
                                        persist(status, selectedObs, remarks)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                    GlassTextField(
                        value = remarks,
                        onValueChange = {
                            remarks = it
                            persist(status, selectedObs, remarks)
                        },
                        label = "Clinical Remarks ($sub)",
                    )
                }
            }
        }
    }
}

@Composable
fun Step7LesionDetails(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
    onAddLesion: (LesionDetailDto) -> Unit,
    onRemoveLesion: (Int) -> Unit,
) {
    var location by remember { mutableStateOf("Buccal Mucosa - Right") }
    var lesionType by remember { mutableStateOf(ClinicalConstants.LESION_TYPES.first()) }
    var size by remember { mutableStateOf("") }
    var appearance by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var referral by remember { mutableStateOf("") }

    val allLocations = remember {
        ClinicalConstants.ANATOMICAL_REGIONS.flatMap { r -> r.subRegions.map { "${r.name} - $it" } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 7: Lesion Details", subtitle = "Document clinical presence or absence of oral lesions")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = !state.lesionPresent,
                onClick = { onUpdate { it.copy(lesionPresent = false) } },
                label = { Text("Lesion Absent") },
            )
            FilterChip(
                selected = state.lesionPresent,
                onClick = { onUpdate { it.copy(lesionPresent = true) } },
                label = { Text("Lesion Present") },
            )
        }

        if (!state.lesionPresent) {
            EmptyStateView(
                title = "No Oral Lesion Recorded",
                subtitle = "Select 'Lesion Present' above if any mucosal lesion was detected during clinical inspection.",
                icon = Icons.Default.VerifiedUser,
            )
            return
        }

        GlassCard(borderColor = OralCareColors.AbnormalRed.copy(alpha = 0.35f), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Lesion Entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AbnormalRed)
                GlassDropdown("Anatomical Location", location, allLocations, { location = it })
                GlassDropdown("Lesion Type", lesionType, ClinicalConstants.LESION_TYPES, { lesionType = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(size, { size = it }, "Size", placeholder = "e.g., 1.5 x 1 cm", modifier = Modifier.weight(1f))
                    GlassTextField(duration, { duration = it }, "Duration", placeholder = "e.g., 3 months", modifier = Modifier.weight(1f))
                }
                GlassTextField(appearance, { appearance = it }, "Appearance (border, color, texture)")
                GlassTextField(symptoms, { symptoms = it }, "Symptoms (pain, burning, bleeding)")
                GlassTextField(observations, { observations = it }, "Clinical Observations", singleLine = false)
                GlassTextField(remarks, { remarks = it }, "Remarks")
                GlassTextField(referral, { referral = it }, "Referral Recommendation")

                GlassButton(
                    text = "Add Lesion Entry",
                    icon = Icons.Default.AddCircleOutline,
                    onClick = {
                        onAddLesion(
                            LesionDetailDto(
                                anatomicalLocation = location,
                                lesionType = lesionType,
                                size = size.ifBlank { null },
                                appearance = appearance.ifBlank { null },
                                duration = duration.ifBlank { null },
                                symptoms = symptoms.ifBlank { null },
                                clinicalObservations = observations.ifBlank { null },
                                remarks = remarks.ifBlank { null },
                                referralRecommendation = referral.ifBlank { null },
                            )
                        )
                        size = ""; appearance = ""; duration = ""; symptoms = ""; observations = ""; remarks = ""; referral = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.lesions.forEachIndexed { idx, l ->
            GlassCard(
                containerColor = OralCareColors.AbnormalRedBg.copy(alpha = 0.85f),
                borderColor = OralCareColors.AbnormalRed.copy(alpha = 0.28f),
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${l.anatomicalLocation} • ${l.lesionType}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = OralCareColors.AbnormalRed)
                        Text(listOfNotNull(l.size, l.appearance, l.duration, l.referralRecommendation).joinToString(" | "), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onRemoveLesion(idx) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove lesion", tint = OralCareColors.AbnormalRed)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step8TeethExamination(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
    onSaveTooth: (ToothExaminationDto) -> Unit,
) {
    var selectedTooth by remember { mutableStateOf<String?>("11") }
    val activeRecord = selectedTooth?.let { state.teethRecords[it] }
    var toothStatus by remember(selectedTooth, activeRecord) { mutableStateOf(activeRecord?.status ?: "HEALTHY") }
    var selectedSurfaces by remember(selectedTooth, activeRecord) {
        mutableStateOf(activeRecord?.surfaces?.map { it.surfaceCode }?.toSet() ?: emptySet())
    }
    var finding by remember(selectedTooth, activeRecord) { mutableStateOf(activeRecord?.clinicalFinding ?: "") }
    var remarks by remember(selectedTooth, activeRecord) { mutableStateOf(activeRecord?.remarks ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 8: Teeth Examination", subtitle = "Interactive FDI permanent & primary dental chart")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.dentitionType == "PERMANENT",
                onClick = { onUpdate { it.copy(dentitionType = "PERMANENT") }; selectedTooth = "11" },
                label = { Text("Permanent (32)") },
            )
            FilterChip(
                selected = state.dentitionType == "PRIMARY",
                onClick = { onUpdate { it.copy(dentitionType = "PRIMARY") }; selectedTooth = "51" },
                label = { Text("Primary (20)") },
            )
        }

        GlassCard(contentPadding = PaddingValues(14.dp)) {
            InteractiveDentalChart(
                dentitionType = state.dentitionType,
                teethRecords = state.teethRecords,
                selectedTooth = selectedTooth,
                onSelectTooth = { selectedTooth = it },
            )
        }

        if (selectedTooth != null) {
            GlassCard(highlighted = true, contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tooth #$selectedTooth Examination (${state.dentitionType})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    GlassDropdown("Tooth Status", toothStatus, ClinicalConstants.TOOTH_STATUSES, { toothStatus = it })
                    Text("Affected Tooth Surfaces", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ClinicalConstants.TOOTH_SURFACES.forEach { (code, name) ->
                            FilterChip(
                                selected = selectedSurfaces.contains(code),
                                onClick = {
                                    val next = selectedSurfaces.toMutableSet()
                                    if (!next.add(code)) next.remove(code)
                                    selectedSurfaces = next
                                },
                                label = { Text("$code – $name") },
                            )
                        }
                    }
                    GlassTextField(finding, { finding = it }, "Clinical Finding")
                    GlassTextField(remarks, { remarks = it }, "Remarks")
                    GlassButton(
                        text = "Save Tooth #$selectedTooth Record",
                        icon = Icons.Default.Save,
                        onClick = {
                            onSaveTooth(
                                ToothExaminationDto(
                                    dentitionType = state.dentitionType,
                                    toothNumber = selectedTooth!!,
                                    status = toothStatus,
                                    clinicalFinding = finding.ifBlank { null },
                                    remarks = remarks.ifBlank { null },
                                    surfaces = selectedSurfaces.map { ToothSurfaceDto(it, toothStatus) },
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Text("Recorded Teeth: ${state.teethRecords.size}", style = MaterialTheme.typography.labelMedium, color = OralCareColors.AccentBlueStrong)
    }
}

@Composable
fun Step9LabInvestigation(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 9: Lab Investigation", subtitle = "Haematology parameters and biopsy findings (reference ranges configured per lab standard)")

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(state.labTestDate, { v -> onUpdate { it.copy(labTestDate = v) } }, "Test Date", placeholder = "YYYY-MM-DD")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(state.haemoglobin, { v -> onUpdate { it.copy(haemoglobin = v) } }, "Haemoglobin", placeholder = "g/dL", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    GlassTextField(state.rbc, { v -> onUpdate { it.copy(rbc = v) } }, "RBC", placeholder = "million/uL", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(state.totalCount, { v -> onUpdate { it.copy(totalCount = v) } }, "Total Count", placeholder = "cells/uL", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    GlassTextField(state.plateletCount, { v -> onUpdate { it.copy(plateletCount = v) } }, "Platelet Count", placeholder = "lakh/uL", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                }
                GlassTextField(state.percentage, { v -> onUpdate { it.copy(percentage = v) } }, "Percentage (%)", keyboardType = KeyboardType.Decimal)
                GlassTextField(state.biopsyReportSummary, { v -> onUpdate { it.copy(biopsyReportSummary = v) } }, "Biopsy Report Summary", singleLine = false, maxLines = 4)
                GlassTextField(state.labRemarks, { v -> onUpdate { it.copy(labRemarks = v) } }, "Laboratory Remarks", singleLine = false)
            }
        }
    }
}

@Composable
fun Step10ImageUploadAndAi(
    state: ScreeningWizardState,
    onAddImage: (String, String?, String) -> Unit,
    onRemoveImage: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedRegion by remember { mutableStateOf(ClinicalConstants.ANATOMICAL_REGIONS.first().name) }
    var selectedSubRegion by remember { mutableStateOf(ClinicalConstants.ANATOMICAL_REGIONS.first().subRegions.first()) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val activeSpec = ClinicalConstants.ANATOMICAL_REGIONS.firstOrNull { it.name == selectedRegion } ?: ClinicalConstants.ANATOMICAL_REGIONS.first()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCameraDialog = true else cameraError = "Camera permission is required to capture clinical photos."
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val dir = File(context.filesDir, "clinical_images").apply { mkdirs() }
                val dest = File(dir, "gallery_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                onAddImage(selectedRegion, selectedSubRegion, dest.absolutePath)
            } catch (e: Exception) {
                cameraError = "Unable to copy selected image: ${e.localizedMessage}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 10: Image Upload & AI Analysis", subtitle = "Capture clinical oral images mapped to Step 6 anatomical regions")

        GlassCard(
            highlighted = true,
            contentPadding = PaddingValues(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = OralCareColors.AccentBlueStrong,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AI-Assisted Oral Screening",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Uploaded clinical images are checked for quality and analyzed automatically upon submission to assist clinical review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                    )
                }
            }
        }

        if (!cameraError.isNullOrBlank()) {
            Text(cameraError!!, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassDropdown(
                    label = "Clinical Region (from Step 6)",
                    selectedValue = selectedRegion,
                    options = ClinicalConstants.ANATOMICAL_REGIONS.map { it.name },
                    onOptionSelected = { picked ->
                        selectedRegion = picked
                        val spec = ClinicalConstants.ANATOMICAL_REGIONS.first { it.name == picked }
                        selectedSubRegion = spec.subRegions.first()
                    },
                )
                GlassDropdown(
                    label = "Sub-Region",
                    selectedValue = selectedSubRegion,
                    options = activeSpec.subRegions,
                    onOptionSelected = { selectedSubRegion = it },
                )
            }
        }

        GlassImageCard(
            regionLabel = "Capture / Select Photo: $selectedRegion",
            subRegionLabel = selectedSubRegion,
            localImagePath = null,
            onCaptureCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            onPickGallery = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )

        if (state.localImages.isEmpty()) {
            EmptyStateView(
                title = "No Clinical Images Attached Yet",
                subtitle = "Use Camera or Gallery above to attach oral region photos. Images are automatically queued if offline.",
                icon = Icons.Default.AddAPhoto,
            )
        } else {
            state.localImages.forEach { img ->
                GlassImageCard(
                    regionLabel = img.clinicalRegion,
                    subRegionLabel = img.subRegion,
                    localImagePath = img.localPath,
                    aiStatusLabel = "Ready for AI analysis upon submission",
                    onCaptureCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickGallery = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemove = { onRemoveImage(img.id) },
                )
            }
        }
    }

    if (showCameraDialog) {
        CameraCaptureDialog(
            regionTitle = "$selectedRegion ($selectedSubRegion)",
            onImageCaptured = { file ->
                showCameraDialog = false
                onAddImage(selectedRegion, selectedSubRegion, file.absolutePath)
            },
            onError = { msg ->
                showCameraDialog = false
                cameraError = msg
            },
            onDismiss = { showCameraDialog = false },
        )
    }
}

@Composable
fun Step11ConsultationSummary(
    state: ScreeningWizardState,
    onJumpToStep: (Int) -> Unit,
    onUpdateRecommendations: (String) -> Unit,
    onRequestSubmit: () -> Unit,
) {
    val abnormalCount = state.clinicalFindings.values.count { it.status == "ABNORMAL" }
    val examinedCount = state.clinicalFindings.values.count { it.status != "NOT_EXAMINED" }
    val previewOutcome = ValidationUtils.evaluateLocalPreviewOutcome(state.lesionPresent, abnormalCount, examinedCount)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Step 11: Consultation Summary", subtitle = "Review all 10 steps before final clinical submission")

        GlassCard(
            containerColor = when (previewOutcome) {
                "NORMAL" -> OralCareColors.NormalGreenBg
                "ABNORMAL" -> OralCareColors.AbnormalRedBg
                else -> OralCareColors.WarningAmberBg
            },
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Projected Clinical Outcome", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                    Text(previewOutcome.replace("_", " "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Based on clinical examination and lesion records.", style = MaterialTheme.typography.bodySmall)
                }
                ScreeningStatusBadge(previewOutcome)
            }
        }

        SummarySectionCard("1. Consent & Location", "Hub: ${state.hub} • Partner: ${state.healthPartner}\nConsent Accepted: ${state.consentAccepted} • Terms Accepted: ${state.termsAccepted}", 1, onJumpToStep)
        SummarySectionCard("2. Personal Details", "${state.prefix} ${state.firstName} ${state.lastName} (${state.age}y, ${state.sex})\nPhone: ${state.phoneNumber} • ${state.address}", 2, onJumpToStep)
        SummarySectionCard("3–4. Personal & Habit History", "Diet: ${state.dietHistory} • Spice: ${state.spiceConsumption}\nHabits Reported: ${if (state.hasHabitHistory) "${state.habits.size} entry(ies)" else "None"}", 3, onJumpToStep)
        SummarySectionCard("5. Medical & Dental History", "Cleaning: ${state.cleaningAid} (${state.materialUsed})\nFamily: ${state.familyHistoryConditions.joinToString(", ")}\nMedical: ${state.pastMedicalConditions.joinToString(", ")}\nDental History: ${if (state.pastDentalHistory) "Yes" else "No"} • Denture: ${if (state.dentureUsage) "Yes" else "No"}", 5, onJumpToStep)
        SummarySectionCard("6. Clinical Findings", "Examined Sites: $examinedCount • Abnormal Sites: $abnormalCount", 6, onJumpToStep)
        SummarySectionCard("7. Lesion Details", if (state.lesionPresent) "Lesion Present (${state.lesions.size} lesion record(s))" else "Lesion Absent", 7, onJumpToStep)
        SummarySectionCard("8. Teeth Examination", "${state.teethRecords.size} tooth record(s) (${state.dentitionType} dentition)", 8, onJumpToStep)
        SummarySectionCard("9. Lab Investigation", "Hb: ${state.haemoglobin.ifBlank { "-" }} • RBC: ${state.rbc.ifBlank { "-" }} • TC: ${state.totalCount.ifBlank { "-" }} • Platelets: ${state.plateletCount.ifBlank { "-" }}\nBiopsy: ${state.biopsyReportSummary.ifBlank { "Not recorded" }}", 9, onJumpToStep)
        SummarySectionCard("10. Clinical Images & AI", "${state.localImages.size} clinical image(s) attached for AI analysis", 10, onJumpToStep)

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            GlassTextField(
                value = state.clinicalRecommendations,
                onValueChange = onUpdateRecommendations,
                label = "Clinician Recommendations & Follow-Up Advice",
                singleLine = false,
                maxLines = 3,
            )
        }

        GlassButton(
            text = "Submit Final Oral Screening",
            onClick = onRequestSubmit,
            loading = state.isSubmitting,
            icon = Icons.Default.CheckCircle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummarySectionCard(title: String, body: String, step: Int, onEdit: (Int) -> Unit) {
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
            TextButton(onClick = { onEdit(step) }) { Text("Edit", color = OralCareColors.AccentBlueStrong) }
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
