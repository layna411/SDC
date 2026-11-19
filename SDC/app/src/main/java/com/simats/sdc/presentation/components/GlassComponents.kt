package com.simats.sdc.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens

data class GlassNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Ambient pastel light-blue glassmorphism background with soft decorative translucent circles,
 * radial pastel glows, and subtle abstract curves rendered behind content at 60fps.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(listOf(OralCareColors.DarkBackground, OralCareColors.DarkSurface))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFF4F9FF),
                            Color(0xFFE9F3FC),
                            Color(0xFFF2F8FF),
                        )
                    )
                }
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (!isDark) {
                // Soft, balanced atmospheric glow at top (centered across width)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OralCareColors.PastelBlue.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w * 0.5f, 0f),
                        radius = w * 0.85f,
                    ),
                    radius = w * 0.85f,
                    center = Offset(w * 0.5f, 0f),
                )
                // Soft, balanced atmospheric glow at bottom (centered across width)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OralCareColors.LightBlue.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.5f, h),
                        radius = w * 0.85f,
                    ),
                    radius = w * 0.85f,
                    center = Offset(w * 0.5f, h),
                )
                // Delicate flowing glass curve lines with soft transparency
                val curve = Path().apply {
                    moveTo(0f, h * 0.22f)
                    cubicTo(w * 0.32f, h * 0.16f, w * 0.68f, h * 0.28f, w, h * 0.20f)
                }
                drawPath(
                    path = curve,
                    color = Color.White.copy(alpha = 0.40f),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                val lowerCurve = Path().apply {
                    moveTo(0f, h * 0.74f)
                    cubicTo(w * 0.36f, h * 0.70f, w * 0.66f, h * 0.80f, w, h * 0.73f)
                }
                drawPath(
                    path = lowerCurve,
                    color = OralCareColors.PastelBlue.copy(alpha = 0.24f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OralCareColors.AccentBlue.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(w * 0.5f, 0f),
                        radius = w * 0.8f,
                    ),
                    radius = w * 0.8f,
                    center = Offset(w * 0.5f, 0f),
                )
            }
        }
        content()
    }
}

/**
 * Core Frosted Glass Card: translucent white with subtle blue tint, delicate light border,
 * soft elevation, and 20dp rounded corners.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = OralCareTokens.CardRadius,
    highlighted: Boolean = false,
    containerColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground
    val shape = RoundedCornerShape(cornerRadius)

    val bgBrush = when {
        containerColor != null -> Brush.verticalGradient(listOf(containerColor, containerColor))
        isDark -> Brush.verticalGradient(listOf(OralCareColors.DarkCard, OralCareColors.DarkSurface))
        else -> OralCareColors.CardSurfaceGradient
    }

    val resolvedBorder = borderColor ?: when {
        isDark -> OralCareColors.DarkBorder
        highlighted -> Color(0xFF98CBF7)
        else -> Color(0xFFC0DEF8)
    }

    val baseModifier = modifier
        .shadow(
            elevation = if (isDark) 2.dp else 2.dp,
            shape = shape,
            ambientColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.08f),
            spotColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.06f),
        )
        .clip(shape)
        .background(bgBrush, shape)
        .border(
            width = 1.dp,
            color = resolvedBorder,
            shape = shape,
        )
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Column(
        modifier = baseModifier.padding(contentPadding),
        content = content,
    )
}

/**
 * Reusable GlassButton supporting full-bleed vibrant sky-blue primary gradient and crisp frosted-white secondary pill.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.97f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "glassButtonScale",
    )

    val shape = OralCareTokens.ButtonShape
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    val bgBrush = when {
        !enabled -> Brush.horizontalGradient(listOf(Color(0xFFE8F1F9), Color(0xFFE2EEF8)))
        isPrimary -> OralCareColors.PrimaryButtonGradient
        isDark -> Brush.verticalGradient(listOf(OralCareColors.DarkCard, OralCareColors.DarkSurface))
        else -> OralCareColors.SecondaryButtonGradient
    }

    val contentColor = when {
        !enabled -> OralCareColors.TextSecondary
        isPrimary -> Color.White
        isDark -> OralCareColors.DarkTextPrimary
        else -> OralCareColors.AccentBlueStrong
    }

    val borderStroke = when {
        !enabled -> BorderStroke(1.dp, Color(0xFFD2E4F4))
        isPrimary -> BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
        isDark -> BorderStroke(1.dp, OralCareColors.DarkBorder)
        else -> BorderStroke(1.2.dp, Color(0xFF90C5F2))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .heightIn(min = 46.dp)
            .then(
                if (isPrimary && enabled) {
                    Modifier.shadow(
                        elevation = 4.dp,
                        shape = shape,
                        ambientColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.25f),
                        spotColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.30f),
                    )
                } else if (enabled) {
                    Modifier.shadow(
                        elevation = 1.5.dp,
                        shape = shape,
                        ambientColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.08f),
                        spotColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.06f),
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(bgBrush, shape)
            .border(borderStroke, shape)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interactionSource,
                indication = ripple(color = if (isPrimary) Color.White else OralCareColors.AccentBlueStrong),
                onClick = onClick,
            )
            .padding(contentPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Frosted GlassTextField with soft pastel blue borders, clean accessible contrast,
 * and automatic BringIntoView auto-scroll whenever focused on forms and dialogs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (isPassword) ImeAction.Done else ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    isError: Boolean = false,
    errorMessage: String? = null,
    readOnly: Boolean = false,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (placeholder.isNotBlank()) { { Text(placeholder, color = OralCareColors.TextSecondary) } } else null,
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = OralCareColors.AccentBlueStrong) } },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = OralCareColors.TextSecondary,
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) imeAction else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (onImeAction != null) onImeAction()
                    else focusManager.moveFocus(FocusDirection.Down)
                },
                onDone = {
                    if (onImeAction != null) onImeAction()
                    else focusManager.clearFocus()
                },
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            readOnly = readOnly,
            shape = OralCareTokens.InputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OralCareColors.AccentBlueStrong,
                unfocusedBorderColor = if (isDark) OralCareColors.DarkBorder else Color(0xFFC5E0F7),
                focusedContainerColor = if (isDark) OralCareColors.DarkSurface else Color.White,
                unfocusedContainerColor = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.85f) else Color(0xFFFAFDFF),
                focusedLabelColor = OralCareColors.AccentBlueStrong,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            delay(220)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = OralCareColors.AbnormalRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp),
            )
        }
    }
}

/**
 * Frosted GlassDropdown using modern ExposedDropdownMenuAnchorType.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = OralCareTokens.InputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OralCareColors.AccentBlueStrong,
                unfocusedBorderColor = if (isDark) OralCareColors.DarkBorder else Color(0xFFC5E0F7),
                focusedContainerColor = if (isDark) OralCareColors.DarkSurface else Color.White,
                unfocusedContainerColor = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.85f) else Color(0xFFFAFDFF),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = if (isDark) OralCareColors.DarkSurface else Color.White.copy(alpha = 0.96f),
            shape = OralCareTokens.InputShape,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Pill-shaped frosted GlassSearchBar.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search by name, ID, or phone...",
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = OralCareColors.TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = OralCareColors.AccentBlueStrong) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = OralCareColors.TextSecondary)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OralCareColors.AccentBlueStrong,
            unfocusedBorderColor = if (isDark) OralCareColors.DarkBorder else Color(0xFFCDE4F8),
            focusedContainerColor = if (isDark) OralCareColors.DarkSurface else Color.White,
            unfocusedContainerColor = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.88f) else Color(0xFFFAFDFF),
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Reusable Top GlassHeader — respects native Android status bar via statusBarsPadding(),
 * NEVER draws a fake status bar.
 */
