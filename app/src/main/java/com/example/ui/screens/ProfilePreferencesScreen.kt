package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.SpatialScaleHint
import com.example.model.UserPreferences
import com.example.ui.i18n.localizePreferenceOption
import com.example.ui.i18n.stringsFor
import com.example.util.ExpiryReminderManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePreferencesScreen(
    preferences: UserPreferences,
    hasHardwareDepthSensor: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleNutrientNeed: (String) -> Unit,
    onToggleDietaryGoal: (String) -> Unit,
    onToggleHealthFocus: (String) -> Unit,
    onToggleCookingTool: (String) -> Unit,
    onSetLightingCalibration: (Boolean) -> Unit,
    onSetSpatialMode: (Boolean) -> Unit,
    onSetSpatialScaleHint: (SpatialScaleHint) -> Unit,
    onSetExpiryReminders: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = stringsFor(preferences.language)
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetExpiryReminders(true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.personalizeTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.personalizeSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                LanguageTogglePill(
                    selected = preferences.language,
                    onSelect = onSelectLanguage,
                    tagPrefix = "profile_lang"
                )
            }

            // Active AI Personalization Summary Banner
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    val activeNeeds = preferences.nutrientNeeds
                        .map { localizePreferenceOption(it, preferences.language) }
                        .joinToString(", ")
                        .ifBlank { localizePreferenceOption("Whole Foods", preferences.language) }
                    val activeGoals = preferences.dietaryGoals
                        .map { localizePreferenceOption(it, preferences.language) }
                        .joinToString(", ")
                    Text(
                        text = listOf(activeNeeds, activeGoals).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Specific Nutrient & Deficiency Needs (Fiber, Vitamin D, Iron, etc.)
            PreferenceSectionCard(
                title = strings.nutrientNeedsTitle,
                subtitle = strings.nutrientNeedsSubtitle,
                options = UserPreferences.ALL_NUTRIENT_NEEDS,
                selected = preferences.nutrientNeeds,
                language = preferences.language,
                tagPrefix = "nutrient_need",
                onToggle = onToggleNutrientNeed
            )

            // Dietary & Weight Loss / Dieting Goals
            PreferenceSectionCard(
                title = strings.dietaryGoalsTitle,
                subtitle = strings.dietaryGoalsSubtitle,
                options = UserPreferences.ALL_DIETARY_GOALS,
                selected = preferences.dietaryGoals,
                language = preferences.language,
                tagPrefix = "dietary_goal",
                onToggle = onToggleDietaryGoal
            )

            // Health Focus
            PreferenceSectionCard(
                title = strings.healthFocusTitle,
                subtitle = strings.healthFocusSubtitle,
                options = UserPreferences.ALL_HEALTH_FOCUSES,
                selected = preferences.healthFocuses,
                language = preferences.language,
                tagPrefix = "health_focus",
                onToggle = onToggleHealthFocus
            )

            // Cooking Tools
            PreferenceSectionCard(
                title = strings.cookingToolsTitle,
                subtitle = strings.cookingToolsSubtitle,
                options = UserPreferences.ALL_COOKING_TOOLS,
                selected = preferences.cookingTools,
                language = preferences.language,
                tagPrefix = "cooking_tool",
                onToggle = onToggleCookingTool
            )

            // 3D Spatial Depth & Size Measurement Preferences
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ViewInAr,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.spatialCalibrationTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.spatialCalibrationSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (hasHardwareDepthSensor) strings.hardwareDepthActive else strings.aiSpatialDepth,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(
                            checked = preferences.spatialModeEnabled,
                            onCheckedChange = onSetSpatialMode,
                            modifier = Modifier.testTag("spatial_mode_switch")
                        )
                    }

                    if (preferences.spatialModeEnabled) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SpatialScaleHint.entries.forEach { hint ->
                                val isSelected = preferences.spatialScaleHint == hint
                                val label = if (preferences.language == AppLanguage.MONGOLIAN) {
                                    hint.labelMn
                                } else {
                                    hint.labelEn
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetSpatialScaleHint(hint) },
                                    label = { Text(label) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("profile_scale_${hint.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Supermarket Lighting Calibration Toggle
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.lightingCalibrationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.lightingCalibrationSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.lightingCalibrationEnabled,
                        onCheckedChange = onSetLightingCalibration,
                        modifier = Modifier.testTag("lighting_calibration_switch")
                    )
                }
            }

            // Expiry & Ripeness Reminders Toggle
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.expiryRemindersTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.expiryRemindersSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.expiryRemindersEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !ExpiryReminderManager.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onSetExpiryReminders(enabled)
                        },
                        modifier = Modifier.testTag("expiry_reminders_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferenceSectionCard(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: Set<String>,
    language: AppLanguage,
    tagPrefix: String,
    onToggle: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option in selected
                    val displayLabel = localizePreferenceOption(option, language)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(option) },
                        label = { Text(displayLabel) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null
                                )
                            }
                        } else {
                            null
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("${tagPrefix}_${option.lowercase().replace(" ", "_")}")
                    )
                }
            }
        }
    }
}
