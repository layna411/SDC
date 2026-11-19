package com.simats.sdc.presentation.screening

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.theme.OralCareColors
import com.simats.sdc.data.dto.HabitItemDto
import com.simats.sdc.presentation.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private suspend fun fetchPlaceName(context: Context, latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
    try {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        val addr = addresses?.firstOrNull() ?: return@withContext null

        val line = addr.getAddressLine(0)
        if (!line.isNullOrBlank()) {
            return@withContext line
        }
        val parts = listOfNotNull(
            addr.featureName,
            addr.subLocality,
            addr.locality ?: addr.subAdminArea,
            addr.adminArea,
        ).filter { it.isNotBlank() }.distinct()
        if (parts.isNotEmpty()) parts.joinToString(", ") else null
    } catch (_: Exception) {
        null
    }
}

@SuppressLint("MissingPermission")
@Composable
fun Step1ConsentLocation(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun updateWithLocation(loc: Location) {
        val lat = loc.latitude
        val lng = loc.longitude
        onUpdate {
            it.copy(
                locationPermissionGranted = true,
                latitude = lat,
                longitude = lng,
                geolocationLabel = if (it.geolocationLabel.isBlank() || it.geolocationLabel.startsWith("GPS Captured") || it.geolocationLabel.startsWith("Screening Site (")) {
                    "Detecting place name..."
                } else it.geolocationLabel,
            )
        }
        coroutineScope.launch {
            val place = fetchPlaceName(context, lat, lng)
            val finalName = place ?: "Screening Site (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)})"
            onUpdate {
                it.copy(
                    geolocationLabel = finalName,
                )
            }
        }
    }

    fun requestLocationCapture() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {
            null
        }

        if (loc != null) {
            updateWithLocation(loc)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { freshLoc ->
                        if (freshLoc != null) {
                            updateWithLocation(freshLoc)
                        }
                    }
                } catch (_: SecurityException) {}
            }
            onUpdate { it.copy(locationPermissionGranted = true) }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            requestLocationCapture()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 1: Consent & Location", subtitle = "Informed consent is mandatory prior to clinical screening")

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(state.hub, { v -> onUpdate { it.copy(hub = v) } }, "Hub *")
                GlassTextField(state.healthPartner, { v -> onUpdate { it.copy(healthPartner = v) } }, "Health Partner *")
                GlassTextField(state.healthworkerName, { v -> onUpdate { it.copy(healthworkerName = v) } }, "Healthworker (Health Organizer Assistant) *")
            }
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Geolocation Capture (Permission Required)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                GlassTextField(state.geolocationLabel, { v -> onUpdate { it.copy(geolocationLabel = v) } }, "Screening Site / Camp Location")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(
                        value = state.latitude?.toString() ?: "",
                        onValueChange = { v -> onUpdate { it.copy(latitude = v.toDoubleOrNull()) } },
                        label = "Latitude",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = state.longitude?.toString() ?: "",
                        onValueChange = { v -> onUpdate { it.copy(longitude = v.toDoubleOrNull()) } },
                        label = "Longitude",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                GlassButton(
                    text = if (state.locationPermissionGranted) "Refresh GPS Coordinates" else "Capture Geolocation",
                    isPrimary = false,
                    icon = Icons.Default.MyLocation,
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            requestLocationCapture()
                        } else {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GlassCard(contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Required Informed Consent Declarations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.TextPrimary)
                CheckboxRow("Participant Information Sheet explained & acknowledged", state.participantInfoSheetAck) { c ->
                    onUpdate { it.copy(participantInfoSheetAck = c) }
                }
                CheckboxRow("Participant Consent Form signed / thumb impression obtained", state.consentFormSigned) { c ->
                    onUpdate { it.copy(consentFormSigned = c) }
                }
                CheckboxRow("Participant consents to oral health screening & clinical photography", state.consentAccepted) { c ->
                    onUpdate { it.copy(consentAccepted = c) }
                }
                CheckboxRow("I agree to the OralCare Terms and Conditions & Privacy Policy", state.termsAccepted) { c ->
                    onUpdate { it.copy(termsAccepted = c) }
                }
            }
        }
    }
}

