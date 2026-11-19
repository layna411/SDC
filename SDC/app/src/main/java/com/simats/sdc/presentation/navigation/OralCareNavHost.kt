package com.simats.sdc.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.simats.sdc.core.datastore.UserSession
import com.simats.sdc.presentation.activity.ActivityScreen
import com.simats.sdc.presentation.activity.ActivityViewModel
import com.simats.sdc.presentation.auth.*
import com.simats.sdc.presentation.components.GlassBackground
import com.simats.sdc.presentation.components.GlassBottomNavigation
import com.simats.sdc.presentation.components.GlassNavDestination
import com.simats.sdc.presentation.dashboard.*
import com.simats.sdc.presentation.education.EducationScreen
import com.simats.sdc.presentation.education.EducationViewModel
import com.simats.sdc.presentation.feedback.FeedbackScreen
import com.simats.sdc.presentation.feedback.FeedbackViewModel
import com.simats.sdc.presentation.patient.PatientsScreen
import com.simats.sdc.presentation.patient.PatientsViewModel
import com.simats.sdc.presentation.reports.ReportsScreen
import com.simats.sdc.presentation.reports.ReportsViewModel
import com.simats.sdc.presentation.screening.ScreeningWizardScreen
import com.simats.sdc.presentation.screening.ScreeningWizardViewModel
import com.simats.sdc.presentation.splash.OralCareSplashScreen

@Composable
fun OralCareAppRoot(authViewModel: AuthViewModel = hiltViewModel()) {
    val session by authViewModel.session.collectAsState()
    val authUiState by authViewModel.uiState.collectAsState()
    val isOnline by authViewModel.isOnline.collectAsState()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showForgotPassword by remember { mutableStateOf(false) }

    if (showSplash) {
        OralCareSplashScreen(onSplashFinished = { showSplash = false })
        return
    }

    if (!session.isAuthenticated) {
        if (showForgotPassword) {
            BackHandler {
                authViewModel.clearMessages()
                showForgotPassword = false
            }
            ForgotPasswordScreen(
                uiState = authUiState,
                onRequestToken = authViewModel::forgotPassword,
                onResetPassword = { tok, pwd ->
                    authViewModel.resetPassword(tok, pwd) { showForgotPassword = false }
                },
                onBack = {
                    authViewModel.clearMessages()
                    showForgotPassword = false
                },
            )
        } else {
            LoginScreen(
                uiState = authUiState,
                currentBaseUrl = session.baseUrl,
                onLogin = authViewModel::login,
                onForgotPasswordClick = {
                    authViewModel.clearMessages()
                    showForgotPassword = true
                },
                onSaveServerUrl = authViewModel::updateServerUrl,
            )
        }
        return
    }

    if (session.mustChangePassword) {
        ChangePasswordScreen(
            isForced = true,
            uiState = authUiState,
            onChangePassword = { cur, newPwd, conf ->
                authViewModel.changePassword(cur, newPwd, conf) {}
            },
            onLogout = authViewModel::logout,
        )
        return
    }

    AuthenticatedRoleShell(
        session = session,
        isOnline = isOnline,
        authUiState = authUiState,
        onToggleDarkMode = { authViewModel.toggleDarkMode(!session.darkMode) },
        onSaveServerUrl = authViewModel::updateServerUrl,
        onChangePassword = authViewModel::changePassword,
        onLogout = authViewModel::logout,
    )
}

