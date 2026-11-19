package com.simats.sdc.presentation.screening

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.simats.sdc.core.common.AnatomicalRegionSpec
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
            subtitle = "Examination across all 11 anatomical oral regions guided by clinical reference photographs",
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

        // Clean Region Navigation Toolbar directly below the reference image
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(
                text = "← Previous Site",
                isPrimary = false,
                onClick = { selectedRegionIdx = (selectedRegionIdx - 1).coerceAtLeast(0) },
                enabled = selectedRegionIdx > 0,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OralCareColors.LightBlue,
                border = BorderStroke(1.dp, Color(0xFFC0DEF8)),
            ) {
                Text(
                    text = "${selectedRegionIdx + 1} of ${ClinicalConstants.ANATOMICAL_REGIONS.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OralCareColors.AccentBlueStrong,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            GlassButton(
                text = "Next Site →",
                isPrimary = false,
                onClick = { selectedRegionIdx = (selectedRegionIdx + 1).coerceAtMost(ClinicalConstants.ANATOMICAL_REGIONS.size - 1) },
                enabled = selectedRegionIdx < ClinicalConstants.ANATOMICAL_REGIONS.size - 1,
                modifier = Modifier.weight(1f),
            )
        }

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
            GlassCard(contentPadding = PaddingValues(16.dp)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step10ImageUploadAndAi(
    state: ScreeningWizardState,
    onAddImage: (String, String?, String) -> Unit,
    onRemoveImage: (String) -> Unit,
) {
    val context = LocalContext.current
    var targetSpec by remember { mutableStateOf(ClinicalConstants.ANATOMICAL_REGIONS.first()) }
    var targetSubRegion by remember { mutableStateOf(ClinicalConstants.ANATOMICAL_REGIONS.first().subRegions.first()) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Auto-prompt state: automatically asks for the next uncaptured image when one is uploaded
    var autoPromptNextRegion by remember { mutableStateOf<AnatomicalRegionSpec?>(null) }
    var autoPromptLastSavedRegion by remember { mutableStateOf<String?>(null) }

    // Zoom preview dialog: Pair(Title, Int drawableRes OR String file path)
    var previewImageDetails by remember { mutableStateOf<Pair<String, Any>?>(null) }

    fun handleImageSaved(regionName: String, subRegion: String?, localPath: String) {
        onAddImage(regionName, subRegion, localPath)

        // Calculate next uncaptured region
        val updatedCapturedNames = (state.localImages.map { it.clinicalRegion.trim().lowercase() } + regionName.trim().lowercase()).toSet()
        val nextMissing = ClinicalConstants.ANATOMICAL_REGIONS.firstOrNull {
            it.name.trim().lowercase() !in updatedCapturedNames
        }

        if (nextMissing != null) {
            targetSpec = nextMissing
            targetSubRegion = nextMissing.subRegions.first()
            autoPromptLastSavedRegion = regionName
            autoPromptNextRegion = nextMissing
        } else {
            autoPromptNextRegion = null
            autoPromptLastSavedRegion = null
        }
    }

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
                handleImageSaved(targetSpec.name, targetSubRegion, dest.absolutePath)
            } catch (e: Exception) {
                cameraError = "Unable to copy selected image: ${e.localizedMessage}"
            }
        }
    }

    fun launchCameraFor(spec: AnatomicalRegionSpec, subRegion: String? = null) {
        targetSpec = spec
        targetSubRegion = subRegion ?: spec.subRegions.first()
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun launchGalleryFor(spec: AnatomicalRegionSpec, subRegion: String? = null) {
        targetSpec = spec
        targetSubRegion = subRegion ?: spec.subRegions.first()
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val capturedRegionMap = remember(state.localImages) {
        state.localImages.associateBy { it.clinicalRegion.trim().lowercase() }
    }
    val capturedCount = ClinicalConstants.ANATOMICAL_REGIONS.count {
        it.name.trim().lowercase() in capturedRegionMap
    }
    val allCompleted = capturedCount == ClinicalConstants.ANATOMICAL_REGIONS.size

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(
            title = "Step 10: Clinical Image Upload & AI",
            subtitle = "Mandatory upload of all 11 anatomical regions before Consultation Summary",
        )

        // Progress Card
        GlassCard(
            borderColor = if (allCompleted) OralCareColors.NormalGreen.copy(alpha = 0.4f) else Color(0xFFC0DEF8),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mandatory Clinical Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OralCareColors.TextPrimary,
                        )
                        Text(
                            text = "$capturedCount of 11 anatomical regions captured",
                            style = MaterialTheme.typography.bodySmall,
                            color = OralCareColors.TextSecondary,
                        )
                    }
                    if (allCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = OralCareColors.NormalGreenBg,
                        ) {
                            Text(
                                text = "✓ 11/11 Complete",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.NormalGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = OralCareColors.WarningAmberBg,
                        ) {
                            Text(
                                text = "⚠ ${11 - capturedCount} Remaining",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.WarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { capturedCount / 11f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (allCompleted) OralCareColors.NormalGreen else OralCareColors.AccentBlueStrong,
                    trackColor = OralCareColors.LightBlue,
                )

                if (!allCompleted) {
                    val nextMissing = ClinicalConstants.ANATOMICAL_REGIONS.firstOrNull {
                        it.name.trim().lowercase() !in capturedRegionMap
                    }
                    Text(
                        text = "Next required photo: ${nextMissing?.name ?: "None"}. All 11 images are required before advancing to Step 11.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.AccentBlueStrong,
                    )
                } else {
                    Text(
                        text = "All 11 clinical oral photographs captured and ready for AI mucosal analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.NormalGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (!cameraError.isNullOrBlank()) {
            GlassCard(
                containerColor = OralCareColors.AbnormalRedBg,
                borderColor = OralCareColors.AbnormalRed.copy(alpha = 0.3f),
                contentPadding = PaddingValues(10.dp),
            ) {
                Text(cameraError!!, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Quick Selector & Action Bar
        GlassCard(contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Quick Photo Capture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                GlassDropdown(
                    label = "Target Anatomical Region",
                    selectedValue = targetSpec.name,
                    options = ClinicalConstants.ANATOMICAL_REGIONS.map { it.name },
                    onOptionSelected = { picked ->
                        val spec = ClinicalConstants.ANATOMICAL_REGIONS.first { it.name == picked }
                        targetSpec = spec
                        targetSubRegion = spec.subRegions.first()
                    },
                )
                GlassDropdown(
                    label = "Sub-Region",
                    selectedValue = targetSubRegion,
                    options = targetSpec.subRegions,
                    onOptionSelected = { targetSubRegion = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton(
                        text = "Camera",
                        icon = Icons.Default.PhotoCamera,
                        onClick = { launchCameraFor(targetSpec, targetSubRegion) },
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Gallery",
                        icon = Icons.Default.PhotoLibrary,
                        isPrimary = false,
                        onClick = { launchGalleryFor(targetSpec, targetSubRegion) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 11-Region Mandatory Image Checklist
        Text(
            text = "11-Region Clinical Image Checklist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OralCareColors.TextPrimary,
        )

        ClinicalConstants.ANATOMICAL_REGIONS.forEachIndexed { idx, spec ->
            val captured = capturedRegionMap[spec.name.trim().lowercase()]
            val isCaptured = captured != null

            GlassCard(
                borderColor = if (isCaptured) OralCareColors.NormalGreen.copy(alpha = 0.45f) else Color(0xFFC0DEF8),
                contentPadding = PaddingValues(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Reference Thumbnail (from Step 6 original photos)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1E2E))
                                .border(1.dp, Color(0xFFC0DEF8), RoundedCornerShape(10.dp))
                                .clickable {
                                    previewImageDetails = "Reference: ${spec.name}" to spec.drawableRes
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = spec.drawableRes),
                                contentDescription = "Reference for ${spec.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {
                                Text(
                                    text = "Ref",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        // Region Details and Status
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${idx + 1}. ${spec.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = spec.referenceGuide,
                                style = MaterialTheme.typography.bodySmall,
                                color = OralCareColors.TextSecondary,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(4.dp))
                            if (isCaptured) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = OralCareColors.NormalGreen,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Captured • ${captured?.subRegion ?: spec.subRegions.first()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OralCareColors.NormalGreen,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = OralCareColors.WarningAmber,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Photo Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OralCareColors.WarningAmber,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        // Captured Photo Thumbnail (if captured)
                        if (isCaptured && captured != null) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, OralCareColors.NormalGreen, RoundedCornerShape(10.dp))
                                    .clickable {
                                        previewImageDetails = "Captured: ${spec.name}" to captured.localPath
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = captured.localPath,
                                    contentDescription = "Captured image of ${spec.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    shape = RoundedCornerShape(topStart = 4.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                ) {
                                    Text(
                                        text = "Photo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Row Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCaptured && captured != null) {
                            GlassButton(
                                text = "Retake",
                                icon = Icons.Default.Refresh,
                                isPrimary = false,
                                onClick = { launchCameraFor(spec, captured.subRegion) },
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "Gallery",
                                icon = Icons.Default.PhotoLibrary,
                                isPrimary = false,
                                onClick = { launchGalleryFor(spec, captured.subRegion) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveImage(captured.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove photo",
                                    tint = OralCareColors.AbnormalRed,
                                )
                            }
                        } else {
                            GlassButton(
                                text = "Take Photo",
                                icon = Icons.Default.PhotoCamera,
                                onClick = { launchCameraFor(spec) },
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "Gallery",
                                icon = Icons.Default.PhotoLibrary,
                                isPrimary = false,
                                onClick = { launchGalleryFor(spec) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    // Active Camera Capture Viewfinder
    if (showCameraDialog) {
        CameraCaptureDialog(
            regionTitle = "${targetSpec.name} (${targetSubRegion})",
            referenceDrawableRes = targetSpec.drawableRes,
            onImageCaptured = { file ->
                showCameraDialog = false
                handleImageSaved(targetSpec.name, targetSubRegion, file.absolutePath)
            },
            onError = { msg ->
                showCameraDialog = false
                cameraError = msg
            },
            onDismiss = { showCameraDialog = false },
        )
    }

    // Automatic Next-Image Prompt Dialog ("If we upload one thing automatically it need to ask for other image")
    if (autoPromptNextRegion != null) {
        val next = autoPromptNextRegion!!
        val currentSavedCount = (state.localImages.map { it.clinicalRegion.trim().lowercase() } + autoPromptLastSavedRegion?.trim()?.lowercase()).filterNotNull().toSet().size
        val nextIdx = ClinicalConstants.ANATOMICAL_REGIONS.indexOfFirst { it.name == next.name }

        Dialog(
            onDismissRequest = { autoPromptNextRegion = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Color(0xFFC0DEF8)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Success badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OralCareColors.NormalGreenBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OralCareColors.NormalGreen, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Saved: ${autoPromptLastSavedRegion ?: "Region"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OralCareColors.NormalGreen,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        text = "Next Required Photo: ${next.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Region ${nextIdx + 1} of 11 • ${11 - currentSavedCount} remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.AccentBlueStrong,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // Reference image preview for next region
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F1E2E))
                            .border(1.dp, Color(0xFFC0DEF8), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = next.drawableRes),
                            contentDescription = next.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            shape = RoundedCornerShape(topStart = 6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) {
                            Text(
                                text = "Reference Standard",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Guidance note
                    Text(
                        text = next.referenceGuide,
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OralCareColors.LightBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )

                    Spacer(Modifier.height(4.dp))

                    GlassButton(
                        text = "Take Photo (${next.name})",
                        icon = Icons.Default.PhotoCamera,
                        onClick = {
                            val target = next
                            autoPromptNextRegion = null
                            launchCameraFor(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    GlassButton(
                        text = "Select from Gallery",
                        icon = Icons.Default.PhotoLibrary,
                        isPrimary = false,
                        onClick = {
                            val target = next
                            autoPromptNextRegion = null
                            launchGalleryFor(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TextButton(
                        onClick = { autoPromptNextRegion = null },
                    ) {
                        Text("Review Uploads / Do Later", color = OralCareColors.TextSecondary)
                    }
                }
            }
        }
    }

    // Zoom Image Preview Dialog
    if (previewImageDetails != null) {
        val (title, imageSource) = previewImageDetails!!
        Dialog(
            onDismissRequest = { previewImageDetails = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0xFFC0DEF8), RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OralCareColors.TextPrimary,
                        )
                        IconButton(onClick = { previewImageDetails = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = OralCareColors.TextPrimary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F1E2E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageSource is Int) {
                            Image(
                                painter = painterResource(id = imageSource),
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (imageSource is String) {
                            AsyncImage(
                                model = imageSource,
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    GlassButton(
                        text = "Close Preview",
                        onClick = { previewImageDetails = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
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
        val captured11Count = ClinicalConstants.ANATOMICAL_REGIONS.count { spec ->
            state.localImages.any { it.clinicalRegion.equals(spec.name, ignoreCase = true) }
        }
        SummarySectionCard(
            title = "10. Clinical Images & AI",
            body = if (captured11Count == 11) "✓ All 11 anatomical oral regions captured and ready for AI analysis"
                   else "⚠ $captured11Count of 11 anatomical regions captured",
            step = 10,
            onEdit = onJumpToStep,
        )

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
