package com.simats.sdc.presentation.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.core.theme.OralCareTokens
import com.simats.sdc.data.dto.*
import com.simats.sdc.presentation.components.*

@Composable
fun HealthOrganizerHomeScreen(
    uiState: DashboardUiState,
    isOnline: Boolean,
    pendingDraftCount: Int,
    onRefresh: () -> Unit,
    onStartScreening: () -> Unit,
    onOpenEducation: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val context = LocalContext.current

    if (uiState.isLoading && uiState.stats == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassHeader(title = "OralCare", subtitle = "Health Organizer Assistant")
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                LoadingView("Loading your clinical dashboard...")
            }
        }
        return
    }
    if (uiState.error != null && uiState.stats == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassHeader(title = "OralCare", subtitle = "Health Organizer Assistant")
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                ErrorStateView(uiState.error, onRetry = onRefresh)
            }
        }
        return
    }

    val stats = uiState.stats ?: DashboardStatsDto("HEALTH_ORGANIZER", "Health Organizer", "Center")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardTopHeader(
                userName = stats.userFullName,
                roleBadge = "Health Organizer",
                orgLabel = stats.organizationLabel,
                isOnline = isOnline,
                pendingDrafts = pendingDraftCount,
                onRefresh = onRefresh,
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = onOpenProfile,
            )
        }

        // Primary Highlight Card CTA
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.88f))
                            .border(1.dp, OralCareColors.PastelBlue, CircleShape),
                    ) {
                        Icon(Icons.Default.MedicalServices, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("11-Step Oral Health Screening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OralCareColors.TextPrimary)
                        Text("Register a patient and complete oral screening", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlassButton(
                    text = "Start New Screening",
                    onClick = onStartScreening,
                    icon = Icons.Default.AddCircleOutline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Quick Statistics
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "Quick Statistics", subtitle = "Your assigned screening overview")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassStatCard("Total Patients", stats.totalPatients.toString(), Icons.Default.People, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                    GlassStatCard("Screenings", stats.totalScreenings.toString(), Icons.Default.MedicalServices, Modifier.weight(1f), OralCareColors.NormalGreen)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassStatCard("Male Patients", stats.malePatients.toString(), Icons.Default.Male, Modifier.weight(1f), OralCareColors.AccentBlue)
                    GlassStatCard("Female Patients", stats.femalePatients.toString(), Icons.Default.Female, Modifier.weight(1f), Color(0xFF4A9AD4))
                }
                Spacer(Modifier.height(12.dp))
                GlassStatCard(
                    title = "Total Feedback",
                    value = stats.totalFeedback.toString(),
                    icon = Icons.Default.Star,
                    accentColor = OralCareColors.WarningAmber,
                    subtitle = "Average satisfaction: ${stats.averageFeedbackRating} / 5.0",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Weekly Screening Chart
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                WeeklyScreeningChart(points = stats.weeklyScreenings)
            }
        }

        // Monthly Overview
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "Monthly Overview", subtitle = "Current month indicators")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassStatCard("New Patients", stats.monthlyOverview.newPatients.toString(), Icons.Default.PersonAdd, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                    GlassStatCard("Lesions Detected", stats.monthlyOverview.lesionsDetected.toString(), Icons.Default.Warning, Modifier.weight(1f), OralCareColors.AbnormalRed)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassStatCard("Reports Generated", stats.monthlyOverview.reportsGenerated.toString(), Icons.Default.Description, Modifier.weight(1f), OralCareColors.AccentBlue)
                    GlassStatCard("Pending Reviews", stats.monthlyOverview.pendingReviews.toString(), Icons.Default.PendingActions, Modifier.weight(1f), OralCareColors.WarningAmber)
                }
            }
        }

        // Educational Resources (English & Tamil Manuals)
        item {
            val manuals = uiState.resources.filter { it.resourceType != "YOUTUBE_VIDEO" }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "Educational Resources",
                    subtitle = "Downloadable English & Tamil manuals",
                    actionLabel = "View All",
                    onActionClick = onOpenEducation,
                )
                Spacer(Modifier.height(6.dp))
                if (manuals.isEmpty()) {
                    EmptyStateView("No manuals available", "English and Tamil usage manuals will appear here.", Icons.AutoMirrored.Filled.MenuBook)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        manuals.take(3).forEach { manual ->
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(14.dp),
                                onClick = {
                                    manual.externalUrl?.let { url ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(OralCareColors.LightBlue),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(manual.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("${manual.language} • ${manual.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Download, contentDescription = "Open manual", tint = OralCareColors.AccentBlueStrong)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Oral Health Aids (YouTube Videos)
        item {
            val videos = uiState.resources.filter { it.resourceType == "YOUTUBE_VIDEO" }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "Oral Health Aids",
                    subtitle = "YouTube educational videos",
                    actionLabel = "Explore",
                    onActionClick = onOpenEducation,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(videos) { vid ->
                        GlassCard(
                            modifier = Modifier.width(240.dp),
                            contentPadding = PaddingValues(14.dp),
                            onClick = {
                                vid.externalUrl?.let { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(86.dp)
                                    .clip(OralCareTokens.InputShape)
                                    .background(OralCareColors.HighlightCardGradient)
                                    .border(1.dp, Color.White, OralCareTokens.InputShape),
                            ) {
                                Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play video", tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(44.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(vid.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Spacer(Modifier.height(2.dp))
                            Text(vid.language, style = MaterialTheme.typography.labelSmall, color = OralCareColors.AccentBlueStrong)
                        }
                    }
                }
            }
        }

        // Awareness & Education
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "Awareness & Education",
                    subtitle = "Oral health prevention guides",
                    actionLabel = "All Articles",
                    onActionClick = onOpenEducation,
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.awareness.take(3).forEach { article ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(article.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                StatusPill(article.language, OralCareColors.LightBlue, OralCareColors.AccentBlueStrong)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(article.summary ?: article.body.take(120), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Recent Activity Timeline
        item {
            RecentActivitySection(stats.recentActivities)
        }
    }
}

/**
 * Clean, Focused Executive Home Dashboard for Super Admin, College Admin, and Center Admin.
 * Detailed Colleges/Centers/Organizers/Users/Screenings live in their dedicated bottom tabs so Home stays crisp & uncluttered!
 */
@Composable
fun AdminRoleDashboardScreen(
    role: String,
    uiState: DashboardUiState,
    isOnline: Boolean,
    onRefresh: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onOpenEducation: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val stats = uiState.stats
    val roleBadge = when (role) {
        "SUPER_ADMIN" -> "Super Admin"
        "COLLEGE_ADMIN" -> "College Admin"
        else -> "Center Admin"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardTopHeader(
                userName = stats?.userFullName ?: roleBadge,
                roleBadge = roleBadge,
                orgLabel = stats?.organizationLabel ?: "OralCare Network",
                isOnline = isOnline,
                pendingDrafts = 0,
                onRefresh = onRefresh,
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = onOpenProfile,
            )
        }

        if (!uiState.error.isNullOrBlank()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ErrorStateView(uiState.error, onRetry = onRefresh)
                }
            }
        }

        if (uiState.isLoading && stats == null) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LoadingView("Loading $roleBadge dashboard...")
                }
            }
        }

        if (stats != null) {
            // Key Metrics relevant ONLY to this role
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = when (role) {
                            "SUPER_ADMIN" -> "Organization Overview"
                            "COLLEGE_ADMIN" -> "College Overview"
                            else -> "Center Overview"
                        },
                        subtitle = "Key clinical statistics",
                    )

                    when (role) {
                        "SUPER_ADMIN" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassStatCard("Dental Colleges", stats.totalColleges.toString(), Icons.Default.AccountBalance, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                                GlassStatCard("Centers", stats.totalCenters.toString(), Icons.Default.LocalHospital, Modifier.weight(1f), OralCareColors.AccentBlue)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassStatCard("Organizers", stats.totalOrganizers.toString(), Icons.Default.Badge, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                                GlassStatCard("Total Patients", stats.totalPatients.toString(), Icons.Default.People, Modifier.weight(1f), OralCareColors.AccentBlue)
                            }
                        }
                        "COLLEGE_ADMIN" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassStatCard("Centers", stats.totalCenters.toString(), Icons.Default.LocalHospital, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                                GlassStatCard("Organizers", stats.totalOrganizers.toString(), Icons.Default.Badge, Modifier.weight(1f), OralCareColors.AccentBlue)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassStatCard("Total Patients", stats.totalPatients.toString(), Icons.Default.People, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                                GlassStatCard("Screenings", stats.totalScreenings.toString(), Icons.Default.MedicalServices, Modifier.weight(1f), OralCareColors.NormalGreen)
                            }
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassStatCard("Organizers", stats.totalOrganizers.toString(), Icons.Default.Badge, Modifier.weight(1f), OralCareColors.AccentBlueStrong)
                                GlassStatCard("Total Patients", stats.totalPatients.toString(), Icons.Default.People, Modifier.weight(1f), OralCareColors.AccentBlue)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassStatCard("Normal", stats.normalScreenings.toString(), Icons.Default.CheckCircle, Modifier.weight(1f), OralCareColors.NormalGreen)
                        GlassStatCard("Abnormal", stats.abnormalScreenings.toString(), Icons.Default.Warning, Modifier.weight(1f), OralCareColors.AbnormalRed)
                        GlassStatCard("Lesions", stats.lesionsDetected.toString(), Icons.Default.LocalHospital, Modifier.weight(1f), OralCareColors.WarningAmber)
                    }
                    Spacer(Modifier.height(4.dp))
                    GlassStatCard(
                        title = "Total Feedback",
                        value = stats.totalFeedback.toString(),
                        icon = Icons.Default.Star,
                        accentColor = OralCareColors.WarningAmber,
                        subtitle = "Average satisfaction: ${stats.averageFeedbackRating} / 5.0 • Tap to view & reply",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFeedback() },
                    )
                }
            }

            // Weekly Screening Chart
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    WeeklyScreeningChart(points = stats.weeklyScreenings)
                }
            }

            // Quick Role Navigation Shortcuts
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        when (role) {
                            "SUPER_ADMIN" -> {
                                GlassButton("Manage Colleges", { onNavigateTab("colleges") }, icon = Icons.Default.AccountBalance, modifier = Modifier.weight(1f))
                                GlassButton("Manage Users", { onNavigateTab("users") }, isPrimary = false, icon = Icons.Default.Group, modifier = Modifier.weight(1f))
                            }
                            "COLLEGE_ADMIN" -> {
                                GlassButton("Manage Centers", { onNavigateTab("centers") }, icon = Icons.Default.LocalHospital, modifier = Modifier.weight(1f))
                                GlassButton("Organizers", { onNavigateTab("organizers") }, isPrimary = false, icon = Icons.Default.Badge, modifier = Modifier.weight(1f))
                            }
                            else -> {
                                GlassButton("Organizers", { onNavigateTab("organizers") }, icon = Icons.Default.Badge, modifier = Modifier.weight(1f))
                                GlassButton("Screenings", { onNavigateTab("screenings") }, isPrimary = false, icon = Icons.Default.MedicalServices, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        GlassButton("Feedback", onOpenFeedback, isPrimary = false, icon = Icons.Default.RateReview, modifier = Modifier.weight(1f))
                        if (role == "SUPER_ADMIN" || role == "COLLEGE_ADMIN") {
                            GlassButton("Education", onOpenEducation, isPrimary = false, icon = Icons.AutoMirrored.Filled.MenuBook, modifier = Modifier.weight(1f))
                        }
                        GlassButton("Activity Logs", onOpenActivity, isPrimary = false, icon = Icons.Default.History, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Recent Activity Timeline
            item {
                RecentActivitySection(stats.recentActivities)
            }
        }
    }
}

@Composable
private fun DashboardTopHeader(
    userName: String,
    roleBadge: String,
    orgLabel: String,
    isOnline: Boolean,
    pendingDrafts: Int,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column {
        GlassHeader(
            title = "Hello, $userName",
            subtitle = "$orgLabel • $roleBadge",
            avatarInitials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").ifBlank { "OC" },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = OralCareColors.TextPrimary)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OralCareColors.LightBlue)
                        .border(1.dp, Color.White, CircleShape)
                        .clickable(onClick = onOpenProfile),
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile & Settings", tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(19.dp))
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(roleBadge, OralCareColors.LightBlue, OralCareColors.AccentBlueStrong)
            StatusPill(
                text = if (isOnline) "● Online" else "● Offline",
                bg = if (isOnline) OralCareColors.NormalGreenBg else OralCareColors.WarningAmberBg,
                fg = if (isOnline) OralCareColors.NormalGreen else OralCareColors.WarningAmber,
            )
            if (pendingDrafts > 0) {
                StatusPill("$pendingDrafts draft(s)", OralCareColors.LightBlue, OralCareColors.AccentBlueStrong)
            }
        }
    }
}

@Composable
private fun RecentActivitySection(activities: List<ActivityLogDto>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "Recent Activity", subtitle = "Your latest recorded activity")
        Spacer(Modifier.height(6.dp))
        if (activities.isEmpty()) {
            EmptyStateView("No recent activity yet", "Your screenings, updates, and reports will appear here.")
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    activities.take(5).forEachIndexed { idx, act ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(OralCareColors.AccentBlueStrong),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(act.action.replace("_", " "), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${act.entityType} ${act.entityId ?: ""} • ${act.createdAt.take(19)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (idx < activities.take(5).lastIndex) {
                            HorizontalDivider(color = OralCareColors.LightBlue)
                        }
                    }
                }
            }
        }
    }
}
