package com.simats.sdc.presentation.dashboard

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onFetchCollegeActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    onErrorDismiss: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCollege by remember { mutableStateOf<CollegeDto?>(null) }

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
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
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
                            GlassCard(
                                onClick = { selectedCollege = col },
                                contentPadding = PaddingValues(14.dp),
                            ) {
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

    selectedCollege?.let { col ->
        CollegeDetailModal(
            college = col,
            onDismiss = { selectedCollege = null },
            onFetchActivities = onFetchCollegeActivities,
            allCenters = uiState.centers,
            allUsers = uiState.users,
        )
    }

    if (uiState.issuedCredentials != null || !uiState.issuedCredentialBanner.isNullOrBlank()) {
        CredentialsIssuedDialog(
            credentials = uiState.issuedCredentials,
            fallbackBanner = uiState.issuedCredentialBanner,
            onDismiss = onDismissBanner,
        )
    }

    if (!uiState.error.isNullOrBlank()) {
        GlassDialog(
            title = "Action Failed",
            onDismiss = onErrorDismiss,
            confirmButton = { GlassButton("OK", onClick = onErrorDismiss) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.AbnormalRedBg)
                    .border(BorderStroke(0.75.dp, Color(0xFFF8D7DA)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = OralCareColors.AbnormalRed,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OralCareColors.AbnormalRed,
                )
            }
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
    onResetAdminCredentials: ((Int) -> Unit)? = null,
    onDismissBanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onFetchCenterActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    onErrorDismiss: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCenter by remember { mutableStateOf<CenterDto?>(null) }

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
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
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
                            GlassCard(
                                onClick = { selectedCenter = ct },
                                contentPadding = PaddingValues(14.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
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
                                                if (!ct.adminUsername.isNullOrBlank()) {
                                                    Text("Admin: @${ct.adminUsername}", style = MaterialTheme.typography.bodySmall, color = OralCareColors.AccentBlueStrong, fontWeight = FontWeight.SemiBold)
                                                } else {
                                                    Text("No Admin Assigned", style = MaterialTheme.typography.bodySmall, color = OralCareColors.AbnormalRed)
                                                }
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

                                    if (onResetAdminCredentials != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            OutlinedButton(
                                                onClick = { onResetAdminCredentials(ct.id) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                border = BorderStroke(1.dp, OralCareColors.AccentBlueStrong),
                                            ) {
                                                Icon(Icons.Default.Key, contentDescription = null, tint = OralCareColors.AccentBlueStrong, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = if (ct.adminUsername.isNullOrBlank()) "Generate Admin Credentials" else "Reset Credentials",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = OralCareColors.AccentBlueStrong,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedCenter?.let { ct ->
        CenterDetailModal(
            center = ct,
            onDismiss = { selectedCenter = null },
            onFetchActivities = onFetchCenterActivities,
            allUsers = uiState.users,
        )
    }

    if (uiState.issuedCredentials != null || !uiState.issuedCredentialBanner.isNullOrBlank()) {
        CredentialsIssuedDialog(
            credentials = uiState.issuedCredentials,
            fallbackBanner = uiState.issuedCredentialBanner,
            onDismiss = onDismissBanner,
        )
    }

    if (!uiState.error.isNullOrBlank()) {
        GlassDialog(
            title = "Action Failed",
            onDismiss = onErrorDismiss,
            confirmButton = { GlassButton("OK", onClick = onErrorDismiss) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.AbnormalRedBg)
                    .border(BorderStroke(0.75.dp, Color(0xFFF8D7DA)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = OralCareColors.AbnormalRed,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OralCareColors.AbnormalRed,
                )
            }
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
    onFetchUserActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    onErrorDismiss: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<UserDto?>(null) }

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
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
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
                            GlassCard(
                                onClick = { if (org.user != null) selectedUser = org.user },
                                contentPadding = PaddingValues(14.dp),
                            ) {
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

    selectedUser?.let { u ->
        UserDetailModal(
            user = u,
            onDismiss = { selectedUser = null },
            onFetchActivities = onFetchUserActivities,
            allColleges = uiState.colleges,
            allCenters = uiState.centers,
        )
    }

    if (uiState.issuedCredentials != null || !uiState.issuedCredentialBanner.isNullOrBlank()) {
        CredentialsIssuedDialog(
            credentials = uiState.issuedCredentials,
            fallbackBanner = uiState.issuedCredentialBanner,
            onDismiss = onDismissBanner,
        )
    }

    if (!uiState.error.isNullOrBlank()) {
        GlassDialog(
            title = "Action Failed",
            onDismiss = onErrorDismiss,
            confirmButton = { GlassButton("OK", onClick = onErrorDismiss) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.AbnormalRedBg)
                    .border(BorderStroke(0.75.dp, Color(0xFFF8D7DA)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = OralCareColors.AbnormalRed,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OralCareColors.AbnormalRed,
                )
            }
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
    onFetchUserActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    onErrorDismiss: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<String?>(null) }
    var showAddOrganizer by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<UserDto?>(null) }

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
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassSearchBar(query, { query = it }, "Search by name, username, or user ID...")

            GlassButton(
                text = "Add Health Organizer Assistant",
                onClick = { showAddOrganizer = true },
                icon = Icons.Default.PersonAdd,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OralCareColors.LightBlue,
                    selectedLabelColor = OralCareColors.AccentBlueStrong,
                    containerColor = Color.White.copy(alpha = 0.85f),
                    labelColor = OralCareColors.TextSecondary,
                )
                FilterChip(
                    selected = roleFilter == null,
                    onClick = { roleFilter = null },
                    label = { Text("All Roles") },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = roleFilter == null,
                        borderColor = OralCareColors.PastelBlue,
                        selectedBorderColor = OralCareColors.AccentBlueStrong,
                        borderWidth = 0.75.dp,
                        selectedBorderWidth = 1.25.dp,
                    ),
                )
                FilterChip(
                    selected = roleFilter == "COLLEGE_ADMIN",
                    onClick = { roleFilter = "COLLEGE_ADMIN" },
                    label = { Text("College Admin") },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = roleFilter == "COLLEGE_ADMIN",
                        borderColor = OralCareColors.PastelBlue,
                        selectedBorderColor = OralCareColors.AccentBlueStrong,
                        borderWidth = 0.75.dp,
                        selectedBorderWidth = 1.25.dp,
                    ),
                )
                FilterChip(
                    selected = roleFilter == "CENTER_ADMIN",
                    onClick = { roleFilter = "CENTER_ADMIN" },
                    label = { Text("Center Admin") },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = roleFilter == "CENTER_ADMIN",
                        borderColor = OralCareColors.PastelBlue,
                        selectedBorderColor = OralCareColors.AccentBlueStrong,
                        borderWidth = 0.75.dp,
                        selectedBorderWidth = 1.25.dp,
                    ),
                )
                FilterChip(
                    selected = roleFilter == "HEALTH_ORGANIZER",
                    onClick = { roleFilter = "HEALTH_ORGANIZER" },
                    label = { Text("Organizer") },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = roleFilter == "HEALTH_ORGANIZER",
                        borderColor = OralCareColors.PastelBlue,
                        selectedBorderColor = OralCareColors.AccentBlueStrong,
                        borderWidth = 0.75.dp,
                        selectedBorderWidth = 1.25.dp,
                    ),
                )
            }

            if (filtered.isEmpty() && !uiState.isLoading) {
                EmptyStateView("No Matching Users", "Adjust filters or create accounts from Colleges, Centers, or Organizers.", Icons.Default.Group)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(filtered, key = { it.id }) { u ->
                        GlassCard(
                            onClick = { selectedUser = u },
                            contentPadding = PaddingValues(14.dp),
                        ) {
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

    selectedUser?.let { u ->
        UserDetailModal(
            user = u,
            onDismiss = { selectedUser = null },
            onFetchActivities = onFetchUserActivities,
            allColleges = uiState.colleges,
            allCenters = uiState.centers,
        )
    }

    if (uiState.issuedCredentials != null || !uiState.issuedCredentialBanner.isNullOrBlank()) {
        CredentialsIssuedDialog(
            credentials = uiState.issuedCredentials,
            fallbackBanner = uiState.issuedCredentialBanner,
            onDismiss = onDismissBanner,
        )
    }

    if (!uiState.error.isNullOrBlank()) {
        GlassDialog(
            title = "Action Failed",
            onDismiss = onErrorDismiss,
            confirmButton = { GlassButton("OK", onClick = onErrorDismiss) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.AbnormalRedBg)
                    .border(BorderStroke(0.75.dp, Color(0xFFF8D7DA)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = OralCareColors.AbnormalRed,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OralCareColors.AbnormalRed,
                )
            }
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
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
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
    onToggleDarkMode: (() -> Unit)? = null,
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
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var adminUser by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }

    GlassDialog(
        title = "Add Dental College & Admin",
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Create", enabled = code.isNotBlank() && name.isNotBlank(), onClick = {
                val cleanCode = code.trim().uppercase()
                val finalAdminUser = if (adminUser.isNotBlank()) adminUser.trim() else "${cleanCode.lowercase()}_admin"
                val finalAdminName = if (adminName.isNotBlank()) adminName.trim() else "${name.trim()} Admin"

                onSubmit(
                    CollegeCreateDto(
                        collegeCode = cleanCode,
                        name = name.trim(),
                        contactEmail = email.trim().ifBlank { null },
                        contactPhone = phone.trim().ifBlank { null },
                        city = city.trim().ifBlank { null },
                        state = state.trim().ifBlank { null },
                        createAdminAccount = true,
                        adminUsername = finalAdminUser,
                        adminFullName = finalAdminName,
                        adminEmail = email.trim().ifBlank { null },
                        adminPhone = phone.trim().ifBlank { null },
                    )
                )
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField(code, { 
                code = it 
                val clean = it.trim().lowercase()
                if (adminUser.isBlank() || adminUser.endsWith("_admin")) {
                    adminUser = if (clean.isNotBlank()) "${clean}_admin" else ""
                }
            }, "College Code (e.g., SIMATS01)")
            GlassTextField(name, { 
                name = it 
                val clean = it.trim()
                if (adminName.isBlank() || adminName.endsWith(" Admin")) {
                    adminName = if (clean.isNotBlank()) "$clean Admin" else ""
                }
            }, "Dental College Name")
            GlassTextField(email, { email = it }, "Email ID (e.g., college@simats.edu)", keyboardType = KeyboardType.Email)
            GlassTextField(phone, { phone = it }, "Mobile Number (e.g., +91 9876543210)", keyboardType = KeyboardType.Phone)
            GlassTextField(city, { city = it }, "City")
            GlassTextField(state, { state = it }, "State")
            GlassTextField(adminUser, { adminUser = it }, "College Admin Username (Unique ID)")
            GlassTextField(adminName, { adminName = it }, "College Admin Full Name", imeAction = ImeAction.Done)
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
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var hub by remember { mutableStateOf("") }
    var partner by remember { mutableStateOf("") }
    var adminUser by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }

    GlassDialog(
        title = "Add Screening Center & Admin",
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Create", enabled = code.isNotBlank() && name.isNotBlank(), onClick = {
                val cleanCode = code.trim().uppercase()
                val finalAdminUser = if (adminUser.isNotBlank()) adminUser.trim() else "${cleanCode.lowercase()}_admin"
                val finalAdminName = if (adminName.isNotBlank()) adminName.trim() else "${name.trim()} Admin"

                onSubmit(
                    CenterCreateDto(
                        collegeId = if (requireCollegePicker) selectedCollegeId else null,
                        centerCode = cleanCode,
                        name = name.trim(),
                        contactEmail = email.trim().ifBlank { null },
                        contactPhone = phone.trim().ifBlank { null },
                        hubName = hub.trim().ifBlank { null },
                        healthPartner = partner.trim().ifBlank { null },
                        createAdminAccount = true,
                        adminUsername = finalAdminUser,
                        adminFullName = finalAdminName,
                        adminEmail = email.trim().ifBlank { null },
                        adminPhone = phone.trim().ifBlank { null },
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
            GlassTextField(code, { 
                code = it 
                val clean = it.trim().lowercase()
                if (adminUser.isBlank() || adminUser.endsWith("_admin")) {
                    adminUser = if (clean.isNotBlank()) "${clean}_admin" else ""
                }
            }, "Center Code (e.g., CTR01)")
            GlassTextField(name, { 
                name = it 
                val clean = it.trim()
                if (adminName.isBlank() || adminName.endsWith(" Admin")) {
                    adminName = if (clean.isNotBlank()) "$clean Admin" else ""
                }
            }, "Center Name")
            GlassTextField(email, { email = it }, "Email ID (e.g., center@simats.edu)", keyboardType = KeyboardType.Email)
            GlassTextField(phone, { phone = it }, "Mobile Number (e.g., +91 9876543210)", keyboardType = KeyboardType.Phone)
            GlassTextField(hub, { hub = it }, "Hub Name (Optional)")
            GlassTextField(partner, { partner = it }, "Health Partner (Optional)")
            GlassTextField(adminUser, { adminUser = it }, "Center Admin Username (Unique ID)")
            GlassTextField(adminName, { adminName = it }, "Center Admin Full Name", imeAction = ImeAction.Done)
            Text(
                text = "An administrator account with login credentials will be generated automatically upon center creation.",
                style = MaterialTheme.typography.bodySmall,
                color = OralCareColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var empCode by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }

    val canSubmit = username.isNotBlank() && fullName.isNotBlank() && empCode.isNotBlank() && (!requireCenterPicker || selectedCenterId != null)

    GlassDialog(
        title = "Create Health Organizer Assistant",
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Create Account", enabled = canSubmit, onClick = {
                onSubmit(
                    OrganizerCreateDto(
                        centerId = if (requireCenterPicker) selectedCenterId else null,
                        username = username.trim(),
                        fullName = fullName.trim(),
                        email = email.trim().ifBlank { null },
                        phone = phone.trim().ifBlank { null },
                        employeeCode = empCode.trim().uppercase(),
                        assignedArea = area.trim().ifBlank { null },
                    )
                )
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (requireCenterPicker) {
                if (centers.isNotEmpty()) {
                    val selectedName = centers.firstOrNull { it.id == selectedCenterId }?.name ?: centers.first().name
                    GlassDropdown(
                        label = "Assigned Screening Center",
                        selectedValue = selectedName,
                        options = centers.map { it.name },
                        onOptionSelected = { picked -> selectedCenterId = centers.firstOrNull { it.name == picked }?.id },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(OralCareColors.AbnormalRedBg)
                            .padding(10.dp),
                    ) {
                        Text(
                            "No Screening Centers available yet. Please add a Screening Center before onboarding health assistants.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OralCareColors.AbnormalRed,
                        )
                    }
                }
            }
            GlassTextField(username, { username = it }, "Login Username (e.g., ast_john)")
            GlassTextField(fullName, { fullName = it }, "Full Name")
            GlassTextField(email, { email = it }, "Email ID (Optional)", keyboardType = KeyboardType.Email)
            GlassTextField(phone, { phone = it }, "Mobile Number (Optional)", keyboardType = KeyboardType.Phone)
            GlassTextField(empCode, { empCode = it }, "Employee / Staff Code (e.g., EMP012)")
            GlassTextField(area, { area = it }, "Assigned Area / Ward (Optional)", imeAction = ImeAction.Done)
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
            GlassTextField(desc, { desc = it }, "Description", imeAction = ImeAction.Done)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    valueColor: Color = OralCareColors.TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.44f),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = OralCareColors.TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OralCareColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.56f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityHistoryList(
    activities: List<ActivityLogDto>,
    isLoading: Boolean,
    showUser: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OralCareColors.SecondaryBackground)
            .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = OralCareColors.AccentBlueStrong,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Recorded Activity History",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OralCareColors.AccentBlueStrong,
                )
            }
            if (activities.isNotEmpty()) {
                Text(
                    "${activities.size} logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = OralCareColors.TextSecondary,
                )
            }
        }

        when {
            isLoading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OralCareColors.AccentBlueStrong,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Loading activity logs...",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                    )
                }
            }
            activities.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No recorded activities found for this entity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                    )
                }
            }
            else -> {
                activities.take(20).forEach { act ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(BorderStroke(0.5.dp, OralCareColors.PastelBlue), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                StatusPill(
                                    text = act.action,
                                    bg = when (act.action.uppercase()) {
                                        "CREATE" -> OralCareColors.NormalGreenBg
                                        "UPDATE" -> OralCareColors.LightBlue
                                        "DELETE" -> OralCareColors.AbnormalRedBg
                                        "LOGIN" -> Color(0xFFFFF4DE)
                                        else -> Color(0xFFEFEFEF)
                                    },
                                    fg = when (act.action.uppercase()) {
                                        "CREATE" -> OralCareColors.NormalGreen
                                        "UPDATE" -> OralCareColors.AccentBlueStrong
                                        "DELETE" -> OralCareColors.AbnormalRed
                                        "LOGIN" -> Color(0xFFB57D00)
                                        else -> Color(0xFF555555)
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = act.entityType + (act.entityId?.let { " #$it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OralCareColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = act.createdAt?.replace("T", " ")?.take(16) ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = OralCareColors.TextSecondary,
                            )
                        }
                        if (showUser && (!act.username.isNullOrBlank() || !act.userFullName.isNullOrBlank())) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "By: ${act.userFullName ?: act.username} (@${act.username ?: ""}) • ${act.role}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = OralCareColors.AccentBlueStrong,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val detailStr = act.details?.toString()?.takeIf { it.isNotBlank() && it != "{}" }
                        if (detailStr != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                detailStr,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = OralCareColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollegeDetailModal(
    college: CollegeDto,
    onDismiss: () -> Unit,
    onFetchActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    allCenters: List<CenterDto> = emptyList(),
    allUsers: List<UserDto> = emptyList(),
) {
    var activities by remember { mutableStateOf<List<ActivityLogDto>>(emptyList()) }
    var isLoadingActivities by remember { mutableStateOf(true) }

    LaunchedEffect(college.id) {
        if (onFetchActivities != null) {
            isLoadingActivities = true
            try {
                activities = onFetchActivities(college.id)
            } finally {
                isLoadingActivities = false
            }
        } else {
            isLoadingActivities = false
        }
    }

    val collegeCenters = remember(college.id, allCenters) {
        allCenters.filter { it.collegeId == college.id }
    }
    val collegeUsers = remember(college.id, allUsers) {
        allUsers.filter { it.collegeId == college.id }
    }

    GlassDialog(
        title = "Dental College Details",
        onDismiss = onDismiss,
        confirmButton = { GlassButton("Close", onClick = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.LightBlue)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OralCareColors.AccentBlueStrong),
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        college.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Code: ${college.collegeCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        text = if (college.isActive) "Active" else "Inactive",
                        bg = if (college.isActive) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                        fg = if (college.isActive) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.SecondaryBackground)
                    .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                DetailRow("Location", listOfNotNull(college.city, college.state).joinToString(", ").ifBlank { "Not set" }, Icons.Default.LocationOn)
                DetailRow("Address", college.address ?: "Not set", Icons.Default.Home)
                DetailRow("Contact Email", college.contactEmail ?: "Not set", Icons.Default.Email)
                DetailRow("Contact Phone", college.contactPhone ?: "Not set", Icons.Default.Phone)
                DetailRow("Admin Account", if (!college.adminUsername.isNullOrBlank()) "@${college.adminUsername}" else "None", Icons.Default.AdminPanelSettings)
                DetailRow("Screening Centers", "${collegeCenters.size} centers", Icons.Default.LocalHospital)
                DetailRow("Staff Members", "${collegeUsers.size} users", Icons.Default.Group)
            }

            if (collegeCenters.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(OralCareColors.SecondaryBackground)
                        .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Affiliated Centers (${collegeCenters.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.AccentBlueStrong,
                    )
                    collegeCenters.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = c.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(c.centerCode, style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                        }
                    }
                }
            }

            ActivityHistoryList(
                activities = activities,
                isLoading = isLoadingActivities,
                showUser = true,
            )
        }
    }
}

@Composable
fun CenterDetailModal(
    center: CenterDto,
    onDismiss: () -> Unit,
    onFetchActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    allUsers: List<UserDto> = emptyList(),
) {
    var activities by remember { mutableStateOf<List<ActivityLogDto>>(emptyList()) }
    var isLoadingActivities by remember { mutableStateOf(true) }

    LaunchedEffect(center.id) {
        if (onFetchActivities != null) {
            isLoadingActivities = true
            try {
                activities = onFetchActivities(center.id)
            } finally {
                isLoadingActivities = false
            }
        } else {
            isLoadingActivities = false
        }
    }

    val centerStaff = remember(center.id, allUsers) {
        allUsers.filter { it.centerId == center.id }
    }

    GlassDialog(
        title = "Screening Center Details",
        onDismiss = onDismiss,
        confirmButton = { GlassButton("Close", onClick = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.LightBlue)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OralCareColors.AccentBlueStrong),
                ) {
                    Icon(
                        Icons.Default.LocalHospital,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        center.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Code: ${center.centerCode} • Hub: ${center.hubName ?: "General"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        text = if (center.isActive) "Active" else "Inactive",
                        bg = if (center.isActive) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                        fg = if (center.isActive) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.SecondaryBackground)
                    .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                DetailRow("Hub Name", center.hubName ?: "General", Icons.Default.Business)
                DetailRow("Health Partner", center.healthPartner ?: "Not specified", Icons.Default.Business)
                DetailRow("Location", listOfNotNull(center.city, center.state).joinToString(", ").ifBlank { "Not set" }, Icons.Default.LocationOn)
                DetailRow("Address", center.address ?: "Not set", Icons.Default.Home)
                DetailRow("Contact Email", center.contactEmail ?: "Not set", Icons.Default.Email)
                DetailRow("Contact Phone", center.contactPhone ?: "Not set", Icons.Default.Phone)
                DetailRow("Center Admin", if (!center.adminUsername.isNullOrBlank()) "@${center.adminUsername}" else "None assigned", Icons.Default.AdminPanelSettings)
                DetailRow("Assigned Staff", "${centerStaff.size} members", Icons.Default.Group)
            }

            if (centerStaff.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(OralCareColors.SecondaryBackground)
                        .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Assigned Staff (${centerStaff.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OralCareColors.AccentBlueStrong,
                    )
                    centerStaff.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = s.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(s.role.replace("_", " "), style = MaterialTheme.typography.bodySmall, color = OralCareColors.TextSecondary)
                        }
                    }
                }
            }

            ActivityHistoryList(
                activities = activities,
                isLoading = isLoadingActivities,
                showUser = true,
            )
        }
    }
}

@Composable
fun UserDetailModal(
    user: UserDto,
    onDismiss: () -> Unit,
    onFetchActivities: (suspend (Int) -> List<ActivityLogDto>)? = null,
    allColleges: List<CollegeDto> = emptyList(),
    allCenters: List<CenterDto> = emptyList(),
) {
    var activities by remember { mutableStateOf<List<ActivityLogDto>>(emptyList()) }
    var isLoadingActivities by remember { mutableStateOf(true) }

    LaunchedEffect(user.id) {
        if (onFetchActivities != null) {
            isLoadingActivities = true
            try {
                activities = onFetchActivities(user.id)
            } finally {
                isLoadingActivities = false
            }
        } else {
            isLoadingActivities = false
        }
    }

    val assignedCollege = remember(user.collegeId, allColleges) {
        allColleges.firstOrNull { it.id == user.collegeId }?.name
    }
    val assignedCenter = remember(user.centerId, allCenters) {
        allCenters.firstOrNull { it.id == user.centerId }?.name
    }

    GlassDialog(
        title = "User Profile & History",
        onDismiss = onDismiss,
        confirmButton = { GlassButton("Close", onClick = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.LightBlue)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OralCareColors.AccentBlueStrong),
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${user.username} • UID: ${user.userUid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = user.role.replace("_", " "),
                            bg = OralCareColors.LightBlue,
                            fg = OralCareColors.AccentBlueStrong,
                        )
                        StatusPill(
                            text = if (user.isActive) "Active" else "Inactive",
                            bg = if (user.isActive) OralCareColors.NormalGreenBg else OralCareColors.AbnormalRedBg,
                            fg = if (user.isActive) OralCareColors.NormalGreen else OralCareColors.AbnormalRed,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.SecondaryBackground)
                    .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                DetailRow("Username", "@${user.username}", Icons.Default.AccountCircle)
                DetailRow("User UID", user.userUid, Icons.Default.Badge)
                DetailRow("Email", user.email ?: "Not configured", Icons.Default.Email)
                DetailRow("Phone", user.phone ?: "Not configured", Icons.Default.Phone)
                if (assignedCollege != null) {
                    DetailRow("Affiliated College", assignedCollege, Icons.Default.AccountBalance)
                }
                if (assignedCenter != null) {
                    DetailRow("Assigned Center", assignedCenter, Icons.Default.LocalHospital)
                }
                DetailRow("Last Login", user.lastLoginAt?.replace("T", " ")?.take(16) ?: "Never", Icons.Default.Schedule)
                DetailRow("Member Since", user.createdAt?.replace("T", " ")?.take(10) ?: "N/A", Icons.Default.DateRange)
            }

            ActivityHistoryList(
                activities = activities,
                isLoading = isLoadingActivities,
                showUser = false,
            )
        }
    }
}

@Composable
fun CredentialsIssuedDialog(
    credentials: IssuedCredentialsDto?,
    fallbackBanner: String?,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    val username = credentials?.username
        ?: fallbackBanner?.lines()?.find { it.contains("Username:", ignoreCase = true) }?.substringAfter(":")?.trim()
        ?: ""
    val userUid = credentials?.userUid
        ?: fallbackBanner?.lines()?.find { it.contains("User ID:", ignoreCase = true) }?.substringAfter(":")?.trim()
    val password = credentials?.temporaryPassword
        ?: fallbackBanner?.lines()?.find { it.contains("Temporary Password:", ignoreCase = true) }?.substringAfter(":")?.trim()
        ?: ""
    val title = credentials?.title ?: "Credentials Issued"
    val entityName = credentials?.entityName

    val fullCredentialsShareText = buildString {
        appendLine("--- OralCare Clinical Platform ---")
        appendLine("Role: ${credentials?.role ?: "Authorized Staff"}")
        if (!entityName.isNullOrBlank()) {
            appendLine("Affiliation: $entityName")
        }
        appendLine("Username: $username")
        if (!userUid.isNullOrBlank()) {
            appendLine("User ID (UID): $userUid")
        }
        appendLine("Temporary Password: $password")
        appendLine("\nPlease log in to the OralCare SDC application and update your password on first sign-in.")
    }

    GlassDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = {
            GlassButton("Done", onClick = onDismiss)
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "OralCare SDC - Login Credentials")
                        putExtra(Intent.EXTRA_TEXT, fullCredentialsShareText)
                    }
                    val chooser = Intent.createChooser(sendIntent, "Share Login Credentials")
                    context.startActivity(chooser)
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, OralCareColors.AccentBlueStrong),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = OralCareColors.AccentBlueStrong,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Share",
                    color = OralCareColors.AccentBlueStrong,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OralCareColors.NormalGreenBg)
                    .border(BorderStroke(0.75.dp, Color(0xFFC7EED8)), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = OralCareColors.NormalGreen,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (!entityName.isNullOrBlank()) "$entityName Onboarded" else "Account Provisioned",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F5132),
                    )
                    Text(
                        text = "Share or copy credentials for the assigned user.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OralCareColors.TextSecondary,
                    )
                }
            }

            if (username.isNotBlank()) {
                CredentialCopyBox(
                    label = "Username / Login ID",
                    value = username,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(username))
                        Toast.makeText(context, "Username copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (!userUid.isNullOrBlank()) {
                CredentialCopyBox(
                    label = "Unique User ID (UID)",
                    value = userUid,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(userUid))
                        Toast.makeText(context, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (password.isNotBlank()) {
                CredentialCopyBox(
                    label = "Temporary Password",
                    value = if (passwordVisible) password else "•".repeat(password.length.coerceAtMost(14)),
                    isPassword = true,
                    isPasswordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(password))
                        Toast.makeText(context, "Temporary password copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            GlassButton(
                text = "Copy All Credentials",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    clipboardManager.setText(AnnotatedString(fullCredentialsShareText))
                    Toast.makeText(context, "All credentials copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CredentialCopyBox(
    label: String,
    value: String,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OralCareColors.SecondaryBackground)
            .border(BorderStroke(0.75.dp, OralCareColors.PastelBlue), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OralCareColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = OralCareColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPassword && onToggleVisibility != null) {
                    IconButton(onClick = onToggleVisibility, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = OralCareColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = OralCareColors.AccentBlueStrong,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
