package com.simats.sdc.presentation.patient

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.simats.sdc.core.common.AnatomicalRegionSpec
import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.data.dto.*
import com.simats.sdc.presentation.components.*
import com.simats.sdc.presentation.screening.LocalCapturedImage
import java.io.File

@Composable
fun PatientsScreen(viewModel: PatientsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadPatients(1) }
    LaunchedEffect(state.reportDownloadedFile) {
        state.reportDownloadedFile?.let { file ->
            Toast.makeText(context, "Report saved to Downloads/OralCare/${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    val detailPatient = state.selectedPatient
    if (detailPatient != null) {
        BackHandler { viewModel.closePatientDetail() }
        PatientDetailView(
            patient = detailPatient,
            screenings = state.patientScreenings,
            isLoading = state.isLoadingDetail,
            baseUrl = state.baseUrl,
            accessToken = state.accessToken,
            isDownloadingReport = state.isDownloadingReport,
            reportDownloadedFile = state.reportDownloadedFile,
            reportMessage = state.reportMessage,
            reportError = state.reportError,
            isReexamining = state.isReexamining,
            reexamineImages = state.reexamineImages,
            isSubmittingReexamination = state.isSubmittingReexamination,
            reexaminationError = state.reexaminationError,
            reexaminationSuccess = state.reexaminationSuccess,
            onStartReexamination = { viewModel.startReexamination() },
            onDismissReexamination = { viewModel.dismissReexamination() },
            onAddReexamineImage = { r, s, p -> viewModel.addReexamineImage(r, s, p) },
            onRemoveReexamineImage = { r -> viewModel.removeReexamineImage(r) },
            onSubmitReexamination = { id -> viewModel.submitReexamination(id) },
            onDownloadReport = { viewModel.downloadPatientReport(detailPatient, state.patientScreenings) },
            onShareReport = { viewModel.sharePatientReport(context, detailPatient, state.patientScreenings) },
            onRefresh = { viewModel.refreshSelectedPatient() },
            onBack = { viewModel.closePatientDetail() },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Patients (${state.totalCount})",
            subtitle = "Search, filter & inspect full clinical records",
            actions = {
                IconButton(onClick = { viewModel.loadPatients(state.page) }) {
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
            GlassSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchChange,
                placeholder = "Search by Patient ID, name, or phone...",
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.ALL,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.ALL) },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.NORMAL,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.NORMAL) },
                    label = { Text("Normal") },
                )
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.ABNORMAL,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.ABNORMAL) },
                    label = { Text("Abnormal") },
                )
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.PENDING_REVIEW,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.PENDING_REVIEW) },
                    label = { Text("Pending Review") },
                )
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.LESION_PRESENT,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.LESION_PRESENT) },
                    label = { Text("Lesion Present") },
                )
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.MALE,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.MALE) },
                    label = { Text("Male") },
                )
                FilterChip(
                    selected = state.activeFilter == PatientFilterType.FEMALE,
                    onClick = { viewModel.onFilterSelect(PatientFilterType.FEMALE) },
                    label = { Text("Female") },
                )
            }

            when {
                state.isLoading -> LoadingView("Loading patients...")
                state.error != null -> ErrorStateView(state.error!!, onRetry = { viewModel.loadPatients(state.page) })
                state.patients.isEmpty() -> EmptyStateView(
                    title = "No Patients Found",
                    subtitle = "Try adjusting your search query or filters, or register a new patient from the Create tab.",
                    icon = Icons.Default.PeopleOutline,
                )
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(state.patients, key = { it.id }) { patient ->
                            PatientCard(patient = patient, onClick = { viewModel.openPatientDetail(patient) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatientDetailView(
    patient: PatientDto,
    screenings: List<ScreeningDetailDto>,
    isLoading: Boolean,
    baseUrl: String,
    accessToken: String,
    isDownloadingReport: Boolean,
    reportDownloadedFile: File?,
    reportMessage: String?,
    reportError: String?,
    isReexamining: Boolean,
    reexamineImages: List<LocalCapturedImage>,
    isSubmittingReexamination: Boolean,
    reexaminationError: String?,
    reexaminationSuccess: ScreeningDetailDto?,
    onStartReexamination: () -> Unit,
    onDismissReexamination: () -> Unit,
    onAddReexamineImage: (String, String?, String) -> Unit,
    onRemoveReexamineImage: (String) -> Unit,
    onSubmitReexamination: (Int) -> Unit,
    onDownloadReport: () -> Unit,
    onShareReport: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var previewImage by remember { mutableStateOf<PatientImageDto?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = patient.displayName,
            subtitle = patient.patientUid,
            showBackButton = true,
            onBack = onBack,
            actions = {
                IconButton(onClick = onDownloadReport) {
                    if (isDownloadingReport) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OralCareColors.AccentBlueStrong,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Download Report", tint = OralCareColors.AccentBlueStrong)
                    }
                }
                IconButton(onClick = onShareReport) {
                    Icon(Icons.Default.Share, contentDescription = "Share Report", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Patient Clinical Report & PDF Export Card
            item {
                GlassCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = CircleShape,
                                    color = OralCareColors.LightBlue,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = OralCareColors.AccentBlueStrong,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Individual Clinical Report",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OralCareColors.TextPrimary,
                                    )
                                    Text(
                                        text = "Saveetha Dental College • Complete PDF Summary",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OralCareColors.TextSecondary,
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Download or share a detailed medical & dental report including full personal history, 11-region findings, teeth records, lab investigations, and AI assessment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OralCareColors.TextSecondary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GlassButton(
                                text = "Download PDF",
                                icon = Icons.Default.Download,
                                loading = isDownloadingReport,
                                onClick = onDownloadReport,
                                fontSize = 11.5.sp,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "Share PDF",
                                icon = Icons.Default.Share,
                                isPrimary = false,
                                enabled = !isDownloadingReport,
                                onClick = onShareReport,
                                fontSize = 11.5.sp,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (!reportError.isNullOrBlank()) {
                            Text(
                                text = reportError,
                                color = OralCareColors.AbnormalRed,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        if (!reportMessage.isNullOrBlank() && reportDownloadedFile != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = OralCareColors.NormalGreenBg,
                                border = BorderStroke(1.dp, OralCareColors.NormalGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = OralCareColors.NormalGreen,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Report Saved Successfully",
                                                color = OralCareColors.NormalGreen,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = "Downloads/OralCare/${reportDownloadedFile.name}",
                                                color = OralCareColors.TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GlassButton(
                                            text = "Open PDF",
                                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                                            isPrimary = true,
                                            fontSize = 12.sp,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            onClick = {
                                                runCatching {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        reportDownloadedFile,
                                                    )
                                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/pdf")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    val shareFallback = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    runCatching {
                                                        context.startActivity(viewIntent)
                                                    }.onFailure {
                                                        context.startActivity(Intent.createChooser(shareFallback, "Open Patient Report"))
                                                    }
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Cannot open PDF: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        )

                                        GlassButton(
                                            text = "Share",
                                            icon = Icons.Default.Share,
                                            isPrimary = false,
                                            fontSize = 12.sp,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            onClick = onShareReport,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Examine Again Action Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OralCareColors.PrimaryBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    tint = OralCareColors.AccentBlueStrong,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Examine Again",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.TextPrimary,
                            )
                            Text(
                                text = "Upload clinical images only & get updated AI assessment",
                                style = MaterialTheme.typography.bodySmall,
                                color = OralCareColors.TextSecondary,
                            )
                        }
                        GlassButton(
                            text = "Examine",
                            icon = Icons.Default.CameraAlt,
                            fontSize = 12.sp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            onClick = onStartReexamination,
                        )
                    }
                }
            }

            // Complete Patient Demographics Card (Step 2)
            item {
                GlassCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Patient Demographics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(patient.patientUid, style = MaterialTheme.typography.labelMedium, color = OralCareColors.AccentBlueStrong, fontWeight = FontWeight.SemiBold)
                            }
                            ScreeningStatusBadge(patient.latestOutcome)
                        }
                        HorizontalDivider(color = OralCareColors.PastelBlue)
                        DetailRow("Full Name", patient.displayName)
                        DetailRow("Registration Date", patient.registrationDate)
                        DetailRow("Age / Sex", "${patient.age} years • ${patient.sex}")
                        DetailRow("Phone", patient.phoneNumber)
                        DetailRow("Address", patient.address)
                        DetailRow("Religion", patient.religion ?: "-")
                        DetailRow("Marital Status", patient.maritalStatus ?: "-")
                        DetailRow("Education", patient.education ?: "-")
                        DetailRow("Occupation", patient.occupation ?: "-")
                        DetailRow("Monthly Family Income", patient.familyIncomePerMonth ?: "-")
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Clinical Screening Records (${screenings.size})",
                    subtitle = "Complete 11-step screening details & uploaded clinical photos",
                )
            }

            if (isLoading) {
                item { LoadingView("Loading complete clinical record & images...") }
            } else if (screenings.isEmpty()) {
                item { EmptyStateView("No Screenings Yet", "This patient does not have any submitted screening records yet.") }
            } else {
                items(screenings, key = { it.id }) { scr ->
                    FullScreeningDetailCard(
                        scr = scr,
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        onOpenFullImage = { previewImage = it },
                    )
                }
            }
        }
    }

    // Full-screen Clinical Photo Lightbox
    previewImage?.let { img ->
        val imageUrl = buildSecureImageUrl(baseUrl, img.id)
        Dialog(
            onDismissRequest = { previewImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewImage = null },
            ) {
                AuthenticatedClinicalImage(
                    imageUrl = imageUrl,
                    accessToken = accessToken,
                    contentDescription = "${img.clinicalRegion} photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${img.clinicalRegion} (${img.subRegion ?: "General"})",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(img.imageUid, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { previewImage = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview", tint = Color.White)
                    }
                }
            }
        }
    }

    if (isReexamining) {
        ReexaminationDialog(
            patient = patient,
            images = reexamineImages,
            isSubmitting = isSubmittingReexamination,
            error = reexaminationError,
            successResult = reexaminationSuccess,
            onAddImage = onAddReexamineImage,
            onRemoveImage = onRemoveReexamineImage,
            onSubmit = { onSubmitReexamination(patient.id) },
            onDismiss = onDismissReexamination,
        )
    }
}

@Composable
private fun ReexaminationDialog(
    patient: PatientDto,
    images: List<LocalCapturedImage>,
    isSubmitting: Boolean,
    error: String?,
    successResult: ScreeningDetailDto?,
    onAddImage: (String, String?, String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var targetSpec by remember { mutableStateOf(ClinicalConstants.ANATOMICAL_REGIONS.first()) }
    var targetSubRegion by remember { mutableStateOf(ClinicalConstants.ANATOMICAL_REGIONS.first().subRegions.first()) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var autoPromptNextRegion by remember { mutableStateOf<AnatomicalRegionSpec?>(null) }
    var autoPromptLastSavedRegion by remember { mutableStateOf<String?>(null) }
    var previewLocalPath by remember { mutableStateOf<String?>(null) }

    fun handleImageSaved(region: String, subRegion: String?, localPath: String) {
        onAddImage(region, subRegion, localPath)
        autoPromptLastSavedRegion = region
        val allRegions = ClinicalConstants.ANATOMICAL_REGIONS
        val currentNames = (images.map { it.clinicalRegion.trim().lowercase() } + region.trim().lowercase()).toSet()
        val next = allRegions.firstOrNull { it.name.trim().lowercase() !in currentNames }
        if (next != null) {
            autoPromptNextRegion = next
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
                val dest = File(dir, "reexamine_${System.currentTimeMillis()}.jpg")
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

    val capturedRegionMap = remember(images) {
        images.associateBy { it.clinicalRegion.trim().lowercase() }
    }
    val capturedCount = ClinicalConstants.ANATOMICAL_REGIONS.count {
        it.name.trim().lowercase() in capturedRegionMap
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF2F7FC),
        ) {
            if (successResult != null) {
                // Success Assessment Card
                Box(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.2.dp, Color(0xFFC0DEF8)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val isNormalResult = successResult.outcome.equals("NORMAL", ignoreCase = true)
                            Surface(
                                shape = CircleShape,
                                color = if (isNormalResult) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                                modifier = Modifier.size(64.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isNormalResult) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isNormalResult) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }

                            Text(
                                text = "Re-Examination Complete!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.TextPrimary,
                                textAlign = TextAlign.Center,
                            )

                            Text(
                                text = "AI analyzed the newly uploaded clinical images for ${patient.displayName} and re-evaluated diagnostic markers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OralCareColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isNormalResult) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                                border = BorderStroke(1.dp, if (isNormalResult) OralCareColors.NormalGreen.copy(alpha = 0.4f) else OralCareColors.AbnormalRed.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Updated Screening Outcome: ${successResult.outcome}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isNormalResult) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                                    )
                                    Text(
                                        text = "Screening ID: ${successResult.screeningUid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OralCareColors.TextSecondary,
                                    )
                                }
                            }

                            GlassButton(
                                text = "View Updated Patient Record",
                                icon = Icons.Default.Check,
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                // Re-Examination Checklist & Uploader
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    GlassHeader(
                        title = "Examine Again",
                        subtitle = "${patient.displayName} • ${patient.patientUid}",
                        showBackButton = true,
                        onBack = onDismiss,
                    )

                    // Scrollable Checklist
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Guide banner
                        GlassCard(
                            borderColor = Color(0xFFC0DEF8),
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = OralCareColors.AccentBlueStrong,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Re-Examination: Upload Images Only",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OralCareColors.TextPrimary,
                                    )
                                }
                                Text(
                                    text = "Patient demographic & medical baselines are preserved. Capture or select clinical images for the oral regions below to re-evaluate with AI.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OralCareColors.TextSecondary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Captured: $capturedCount / 11 Regions",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OralCareColors.AccentBlueStrong,
                                    )
                                    if (capturedCount > 0) {
                                        Text(
                                            text = "${images.size} photo(s) ready",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OralCareColors.NormalGreen,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { (capturedCount.toFloat() / 11f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = OralCareColors.AccentBlueStrong,
                                    trackColor = Color(0xFFD6E8FA),
                                )
                            }
                        }

                        // Quick Capture Target Card
                        GlassCard(contentPadding = PaddingValues(14.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Quick Photo Capture",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OralCareColors.TextPrimary,
                                )
                                GlassDropdown(
                                    label = "Select Anatomical Region",
                                    selectedValue = targetSpec.name,
                                    options = ClinicalConstants.ANATOMICAL_REGIONS.map { it.name },
                                    onOptionSelected = { name ->
                                        val s = ClinicalConstants.getRegionSpecByName(name)
                                        targetSpec = s
                                        targetSubRegion = s.subRegions.first()
                                    },
                                )
                                if (targetSpec.subRegions.size > 1) {
                                    GlassDropdown(
                                        label = "Sub-Region",
                                        selectedValue = targetSubRegion,
                                        options = targetSpec.subRegions,
                                        onOptionSelected = { targetSubRegion = it },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    GlassButton(
                                        text = "Take Photo",
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

                        // 11 Anatomical Regions List
                        Text(
                            text = "11-Region Checklist",
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
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        // Standard Anatomical Reference Thumbnail
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF0F1E2E))
                                                .border(1.dp, Color(0xFFC0DEF8), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Image(
                                                painter = painterResource(id = spec.drawableRes),
                                                contentDescription = spec.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${idx + 1}. ${spec.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = OralCareColors.TextPrimary,
                                            )
                                            Text(
                                                text = spec.referenceGuide,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OralCareColors.TextSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(3.dp))
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
                                                        fontWeight = FontWeight.Bold,
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
                                                        text = "Photo Needed",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = OralCareColors.WarningAmber,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                        }

                                        // Captured thumbnail if ready
                                        if (isCaptured && captured != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.5.dp, OralCareColors.NormalGreen, RoundedCornerShape(8.dp))
                                                    .clickable { previewLocalPath = captured.localPath },
                                            ) {
                                                SubcomposeAsyncImage(
                                                    model = File(captured.localPath),
                                                    contentDescription = "Captured ${spec.name}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (isCaptured) {
                                            GlassButton(
                                                text = "Retake",
                                                icon = Icons.Default.CameraAlt,
                                                fontSize = 11.sp,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
                                                onClick = { launchCameraFor(spec) },
                                                modifier = Modifier.weight(1f),
                                            )
                                            GlassButton(
                                                text = "Gallery",
                                                icon = Icons.Default.PhotoLibrary,
                                                isPrimary = false,
                                                fontSize = 11.sp,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
                                                onClick = { launchGalleryFor(spec) },
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = { onRemoveImage(spec.name) },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Remove",
                                                    tint = OralCareColors.AbnormalRed,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        } else {
                                            GlassButton(
                                                text = "Take Photo",
                                                icon = Icons.Default.CameraAlt,
                                                fontSize = 11.5.sp,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
                                                onClick = { launchCameraFor(spec) },
                                                modifier = Modifier.weight(1f),
                                            )
                                            GlassButton(
                                                text = "Gallery",
                                                icon = Icons.Default.PhotoLibrary,
                                                isPrimary = false,
                                                fontSize = 11.5.sp,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
                                                onClick = { launchGalleryFor(spec) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pinned Bottom Action Footer
                    Surface(
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, Color(0xFFE4EFF8)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!error.isNullOrBlank()) {
                                Text(
                                    text = error,
                                    color = OralCareColors.AbnormalRed,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!cameraError.isNullOrBlank()) {
                                Text(
                                    text = cameraError!!,
                                    color = OralCareColors.AbnormalRed,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GlassButton(
                                    text = if (isSubmitting) "Analyzing with AI..." else "Run AI Assessment",
                                    icon = Icons.Default.Psychology,
                                    loading = isSubmitting,
                                    enabled = images.isNotEmpty() && !isSubmitting,
                                    onClick = onSubmit,
                                    modifier = Modifier.weight(1.4f),
                                )
                                GlassButton(
                                    text = "Cancel",
                                    isPrimary = false,
                                    enabled = !isSubmitting,
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(0.8f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Active Camera Capture Viewfinder
    if (showCameraDialog) {
        CameraCaptureDialog(
            regionTitle = "${targetSpec.name} ($targetSubRegion)",
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
        val currentSavedCount = (images.map { it.clinicalRegion.trim().lowercase() } + autoPromptLastSavedRegion?.trim()?.lowercase()).filterNotNull().toSet().size
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
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
                    }

                    Text(
                        text = next.referenceGuide,
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassButton(
                            text = "Take Photo",
                            icon = Icons.Default.CameraAlt,
                            onClick = {
                                val target = next
                                autoPromptNextRegion = null
                                launchCameraFor(target)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "Gallery",
                            icon = Icons.Default.PhotoLibrary,
                            isPrimary = false,
                            onClick = {
                                val target = next
                                autoPromptNextRegion = null
                                launchGalleryFor(target)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    TextButton(
                        onClick = { autoPromptNextRegion = null },
                    ) {
                        Text("Review Uploads / Later", color = OralCareColors.TextSecondary)
                    }
                }
            }
        }
    }

    // Zoom Image Preview Dialog
    if (previewLocalPath != null) {
        val f = File(previewLocalPath!!)
        Dialog(
            onDismissRequest = { previewLocalPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewLocalPath = null },
                contentAlignment = Alignment.Center,
            ) {
                if (f.exists()) {
                    SubcomposeAsyncImage(
                        model = f,
                        contentDescription = "Zoomed Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
                IconButton(
                    onClick = { previewLocalPath = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullScreeningDetailCard(
    scr: ScreeningDetailDto,
    baseUrl: String,
    accessToken: String,
    onOpenFullImage: (PatientImageDto) -> Unit,
) {
    val isNormal = scr.outcome.equals("NORMAL", ignoreCase = true)
    val isAbnormal = scr.outcome.equals("ABNORMAL", ignoreCase = true)
    val outcomeBorder = when {
        isNormal -> Color(0xFF16A34A)
        isAbnormal -> Color(0xFFDC2626)
        else -> OralCareColors.WarningAmber
    }
    val outcomeBg = when {
        isNormal -> OralCareColors.NormalGreenBg
        isAbnormal -> OralCareColors.AbnormalRedBg
        else -> OralCareColors.WarningAmberBg
    }
    val outcomeFg = when {
        isNormal -> OralCareColors.NormalGreen
        isAbnormal -> OralCareColors.AbnormalRed
        else -> OralCareColors.WarningAmber
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Outcome & AI Consultation Summary Header Banner
        GlassCard(
            containerColor = outcomeBg,
            borderColor = outcomeBorder.copy(alpha = 0.6f),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(scr.screeningUid, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.TextPrimary)
                        Text("Date: ${scr.screeningDate} • Status: ${scr.workflowStatus}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                    }
                    ScreeningStatusBadge(scr.outcome)
                }
                scr.consultationSummary?.let { summary ->
                    Text(summary.outcomeReason, style = MaterialTheme.typography.bodySmall, color = outcomeFg, fontWeight = FontWeight.Medium)
                    if (!summary.clinicalRecommendations.isNullOrBlank()) {
                        Text("Recommendations: ${summary.clinicalRecommendations}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextPrimary)
                    }
                }
            }
        }

        // Step 10 – Uploaded Clinical Images & AI Analysis (Displayed Prominently!)
        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Uploaded Clinical Images & AI Analysis (${scr.patientImages.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OralCareColors.AccentBlueStrong,
                )
                if (scr.patientImages.isEmpty()) {
                    Text("No clinical images uploaded for this screening.", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                } else {
                    scr.patientImages.forEach { img ->
                        val imageUrl = buildSecureImageUrl(baseUrl, img.id)
                        val latestAi = img.aiAnalyses.firstOrNull()
                        val aiClass = latestAi?.modelOutput?.get("classification")?.toString()?.uppercase()
                        val aiConf = latestAi?.confidenceScore?.let { "${(it * 100).toInt()}%" }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(OralCareTokens.InputShape)
                                .background(OralCareColors.PrimaryBackground.copy(alpha = 0.85f))
                                .border(1.dp, OralCareColors.PastelBlue, OralCareTokens.InputShape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${img.clinicalRegion} – ${img.subRegion ?: "General"}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text("${img.originalFilename} • ${img.imageUid}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
                                }
                                if (aiClass != null) {
                                    StatusPill(
                                        text = if (aiConf != null) "$aiClass ($aiConf)" else aiClass,
                                        bg = if (aiClass == "ABNORMAL") OralCareColors.AbnormalRedBg else OralCareColors.NormalGreenBg,
                                        fg = if (aiClass == "ABNORMAL") OralCareColors.AbnormalRed else OralCareColors.NormalGreen,
                                    )
                                }
                            }

                            // Actual Authenticated Clinical Photo
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(OralCareTokens.InputShape)
                                    .background(OralCareColors.LightBlue.copy(alpha = 0.5f))
                                    .border(1.dp, Color.White, OralCareTokens.InputShape)
                                    .clickable { onOpenFullImage(img) },
                            ) {
                                AuthenticatedClinicalImage(
                                    imageUrl = imageUrl,
                                    accessToken = accessToken,
                                    contentDescription = "Uploaded photo of ${img.clinicalRegion}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    shape = OralCareTokens.BadgeShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Tap to Zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (latestAi != null) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    StatusPill("Quality: ${latestAi.imageQualityStatus}", OralCareColors.LightBlue, OralCareColors.AccentBlueStrong)
                                    StatusPill("Review: ${latestAi.reviewStatus.replace("_", " ")}", OralCareColors.WarningAmberBg, OralCareColors.WarningAmber)
                                }
                                latestAi.modelOutput?.get("summary")?.toString()?.let { summary ->
                                    Text(summary, style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 1 – Consent & Location
        scr.consentRecord?.let { c ->
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Consent & Location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    DetailRow("Hub", c.hub)
                    DetailRow("Health Partner", c.healthPartner)
                    DetailRow("Healthworker", c.healthworkerName)
                    DetailRow("Location", c.geolocationLabel ?: "-")
                    if (c.latitude != null && c.longitude != null) {
                        DetailRow("GPS Coordinates", "${String.format("%.5f", c.latitude)}, ${String.format("%.5f", c.longitude)}")
                    }
                    DetailRow("Consent & Terms", if (c.consentAccepted && c.termsAccepted && c.consentFormSigned) "Signed & Accepted" else "Incomplete")
                }
            }
        }

        // Steps 3 & 4 – Personal & Habit History
        scr.personalHistory?.let { ph ->
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("3–4. Personal & Habit History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    DetailRow("Diet History", ph.dietHistory)
                    DetailRow("Spice Consumption", ph.spiceConsumption)
                    DetailRow("Habit History Reported", if (ph.hasHabitHistory) "Yes (${scr.habitHistories.size} habit(s))" else "No")
                    if (!ph.notes.isNullOrBlank()) DetailRow("Notes", ph.notes)
                    scr.habitHistories.forEach { h ->
                        Text(
                            "• ${h.habitCategory} (${h.usageStatus}): ${listOfNotNull(h.habitType, h.frequency, h.duration, h.quantity).joinToString(" • ").ifBlank { "Recorded" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OralCareColors.TextPrimary,
                        )
                    }
                }
            }
        }

        // Step 5 – Medical & Dental History
        if (scr.medicalHistory != null || scr.dentalHistory != null) {
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("5. Medical & Dental History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    scr.dentalHistory?.let { d ->
                        DetailRow("Cleaning Aid", d.cleaningAid)
                        DetailRow("Material Used", listOfNotNull(d.materialUsed, d.materialOther).filter { it.isNotBlank() }.joinToString(" – "))
                        DetailRow("Brushing Method", d.brushingMethod ?: "-")
                        DetailRow("Brushing Frequency", d.brushingFrequency ?: "-")
                        DetailRow("Brushing Duration", d.brushingDuration ?: "-")
                        DetailRow("Toothbrush Change", d.toothbrushChangeFrequency ?: "-")
                        DetailRow("Other Hygiene Aids", d.otherHygieneAids ?: "-")
                        DetailRow("Past Dental History", if (d.pastDentalHistory) "Yes (${d.pastDentalDetails ?: "Details not specified"})" else "No")
                        DetailRow("Denture Usage", if (d.dentureUsage) "Yes (${d.dentureDetails ?: "Details not specified"})" else "No")
                    }
                    scr.medicalHistory?.let { m ->
                        DetailRow("Family History", m.familyHistoryConditions.joinToString(", ").ifBlank { "No Relevant History" })
                        if (!m.familyHistoryOther.isNullOrBlank()) DetailRow("Family History (Other)", m.familyHistoryOther)
                        DetailRow("Past Medical History", m.pastMedicalConditions.joinToString(", ").ifBlank { "No Relevant History" })
                        if (!m.pastMedicalOther.isNullOrBlank()) DetailRow("Medical History (Other)", m.pastMedicalOther)
                    }
                }
            }
        }

        // Step 6 – Clinical Findings
        if (scr.clinicalFindings.isNotEmpty()) {
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("6. Clinical Findings (${scr.clinicalFindings.size} site(s))", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    scr.clinicalFindings.forEach { cf ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${cf.anatomicalRegion} – ${cf.subRegion ?: "General"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (cf.observations.isNotEmpty()) {
                                    Text(cf.observations.joinToString(", ") { it.observationLabel }, style = MaterialTheme.typography.bodySmall, color = OralCareColors.AbnormalRed)
                                }
                                if (!cf.remarks.isNullOrBlank()) {
                                    Text("Remarks: ${cf.remarks}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            ScreeningStatusBadge(cf.status)
                        }
                        HorizontalDivider(color = OralCareColors.LightBlue)
                    }
                }
            }
        }

        // Step 7 – Lesion Details
        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("7. Lesion Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                if (!scr.lesionPresent || scr.lesionDetails.isEmpty()) {
                    Text("Lesion Absent", style = MaterialTheme.typography.bodySmall, color = OralCareColors.NormalGreen, fontWeight = FontWeight.SemiBold)
                } else {
                    scr.lesionDetails.forEach { l ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(OralCareTokens.InputShape)
                                .background(OralCareColors.AbnormalRedBg.copy(alpha = 0.7f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text("${l.anatomicalLocation} • ${l.lesionType}", fontWeight = FontWeight.Bold, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.titleSmall)
                            if (!l.size.isNullOrBlank() || !l.duration.isNullOrBlank()) {
                                Text("Size: ${l.size ?: "-"} • Duration: ${l.duration ?: "-"}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (!l.appearance.isNullOrBlank()) Text("Appearance: ${l.appearance}", style = MaterialTheme.typography.bodySmall)
                            if (!l.symptoms.isNullOrBlank()) Text("Symptoms: ${l.symptoms}", style = MaterialTheme.typography.bodySmall)
                            if (!l.clinicalObservations.isNullOrBlank()) Text("Observations: ${l.clinicalObservations}", style = MaterialTheme.typography.bodySmall)
                            if (!l.referralRecommendation.isNullOrBlank()) Text("Referral: ${l.referralRecommendation}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Step 8 – Teeth Examination
        if (scr.teethExaminations.isNotEmpty()) {
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("8. Teeth Examination (${scr.teethExaminations.size} recorded)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    scr.teethExaminations.forEach { t ->
                        val surf = if (t.surfaces.isNotEmpty()) " [Surfaces: ${t.surfaces.joinToString(",") { it.surfaceCode }}]" else ""
                        Text(
                            "• Tooth #${t.toothNumber} (${t.dentitionType}): ${t.status}$surf${if (!t.clinicalFinding.isNullOrBlank()) " – ${t.clinicalFinding}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Step 9 – Lab Investigation
        scr.labInvestigation?.let { lab ->
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("9. Lab Investigation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    if (!lab.testDate.isNullOrBlank()) DetailRow("Test Date", lab.testDate)
                    DetailRow("Haemoglobin", lab.haemoglobin?.let { "$it ${lab.haemoglobinUnit ?: "g/dL"}" } ?: "-")
                    DetailRow("RBC", lab.rbc?.let { "$it ${lab.rbcUnit ?: "million/uL"}" } ?: "-")
                    DetailRow("Total Count", lab.totalCount?.let { "$it ${lab.totalCountUnit ?: "cells/uL"}" } ?: "-")
                    DetailRow("Platelet Count", lab.plateletCount?.let { "$it ${lab.plateletCountUnit ?: "lakh/uL"}" } ?: "-")
                    DetailRow("Percentage", lab.percentage?.let { "$it%" } ?: "-")
                    DetailRow("Biopsy Report", lab.biopsyReportSummary ?: "Not recorded")
                    if (!lab.laboratoryRemarks.isNullOrBlank()) DetailRow("Lab Remarks", lab.laboratoryRemarks)
                }
            }
        }

        // Follow-Ups & Referrals
        if (scr.followUps.isNotEmpty() || scr.referrals.isNotEmpty()) {
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Follow-Ups & Referrals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                    scr.followUps.forEach { f ->
                        Text("• Follow-up ${f.scheduledDate} (${f.status})${if (!f.notes.isNullOrBlank()) ": ${f.notes}" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                    scr.referrals.forEach { r ->
                        Text("• Referral to ${r.referredTo} [${r.urgency}]: ${r.reason}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun buildSecureImageUrl(baseUrl: String, imageId: Int): String {
    val cleanBase = baseUrl.trimEnd('/')
    return "$cleanBase/images/$imageId/content"
}

@Composable
private fun AuthenticatedClinicalImage(
    imageUrl: String,
    accessToken: String,
    contentDescription: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(imageUrl, accessToken) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("ngrok-skip-browser-warning", "true")
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = OralCareColors.AccentBlueStrong,
                )
            }
        },
        error = {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = OralCareColors.TextSecondary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(4.dp))
                Text("Unable to load clinical image", style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OralCareColors.TextSecondary,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f),
        )
    }
}
