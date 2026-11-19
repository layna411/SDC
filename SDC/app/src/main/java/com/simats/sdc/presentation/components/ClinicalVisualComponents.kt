package com.simats.sdc.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .background(OralCareColors.SecondaryBackground.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.8f), OralCareTokens.InputShape)
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
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        highlighted = true,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .border(1.dp, OralCareColors.PastelBlue, RoundedCornerShape(16.dp))
                    .padding(6.dp),
            ) {
                val w = size.width
                val h = size.height
                drawOval(
                    color = Color(0xFFF7D1D1),
                    topLeft = Offset(w * 0.08f, h * 0.14f),
                    size = Size(w * 0.84f, h * 0.72f),
                )
                drawOval(
                    color = Color(0xFFB85454),
                    topLeft = Offset(w * 0.18f, h * 0.24f),
                    size = Size(w * 0.64f, h * 0.52f),
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.24f, h * 0.26f),
                    size = Size(w * 0.52f, h * 0.12f),
                    cornerRadius = CornerRadius(6f, 6f),
                )
                drawOval(
                    color = Color(0xFFEA8B8B),
                    topLeft = Offset(w * 0.28f, h * 0.50f),
                    size = Size(w * 0.44f, h * 0.22f),
                )

                val highlight = OralCareColors.AccentBlueStrong
                when (regionCode) {
                    "LABIAL_MUCOSA", "LIP_VERMILLION_BORDER" -> drawOval(
                        color = highlight,
                        topLeft = Offset(w * 0.08f, h * 0.14f),
                        size = Size(w * 0.84f, h * 0.72f),
                        style = Stroke(width = 4f),
                    )
                    "BUCCAL_MUCOSA" -> {
                        drawCircle(highlight, radius = w * 0.1f, center = Offset(w * 0.18f, h * 0.5f))
                        drawCircle(highlight, radius = w * 0.1f, center = Offset(w * 0.82f, h * 0.5f))
                    }
                    "PALATAL_MUCOSA" -> drawOval(
                        color = highlight.copy(alpha = 0.78f),
                        topLeft = Offset(w * 0.30f, h * 0.30f),
                        size = Size(w * 0.40f, h * 0.18f),
                    )
                    "ALVEOLAR_MUCOSA" -> drawRoundRect(
                        color = highlight,
                        topLeft = Offset(w * 0.22f, h * 0.24f),
                        size = Size(w * 0.56f, h * 0.16f),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(width = 3.5f),
                    )
                    "TONGUE" -> drawOval(
                        color = highlight.copy(alpha = 0.82f),
                        topLeft = Offset(w * 0.28f, h * 0.50f),
                        size = Size(w * 0.44f, h * 0.22f),
                    )
                    "FLOOR_OF_MOUTH" -> drawRoundRect(
                        color = highlight,
                        topLeft = Offset(w * 0.30f, h * 0.66f),
                        size = Size(w * 0.40f, h * 0.10f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                    "PALATINE_TONSIL", "PILLAR_OF_FAUCES" -> {
                        drawCircle(highlight, radius = w * 0.08f, center = Offset(w * 0.34f, h * 0.44f))
                        drawCircle(highlight, radius = w * 0.08f, center = Offset(w * 0.66f, h * 0.44f))
                    }
                    "FRENIAL_ATTACHMENT" -> drawLine(
                        color = highlight,
                        start = Offset(w * 0.50f, h * 0.18f),
                        end = Offset(w * 0.50f, h * 0.78f),
                        strokeWidth = 4f,
                    )
                    "ANGLE_OF_MOUTH" -> {
                        drawCircle(highlight, radius = w * 0.09f, center = Offset(w * 0.10f, h * 0.50f))
                        drawCircle(highlight, radius = w * 0.09f, center = Offset(w * 0.90f, h * 0.50f))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = regionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = OralCareColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = guideText,
                    style = MaterialTheme.typography.bodySmall,
                    color = OralCareColors.TextSecondary,
                )
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
