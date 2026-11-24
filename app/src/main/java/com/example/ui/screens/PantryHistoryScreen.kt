package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.ProduceAnalysisResult
import com.example.model.RipenessStage
import com.example.ui.components.ProduceArtworkImage
import com.example.ui.components.RipenessBadge
import com.example.ui.i18n.UiStrings
import com.example.ui.i18n.stringsFor
import com.example.util.ExpiryReminderManager
import com.example.util.ExpiryUrgency
import com.example.util.FreshnessProjection
import com.example.util.FreshnessTracker
import com.example.util.ScanStorageStats

private enum class PantryFilterKey {
    ALL, USE_SOON, PERFECTLY_RIPE, UNDERRIPE
}

@Composable
fun PantryHistoryScreen(
    scans: List<ProduceAnalysisResult>,
    storageStats: ScanStorageStats = ScanStorageStats(),
    language: AppLanguage,
    expiryRemindersEnabled: Boolean = true,
    onSetExpiryReminders: (Boolean) -> Unit = {},
    onTriggerExpiryCheck: () -> Unit = {},
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectScan: (ProduceAnalysisResult) -> Unit,
    onDeleteScan: (String) -> Unit,
    onClearAllScans: () -> Unit = {},
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = stringsFor(language)
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(PantryFilterKey.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(ExpiryReminderManager.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            onSetExpiryReminders(true)
            onTriggerExpiryCheck()
        }
    }

    val projectedItems = remember(scans) {
        scans.map { item -> item to FreshnessTracker.evaluate(item) }
    }

    val urgentItems = remember(projectedItems) {
        projectedItems.filter { (_, projection) -> projection.urgency != ExpiryUrgency.FRESH }
    }

    val filteredScans = remember(projectedItems, selectedFilter, searchQuery) {
        val query = searchQuery.trim().lowercase()
        projectedItems.filter { (item, projection) ->
            val matchesFilter = when (selectedFilter) {
                PantryFilterKey.USE_SOON -> projection.urgency != ExpiryUrgency.FRESH
                PantryFilterKey.PERFECTLY_RIPE -> projection.projectedStage == RipenessStage.PERFECTLY_RIPE
                PantryFilterKey.UNDERRIPE -> projection.projectedStage == RipenessStage.UNDERRIPE
                PantryFilterKey.ALL -> true
            }
            val matchesQuery = query.isEmpty() ||
                item.produceName.lowercase().contains(query) ||
                item.variety.lowercase().contains(query) ||
                item.category.lowercase().contains(query)
            matchesFilter && matchesQuery
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(strings.pantryClearDialogTitle) },
            text = { Text(strings.pantryClearDialogBody) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAllScans()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_pantry_button")
                ) {
                    Text(strings.confirmClearLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_clear_pantry_button")
                ) {
                    Text(strings.cancelLabel)
                }
            }
        )
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.pantryTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.pantrySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                LanguageTogglePill(
                    selected = language,
                    onSelect = onSelectLanguage
                )
            }

            if (scans.isNotEmpty() || storageStats.fileCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.pantryStorageSummary(
                                    scans.size,
                                    storageStats.formattedSize(language)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("pantry_storage_summary")
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_all_pantry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = strings.pantryClearAllLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (urgentItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                val previewNames = urgentItems.take(3).joinToString(", ") { it.first.produceName }
                val alertsReady = expiryRemindersEnabled && hasNotificationPermission
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .testTag("pantry_expiry_alert_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.pantryExpiryAlertTitle(urgentItems.size),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (alertsReady) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    modifier = Modifier.testTag("alerts_active_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsActive,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = strings.alertsActiveBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = strings.pantryExpiryAlertBody(previewNames),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (!alertsReady) {
                            FilledTonalButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !ExpiryReminderManager.hasNotificationPermission(context)
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onSetExpiryReminders(true)
                                        onTriggerExpiryCheck()
                                    }
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("enable_expiry_alerts_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.enableAlertsButton,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            if (scans.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = strings.pantrySearchHint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = strings.dismissContentDesc
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pantry_search_input")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PantryFilterKey.entries) { filter ->
                    val label = when (filter) {
                        PantryFilterKey.ALL -> strings.pantryFilterAll
                        PantryFilterKey.USE_SOON -> strings.pantryFilterSoon
                        PantryFilterKey.PERFECTLY_RIPE -> strings.pantryFilterRipe
                        PantryFilterKey.UNDERRIPE -> strings.pantryFilterUnder
                    }
                    val leadingIcon = when (filter) {
                        PantryFilterKey.ALL -> Icons.Rounded.AutoAwesome
                        PantryFilterKey.USE_SOON -> Icons.Rounded.Schedule
                        PantryFilterKey.PERFECTLY_RIPE -> Icons.Rounded.CheckCircle
                        PantryFilterKey.UNDERRIPE -> Icons.Rounded.Eco
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        leadingIcon = {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredScans.isEmpty()) {
                EmptyPantryState(
                    hasAnyScans = scans.isNotEmpty(),
                    language = language,
                    onNavigateToScanner = onNavigateToScanner,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pantry_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredScans, key = { it.first.id }) { (item, projection) ->
                        PantryScanCard(
                            item = item,
                            projection = projection,
                            language = language,
                            strings = strings,
                            onClick = { onSelectScan(item) },
                            onDelete = { onDeleteScan(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PantryScanCard(
    item: ProduceAnalysisResult,
    projection: FreshnessProjection,
    language: AppLanguage,
    strings: UiStrings,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val urgencyColor = when (projection.urgency) {
        ExpiryUrgency.EXPIRED -> MaterialTheme.colorScheme.errorContainer
        ExpiryUrgency.USE_TODAY -> MaterialTheme.colorScheme.tertiaryContainer
        ExpiryUrgency.EXPIRING_SOON -> MaterialTheme.colorScheme.secondaryContainer
        ExpiryUrgency.FRESH -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val urgencyTextColor = when (projection.urgency) {
        ExpiryUrgency.EXPIRED -> MaterialTheme.colorScheme.onErrorContainer
        ExpiryUrgency.USE_TODAY -> MaterialTheme.colorScheme.onTertiaryContainer
        ExpiryUrgency.EXPIRING_SOON -> MaterialTheme.colorScheme.onSecondaryContainer
        ExpiryUrgency.FRESH -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val urgencyLabel = when (projection.urgency) {
        ExpiryUrgency.EXPIRED -> strings.expiryExpiredLabel
        ExpiryUrgency.USE_TODAY -> strings.expiryUseTodayLabel
        ExpiryUrgency.EXPIRING_SOON -> strings.expiryTomorrowLabel
        ExpiryUrgency.FRESH -> strings.expiryDaysLeftLabel(projection.remainingDaysMax.coerceAtLeast(1))
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("pantry_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProduceArtworkImage(
                localImagePath = item.localImagePath,
                sampleKey = item.sampleKey,
                contentDescription = item.produceName,
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RipenessBadge(stage = projection.projectedStage, language = language)
                    val metaLabel = buildString {
                        append("~${item.estimatedWeightGrams}${strings.weightUnit}")
                        if (item.estimatedCaloriesKcal > 0) {
                            append(" · ~${item.estimatedCaloriesKcal} ${strings.kcalUnit}")
                        }
                    }
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = item.produceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = urgencyColor
                ) {
                    Text(
                        text = urgencyLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = urgencyTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = strings.scannedAtLabel(
                        FreshnessTracker.formatScanTimestamp(item.scannedAtMillis, language)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (projection.hasRipenessChanged) {
                    val deltaText = if (projection.scoreDelta < 0) {
                        strings.ripenessDroppedLabel(
                            projection.originalScore,
                            projection.projectedScore
                        )
                    } else {
                        strings.ripenessImprovedLabel(
                            projection.originalScore,
                            projection.projectedScore
                        )
                    }
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (projection.scoreDelta < 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_pantry_item_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = strings.deleteItemContentDesc(item.produceName),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyPantryState(
    hasAnyScans: Boolean,
    language: AppLanguage,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = stringsFor(language)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_sample_tomatoes_1795260015565),
            contentDescription = strings.pantryTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(32.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (hasAnyScans) strings.pantryEmptyFilterTitle else strings.pantryEmptyTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = strings.pantryEmptySubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onNavigateToScanner,
            shape = CircleShape,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("empty_pantry_scan_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.scanProduceButton)
        }
    }
}
