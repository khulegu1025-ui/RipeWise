package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.ProduceVisionAiService
import com.example.model.AppLanguage
import com.example.model.CookingSuggestion
import com.example.model.HealthBenefit
import com.example.model.NutritionHighlight
import com.example.model.ProduceAnalysisResult
import com.example.model.ReportSectionType
import com.example.model.ResultFilterTab
import com.example.model.UserPreferences
import com.example.ui.components.ProduceArtworkImage
import com.example.ui.components.ProduceChatOverlay
import com.example.ui.components.RipenessArcGauge
import com.example.ui.components.RipenessBadge
import com.example.ui.i18n.UiStrings
import com.example.ui.i18n.buildRecipeDetailsAiPrompt
import com.example.ui.i18n.localizePreferenceOption
import com.example.ui.i18n.stringsFor
import com.example.ui.viewmodel.ChatUiState
import com.example.util.ExpiryUrgency
import com.example.util.FreshnessTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProduceReportScreen(
    results: List<ProduceAnalysisResult>,
    selectedIndex: Int,
    totalAttempted: Int,
    isTranslating: Boolean,
    translationWarning: String?,
    preferences: UserPreferences,
    chatState: ChatUiState,
    onSelectBatchIndex: (Int) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onRefreshForProfile: () -> Unit = {},
    onUpdateSectionOrder: (List<ReportSectionType>) -> Unit = {},
    onDismissTranslationWarning: () -> Unit,
    onBackToScanner: () -> Unit,
    onOpenChat: () -> Unit,
    onCloseChat: () -> Unit,
    onSendChatMessage: (String) -> Unit,
    onDismissChatError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeResults = if (results.isEmpty()) return else results
    val currentIndex = selectedIndex.coerceIn(0, safeResults.lastIndex)
    val activeAnalysis = safeResults[currentIndex]
    val strings = stringsFor(preferences.language)

    var selectedFilter by rememberSaveable(safeResults.firstOrNull()?.id) {
        mutableStateOf(ResultFilterTab.ALL)
    }
    var showOrderSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = !chatState.isSheetOpen && !showOrderSheet) {
        onBackToScanner()
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWide = maxWidth >= 840.dp

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        key(safeResults.firstOrNull()?.id, safeResults.size) {
                            ReportContentList(
                                results = safeResults,
                                selectedIndex = currentIndex,
                                totalAttempted = totalAttempted,
                                isTranslating = isTranslating,
                                translationWarning = translationWarning,
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                onOpenOrderEditor = { showOrderSheet = true },
                                onSelectBatchIndex = onSelectBatchIndex,
                                onSelectLanguage = onSelectLanguage,
                                onRefreshForProfile = onRefreshForProfile,
                                onDismissTranslationWarning = onDismissTranslationWarning,
                                preferences = preferences,
                                strings = strings,
                                onBackToScanner = onBackToScanner,
                                onOpenChat = onOpenChat,
                                onQuickAsk = { question ->
                                    onOpenChat()
                                    onSendChatMessage(question)
                                },
                                showBottomCtaSpacer = false,
                                showInlineAiCard = false
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                    ) {
                        InlineAiPreviewCard(
                            analysis = activeAnalysis,
                            preferences = preferences,
                            strings = strings,
                            onOpenChat = onOpenChat,
                            onQuickAsk = { question ->
                                onOpenChat()
                                onSendChatMessage(question)
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        key(safeResults.firstOrNull()?.id, safeResults.size) {
                            ReportContentList(
                                results = safeResults,
                                selectedIndex = currentIndex,
                                totalAttempted = totalAttempted,
                                isTranslating = isTranslating,
                                translationWarning = translationWarning,
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                onOpenOrderEditor = { showOrderSheet = true },
                                onSelectBatchIndex = onSelectBatchIndex,
                                onSelectLanguage = onSelectLanguage,
                                onRefreshForProfile = onRefreshForProfile,
                                onDismissTranslationWarning = onDismissTranslationWarning,
                                preferences = preferences,
                                strings = strings,
                                onBackToScanner = onBackToScanner,
                                onOpenChat = onOpenChat,
                                onQuickAsk = { question ->
                                    onOpenChat()
                                    onSendChatMessage(question)
                                },
                                showBottomCtaSpacer = false,
                                showInlineAiCard = true,
                                modifier = Modifier.widthIn(max = 640.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !chatState.isSheetOpen,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = onOpenChat,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 420.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_ai_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${strings.askAiButton} · ${activeAnalysis.produceName}",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showOrderSheet) {
            ReportSectionOrderSheet(
                currentOrder = preferences.reportSectionOrder,
                strings = strings,
                onUpdateOrder = onUpdateSectionOrder,
                onDismiss = { showOrderSheet = false }
            )
        }

        ProduceChatOverlay(
            visible = chatState.isSheetOpen,
            analysis = activeAnalysis,
            preferences = preferences,
            chatState = chatState,
            onSendMessage = onSendChatMessage,
            onDismissError = onDismissChatError,
            onDismiss = onCloseChat
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportContentList(
    results: List<ProduceAnalysisResult>,
    selectedIndex: Int,
    totalAttempted: Int,
    isTranslating: Boolean,
    translationWarning: String?,
    selectedFilter: ResultFilterTab,
    onSelectFilter: (ResultFilterTab) -> Unit,
    onOpenOrderEditor: () -> Unit,
    onSelectBatchIndex: (Int) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onRefreshForProfile: () -> Unit,
    onDismissTranslationWarning: () -> Unit,
    preferences: UserPreferences,
    strings: UiStrings,
    onBackToScanner: () -> Unit,
    onOpenChat: () -> Unit,
    onQuickAsk: (String) -> Unit,
    showBottomCtaSpacer: Boolean,
    showInlineAiCard: Boolean,
    modifier: Modifier = Modifier
) {
    val analysis = results[selectedIndex]
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { results.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copiedNoticeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(copiedNoticeVisible) {
        if (copiedNoticeVisible) {
            delay(2200)
            copiedNoticeVisible = false
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != latestSelectedIndex) {
            onSelectBatchIndex(pagerState.settledPage)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Pinned Top Report Controls: Row 1 (Back + Category/Batch + EN/МОН), Row 2 (Copy + Share + Refresh + Reorder)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        IconButton(
                            onClick = onBackToScanner,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("report_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = strings.backContentDesc,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (results.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val prev = (pagerState.targetPage - 1).coerceAtLeast(0)
                                        coroutineScope.launch { pagerState.animateScrollToPage(prev) }
                                    },
                                    enabled = selectedIndex > 0,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("batch_prev_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = strings.prevItemContentDesc
                                    )
                                }
                                Text(
                                    text = strings.batchItemCounter(selectedIndex, results.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                IconButton(
                                    onClick = {
                                        val next = (pagerState.targetPage + 1).coerceAtMost(results.lastIndex)
                                        coroutineScope.launch { pagerState.animateScrollToPage(next) }
                                    },
                                    enabled = selectedIndex < results.lastIndex,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("batch_next_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = strings.nextItemContentDesc
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = analysis.category,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LanguageTogglePill(
                    selected = preferences.language,
                    onSelect = onSelectLanguage
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Surface(
                        shape = CircleShape,
                        color = if (copiedNoticeVisible) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                val formatted = ProduceVisionAiService.formatAnalysisForCopy(
                                    analysis = analysis,
                                    language = preferences.language
                                )
                                clipboardManager.setText(AnnotatedString(formatted))
                                copiedNoticeVisible = true
                            }
                            .testTag("copy_report_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedNoticeVisible) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = strings.copyContentDesc,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.copyInfoLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                val formatted = ProduceVisionAiService.formatAnalysisForCopy(
                                    analysis = analysis,
                                    language = preferences.language
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, analysis.produceName)
                                    putExtra(Intent.EXTRA_TEXT, formatted)
                                }
                                val chooser = Intent.createChooser(sendIntent, strings.shareChooserTitle).apply {
                                    if (context !is android.app.Activity) {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                                runCatching { context.startActivity(chooser) }
                            }
                            .testTag("share_report_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = strings.shareContentDesc,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.shareInfoLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (!analysis.localImagePath.isNullOrBlank() || !analysis.sampleKey.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable(enabled = !isTranslating) {
                                    onRefreshForProfile()
                                }
                                .testTag("refresh_report_button")
                        ) {
                            Box(
                                modifier = Modifier.padding(9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = strings.refreshForProfileContentDesc,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onOpenOrderEditor() }
                            .testTag("customize_report_order_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = strings.customizeOrderLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.customizeOrderLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("report_content_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = if (showBottomCtaSpacer) 92.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

        if (copiedNoticeVisible) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copied_confirmation_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.copiedInfoBanner,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (results.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(results, key = { idx, r -> "${r.id}_$idx" }) { idx, item ->
                            val isSelected = idx == selectedIndex
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                                    }
                                    .testTag("batch_pill_$idx")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ProduceArtworkImage(
                                        localImagePath = item.localImagePath,
                                        sampleKey = item.sampleKey,
                                        contentDescription = item.produceName,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = item.produceName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 110.dp)
                                    )
                                }
                            }
                        }
                    }

                    val skippedCount = (totalAttempted - results.size).coerceAtLeast(0)
                    if (skippedCount > 0) {
                        Text(
                            text = strings.batchSkippedNote(results.size, totalAttempted, skippedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Translating indicator or translation warning when switching language on an existing scan
        if (isTranslating) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.translatingReportLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        } else if (!translationWarning.isNullOrBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = translationWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissTranslationWarning,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.dismissContentDesc,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Swipeable Top Hero Pager
        item {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("batch_hero_pager")
            ) { page ->
                val pageResult = results[page]
                HeroProduceCard(
                    analysis = pageResult,
                    language = preferences.language,
                    strings = strings
                )
            }
        }

        // Interactive Result Focus Filters
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_filter_bar")
            ) {
                items(ResultFilterTab.entries) { filter ->
                    val isSelected = selectedFilter == filter
                    val leadingIcon = when (filter) {
                        ResultFilterTab.ALL -> Icons.Rounded.AutoAwesome
                        ResultFilterTab.RIPENESS_SIZE -> Icons.Rounded.Eco
                        ResultFilterTab.VITAMINS -> Icons.Rounded.WbSunny
                        ResultFilterTab.NUTRIENTS -> Icons.Rounded.Spa
                        ResultFilterTab.USAGE -> Icons.Rounded.RestaurantMenu
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFilter(filter) },
                        leadingIcon = {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(strings.filterLabel(filter)) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("result_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        val orderedSections = (
            preferences.reportSectionOrder.distinct() +
                ReportSectionType.entries.filterNot { it in preferences.reportSectionOrder }
            )

        orderedSections.forEach { section ->
            when (section) {
                ReportSectionType.RIPENESS -> {
                    if (selectedFilter == ResultFilterTab.ALL || selectedFilter == ResultFilterTab.RIPENESS_SIZE) {
                        item(key = "section_ripeness") {
                            RipenessDetailsCard(
                                analysis = analysis,
                                language = preferences.language,
                                strings = strings
                            )
                        }
                    }
                }

                ReportSectionType.SIZE_CALORIES -> {
                    if (selectedFilter == ResultFilterTab.ALL ||
                        selectedFilter == ResultFilterTab.RIPENESS_SIZE ||
                        selectedFilter == ResultFilterTab.NUTRIENTS
                    ) {
                        item(key = "section_size_calories") {
                            SpatialSizeWeightCard(
                                analysis = analysis,
                                strings = strings
                            )
                        }
                    }
                }

                ReportSectionType.STORAGE -> {
                    if (selectedFilter == ResultFilterTab.ALL ||
                        selectedFilter == ResultFilterTab.RIPENESS_SIZE ||
                        selectedFilter == ResultFilterTab.USAGE
                    ) {
                        item(key = "section_storage") {
                            StorageCard(
                                analysis = analysis,
                                strings = strings
                            )
                        }
                    }
                }

                ReportSectionType.VITAMINS_NUTRIENTS -> {
                    val showBanner = selectedFilter == ResultFilterTab.ALL ||
                        selectedFilter == ResultFilterTab.VITAMINS ||
                        selectedFilter == ResultFilterTab.NUTRIENTS
                    val showVitamins = (selectedFilter == ResultFilterTab.ALL || selectedFilter == ResultFilterTab.VITAMINS) &&
                        analysis.vitamins.isNotEmpty()
                    val showNutrients = (selectedFilter == ResultFilterTab.ALL || selectedFilter == ResultFilterTab.NUTRIENTS) &&
                        analysis.nutrients.isNotEmpty()
                    if (showBanner || showVitamins || showNutrients) {
                        item(key = "section_vitamins_nutrients") {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (showBanner) {
                                    NutrientPreservationBanner(
                                        rule = analysis.nutrientPreservationRule,
                                        title = strings.nutrientRuleTitle
                                    )
                                }
                                if (showVitamins) {
                                    NutritionGroupSection(
                                        title = strings.vitaminsTitle,
                                        itemsList = analysis.vitamins,
                                        expandedLayout = (selectedFilter == ResultFilterTab.VITAMINS)
                                    )
                                }
                                if (showNutrients) {
                                    NutritionGroupSection(
                                        title = strings.nutrientsTitle,
                                        itemsList = analysis.nutrients,
                                        expandedLayout = (selectedFilter == ResultFilterTab.NUTRIENTS)
                                    )
                                }
                            }
                        }
                    }
                }

                ReportSectionType.HEALTH_BENEFITS -> {
                    if ((selectedFilter == ResultFilterTab.ALL || selectedFilter == ResultFilterTab.VITAMINS) &&
                        analysis.healthBenefits.isNotEmpty()
                    ) {
                        item(key = "section_health_benefits") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = strings.healthBenefitsTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                analysis.healthBenefits.forEach { benefit ->
                                    val matchesGoal = matchesPreferenceFocus(
                                        benefitCategory = benefit.category,
                                        preferences = preferences
                                    )
                                    HealthBenefitCard(
                                        benefit = benefit,
                                        isFocusMatch = matchesGoal,
                                        focusBadgeLabel = strings.yourFocusBadge
                                    )
                                }
                            }
                        }
                    }
                }

                ReportSectionType.RECIPES -> {
                    if ((selectedFilter == ResultFilterTab.ALL || selectedFilter == ResultFilterTab.USAGE) &&
                        analysis.cookingSuggestions.isNotEmpty()
                    ) {
                        item(key = "section_recipes") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = strings.usageRecipesTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                analysis.cookingSuggestions.forEachIndexed { idx, suggestion ->
                                    CookingSuggestionCard(
                                        suggestion = suggestion,
                                        index = idx,
                                        pairingPrefix = strings.perfectPairing,
                                        aiStepsButtonLabel = strings.recipeStepsAiButton,
                                        language = preferences.language,
                                        onAskAiRecipeSteps = {
                                            onQuickAsk(
                                                buildRecipeDetailsAiPrompt(
                                                    produceName = analysis.produceName,
                                                    weightGrams = analysis.estimatedWeightGrams,
                                                    suggestion = suggestion,
                                                    language = preferences.language
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showInlineAiCard) {
            item(key = "inline_ai_preview") {
                InlineAiPreviewCard(
                    analysis = analysis,
                    preferences = preferences,
                    strings = strings,
                    onOpenChat = onOpenChat,
                    onQuickAsk = onQuickAsk
                )
            }
        }
    }
    }
}

@Composable
private fun HeroProduceCard(
    analysis: ProduceAnalysisResult,
    language: AppLanguage,
    strings: UiStrings
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        ProduceArtworkImage(
            localImagePath = analysis.localImagePath,
            sampleKey = analysis.sampleKey,
            contentDescription = analysis.produceName,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color(0xFF131D17).copy(alpha = 0.86f)
                        )
                    )
                )
        )

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFFAD78A),
                    modifier = Modifier.size(14.dp)
                )
                val badgeText = buildString {
                    append("${analysis.confidencePercent}% ${strings.matchLabel} · ~${analysis.estimatedWeightGrams}${strings.weightUnit}")
                    if (analysis.estimatedCaloriesKcal > 0) {
                        append(" · ~${analysis.estimatedCaloriesKcal} ${strings.kcalUnit}")
                    }
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        SelectionContainer(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            val heroProjection = remember(
                analysis.id,
                analysis.scannedAtMillis,
                analysis.ripenessStage,
                analysis.ripenessScore,
                analysis.daysRemainingMin,
                analysis.daysRemainingMax
            ) { FreshnessTracker.evaluate(analysis) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RipenessBadge(stage = heroProjection.projectedStage, language = language)
                Text(
                    text = analysis.produceName,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${analysis.variety} · ${analysis.lightingNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RipenessDetailsCard(
    analysis: ProduceAnalysisResult,
    language: AppLanguage,
    strings: UiStrings
) {
    val projection = remember(
        analysis.id,
        analysis.scannedAtMillis,
        analysis.ripenessStage,
        analysis.ripenessScore,
        analysis.daysRemainingMin,
        analysis.daysRemainingMax
    ) { FreshnessTracker.evaluate(analysis) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RipenessArcGauge(
                    score = projection.projectedScore,
                    stage = projection.projectedStage,
                    language = language
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.bestTimeToConsume,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = analysis.bestTimeToConsume,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val daysText = if (projection.urgency == ExpiryUrgency.EXPIRED) {
                        strings.expiryExpiredLabel
                    } else if (language == AppLanguage.MONGOLIAN) {
                        when {
                            projection.remainingDaysMax == 0 -> "Өнөөдөр хэрэглэхэд хамгийн тохиромжтой"
                            projection.remainingDaysMin == projection.remainingDaysMax ->
                                "${projection.remainingDaysMin} хоногт хэрэглэнэ"
                            else -> "Хугацаа: ${projection.remainingDaysMin}–${projection.remainingDaysMax} хоног"
                        }
                    } else {
                        when {
                            projection.remainingDaysMax == 0 -> "Enjoy today for peak quality"
                            projection.remainingDaysMin == projection.remainingDaysMax ->
                                "Window: ${projection.remainingDaysMin} day${if (projection.remainingDaysMin == 1) "" else "s"}"
                            else -> "Window: ${projection.remainingDaysMin}–${projection.remainingDaysMax} days"
                        }
                    }
                    Text(
                        text = daysText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (projection.urgency == ExpiryUrgency.EXPIRED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = strings.scannedAtLabel(
                            FreshnessTracker.formatScanTimestamp(analysis.scannedAtMillis, language)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("report_scanned_timestamp")
                    )
                }
            }

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
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (projection.scoreDelta < 0) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ripeness_degradation_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (projection.scoreDelta < 0) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (projection.scoreDelta < 0) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.visualCuesDetected,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                analysis.visualCues.forEach { cue ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = cue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SpatialSizeWeightCard(
    analysis: ProduceAnalysisResult,
    strings: UiStrings
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .testTag("spatial_size_weight_card")
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ViewInAr,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.spatialSizeWeightTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Straighten,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.approxDimensions,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "~${analysis.estimatedLengthCm} × ${analysis.estimatedWidthCm} ${strings.lengthUnit}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Scale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.approxWeight,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "~${analysis.estimatedWeightGrams} ${strings.weightUnit}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (analysis.estimatedCaloriesKcal > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calorie_estimate_tile")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = strings.approxCalories,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "~${analysis.estimatedCaloriesKcal} ${strings.kcalUnit}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        if (analysis.caloriesPer100g > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${analysis.caloriesPer100g} ${strings.kcalUnit} ${strings.per100gLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "${analysis.servingWeightSummary} · ${analysis.spatialMethodNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
    }
}

@Composable
private fun StorageCard(
    analysis: ProduceAnalysisResult,
    strings: UiStrings
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .testTag("storage_card")
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Kitchen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${strings.storeForFreshness} · ${analysis.storageTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = analysis.storageInstructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = analysis.storageAvoidTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientPreservationBanner(
    rule: String,
    title: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = rule,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionGroupSection(
    title: String,
    itemsList: List<NutritionHighlight>,
    expandedLayout: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (expandedLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsList.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = item.amount,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = item.benefit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(itemsList) { nutrient ->
                    NutritionCard(nutrient = nutrient)
                }
            }
        }
    }
}

@Composable
private fun NutritionCard(nutrient: NutritionHighlight) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .width(164.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            Text(
                text = nutrient.amount,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = nutrient.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = nutrient.benefit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            }
        }
    }
}

@Composable
private fun HealthBenefitCard(
    benefit: HealthBenefit,
    isFocusMatch: Boolean,
    focusBadgeLabel: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFocusMatch) Icons.Rounded.Favorite else Icons.Rounded.Spa,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = benefit.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isFocusMatch) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = focusBadgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = benefit.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = benefit.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CookingSuggestionCard(
    suggestion: CookingSuggestion,
    index: Int,
    pairingPrefix: String,
    aiStepsButtonLabel: String,
    language: AppLanguage,
    onAskAiRecipeSteps: () -> Unit
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
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalDining,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = suggestion.method,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (language == AppLanguage.MONGOLIAN) {
                                    "${suggestion.prepMinutes} мин"
                                } else {
                                    "${suggestion.prepMinutes} min"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (suggestion.goalFocus.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = suggestion.goalFocus,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = suggestion.nutrientTip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$pairingPrefix: ${suggestion.pairing}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            FilledTonalButton(
                onClick = onAskAiRecipeSteps,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("recipe_ai_guide_$index")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = aiStepsButtonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSectionOrderSheet(
    currentOrder: List<ReportSectionType>,
    strings: UiStrings,
    onUpdateOrder: (List<ReportSectionType>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val normalizedOrder = (
        currentOrder.distinct() + ReportSectionType.entries.filterNot { it in currentOrder }
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("report_order_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.customizeOrderTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.customizeOrderSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            normalizedOrder.forEachIndexed { idx, section ->
                val sectionIcon = when (section) {
                    ReportSectionType.RIPENESS -> Icons.Rounded.Eco
                    ReportSectionType.SIZE_CALORIES -> Icons.Rounded.ViewInAr
                    ReportSectionType.STORAGE -> Icons.Rounded.Kitchen
                    ReportSectionType.VITAMINS_NUTRIENTS -> Icons.Rounded.Spa
                    ReportSectionType.HEALTH_BENEFITS -> Icons.Rounded.Favorite
                    ReportSectionType.RECIPES -> Icons.Rounded.RestaurantMenu
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .testTag("order_row_${section.key}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Icon(
                            imageVector = sectionIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.sectionLabel(section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val mutable = normalizedOrder.toMutableList()
                                    val moved = mutable.removeAt(idx)
                                    mutable.add(idx - 1, moved)
                                    onUpdateOrder(mutable)
                                }
                            },
                            enabled = idx > 0,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("move_up_${section.key}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = strings.moveUpContentDesc
                            )
                        }
                        IconButton(
                            onClick = {
                                if (idx < normalizedOrder.lastIndex) {
                                    val mutable = normalizedOrder.toMutableList()
                                    val moved = mutable.removeAt(idx)
                                    mutable.add(idx + 1, moved)
                                    onUpdateOrder(mutable)
                                }
                            },
                            enabled = idx < normalizedOrder.lastIndex,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("move_down_${section.key}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = strings.moveDownContentDesc
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onUpdateOrder(ReportSectionType.DEFAULT_ORDER) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_report_order_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.resetOrderLabel)
                }
                Button(
                    onClick = onDismiss,
                    shape = CircleShape,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_report_order_button")
                ) {
                    Text(strings.doneLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineAiPreviewCard(
    analysis: ProduceAnalysisResult,
    preferences: UserPreferences,
    strings: UiStrings,
    onOpenChat: () -> Unit,
    onQuickAsk: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = strings.aiAssistantTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (preferences.language == AppLanguage.MONGOLIAN) {
                    "${analysis.produceName} (~${analysis.estimatedWeightGrams}${strings.weightUnit})-д тохирсон асуулт дээр дарж AI-тай ярилцаарай:"
                } else {
                    "Knows your ${analysis.produceName} (~${analysis.estimatedWeightGrams}${strings.weightUnit}) and health needs. Tap a question:"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                analysis.quickQuestions.forEachIndexed { index, question ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onQuickAsk(question) }
                            .testTag("report_quick_question_$index")
                    ) {
                        Text(
                            text = question,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            Button(
                onClick = onOpenChat,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Text(strings.askAiButton)
            }
        }
    }
}

private fun matchesPreferenceFocus(
    benefitCategory: String,
    preferences: UserPreferences
): Boolean {
    val category = benefitCategory.trim().lowercase()
    if (category.isEmpty()) return false
    return (preferences.healthFocuses + preferences.nutrientNeeds).any { focus ->
        val candidates = listOf(
            focus,
            localizePreferenceOption(focus, AppLanguage.MONGOLIAN)
        )
        candidates.any { option ->
            val tokens = option.lowercase()
                .split(" ", "/", "&", "(", ")", "-")
                .map { it.trim() }
                .filter { it.length >= 3 && it !in setOf("and", "support", "boost", "needed", "хэрэгтэй", "дэмжих", "нэмэгдүүлэх", "эрүүл", "мэнд") }
            tokens.any { token -> category.contains(token) } || category.contains(option.lowercase())
        }
    }
}