@Composable
fun GlassHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    avatarInitials: String? = null,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.08f),
                spotColor = OralCareColors.AccentBlueStrong.copy(alpha = 0.06f),
            )
            .clip(shape)
            .background(
                if (isDark) Brush.verticalGradient(listOf(OralCareColors.DarkSurface, OralCareColors.DarkCard))
                else OralCareColors.HeaderGradient,
                shape,
            )
            .border(BorderStroke(1.dp, if (isDark) OralCareColors.DarkBorder else Color(0xFFC0DEF8)), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton && onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = if (isDark) OralCareColors.DarkCard else OralCareColors.LightBlue.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OralCareColors.AccentBlueStrong,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            } else if (!avatarInitials.isNullOrBlank()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(OralCareColors.LightBlue, OralCareColors.PastelBlue))
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                ) {
                    Text(
                        text = avatarInitials.take(2).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.AccentBlueStrong,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }
    }
}

/**
 * Floating Capsule GlassBottomNavigation — respects Android system navigation bar insets,
 * adapts gracefully to 6 organizer tabs or 5 admin tabs on smaller phones without clipping.
 */
@Composable
fun GlassBottomNavigation(
    destinations: List<GlassNavDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { dest ->
                val selected = dest.route == selectedRoute
                val iconTint by animateColorAsState(
                    targetValue = if (selected) OralCareColors.AccentBlueStrong else OralCareColors.TextSecondary,
                    animationSpec = tween(220),
                    label = "navIconTint",
                )
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        if (isDark) OralCareColors.AccentBlue.copy(alpha = 0.18f) else OralCareColors.LightBlue.copy(alpha = 0.55f)
                    } else Color.Transparent,
                    animationSpec = tween(220),
                    label = "navPillColor",
                )
                val iconSize by animateDpAsState(
                    targetValue = if (selected) 22.dp else 20.dp,
                    animationSpec = tween(200),
                    label = "navIconSize",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(pillColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(dest.route) },
                        )
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        tint = iconTint,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dest.label,
                        color = iconTint,
                        fontSize = if (destinations.size >= 6) 9.5.sp else 10.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Translucent GlassStatCard with soft pastel glass icon orb.
 */
