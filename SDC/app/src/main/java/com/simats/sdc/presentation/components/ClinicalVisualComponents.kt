package com.simats.sdc.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.data.dto.ToothExaminationDto
import com.simats.sdc.data.dto.WeeklyPointDto

@Composable
fun WeeklyScreeningChart(
    points: List<WeeklyPointDto>,
    modifier: Modifier = Modifier,
    title: String = "Weekly Screening Activity",
) {
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "Screenings recorded over the past 7 days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        val safePoints = if (points.isEmpty()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").map { WeeklyPointDto("", it, 0) }
        } else points
        val maxVal = (safePoints.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OralCareTokens.InputShape)
                .background(Brush.verticalGradient(listOf(Color(0x66DDEEFF), Color(0x33EAF4FD))))
                .border(1.dp, Color(0x66B6D9F5), OralCareTokens.InputShape)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                safePoints.forEach { pt ->
                    val fraction = (pt.count.toFloat() / maxVal.toFloat()).coerceIn(0.08f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = pt.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = OralCareColors.AccentBlueStrong,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight(fraction * 0.72f)
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(OralCareColors.AccentBlue, OralCareColors.SoftBlue)
                                    )
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = pt.label.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = OralCareColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnatomicalReferenceDiagram(
    regionCode: String,
    regionName: String,
    guideText: String,
    modifier: Modifier = Modifier,
) {
    val spec = remember(regionCode, regionName) {
        ClinicalConstants.ANATOMICAL_REGIONS.firstOrNull { it.code.equals(regionCode, ignoreCase = true) }
            ?: ClinicalConstants.getRegionSpecByName(regionName)
    }
    var showZoomDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row with Region title and zoom action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OralCareColors.LightBlue,
                    ) {
                        Text(
                            text = "Reference Photo",
                            style = MaterialTheme.typography.labelSmall,
                            color = OralCareColors.AccentBlueStrong,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = regionName,
                        style = MaterialTheme.typography.titleMedium,
                        color = OralCareColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = { showZoomDialog = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Enlarge photo",
                        tint = OralCareColors.AccentBlueStrong,
                    )
                }
            }

            // Real Clinical Reference Photo from Step 6 folder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1E2E))
                    .border(1.dp, Color(0xFFC0DEF8), RoundedCornerShape(14.dp))
                    .clickable { showZoomDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = spec.drawableRes),
                    contentDescription = "Clinical reference photograph of $regionName",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                // Bottom subtle hint banner
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Tap to enlarge",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }

            // Clinical Examination Guidance Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33DDEEFF))
                    .border(1.dp, Color(0x66B6D9F5), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = OralCareColors.AccentBlueStrong,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Examination Protocol",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.AccentBlueStrong,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = guideText,
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextPrimary,
                    )
                }
            }
        }
    }

    // Full-screen enlargement dialog
    if (showZoomDialog) {
        Dialog(
            onDismissRequest = { showZoomDialog = false },
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
                        Column {
                            Text(
                                text = "Clinical Reference: $regionName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.TextPrimary,
                            )
                            Text(
                                text = "Saveetha Dental College Reference Standard",
                                style = MaterialTheme.typography.labelSmall,
                                color = OralCareColors.TextSecondary,
                            )
                        }
                        IconButton(onClick = { showZoomDialog = false }) {
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
                        Image(
                            painter = painterResource(id = spec.drawableRes),
                            contentDescription = regionName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Text(
                        text = guideText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OralCareColors.TextPrimary,
                    )

                    GlassButton(
                        text = "Close Preview",
                        onClick = { showZoomDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveDentalChart(
    dentitionType: String,
    teethRecords: Map<String, ToothExaminationDto>,
    selectedTooth: String?,
    onSelectTooth: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val upperRight = if (dentitionType == "PRIMARY") ClinicalConstants.PRIMARY_UPPER_RIGHT else ClinicalConstants.PERMANENT_UPPER_RIGHT
    val upperLeft = if (dentitionType == "PRIMARY") ClinicalConstants.PRIMARY_UPPER_LEFT else ClinicalConstants.PERMANENT_UPPER_LEFT
    val lowerRight = if (dentitionType == "PRIMARY") ClinicalConstants.PRIMARY_LOWER_RIGHT else ClinicalConstants.PERMANENT_LOWER_RIGHT
    val lowerLeft = if (dentitionType == "PRIMARY") ClinicalConstants.PRIMARY_LOWER_LEFT else ClinicalConstants.PERMANENT_LOWER_LEFT

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Upper Arch (Right → Left)",
            style = MaterialTheme.typography.labelMedium,
            color = OralCareColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            upperRight.forEach { num ->
                ToothCell(num, teethRecords[num], selectedTooth == num) { onSelectTooth(num) }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(44.dp)
                    .background(OralCareColors.AccentBlue),
            )
            upperLeft.forEach { num ->
                ToothCell(num, teethRecords[num], selectedTooth == num) { onSelectTooth(num) }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = OralCareColors.PastelBlue)
        Spacer(Modifier.height(10.dp))

        Text(
            text = "Lower Arch (Right → Left)",
            style = MaterialTheme.typography.labelMedium,
            color = OralCareColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lowerRight.forEach { num ->
                ToothCell(num, teethRecords[num], selectedTooth == num) { onSelectTooth(num) }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(44.dp)
                    .background(OralCareColors.AccentBlue),
            )
            lowerLeft.forEach { num ->
                ToothCell(num, teethRecords[num], selectedTooth == num) { onSelectTooth(num) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            LegendDot("Healthy", OralCareColors.NormalGreen)
            LegendDot("Caries / Fractured", OralCareColors.AbnormalRed)
            LegendDot("Filled / Crown", OralCareColors.AccentBlueStrong)
            LegendDot("Missing", OralCareColors.TextSecondary)
            LegendDot("Other", OralCareColors.WarningAmber)
        }
    }
}

@Composable
private fun ToothCell(
    toothNumber: String,
    exam: ToothExaminationDto?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val status = exam?.status ?: "UNEXAMINED"
    val bgColor = when (status) {
        "HEALTHY" -> OralCareColors.NormalGreenBg
        "CARIES", "ROOT_STUMP", "FRACTURED" -> OralCareColors.AbnormalRedBg
        "FILLED", "CROWN", "PROSTHESIS" -> OralCareColors.LightBlue
        "MISSING" -> Color(0xFFE9EFF6)
        "MOBILE", "IMPACTED" -> OralCareColors.WarningAmberBg
        else -> Color.White.copy(alpha = 0.88f)
    }
    val borderColor = if (isSelected) OralCareColors.AccentBlueStrong else OralCareColors.PastelBlue

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(44.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Text(
            text = toothNumber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OralCareColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (status == "UNEXAMINED") "•" else status.take(3),
            fontSize = 9.sp,
            color = OralCareColors.TextSecondary,
            maxLines = 1,
        )
        if (!exam?.surfaces.isNullOrEmpty()) {
            Text(
                text = exam!!.surfaces.joinToString("") { it.surfaceCode },
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = OralCareColors.AccentBlueStrong,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
    }
}