@Composable
fun Step2PersonalDetails(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 2: Personal Details", subtitle = "Patient ID is generated automatically upon registration")

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(state.registrationDate, { v -> onUpdate { it.copy(registrationDate = v) } }, "Date", placeholder = "YYYY-MM-DD", modifier = Modifier.weight(1f))
                    GlassTextField("Auto-Generated", {}, "Patient ID", readOnly = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassDropdown("Prefix", state.prefix, ClinicalConstants.PREFIXES, { v -> onUpdate { it.copy(prefix = v) } }, Modifier.weight(0.42f))
                    GlassTextField(state.firstName, { v -> onUpdate { it.copy(firstName = v) } }, "First Name *", modifier = Modifier.weight(0.58f))
                }
                GlassTextField(state.lastName, { v -> onUpdate { it.copy(lastName = v) } }, "Last Name *")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(state.age, { v -> onUpdate { it.copy(age = v.filter { c -> c.isDigit() }) } }, "Age *", keyboardType = KeyboardType.Number, modifier = Modifier.weight(0.4f))
                    GlassDropdown("Sex *", state.sex, ClinicalConstants.SEX_OPTIONS, { v -> onUpdate { it.copy(sex = v) } }, Modifier.weight(0.6f))
                }
                GlassDropdown("Religion", state.religion, ClinicalConstants.RELIGIONS, { v -> onUpdate { it.copy(religion = v) } })
                GlassDropdown("Marital Status", state.maritalStatus, ClinicalConstants.MARITAL_STATUSES, { v -> onUpdate { it.copy(maritalStatus = v) } })
                GlassDropdown("Education", state.education, ClinicalConstants.EDUCATION_LEVELS, { v -> onUpdate { it.copy(education = v) } })
                GlassTextField(state.occupation, { v -> onUpdate { it.copy(occupation = v) } }, "Occupation")
                GlassDropdown("Family Income / Month", state.familyIncomePerMonth, ClinicalConstants.INCOME_BRACKETS, { v -> onUpdate { it.copy(familyIncomePerMonth = v) } })
                GlassTextField(state.phoneNumber, { v -> onUpdate { it.copy(phoneNumber = v) } }, "Phone Number *", keyboardType = KeyboardType.Phone, leadingIcon = Icons.Default.Phone)
                GlassTextField(state.address, { v -> onUpdate { it.copy(address = v) } }, "Residential Address *", singleLine = false, maxLines = 3)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step3PersonalHistory(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 3: Personal History", subtitle = "Dietary patterns and risk habit screening")

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassDropdown("Diet History", state.dietHistory, ClinicalConstants.DIET_HISTORIES, { v -> onUpdate { it.copy(dietHistory = v) } })
                GlassDropdown("Spice Consumption", state.spiceConsumption, ClinicalConstants.SPICE_LEVELS, { v -> onUpdate { it.copy(spiceConsumption = v) } })
            }
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Habit History (Tobacco / Arecanut / Alcohol / Others)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("If YES, Step 4 will collect structured habit frequency, duration, and usage details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = state.hasHabitHistory,
                        onClick = { onUpdate { it.copy(hasHabitHistory = true) } },
                        label = { Text("YES – Record Habits") },
                    )
                    FilterChip(
                        selected = !state.hasHabitHistory,
                        onClick = { onUpdate { it.copy(hasHabitHistory = false) } },
                        label = { Text("NO – No Habits") },
                    )
                }
            }
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            GlassTextField(state.personalHistoryNotes, { v -> onUpdate { it.copy(personalHistoryNotes = v) } }, "Additional Notes (Optional)", singleLine = false)
        }
    }
}