@Composable
fun GlassStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = OralCareColors.AccentBlueStrong,
    highlighted: Boolean = false,
    subtitle: String? = null,
) {
    GlassCard(
        modifier = modifier,
        highlighted = highlighted,
        contentPadding = PaddingValues(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Animated GlassProgressIndicator for the 11-step Patient Registration Wizard.
 */
@Composable
fun GlassProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "stepProgress",
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = OralCareTokens.BadgeShape,
                color = Color.White.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, OralCareColors.PastelBlue),
            ) {
                Text(
                    text = "Step $currentStep of $totalSteps",
                    style = MaterialTheme.typography.labelMedium,
                    color = OralCareColors.AccentBlueStrong,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(OralCareTokens.BadgeShape)
                .background(Color.White.copy(alpha = 0.72f))
                .border(0.8.dp, OralCareColors.PastelBlue, OralCareTokens.BadgeShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(OralCareTokens.BadgeShape)
                    .background(OralCareColors.PrimaryButtonGradient),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Subtle 11-dot step completion bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (s in 1..totalSteps) {
                val color = when {
                    s < currentStep -> OralCareColors.NormalGreen
                    s == currentStep -> OralCareColors.AccentBlueStrong
                    else -> OralCareColors.PastelBlue
                }
                Box(
                    modifier = Modifier
                        .size(width = if (s == currentStep) 18.dp else 7.dp, height = 7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

/**
 * Premium GlassImageCard with light-blue dashed border, Camera/Gallery actions, preview,
 * quality badge, and distinct visual states for AI Pending / Completed / Unavailable / Failed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassImageCard(
    regionLabel: String,
    subRegionLabel: String?,
    localImagePath: String?,
    aiStatusLabel: String? = null,
    aiState: String? = null, // PENDING, COMPLETED, MODEL_UNAVAILABLE, FAILED
    qualityStatus: String? = null,
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dashStroke = remember {
        Stroke(
            width = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OralCareTokens.CardShape)
            .background(OralCareColors.CardSurfaceGradient, OralCareTokens.CardShape)
            .border(1.dp, Color(0xFFC0DEF8), OralCareTokens.CardShape),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = OralCareColors.AccentBlue.copy(alpha = 0.75f),
                style = dashStroke,
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
            )
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(OralCareColors.LightBlue),
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(regionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (!subRegionLabel.isNullOrBlank()) {
                            Text("Sub-region: $subRegionLabel", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                        }
                    }
                }
                if (onRemove != null && localImagePath != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = OralCareColors.AbnormalRed)
                    }
                }
            }

            if (!localImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = localImagePath,
                    contentDescription = "Clinical image of $regionLabel",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(OralCareTokens.InputShape)
                        .border(1.dp, OralCareColors.PastelBlue, OralCareTokens.InputShape),
                )
            }

            if (!qualityStatus.isNullOrBlank() || !aiState.isNullOrBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!qualityStatus.isNullOrBlank()) {
                        val ok = qualityStatus.equals("ACCEPTABLE", ignoreCase = true)
                        StatusPill(
                            text = "Quality: $qualityStatus",
                            bg = if (ok) OralCareColors.NormalGreenBg else OralCareColors.WarningAmberBg,
                            fg = if (ok) OralCareColors.NormalGreen else OralCareColors.WarningAmber,
                        )
                    }
                    if (!aiState.isNullOrBlank()) {
                        val (bg, fg) = when (aiState.uppercase()) {
                            "COMPLETED" -> OralCareColors.NormalGreenBg to OralCareColors.NormalGreen
                            "FAILED" -> OralCareColors.AbnormalRedBg to OralCareColors.AbnormalRed
                            "PENDING" -> OralCareColors.LightBlue to OralCareColors.AccentBlueStrong
                            else -> OralCareColors.WarningAmberBg to OralCareColors.WarningAmber
                        }
                        StatusPill(text = "AI: ${aiState.replace("_", " ")}", bg = bg, fg = fg)
                    }
                }
            }

            if (!aiStatusLabel.isNullOrBlank()) {
                Text(aiStatusLabel, style = MaterialTheme.typography.bodySmall, color = OralCareColors.AccentBlueStrong)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GlassButton(
                    text = "Camera",
                    onClick = onCaptureCamera,
                    isPrimary = false,
                    icon = Icons.Default.PhotoCamera,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = "Gallery",
                    onClick = onPickGallery,
                    isPrimary = false,
                    icon = Icons.Default.PhotoLibrary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun StatusPill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(shape = OralCareTokens.BadgeShape, color = bg, modifier = modifier) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Frosted GlassDialog with soft white-blue translucent container.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = OralCareTokens.DialogShape,
        containerColor = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.94f),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

/**
 * Reusable Frosted GlassBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == OralCareColors.DarkBackground
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isDark) OralCareColors.DarkSurface.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.95f),
        content = content,
    )
}