@Composable
private fun AuthenticatedRoleShell(
    session: UserSession,
    isOnline: Boolean,
    authUiState: AuthUiState,
    onToggleDarkMode: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onChangePassword: (String, String, String, () -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    val role = session.role

    // Exact Role-Based Bottom Navigation Structures
    val tabs = remember(role) {
        when (role) {
            "SUPER_ADMIN" -> listOf(
                GlassNavDestination("home", "Home", Icons.Default.Home),
                GlassNavDestination("colleges", "Colleges", Icons.Default.AccountBalance),
                GlassNavDestination("centers", "Centers", Icons.Default.LocalHospital),
                GlassNavDestination("users", "Users", Icons.Default.Group),
                GlassNavDestination("reports", "Reports", Icons.Default.BarChart),
            )
            "COLLEGE_ADMIN" -> listOf(
                GlassNavDestination("home", "Home", Icons.Default.Home),
                GlassNavDestination("centers", "Centers", Icons.Default.LocalHospital),
                GlassNavDestination("organizers", "Organizers", Icons.Default.Badge),
                GlassNavDestination("patients", "Patients", Icons.Default.People),
                GlassNavDestination("reports", "Reports", Icons.Default.BarChart),
            )
            "CENTER_ADMIN" -> listOf(
                GlassNavDestination("home", "Home", Icons.Default.Home),
                GlassNavDestination("organizers", "Organizers", Icons.Default.Badge),
                GlassNavDestination("patients", "Patients", Icons.Default.People),
                GlassNavDestination("screenings", "Screenings", Icons.Default.MedicalServices),
                GlassNavDestination("reports", "Reports", Icons.Default.BarChart),
            )
            else -> listOf(
                // Health Organizer Assistant (6 tabs)
                GlassNavDestination("home", "Home", Icons.Default.Home),
                GlassNavDestination("create", "Create", Icons.Default.AddCircle),
                GlassNavDestination("patients", "Patients", Icons.Default.People),
                GlassNavDestination("feedback", "Feedback", Icons.Default.RateReview),
                GlassNavDestination("reports", "Reports", Icons.Default.BarChart),
                GlassNavDestination("activity", "Activity", Icons.Default.History),
            )
        }
    }

    var selectedTab by remember(role) { mutableStateOf("home") }
    var overlayRoute by remember { mutableStateOf<String?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val screeningViewModel: ScreeningWizardViewModel = hiltViewModel()
    val patientsViewModel: PatientsViewModel = hiltViewModel()
    val feedbackViewModel: FeedbackViewModel = hiltViewModel()
    val reportsViewModel: ReportsViewModel = hiltViewModel()
    val activityViewModel: ActivityViewModel = hiltViewModel()
    val educationViewModel: EducationViewModel = hiltViewModel()

    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val savedDrafts by screeningViewModel.savedDrafts.collectAsState()

    LaunchedEffect(session.userId, role) {
        activityViewModel.reset()
        reportsViewModel.reset()
        dashboardViewModel.loadDashboardForRole(role)
    }

    BackHandler(enabled = overlayRoute != null || selectedTab != "home") {
        if (overlayRoute != null) {
            overlayRoute = null
        } else if (selectedTab != "home") {
            selectedTab = "home"
        }
    }

    when (overlayRoute) {
        "profile" -> {
            ProfileSettingsScreen(
                session = session,
                isOnline = isOnline,
                appSettings = dashboardState.settings,
                onBack = { overlayRoute = null },
                onToggleDarkMode = onToggleDarkMode,
                onChangePassword = { overlayRoute = "change_password" },
                onSaveServerUrl = onSaveServerUrl,
                onSaveAppSetting = dashboardViewModel::saveSetting,
                onOpenEducation = { overlayRoute = "education" },
                onOpenActivity = { overlayRoute = "activity_overlay" },
                onOpenFeedback = { overlayRoute = "feedback_overlay" },
                onLogout = onLogout,
            )
            return
        }
        "education" -> {
            EducationScreen(
                viewModel = educationViewModel,
                canManage = role == "SUPER_ADMIN" || role == "COLLEGE_ADMIN",
                onBack = { overlayRoute = null },
            )
            return
        }
        "activity_overlay" -> {
            GlassBackground {
                Box(modifier = Modifier.fillMaxSize()) {
                    ActivityScreen(viewModel = activityViewModel, showBackButton = true, onBack = { overlayRoute = null })
                }
            }
            return
        }
        "feedback_overlay" -> {
            GlassBackground {
                Box(modifier = Modifier.fillMaxSize()) {
                    FeedbackScreen(viewModel = feedbackViewModel, showBackButton = true, onBack = { overlayRoute = null })
                }
            }
            return
        }
        "change_password" -> {
            ChangePasswordScreen(
                isForced = false,
                uiState = authUiState,
                onChangePassword = { cur, newPwd, conf ->
                    onChangePassword(cur, newPwd, conf) { overlayRoute = null }
                },
                onBack = { overlayRoute = null },
            )
            return
        }
    }

    val hideBottomNav = selectedTab == "create"

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!hideBottomNav) {
                    GlassBottomNavigation(
                        destinations = tabs,
                        selectedRoute = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (!hideBottomNav) innerPadding.calculateBottomPadding() else 0.dp),
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(220),
                    label = "tabTransition",
                ) { activeTab ->
                    when (activeTab) {
                        "home" -> {
                            if (role == "HEALTH_ORGANIZER") {
                                HealthOrganizerHomeScreen(
                                    uiState = dashboardState,
                                    isOnline = isOnline,
                                    pendingDraftCount = savedDrafts.size,
                                    onRefresh = { dashboardViewModel.loadDashboardForRole(role) },
                                    onStartScreening = { selectedTab = "create" },
                                    onOpenEducation = { overlayRoute = "education" },
                                    onOpenProfile = { overlayRoute = "profile" },
                                    onOpenNotifications = { showNotificationsSheet = true },
                                )
                            } else {
                                AdminRoleDashboardScreen(
                                    role = role,
                                    uiState = dashboardState,
                                    isOnline = isOnline,
                                    onRefresh = { dashboardViewModel.loadDashboardForRole(role) },
                                    onNavigateTab = { selectedTab = it },
                                    onOpenEducation = { overlayRoute = "education" },
                                    onOpenActivity = { overlayRoute = "activity_overlay" },
                                    onOpenFeedback = { overlayRoute = "feedback_overlay" },
                                    onOpenProfile = { overlayRoute = "profile" },
                                    onOpenNotifications = { showNotificationsSheet = true },
                                )
                            }
                        }
                        "colleges" -> CollegesManagementScreen(
                            uiState = dashboardState,
                            onRefresh = { dashboardViewModel.loadDashboardForRole(role) },
                            onCreateCollege = { dashboardViewModel.createCollege(it, role) },
                            onToggleCollege = { id, active -> dashboardViewModel.toggleCollege(id, active, role) },
                            onDismissBanner = dashboardViewModel::clearCredentialBanner,
                            onOpenProfile = { overlayRoute = "profile" },
                            onOpenNotifications = { showNotificationsSheet = true },
                        )
                        "centers" -> CentersManagementScreen(
                            role = role,
                            uiState = dashboardState,
                            onCreateCenter = { dashboardViewModel.createCenter(it, role) },
                            onToggleCenter = { id, active -> dashboardViewModel.toggleCenter(id, active, role) },
                            onDismissBanner = dashboardViewModel::clearCredentialBanner,
                            onOpenProfile = { overlayRoute = "profile" },
                            onOpenNotifications = { showNotificationsSheet = true },
                        )
                        "organizers" -> OrganizersManagementScreen(
                            role = role,
                            uiState = dashboardState,
                            onCreateOrganizer = { dashboardViewModel.createOrganizer(it, role) },
                            onToggleOrganizer = { id, active -> dashboardViewModel.toggleOrganizer(id, active, role) },
                            onDismissBanner = dashboardViewModel::clearCredentialBanner,
                            onOpenProfile = { overlayRoute = "profile" },
                            onOpenNotifications = { showNotificationsSheet = true },
                        )
                        "users" -> UsersDirectoryScreen(
                            uiState = dashboardState,
                            currentUserId = session.userId,
                            onCreateOrganizer = { dashboardViewModel.createOrganizer(it, role) },
                            onToggleUserStatus = { id, active -> dashboardViewModel.toggleUserStatus(id, active, role) },
                            onDismissBanner = dashboardViewModel::clearCredentialBanner,
                            onOpenProfile = { overlayRoute = "profile" },
                            onOpenNotifications = { showNotificationsSheet = true },
                        )
                        "screenings" -> ScreeningsMonitorScreen(
                            uiState = dashboardState,
                            onRefresh = { dashboardViewModel.loadDashboardForRole(role) },
                            onOpenProfile = { overlayRoute = "profile" },
                            onOpenNotifications = { showNotificationsSheet = true },
                        )
                        "create" -> ScreeningWizardScreen(
                            viewModel = screeningViewModel,
                            onBack = { selectedTab = "home" },
                            onCompletedNavigatePatients = {
                                dashboardViewModel.loadDashboardForRole(role)
                                selectedTab = "patients"
                            },
                        )
                        "patients" -> PatientsScreen(viewModel = patientsViewModel)
                        "feedback" -> FeedbackScreen(viewModel = feedbackViewModel)
                        "reports" -> ReportsScreen(viewModel = reportsViewModel)
                        "activity" -> ActivityScreen(viewModel = activityViewModel)
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            activities = dashboardState.stats?.recentActivities ?: emptyList(),
            pendingReviews = dashboardState.stats?.pendingReviewScreenings ?: 0,
            onDismiss = { showNotificationsSheet = false },
        )
    }
}
