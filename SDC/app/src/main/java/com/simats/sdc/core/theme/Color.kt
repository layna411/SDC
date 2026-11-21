package com.simats.sdc.core.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Saveetha Dental College & Hospital (SDC) Brand Color System
 * Extracted directly from official SDC Emblem:
 * - Saveetha Forest Green / Jade (Outer Triangle): #226E56 (Dark accessible: #1B5E4A)
 * - Saveetha Clinical Teal (Inner Triangle): #38A398 / #48ACA3
 * - Saveetha Gold (Emblem Border & Sunburst): #E59838 / #EB9C43
 * - Caduceus Orange (Emblem Staff): #C1501F / #BA380B
 * - Clinical Sage & Frosted Glass Surfaces
 */
object OralCareColors {
    // Official SDC Brand Colors
    val BrandGreen = Color(0xFF226E56)
    val BrandGreenDark = Color(0xFF1B5E4A)
    val BrandTeal = Color(0xFF38A398)
    val BrandTealLight = Color(0xFF48ACA3)
    val BrandGold = Color(0xFFE59838)
    val BrandGoldLight = Color(0xFFF0B364)
    val BrandCaduceusOrange = Color(0xFFC1501F)

    // Core Light Sage Clinical Glassmorphism Palette
    val PrimaryBackground = Color(0xFFF4F9F6)
    val SecondaryBackground = Color(0xFFEAF4F0)
    val LightSage = Color(0xFFD7EFE6)
    val PastelSage = Color(0xFFC0E5D8)
    val SoftSage = Color(0xFF9ED5C3)
    val AccentTeal = BrandTeal
    val AccentTealStrong = BrandGreenDark // Crisp, accessible clinical forest green for icons/buttons/active text (WCAG AAA)

    // Direct aliases for complete compatibility with existing codebase
    val AccentBlueStrong = AccentTealStrong
    val AccentBlue = AccentTeal
    val LightBlue = LightSage
    val PastelBlue = PastelSage
    val SoftBlue = SoftSage
    val PrimaryBlue = AccentBlueStrong
    val SecondaryBlue = AccentTeal
    val BorderLight = PastelSage
    val DividerLight = LightSage

    // Additional theme accent tokens
    val GoldAccent = BrandGold
    val OrangeAccent = BrandCaduceusOrange

    // Typography Colors
    val TextPrimary = Color(0xFF1A3830)   // Deep forest slate, high contrast readability
    val TextSecondary = Color(0xFF53756A) // Balanced sage slate
    val White = Color(0xFFFFFFFF)
    val DarkNavy = TextPrimary

    // Translucent Glass Surfaces
    val GlassSurface = Color(0xCCFFFFFF)          // 80% frosted white
    val GlassSurfaceStrong = Color(0xE8FFFFFF)    // 91% frosted white for forms/dialogs
    val GlassSurfaceTinted = Color(0xBDD7EFE6)    // 74% soft mint/sage glass
    val GlassBorderWhite = Color(0xE6FFFFFF)      // Delicate white glass edge
    val GlassBorderSage = Color(0x999ED5C3)       // Soft sage glass border
    val GlassBorderBlue = GlassBorderSage         // Backward compatibility
    val GlassNavSurface = Color(0xDEFFFFFF)       // Floating bottom nav frosted glass
    val Background = PrimaryBackground
    val CardBackground = GlassSurface

    // Clinical Semantic Status Colors (Pastel Glass Fill + Accessible Dark Text)
    val NormalGreenBg = Color(0xFFDDF5E7)
    val NormalGreen = Color(0xFF166534)
    val AbnormalRedBg = Color(0xFFFFE4E4)
    val AbnormalRed = Color(0xFF991B1B)
    val WarningAmberBg = Color(0xFFFFF2D6)
    val WarningAmber = Color(0xFF92400E)

    // Dark Theme Palette (Rich Forest Glass)
    val DarkBackground = Color(0xFF0E231C)
    val DarkSurface = Color(0xFF142F27)
    val DarkCard = Color(0xCC1A3B31)
    val DarkPrimary = Color(0xFF58C2A5)
    val DarkSecondary = Color(0xFF76D3C7)
    val DarkTextPrimary = Color(0xFFF0F8F5)
    val DarkTextSecondary = Color(0xFFA3C6BC)
    val DarkBorder = Color(0x6658C2A5)

    // Soft Pastel Gradients
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(PrimaryBackground, SecondaryBackground, PrimaryBackground)
    )

    // Unified Light Sage Glassmorphism Card Gradient:
    // Translucent frosted glass with soft white sheen at top blending into delicate sage tint
    val CardSurfaceGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xF4FFFFFF), // Frosted white reflection at top
            Color(0xDFE6F4EF), // Delicate sage glass tint at bottom
        )
    )

    // Highlight Card Gradient (for hero CTA banners)
    val HighlightCardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xF6FFFFFF),
            Color(0xD8DFEDE7),
        )
    )

    // Signature Saveetha Primary Button Gradient (Forest Green to Teal)
    val PrimaryButtonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E604B), Color(0xFF32998C))
    )

    // Gold Accent Button Gradient
    val GoldButtonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFD88927), Color(0xFFEDB05B))
    )

    val SecondaryButtonGradient = Brush.verticalGradient(
        colors = listOf(Color(0xF8FFFFFF), Color(0xEDE2F2EC))
    )

    val HeaderGradient = Brush.verticalGradient(
        colors = listOf(Color(0xF6FFFFFF), Color(0xDFEAF4F0))
    )
}
