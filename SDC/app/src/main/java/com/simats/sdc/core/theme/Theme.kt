package com.simats.sdc.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OralCareLightColorScheme = lightColorScheme(
    primary = OralCareColors.AccentBlueStrong,
    onPrimary = Color.White,
    primaryContainer = OralCareColors.LightSage,
    onPrimaryContainer = OralCareColors.TextPrimary,
    secondary = OralCareColors.AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = OralCareColors.PastelSage,
    onSecondaryContainer = OralCareColors.TextPrimary,
    tertiary = OralCareColors.GoldAccent,
    onTertiary = Color.White,
    background = OralCareColors.PrimaryBackground,
    onBackground = OralCareColors.TextPrimary,
    surface = OralCareColors.White,
    onSurface = OralCareColors.TextPrimary,
    surfaceVariant = OralCareColors.SecondaryBackground,
    onSurfaceVariant = OralCareColors.TextSecondary,
    outline = OralCareColors.PastelSage,
    outlineVariant = OralCareColors.LightSage,
    error = OralCareColors.AbnormalRed,
    onError = Color.White,
    errorContainer = OralCareColors.AbnormalRedBg,
    onErrorContainer = OralCareColors.AbnormalRed,
)

private val OralCareDarkColorScheme = darkColorScheme(
    primary = OralCareColors.DarkPrimary,
    onPrimary = Color(0xFF0A1F18),
    primaryContainer = Color(0xFF1B4D3F),
    onPrimaryContainer = OralCareColors.LightSage,
    secondary = OralCareColors.DarkSecondary,
    onSecondary = Color(0xFF0A1F18),
    secondaryContainer = Color(0xFF174438),
    onSecondaryContainer = OralCareColors.LightSage,
    tertiary = OralCareColors.GoldAccent,
    onTertiary = Color.White,
    background = OralCareColors.DarkBackground,
    onBackground = OralCareColors.DarkTextPrimary,
    surface = OralCareColors.DarkSurface,
    onSurface = OralCareColors.DarkTextPrimary,
    surfaceVariant = Color(0xFF1B382F),
    onSurfaceVariant = OralCareColors.DarkTextSecondary,
    outline = OralCareColors.DarkBorder,
    outlineVariant = Color(0xFF224A3E),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFE4E4),
)

private val OralCareShapes = Shapes(
    small = OralCareTokens.InputShape,
    medium = OralCareTokens.CardShape,
    large = OralCareTokens.DialogShape,
)

@Composable
fun OralCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) OralCareDarkColorScheme else OralCareLightColorScheme
    val view = LocalView.current

    // Synchronize real native Android status bar & navigation bar icon contrast
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OralCareTypography,
        shapes = OralCareShapes,
        content = content,
    )
}
