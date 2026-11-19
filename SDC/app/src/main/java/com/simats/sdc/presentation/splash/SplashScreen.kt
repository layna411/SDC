package com.simats.sdc.presentation.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simats.sdc.R
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.presentation.components.GlassBackground
import kotlinx.coroutines.delay

@Composable
fun OralCareSplashScreen(
    onSplashFinished: () -> Unit,
) {
    var startAnim by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.65f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "splashLogoScale",
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splashLogoAlpha",
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 320, easing = FastOutSlowInEasing),
        label = "splashTextAlpha",
    )

    val textOffsetY by animateDpAsState(
        targetValue = if (startAnim) 0.dp else 22.dp,
        animationSpec = tween(durationMillis = 700, delayMillis = 320, easing = FastOutSlowInEasing),
        label = "splashTextOffset",
    )

    val progressFraction by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.05f,
        animationSpec = tween(durationMillis = 1950, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "splashProgress",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "splashHalo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(2200)
        onSplashFinished()
    }

    GlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Animated Soft Halo + Transparent Saveetha Dental College & Hospital Logo (no white overlay)
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(228.dp)
                            .scale(haloScale)
                            .alpha(haloAlpha)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(OralCareColors.PastelBlue, Color.Transparent)
                                )
                            ),
                    )

                    Image(
                        painter = painterResource(id = R.drawable.oralcare_logo),
                        contentDescription = "OralCare Saveetha Dental College Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(188.dp)
                            .scale(logoScale)
                            .alpha(logoAlpha),
                    )
                }

                Spacer(Modifier.height(26.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(y = textOffsetY)
                        .alpha(textAlpha),
                ) {
                    Text(
                        text = "ORALCARE",
                        style = MaterialTheme.typography.headlineLarge,
                        color = OralCareColors.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Saveetha Dental College & Hospital",
                        style = MaterialTheme.typography.titleMedium,
                        color = OralCareColors.AccentBlueStrong,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Oral Health Screening & Clinical Management",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Smooth Animated Bottom Loading Progress Pill
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.62f)
                    .padding(bottom = 18.dp)
                    .alpha(textAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(OralCareTokens.BadgeShape)
                        .background(Color.White.copy(alpha = 0.72f))
                        .border(0.8.dp, OralCareColors.PastelBlue, OralCareTokens.BadgeShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(OralCareTokens.BadgeShape)
                            .background(OralCareColors.PrimaryButtonGradient),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Preparing clinical workspace...",
                    style = MaterialTheme.typography.labelSmall,
                    color = OralCareColors.TextSecondary,
                )
            }
        }
    }
}
