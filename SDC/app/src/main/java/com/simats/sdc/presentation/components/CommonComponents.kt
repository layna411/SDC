package com.simats.sdc.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.data.dto.PatientDto

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) = GlassButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    isPrimary = true,
    enabled = enabled,
    loading = loading,
    icon = icon,
)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = GlassButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    isPrimary = false,
    enabled = enabled,
    icon = icon,
)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    isError: Boolean = false,
    errorMessage: String? = null,
    readOnly: Boolean = false,
    imeAction: ImeAction = if (isPassword) ImeAction.Done else ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) = GlassTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    placeholder = placeholder,
    leadingIcon = leadingIcon,
    isPassword = isPassword,
    keyboardType = keyboardType,
    singleLine = singleLine,
    maxLines = maxLines,
    isError = isError,
    errorMessage = errorMessage,
    readOnly = readOnly,
    imeAction = imeAction,
    onImeAction = onImeAction,
)

@Composable
fun AppDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) = GlassDropdown(
    label = label,
    selectedValue = selectedValue,
    options = options,
    onOptionSelected = onOptionSelected,
    modifier = modifier,
)

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search by name, ID, or phone...",
    modifier: Modifier = Modifier,
) = GlassSearchBar(
    query = query,
    onQueryChange = onQueryChange,
    placeholder = placeholder,
    modifier = modifier,
)

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = OralCareColors.AccentBlueStrong,
    subtitle: String? = null,
    highlighted: Boolean = false,
) = GlassStatCard(
    title = title,
    value = value,
    icon = icon,
    modifier = modifier,
    accentColor = accentColor,
    highlighted = highlighted,
    subtitle = subtitle,
)

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionLabel, color = OralCareColors.AccentBlueStrong, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun LoadingView(message: String = "Loading clinical records...", modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = OralCareColors.AccentBlueStrong,
                trackColor = OralCareColors.LightBlue,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.Inbox,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(26.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(OralCareColors.LightBlue)
                    .border(1.dp, Color.White, CircleShape),
            ) {
                Icon(icon, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                PrimaryButton(text = actionText, onClick = onAction)
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = OralCareColors.AbnormalRedBg.copy(alpha = 0.88f),
        borderColor = OralCareColors.AbnormalRed.copy(alpha = 0.28f),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = OralCareColors.AbnormalRed, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = OralCareColors.AbnormalRed)
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = "Retry", onClick = onRetry, icon = Icons.Default.Refresh)
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(text = confirmText, onClick = onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
    }

    @Composable
    fun ScreeningStatusBadge(outcome: String?, modifier: Modifier = Modifier) {
        val (bg, fg, label) = when (outcome?.uppercase()) {
            "NORMAL" -> Triple(OralCareColors.NormalGreenBg, OralCareColors.NormalGreen, "NORMAL")
            "ABNORMAL" -> Triple(OralCareColors.AbnormalRedBg, OralCareColors.AbnormalRed, "ABNORMAL")
            "PENDING_CLINICAL_REVIEW", "PENDING_REVIEW" -> Triple(OralCareColors.WarningAmberBg, OralCareColors.WarningAmber, "PENDING REVIEW")
            else -> Triple(OralCareColors.LightBlue, OralCareColors.AccentBlueStrong, outcome ?: "DRAFT")
        }
        Surface(
            shape = OralCareTokens.BadgeShape,
            color = bg,
            border = BorderStroke(0.8.dp, fg.copy(alpha = 0.18f)),
            modifier = modifier,
        ) {
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    @Composable
    fun PatientCard(
        patient: PatientDto,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        GlassCard(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            contentPadding = PaddingValues(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(OralCareColors.LightBlue, OralCareColors.PastelBlue)))
                        .border(1.2.dp, Color.White, CircleShape),
                ) {
                    Text(
                        text = patient.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.AccentBlueStrong,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = patient.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ScreeningStatusBadge(patient.latestOutcome)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${patient.patientUid} • ${patient.age}y • ${patient.sex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = patient.phoneNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = OralCareColors.AccentBlueStrong,
                        )
                        Text(
                            text = "${patient.totalScreenings} screening(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (patient.latestLesionPresent == true) {
                            Text(
                                text = "Lesion Flagged",
                                style = MaterialTheme.typography.labelSmall,
                                color = OralCareColors.AbnormalRed,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun StepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String,
    modifier: Modifier = Modifier,
) = GlassProgressIndicator(
    currentStep = currentStep,
    totalSteps = totalSteps,
    stepTitle = stepTitle,
    modifier = modifier,
)

@Composable
fun ImageUploadCard(
    regionLabel: String,
    subRegionLabel: String?,
    localImagePath: String?,
    aiStatusLabel: String? = null,
    aiState: String? = null,
    qualityStatus: String? = null,
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) = GlassImageCard(
    regionLabel = regionLabel,
    subRegionLabel = subRegionLabel,
    localImagePath = localImagePath,
    aiStatusLabel = aiStatusLabel,
    aiState = aiState,
    qualityStatus = qualityStatus,
    onCaptureCamera = onCaptureCamera,
    onPickGallery = onPickGallery,
    onRemove = onRemove,
    modifier = modifier,
)