@Composable
fun Step4HabitHistory(
    state: ScreeningWizardState,
    onAddHabit: (HabitItemDto) -> Unit,
    onRemoveHabit: (Int) -> Unit,
) {
    var category by remember { mutableStateOf("TOBACCO") }
    var type by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var usageStatus by remember { mutableStateOf("CURRENT") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 4: Habit History", subtitle = "Structured clinical history for tobacco, arecanut, alcohol, and other habits")

        if (!state.hasHabitHistory) {
            EmptyStateView(
                title = "No Habit History Reported",
                subtitle = "Step 3 indicated NO habits. You can proceed directly to Step 5 – Medical & Dental History.",
                icon = Icons.Default.CheckCircleOutline,
            )
            return
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Habit Entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                GlassDropdown("Habit Category", category, ClinicalConstants.HABIT_CATEGORIES, { category = it })
                GlassTextField(type, { type = it }, "Specific Type", placeholder = "e.g., Cigarette, Gutka, Betel Quid")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(frequency, { frequency = it }, "Frequency", placeholder = "e.g., 3/day", modifier = Modifier.weight(1f))
                    GlassTextField(duration, { duration = it }, "Duration", placeholder = "e.g., 5 yrs", modifier = Modifier.weight(1f))
                }
                GlassTextField(quantity, { quantity = it }, "Quantity", placeholder = "e.g., 2 packets/day")
                GlassDropdown("Usage Status", usageStatus, ClinicalConstants.HABIT_USAGE_STATUSES, { usageStatus = it })
                GlassButton(
                    text = "Add Habit to Record",
                    icon = Icons.Default.Add,
                    onClick = {
                        onAddHabit(
                            HabitItemDto(
                                habitCategory = category,
                                habitType = type.ifBlank { null },
                                frequency = frequency.ifBlank { null },
                                duration = duration.ifBlank { null },
                                quantity = quantity.ifBlank { null },
                                usageStatus = usageStatus,
                            )
                        )
                        type = ""; frequency = ""; duration = ""; quantity = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.habits.forEachIndexed { idx, habit ->
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${habit.habitCategory} (${habit.usageStatus})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(habit.habitType, habit.frequency, habit.duration, habit.quantity).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = OralCareColors.TextSecondary,
                        )
                    }
                    IconButton(onClick = { onRemoveHabit(idx) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = OralCareColors.AbnormalRed)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step5MedicalDentalHistory(
    state: ScreeningWizardState,
    onUpdate: ((ScreeningWizardState) -> ScreeningWizardState) -> Unit,
    onToggleFamily: (String) -> Unit,
    onToggleMedical: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Step 5: Medical & Dental History", subtitle = "Oral hygiene practices, family & medical history, dental & denture history")

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Oral Hygiene Practices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                GlassDropdown("Type of Cleaning Aid", state.cleaningAid, ClinicalConstants.CLEANING_AIDS, { v -> onUpdate { it.copy(cleaningAid = v) } })
                GlassDropdown("Material Used", state.materialUsed, ClinicalConstants.CLEANING_MATERIALS, { v -> onUpdate { it.copy(materialUsed = v) } })
                if (state.materialUsed == "Other") {
                    GlassTextField(state.materialOther, { v -> onUpdate { it.copy(materialOther = v) } }, "Specify Other Cleaning Material")
                }
                GlassDropdown("Method of Brushing", state.brushingMethod, ClinicalConstants.BRUSHING_METHODS, { v -> onUpdate { it.copy(brushingMethod = v) } })
                GlassDropdown("Frequency of Brushing", state.brushingFrequency, ClinicalConstants.BRUSHING_FREQUENCIES, { v -> onUpdate { it.copy(brushingFrequency = v) } })
                GlassDropdown("Duration of Brushing", state.brushingDuration, ClinicalConstants.BRUSHING_DURATIONS, { v -> onUpdate { it.copy(brushingDuration = v) } })
                GlassDropdown("Frequency of Changing Toothbrush", state.toothbrushChangeFrequency, ClinicalConstants.TOOTHBRUSH_CHANGE_FREQUENCIES, { v -> onUpdate { it.copy(toothbrushChangeFrequency = v) } })
                GlassDropdown("Usage of Other Oral Hygiene Aids", state.otherHygieneAids, ClinicalConstants.OTHER_ORAL_HYGIENE_AIDS, { v -> onUpdate { it.copy(otherHygieneAids = v) } })
            }
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Family Medical History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClinicalConstants.MEDICAL_CONDITIONS.forEach { cond ->
                        FilterChip(
                            selected = state.familyHistoryConditions.contains(cond),
                            onClick = { onToggleFamily(cond) },
                            label = { Text(cond) },
                        )
                    }
                }
                if (state.familyHistoryConditions.contains("Any Other")) {
                    GlassTextField(state.familyHistoryOther, { v -> onUpdate { it.copy(familyHistoryOther = v) } }, "Specify Other Family History")
                }
            }
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Past Medical History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClinicalConstants.MEDICAL_CONDITIONS.forEach { cond ->
                        FilterChip(
                            selected = state.pastMedicalConditions.contains(cond),
                            onClick = { onToggleMedical(cond) },
                            label = { Text(cond) },
                        )
                    }
                }
                if (state.pastMedicalConditions.contains("Any Other")) {
                    GlassTextField(state.pastMedicalOther, { v -> onUpdate { it.copy(pastMedicalOther = v) } }, "Specify Other Past Medical History")
                }
            }
        }

        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Past Dental History & Denture Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OralCareColors.AccentBlueStrong)
                Text("Past Dental History", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = state.pastDentalHistory, onClick = { onUpdate { it.copy(pastDentalHistory = true) } }, label = { Text("Yes") })
                    FilterChip(selected = !state.pastDentalHistory, onClick = { onUpdate { it.copy(pastDentalHistory = false) } }, label = { Text("No") })
                }
                if (state.pastDentalHistory) {
                    GlassTextField(state.pastDentalDetails, { v -> onUpdate { it.copy(pastDentalDetails = v) } }, "Past Dental Treatment Details", singleLine = false)
                }

                Text("Denture Usage", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = state.dentureUsage, onClick = { onUpdate { it.copy(dentureUsage = true) } }, label = { Text("Yes") })
                    FilterChip(selected = !state.dentureUsage, onClick = { onUpdate { it.copy(dentureUsage = false) } }, label = { Text("No") })
                }
                if (state.dentureUsage) {
                    GlassTextField(state.dentureDetails, { v -> onUpdate { it.copy(dentureDetails = v) } }, "Denture Type / Duration / Fit Details", singleLine = false)
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
