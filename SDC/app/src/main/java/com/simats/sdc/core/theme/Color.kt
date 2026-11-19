package com.simats.sdc.core.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object OralCareColors {
    // Core Light Blue Glassmorphism Palette
    val PrimaryBackground = Color(0xFFF4F9FF)
    val SecondaryBackground = Color(0xFFEAF4FD)
    val LightBlue = Color(0xFFDDEEFF)
    val PastelBlue = Color(0xFFC9E4FA)
    val SoftBlue = Color(0xFFB6D9F5)
    val AccentBlue = Color(0xFF5CA8E4)
    val AccentBlueStrong = Color(0xFF2B7FC3) // Crisp, accessible clinical sky-blue for icons/buttons/active text

    // Typography Colors
    val TextPrimary = Color(0xFF334B63)
    val TextSecondary = Color(0xFF8496A8)
    val White = Color(0xFFFFFFFF)

    // Translucent Glass Surfaces
    val GlassSurface = Color(0xCCFFFFFF)          // 80% frosted white
    val GlassSurfaceStrong = Color(0xE8FFFFFF)    // 91% frosted white for forms/dialogs
    val GlassSurfaceTinted = Color(0xBDDDEEFF)    // 74% pastel blue glass
    val GlassBorderWhite = Color(0xE6FFFFFF)      // Delicate white glass edge
    val GlassBorderBlue = Color(0x99B6D9F5)       // Soft light-blue glass border
    val GlassNavSurface = Color(0xDEFFFFFF)       // Floating bottom nav frosted glass

    // Clinical Semantic Status Colors (Pastel Glass Fill + Accessible Dark Text)
    val NormalGreenBg = Color(0xFFDDF5E5)
    val NormalGreen = Color(0xFF166534)
    val AbnormalRedBg = Color(0xFFFFE4E4)
    val AbnormalRed = Color(0xFF991B1B)
    val WarningAmberBg = Color(0xFFFFF2D6)
    val WarningAmber = Color(0xFF92400E)

    // Legacy aliases mapped cleanly to the new light-blue glass palette
    val PrimaryBlue = AccentBlueStrong
    val SecondaryBlue = AccentBlue
    val DarkNavy = TextPrimary
    val Background = PrimaryBackground
    val CardBackground = GlassSurface
    val BorderLight = PastelBlue
    val DividerLight = LightBlue

    // Subtle Dark Theme Glass Palette (when user toggles Dark Mode)
    val DarkBackground = Color(0xFF0E1B2B)
    val DarkSurface = Color(0xFF15273D)
    val DarkCard = Color(0xCC1B314C)
    val DarkPrimary = Color(0xFF76B7E5)
    val DarkSecondary = Color(0xFFB6D9F5)
    val DarkTextPrimary = Color(0xFFF0F6FC)
    val DarkTextSecondary = Color(0xFF9FB4C9)
    val DarkBorder = Color(0x6676B7E5)

    // Soft Pastel Gradients (never heavy or dark)
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(PrimaryBackground, SecondaryBackground, PrimaryBackground)
    )

    val HighlightCardGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFEAF5FF), Color(0xFFDAEEFF), Color(0xFFF3F9FF))
    )

    val CardSurfaceGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF6FAFF))
    )

    val PrimaryButtonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF2E85CC), Color(0xFF56A9E6))
    )

    val SecondaryButtonGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFEEF6FF))
    )

    val HeaderGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9FCFF), Color(0xFFF2F8FF))
    )
}
