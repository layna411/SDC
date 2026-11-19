package com.simats.sdc.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simats.sdc.BuildConfig
import com.simats.sdc.core.datastore.UserSession
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.*
import com.simats.sdc.presentation.components.*

@Composable
fun CollegesManagementScreen(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onCreateCollege: (CollegeCreateDto) -> Unit,
    onToggleCollege: (Int, Boolean) -> Unit,
    onDismissBanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(uiState.colleges, query) {
        if (query.isBlank()) uiState.colleges
        else uiState.colleges.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.collegeCode.contains(query, ignoreCase = true) ||
                (it.city?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Dental Colleges",
            subtitle = "${uiState.colleges.size} registered institutions",
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add College", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = OralCareColors.TextPrimary)
                }
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassSearchBar(query, { query = it }, "Search colleges by name, code, or city...")

            GlassButton(
                text = "Add New Dental College",
                onClick = { showAddDialog = true },
                icon = Icons.Default.AddBusiness,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                uiState.isLoading && uiState.colleges.isEmpty() -> LoadingView("Loading dental colleges...")
                filtered.isEmpty() -> EmptyStateView(
                    title = "No Dental Colleges Found",
                    subtitle = "Tap 'Add New Dental College' above to onboard a college and issue College Admin credentials.",
                    icon = Icons.Default.AccountBalance,
                )
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(filtered, key = { it.id }) { col ->
                            GlassCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(OralCareColors.LightBlue),
                                        ) {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(col.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${col.collegeCode} • ${listOfNotNull(col.city, col.state).joinToString(", ").ifBlank { "Location not set" }}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                                            Spacer(Modifier.height(4.dp))
                                            StatusPill(
                                                text = if (col.isActive) "Active" else "Inactive",
                                                bg = if (col.isActive) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                                                fg = if (col.isActive) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                                            )
                                        }
                                    }
                                    Switch(checked = col.isActive, onCheckedChange = { onToggleCollege(col.id, !col.isActive) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!uiState.issuedCredentialBanner.isNullOrBlank()) {
        GlassDialog(title = "Credentials Issued", onDismiss = onDismissBanner, confirmButton = { GlassButton("Done", onDismissBanner) }) {
            Text(uiState.issuedCredentialBanner)
        }
    }

    if (showAddDialog) {
        AddCollegeModal(
            onDismiss = { showAddDialog = false },
            onSubmit = { onCreateCollege(it); showAddDialog = false },
        )
    }
}

@Composable
fun CentersManagementScreen(
    role: String,
    uiState: DashboardUiState,
    onCreateCenter: (CenterCreateDto) -> Unit,
    onToggleCenter: (Int, Boolean) -> Unit,
    onDismissBanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(uiState.centers, query) {
        if (query.isBlank()) uiState.centers
        else uiState.centers.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.centerCode.contains(query, ignoreCase = true) ||
                (it.hubName?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Screening Centers",
            subtitle = "${uiState.centers.size} outreach centers",
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Center", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = OralCareColors.TextPrimary)
                }
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassSearchBar(query, { query = it }, "Search centers by name, code, or hub...")

            GlassButton(
                text = "Add New Screening Center",
                onClick = { showAddDialog = true },
                icon = Icons.Default.AddLocationAlt,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                uiState.isLoading && uiState.centers.isEmpty() -> LoadingView("Loading centers...")
                filtered.isEmpty() -> EmptyStateView(
                    title = "No Screening Centers Found",
                    subtitle = "Add a screening center to assign Center Admins and Health Organizer Assistants.",
                    icon = Icons.Default.LocalHospital,
                )
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(filtered, key = { it.id }) { ct ->
                            GlassCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(OralCareColors.LightBlue),
                                        ) {
                                            Icon(Icons.Default.LocalHospital, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(ct.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${ct.centerCode} • Hub: ${ct.hubName ?: "General"}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                                            Spacer(Modifier.height(4.dp))
                                            StatusPill(
                                                text = if (ct.isActive) "Active" else "Inactive",
                                                bg = if (ct.isActive) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                                                fg = if (ct.isActive) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                                            )
                                        }
                                    }
                                    Switch(checked = ct.isActive, onCheckedChange = { onToggleCenter(ct.id, !ct.isActive) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!uiState.issuedCredentialBanner.isNullOrBlank()) {
        GlassDialog(title = "Credentials Issued", onDismiss = onDismissBanner, confirmButton = { GlassButton("Done", onDismissBanner) }) {
            Text(uiState.issuedCredentialBanner)
        }
    }

    if (showAddDialog) {
        AddCenterModal(
            colleges = uiState.colleges,
            requireCollegePicker = role == "SUPER_ADMIN",
            onDismiss = { showAddDialog = false },
            onSubmit = { onCreateCenter(it); showAddDialog = false },
        )
    }
}

@Composable
fun OrganizersManagementScreen(
    role: String,
    uiState: DashboardUiState,
    onCreateOrganizer: (OrganizerCreateDto) -> Unit,
    onToggleOrganizer: (Int, Boolean) -> Unit,
    onDismissBanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(uiState.organizers, query) {
        if (query.isBlank()) uiState.organizers
        else uiState.organizers.filter {
            (it.user?.fullName?.contains(query, ignoreCase = true) == true) ||
                it.employeeCode.contains(query, ignoreCase = true) ||
                (it.assignedArea?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Health Organizers",
            subtitle = "${uiState.organizers.size} assigned assistants",
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Organizer", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = OralCareColors.TextPrimary)
                }
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassSearchBar(query, { query = it }, "Search organizers by name, code, or area...")

            GlassButton(
                text = "Add Health Organizer Assistant",
                onClick = { showAddDialog = true },
                icon = Icons.Default.PersonAdd,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                uiState.isLoading && uiState.organizers.isEmpty() -> LoadingView("Loading health organizers...")
                filtered.isEmpty() -> EmptyStateView(
                    title = "No Health Organizers Assigned",
                    subtitle = "Create a Health Organizer Assistant account to begin patient registration and field screenings.",
                    icon = Icons.Default.Badge,
                )
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(filtered, key = { it.id }) { org ->
                            GlassCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(OralCareColors.LightBlue),
                                        ) {
                                            Text(
                                                (org.user?.fullName ?: org.employeeCode).take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = OralCareColors.AccentBlueStrong,
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(org.user?.fullName ?: org.employeeCode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${org.employeeCode} • @${org.user?.username ?: ""}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                                            Text("Area: ${org.assignedArea ?: "General"}", style = MaterialTheme.typography.labelSmall, color = OralCareColors.AccentBlueStrong)
                                        }
                                    }
                                    Switch(checked = org.isActive, onCheckedChange = { onToggleOrganizer(org.id, !org.isActive) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!uiState.issuedCredentialBanner.isNullOrBlank()) {
        GlassDialog(title = "Credentials Issued", onDismiss = onDismissBanner, confirmButton = { GlassButton("Done", onDismissBanner) }) {
            Text(uiState.issuedCredentialBanner)
        }
    }

    if (showAddDialog) {
        AddOrganizerModal(
            centers = uiState.centers,
            requireCenterPicker = role != "CENTER_ADMIN",
            onDismiss = { showAddDialog = false },
            onSubmit = { onCreateOrganizer(it); showAddDialog = false },
        )
    }
}

@Composable
fun UsersDirectoryScreen(
    uiState: DashboardUiState,
    currentUserId: Int,
    onCreateOrganizer: (OrganizerCreateDto) -> Unit,
    onToggleUserStatus: (Int, Boolean) -> Unit,
    onDismissBanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<String?>(null) }
    var showAddOrganizer by remember { mutableStateOf(false) }

    val filtered = remember(uiState.users, query, roleFilter) {
        uiState.users.filter { u ->
            (roleFilter == null || u.role == roleFilter) &&
                (query.isBlank() || u.fullName.contains(query, true) || u.username.contains(query, true) || u.userUid.contains(query, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Users & Access",
            subtitle = "${uiState.users.size} accounts across organization",
            actions = {
                IconButton(onClick = { showAddOrganizer = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Organizer", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = OralCareColors.TextPrimary)
                }
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassSearchBar(query, { query = it }, "Search by name, username, or user ID...")

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = roleFilter == null, onClick = { roleFilter = null }, label = { Text("All Roles") })
                FilterChip(selected = roleFilter == "COLLEGE_ADMIN", onClick = { roleFilter = "COLLEGE_ADMIN" }, label = { Text("College Admin") })
                FilterChip(selected = roleFilter == "CENTER_ADMIN", onClick = { roleFilter = "CENTER_ADMIN" }, label = { Text("Center Admin") })
                FilterChip(selected = roleFilter == "HEALTH_ORGANIZER", onClick = { roleFilter = "HEALTH_ORGANIZER" }, label = { Text("Organizer") })
            }

            if (filtered.isEmpty() && !uiState.isLoading) {
                EmptyStateView("No Matching Users", "Adjust filters or create accounts from Colleges, Centers, or Organizers.", Icons.Default.Group)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(filtered, key = { it.id }) { u ->
                        GlassCard(contentPadding = PaddingValues(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("@${u.username} • ${u.userUid}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    StatusPill(
                                        text = u.role.replace("_", " "),
                                        bg = OralCareColors.LightBlue,
                                        fg = OralCareColors.AccentBlueStrong,
                                    )
                                }
                                if (u.id != currentUserId) {
                                    Switch(checked = u.isActive, onCheckedChange = { onToggleUserStatus(u.id, !u.isActive) })
                                } else {
                                    StatusPill("You", OralCareColors.NormalGreenBg, OralCareColors.NormalGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!uiState.issuedCredentialBanner.isNullOrBlank()) {
        GlassDialog(title = "Credentials Issued", onDismiss = onDismissBanner, confirmButton = { GlassButton("Done", onDismissBanner) }) {
            Text(uiState.issuedCredentialBanner)
        }
    }

    if (showAddOrganizer) {
        AddOrganizerModal(
            centers = uiState.centers,
            requireCenterPicker = true,
            onDismiss = { showAddOrganizer = false },
            onSubmit = { onCreateOrganizer(it); showAddOrganizer = false },
        )
    }
}

@Composable
fun ScreeningsMonitorScreen(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var outcomeFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(uiState.screenings, outcomeFilter, query) {
        uiState.screenings.filter { s ->
            (outcomeFilter == null || s.outcome == outcomeFilter) &&
                (query.isBlank() ||
                    s.screeningUid.contains(query, true) ||
                    (s.patient?.displayName?.contains(query, true) == true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(
            title = "Screenings Monitor",
            subtitle = "${uiState.screenings.size} clinical screenings recorded",
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OralCareColors.AccentBlueStrong)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = OralCareColors.TextPrimary)
                }
                IconButton(onClick = onOpenProfile) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = OralCareColors.AccentBlueStrong)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassSearchBar(query, { query = it }, "Search by Screening ID or patient name...")

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = outcomeFilter == null, onClick = { outcomeFilter = null }, label = { Text("All") })
                FilterChip(selected = outcomeFilter == "NORMAL", onClick = { outcomeFilter = "NORMAL" }, label = { Text("Normal") })
                FilterChip(selected = outcomeFilter == "ABNORMAL", onClick = { outcomeFilter = "ABNORMAL" }, label = { Text("Abnormal") })
                FilterChip(selected = outcomeFilter == "PENDING_CLINICAL_REVIEW", onClick = { outcomeFilter = "PENDING_CLINICAL_REVIEW" }, label = { Text("Pending Review") })
            }

            if (uiState.isLoading && uiState.screenings.isEmpty()) {
                LoadingView("Loading screening records...")
            } else if (filtered.isEmpty()) {
                EmptyStateView("No Screenings Found", "Screenings submitted by your center's Health Organizers will appear here.", Icons.Default.MedicalServices)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(filtered, key = { it.id }) { scr ->
                        GlassCard(
                            borderColor = when (scr.outcome) {
                                "ABNORMAL" -> OralCareColors.AbnormalRed.copy(alpha = 0.4f)
                                "NORMAL" -> OralCareColors.NormalGreen.copy(alpha = 0.4f)
                                else -> null
                            },
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = scr.patient?.displayName ?: scr.screeningUid,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    ScreeningStatusBadge(scr.outcome)
                                }
                                Text("${scr.screeningUid} • Date: ${scr.screeningDate}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                                Text(
                                    "Findings: ${scr.clinicalFindings.size} • Lesions: ${scr.lesionDetails.size} • Images: ${scr.patientImages.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OralCareColors.AccentBlueStrong,
                                )
                                scr.consultationSummary?.outcomeReason?.let { reason ->
                                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsScreen(
    session: UserSession,
    isOnline: Boolean,
    appSettings: List<AppSettingDto>,
    onBack: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onChangePassword: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onSaveAppSetting: (String, String, String) -> Unit,
    onOpenEducation: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenFeedback: () -> Unit,
    onLogout: () -> Unit,
) {
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val roleLabel = when (session.role) {
        "SUPER_ADMIN" -> "Super Administrator"
        "COLLEGE_ADMIN" -> "Dental College Admin"
        "CENTER_ADMIN" -> "Center Administrator"
        else -> "Health Organizer Assistant"
    }

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            GlassHeader(
                title = "Profile & Settings",
                subtitle = roleLabel,
                showBackButton = true,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Profile Identity Card
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color.White, OralCareColors.LightBlue)))
                                .border(2.dp, Color.White, CircleShape),
                        ) {
                            Text(
                                text = session.fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").ifBlank { "OC" }.uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OralCareColors.AccentBlueStrong,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.fullName.ifBlank { session.username }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("@${session.username} • ${session.userUid}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(roleLabel, Color.White.copy(alpha = 0.85f), OralCareColors.AccentBlueStrong)
                                StatusPill(
                                    if (isOnline) "Online" else "Offline",
                                    if (isOnline) OralCareColors.NormalGreenBg else OralCareColors.WarningAmberBg,
                                    if (isOnline) OralCareColors.NormalGreen else OralCareColors.WarningAmber,
                                )
                            }
                        }
                    }
                }

                // Quick Clinical Tools
                SectionHeader("Modules & Resources")
                GlassCard(contentPadding = PaddingValues(6.dp)) {
                    ProfileActionRow(Icons.AutoMirrored.Filled.MenuBook, "Educational Resources & Manuals", "English & Tamil guides, videos, awareness", onOpenEducation)
                    HorizontalDivider(color = OralCareColors.LightBlue, modifier = Modifier.padding(horizontal = 12.dp))
                    ProfileActionRow(Icons.Default.History, "Activity History", "View recent screening and account activity", onOpenActivity)
                    HorizontalDivider(color = OralCareColors.LightBlue, modifier = Modifier.padding(horizontal = 12.dp))
                    ProfileActionRow(Icons.Default.RateReview, "Patient Feedback", "View & manage 5-star patient feedback", onOpenFeedback)
                }

                // Preferences & Security
                SectionHeader("Account & Preferences")
                GlassCard(contentPadding = PaddingValues(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Brightness6, contentDescription = null, tint = OralCareColors.AccentBlueStrong)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Dark Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Switch between light and dark appearance", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                            }
                        }
                        Switch(checked = session.darkMode, onCheckedChange = { onToggleDarkMode() })
                    }
                    HorizontalDivider(color = OralCareColors.LightBlue, modifier = Modifier.padding(horizontal = 12.dp))
                    ProfileActionRow(Icons.Default.LockReset, "Change Password", "Update your login password", onChangePassword)
                    if (session.role == "SUPER_ADMIN") {
                        HorizontalDivider(color = OralCareColors.LightBlue, modifier = Modifier.padding(horizontal = 12.dp))
                        ProfileActionRow(Icons.Default.Tune, "Organization Settings", "Configure clinic policies and preferences") { showAppSettingsDialog = true }
                    }
                }

                GlassButton(
                    text = "Sign Out",
                    isPrimary = false,
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showAppSettingsDialog) {
        AppSettingsModal(
            settings = appSettings,
            onDismiss = { showAppSettingsDialog = false },
            onSave = { k, v, d -> onSaveAppSetting(k, v, d); showAppSettingsDialog = false },
        )
    }

    if (showLogoutConfirm) {
        ConfirmationDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out of OralCare?",
            confirmText = "Sign Out",
            onConfirm = { showLogoutConfirm = false; onLogout() },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OralCareColors.TextSecondary)
        }
    }
}

@Composable
fun NotificationsSheet(
    activities: List<ActivityLogDto>,
    pendingReviews: Int,
    onDismiss: () -> Unit,
) {
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Clinical Notifications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (pendingReviews > 0) {
                GlassCard(
                    containerColor = OralCareColors.WarningAmberBg,
                    borderColor = OralCareColors.WarningAmber.copy(alpha = 0.25f),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Text("$pendingReviews screening(s) awaiting clinical review.", color = OralCareColors.WarningAmber, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (activities.isEmpty()) {
                Text("You are all caught up! Recent screening and account notifications appear here.", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
            } else {
                activities.take(6).forEach { act ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OralCareColors.AccentBlueStrong))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(act.action.replace("_", " "), style = MaterialTheme.typography.labelLarge)
                            Text("${act.entityType} ${act.entityId ?: ""} • ${act.createdAt.take(19)}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun AddCollegeModal(onDismiss: () -> Unit, onSubmit: (CollegeCreateDto) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var adminUser by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }

    GlassDialog(
        title = "Add Dental College & Admin",
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Create", enabled = code.isNotBlank() && name.isNotBlank(), onClick = {
                onSubmit(
                    CollegeCreateDto(
                        collegeCode = code,
                        name = name,
                        city = city.ifBlank { null },
                        state = state.ifBlank { null },
                        createAdminAccount = adminUser.isNotBlank(),
                        adminUsername = adminUser.ifBlank { null },
                        adminFullName = adminName.ifBlank { null },
                    )
                )
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField(code, { code = it }, "College Code (e.g., SIMATS01)")
            GlassTextField(name, { name = it }, "Dental College Name")
            GlassTextField(city, { city = it }, "City")
            GlassTextField(state, { state = it }, "State")
            GlassTextField(adminUser, { adminUser = it }, "College Admin Username")
            GlassTextField(adminName, { adminName = it }, "College Admin Full Name")
        }
    }
}

@Composable
internal fun AddCenterModal(
    colleges: List<CollegeDto>,
    requireCollegePicker: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CenterCreateDto) -> Unit,
) {
    var selectedCollegeId by remember { mutableStateOf(colleges.firstOrNull()?.id) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var hub by remember { mutableStateOf("") }
    var partner by remember { mutableStateOf("") }
    var adminUser by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }

    GlassDialog(
        title = "Add Screening Center & Admin",
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Create", enabled = code.isNotBlank() && name.isNotBlank(), onClick = {
                onSubmit(
                    CenterCreateDto(
                        collegeId = if (requireCollegePicker) selectedCollegeId else null,
                        centerCode = code,
                        name = name,
                        hubName = hub.ifBlank { null },
                        healthPartner = partner.ifBlank { null },
                        createAdminAccount = adminUser.isNotBlank(),
                        adminUsername = adminUser.ifBlank { null },
                        adminFullName = adminName.ifBlank { null },
                    )
                )
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (requireCollegePicker && colleges.isNotEmpty()) {
                val selectedName = colleges.firstOrNull { it.id == selectedCollegeId }?.name ?: colleges.first().name
                GlassDropdown(
                    label = "Parent Dental College",
                    selectedValue = selectedName,
                    options = colleges.map { it.name },
                    onOptionSelected = { picked -> selectedCollegeId = colleges.firstOrNull { it.name == picked }?.id },
                )
            }
            GlassTextField(code, { code = it }, "Center Code (e.g., CTR01)")
            GlassTextField(name, { name = it }, "Center Name")
            GlassTextField(hub, { hub = it }, "Hub Name")
            GlassTextField(partner, { partner = it }, "Health Partner")
            GlassTextField(adminUser, { adminUser = it }, "Center Admin Username")
            GlassTextField(adminName, { adminName = it }, "Center Admin Full Name")
        }
    }
}

@Composable
internal fun AddOrganizerModal(
    centers: List<CenterDto>,
    requireCenterPicker: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (OrganizerCreateDto) -> Unit,
) {
    var selectedCenterId by remember { mutableStateOf(centers.firstOrNull()?.id) }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var empCode by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }

    GlassDialog(
        title = "Create Health Organizer Assistant",
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Create Account", enabled = username.isNotBlank() && fullName.isNotBlank() && empCode.isNotBlank(), onClick = {
                onSubmit(
                    OrganizerCreateDto(
                        centerId = if (requireCenterPicker) selectedCenterId else null,
                        username = username,
                        fullName = fullName,
                        employeeCode = empCode,
                        assignedArea = area.ifBlank { null },
                    )
                )
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (requireCenterPicker && centers.isNotEmpty()) {
                val selectedName = centers.firstOrNull { it.id == selectedCenterId }?.name ?: centers.first().name
                GlassDropdown(
                    label = "Assigned Center",
                    selectedValue = selectedName,
                    options = centers.map { it.name },
                    onOptionSelected = { picked -> selectedCenterId = centers.firstOrNull { it.name == picked }?.id },
                )
            }
            GlassTextField(username, { username = it }, "Login Username")
            GlassTextField(fullName, { fullName = it }, "Full Name")
            GlassTextField(empCode, { empCode = it }, "Employee / Staff Code")
            GlassTextField(area, { area = it }, "Assigned Area / Ward")
        }
    }
}

@Composable
internal fun AppSettingsModal(
    settings: List<AppSettingDto>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var key by remember { mutableStateOf("data_retention_days") }
    var value by remember { mutableStateOf("1825") }
    var desc by remember { mutableStateOf("Clinical data retention period in days") }

    GlassDialog(
        title = "Organization Settings",
        onDismiss = onDismiss,
        confirmButton = { GlassButton("Save Setting", onClick = { onSave(key, value, desc) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.take(4).forEach { s ->
                Text("${s.settingKey} = ${s.settingValue}", style = MaterialTheme.typography.bodySmall)
            }
            GlassTextField(key, { key = it }, "Setting Key")
            GlassTextField(value, { value = it }, "Setting Value")
            GlassTextField(desc, { desc = it }, "Description")
        }
    }
}
