package com.simats.sdc.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simats.sdc.R
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.presentation.components.*

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    currentBaseUrl: String,
    onLogin: (String, String) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Saveetha Dental College Logo & Brand Title (clean transparent emblem, no white overlay)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.oralcare_logo),
                    contentDescription = "OralCare Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "ORALCARE",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OralCareColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Oral Health Screening & Clinical Management",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OralCareColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // Frosted Glass Sign-In Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Enter your credentials to access your OralCare workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!uiState.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = OralCareColors.AbnormalRedBg,
                            shape = OralCareTokens.InputShape,
                            border = BorderStroke(1.dp, OralCareColors.AbnormalRed.copy(alpha = 0.25f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = OralCareColors.AbnormalRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(uiState.errorMessage, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (!uiState.infoMessage.isNullOrBlank()) {
                        Surface(
                            color = OralCareColors.NormalGreenBg,
                            shape = OralCareTokens.InputShape,
                        ) {
                            Text(
                                text = uiState.infoMessage,
                                color = OralCareColors.NormalGreen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }

                    GlassTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username or ID",
                        leadingIcon = Icons.Default.Person,
                    )

                    GlassTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onImeAction = { onLogin(username, password) },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onForgotPasswordClick) {
                            Text("Forgot Password?", color = OralCareColors.AccentBlueStrong)
                        }
                    }

                    GlassButton(
                        text = "Sign In",
                        onClick = { onLogin(username, password) },
                        loading = uiState.isLoading,
                        icon = Icons.AutoMirrored.Filled.Login,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ChangePasswordScreen(
    isForced: Boolean,
    uiState: AuthUiState,
    onChangePassword: (String, String, String) -> Unit,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) {
    var currentPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            GlassHeader(
                title = if (isForced) "Update Temporary Password" else "Change Password",
                subtitle = "Keep your clinical account secure",
                showBackButton = !isForced && onBack != null,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isForced) {
                    GlassCard(
                        containerColor = OralCareColors.WarningAmberBg,
                        borderColor = OralCareColors.WarningAmber.copy(alpha = 0.25f),
                    ) {
                        Text(
                            "For clinical data security, please replace your administrator-issued temporary password before continuing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OralCareColors.WarningAmber,
                        )
                    }
                }

                GlassCard(contentPadding = PaddingValues(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Text(uiState.errorMessage, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
                        }
                        GlassTextField(currentPwd, { currentPwd = it }, "Current Password", isPassword = true)
                        GlassTextField(newPwd, { newPwd = it }, "New Password (min 8 chars)", isPassword = true)
                        GlassTextField(confirmPwd, { confirmPwd = it }, "Confirm New Password", isPassword = true)
                        GlassButton(
                            text = "Update Password",
                            onClick = { onChangePassword(currentPwd, newPwd, confirmPwd) },
                            loading = uiState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (isForced && onLogout != null) {
                            GlassButton(text = "Sign Out", isPrimary = false, onClick = onLogout, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    uiState: AuthUiState,
    onRequestToken: (String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var identifier by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    LaunchedEffect(uiState.devResetToken) {
        if (!uiState.devResetToken.isNullOrBlank()) {
            resetToken = uiState.devResetToken
        }
    }

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            GlassHeader(
                title = "Password Recovery",
                subtitle = "Request and apply a secure reset token",
                showBackButton = true,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Step 1: Request Reset Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        GlassTextField(identifier, { identifier = it }, "Username or Email")
                        GlassButton(
                            text = "Request Reset Token",
                            onClick = { onRequestToken(identifier) },
                            loading = uiState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!uiState.infoMessage.isNullOrBlank()) {
                            Surface(color = OralCareColors.NormalGreenBg, shape = OralCareTokens.InputShape) {
                                Text(uiState.infoMessage, color = OralCareColors.NormalGreen, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Text(uiState.errorMessage, color = OralCareColors.AbnormalRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Step 2: Apply Reset Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        GlassTextField(resetToken, { resetToken = it }, "Reset Token")
                        GlassTextField(newPassword, { newPassword = it }, "New Password", isPassword = true)
                        GlassButton(
                            text = "Reset Password",
                            onClick = { onResetPassword(resetToken, newPassword) },
                            loading = uiState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
