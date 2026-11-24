package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ProduceVisionAiService
import com.example.data.PreferencesRepository
import com.example.data.RipeWiseDatabase
import com.example.data.ScanHistoryRepository
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.ProduceAnalysisResult
import com.example.model.ReportSectionType
import com.example.model.SampleProduce
import com.example.model.SampleProduceCatalog
import com.example.model.SpatialScaleHint
import com.example.model.UserPreferences
import com.example.ui.i18n.stringsFor
import com.example.util.DepthCapabilityInfo
import com.example.util.DepthSensorHelper
import com.example.util.ExpiryReminderManager
import com.example.util.ImageUtils
import com.example.util.ScanStorageStats
import com.google.firebase.ai.Chat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val TAG = "RipeWiseViewModel"
private const val MAX_BATCH_PHOTOS = 10

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Analyzing(
        val label: String,
        val completedCount: Int = 0,
        val totalCount: Int = 1,
        val previewBitmap: Bitmap? = null,
        val sampleKey: String? = null
    ) : ScanUiState

    data class Success(
        val results: List<ProduceAnalysisResult>,
        val selectedIndex: Int = 0,
        val totalAttempted: Int = results.size,
        val isTranslating: Boolean = false,
        val translationWarning: String? = null
    ) : ScanUiState {
        val currentResult: ProduceAnalysisResult
            get() = results[selectedIndex.coerceIn(0, results.lastIndex)]
    }

    data class Error(val message: String) : ScanUiState
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val isSheetOpen: Boolean = false
)

class RipeWiseViewModel(application: Application) : AndroidViewModel(application) {

    private val aiService = ProduceVisionAiService()
    private val preferencesRepository = PreferencesRepository(application)
    private val db = RipeWiseDatabase.getInstance(application)
    private val historyRepository = ScanHistoryRepository(
        dao = db.scanHistoryDao(),
        chatDao = db.chatMessageDao()
    )

    val depthCapability: DepthCapabilityInfo =
        DepthSensorHelper.inspectDeviceDepthCapability(application)

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )

    val recentScans: StateFlow<List<ProduceAnalysisResult>> = historyRepository.recentScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _chatState = MutableStateFlow(ChatUiState())
    val chatState: StateFlow<ChatUiState> = _chatState.asStateFlow()

    private val _scanStorageStats = MutableStateFlow(ScanStorageStats())
    val scanStorageStats: StateFlow<ScanStorageStats> = _scanStorageStats.asStateFlow()

    private var activeChatSession: Chat? = null
    private var activeResultForChat: ProduceAnalysisResult? = null
    private var chatLoadJob: Job? = null
    private var chatStreamJob: Job? = null
    private var translateJob: Job? = null
    private var pantryTranslateJob: Job? = null
    private val translationCache = ConcurrentHashMap<String, ProduceAnalysisResult>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { historyRepository.cleanupOrphanedImages(getApplication()) }
            _scanStorageStats.value = ImageUtils.getScanStorageStats(getApplication())
        }
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            val scans = historyRepository.recentScans.first()
            scans.forEach { rememberTranslation(it) }
            if (prefs.expiryRemindersEnabled && scans.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    ExpiryReminderManager.schedulePeriodicCheck(getApplication())
                    ExpiryReminderManager.checkAndNotifyIfNeeded(
                        context = getApplication(),
                        scans = scans,
                        language = prefs.language
                    )
                }
            }
            if (scans.any { it.languageCode != prefs.language.code }) {
                localizePantryScans(prefs)
            }
        }
    }

    private fun cacheKey(id: String, languageCode: String): String = "${id}_${languageCode}"

    private fun rememberTranslation(result: ProduceAnalysisResult) {
        translationCache[cacheKey(result.id, result.languageCode)] = result
    }

    private fun invalidateScanCache(id: String) {
        val prefix = "${id}_"
        translationCache.keys.filter { it.startsWith(prefix) }.forEach { translationCache.remove(it) }
    }

    private fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _scanStorageStats.value = ImageUtils.getScanStorageStats(getApplication())
        }
    }

    fun analyzeCameraCapture(imageProxy: ImageProxy) {
        if (_scanState.value is ScanUiState.Analyzing) {
            imageProxy.close()
            return
        }
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            val strings = stringsFor(prefs.language)
            _scanState.value = ScanUiState.Analyzing(
                label = strings.aiWorkingPrefix,
                completedCount = 0,
                totalCount = 1
            )
            try {
                val bitmap = try {
                    withContext(Dispatchers.Default) {
                        imageProxy.use { proxy -> ImageUtils.imageProxyToBitmap(proxy) }
                    }
                } finally {
                    runCatching { imageProxy.close() }
                }
                runSingleProduceAnalysis(
                    bitmap = bitmap,
                    label = strings.aiWorkingPrefix,
                    shouldPersistBitmap = true,
                    sampleKey = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Camera capture processing failed", e)
                _scanState.value = ScanUiState.Error(
                    e.localizedMessage ?: if (prefs.language == AppLanguage.MONGOLIAN) {
                        "Зураг авч чадсангүй. Дахин оролдоно уу."
                    } else {
                        "Unable to capture photo. Please try again."
                    }
                )
            }
        }
    }

    fun analyzePhotoUris(uris: List<Uri>) {
        if (uris.isEmpty() || _scanState.value is ScanUiState.Analyzing) return
        val limitedUris = uris.take(MAX_BATCH_PHOTOS)
        val total = limitedUris.size

        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            val strings = stringsFor(prefs.language)
            _scanState.value = ScanUiState.Analyzing(
                label = if (total == 1) strings.aiWorkingPrefix else strings.scanningItemProgress(0, total),
                completedCount = 0,
                totalCount = total
            )

            try {
                val firstPreview = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageUtils.loadScaledBitmapFromUri(getApplication(), limitedUris.first(), maxDim = 640)
                    }.getOrNull()
                }
                if (firstPreview != null) {
                    _scanState.update { state ->
                        if (state is ScanUiState.Analyzing) state.copy(previewBitmap = firstPreview) else state
                    }
                }

                val semaphore = Semaphore(permits = 4)
                val completedCounter = AtomicInteger(0)
                val firstErrorRef = AtomicReference<Throwable?>(null)

                val indexedResults: List<ProduceAnalysisResult?> = coroutineScope {
                    limitedUris.mapIndexed { index, uri ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                try {
                                    val bitmap = if (index == 0 && firstPreview != null) {
                                        firstPreview
                                    } else {
                                        ImageUtils.loadScaledBitmapFromUri(
                                            getApplication(),
                                            uri,
                                            maxDim = 640
                                        )
                                    }
                                    val analyzed = aiService.analyzeProduce(
                                        bitmap = bitmap,
                                        preferences = prefs,
                                        hasHardwareDepthSensor = depthCapability.hasHardwareDepthSensor,
                                        localImagePath = null,
                                        sampleKey = null
                                    )
                                    val savedPath = ImageUtils.saveScanBitmap(getApplication(), bitmap)
                                    val result = analyzed.copy(localImagePath = savedPath)
                                    rememberTranslation(result)
                                    try {
                                        historyRepository.saveScan(result)
                                    } catch (saveError: Throwable) {
                                        ImageUtils.deleteScanFile(savedPath)
                                        throw saveError
                                    }
                                    val done = completedCounter.incrementAndGet()
                                    _scanState.update { state ->
                                        if (state is ScanUiState.Analyzing) {
                                            val monotonic = maxOf(state.completedCount, done)
                                            state.copy(
                                                completedCount = monotonic,
                                                label = if (total == 1) {
                                                    strings.aiWorkingPrefix
                                                } else {
                                                    strings.scanningItemProgress(monotonic, total)
                                                }
                                            )
                                        } else {
                                            state
                                        }
                                    }
                                    result
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w(TAG, "Batch item #$index failed", e)
                                    firstErrorRef.compareAndSet(null, e)
                                    val done = completedCounter.incrementAndGet()
                                    _scanState.update { state ->
                                        if (state is ScanUiState.Analyzing) {
                                            val monotonic = maxOf(state.completedCount, done)
                                            state.copy(
                                                completedCount = monotonic,
                                                label = if (total == 1) {
                                                    strings.aiWorkingPrefix
                                                } else {
                                                    strings.scanningItemProgress(monotonic, total)
                                                }
                                            )
                                        } else {
                                            state
                                        }
                                    }
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                }

                val validResults = indexedResults.filterNotNull()
                refreshStorageStats()
                if (validResults.isNotEmpty()) {
                    if (prefs.expiryRemindersEnabled) {
                        withContext(Dispatchers.IO) {
                            ExpiryReminderManager.schedulePeriodicCheck(getApplication())
                        }
                    }
                    loadOrPrepareChatForResult(validResults.first(), prefs)
                    _scanState.value = ScanUiState.Success(
                        results = validResults,
                        selectedIndex = 0,
                        totalAttempted = total
                    )
                } else {
                    val err = firstErrorRef.get() ?: IllegalStateException("Vision analysis failed.")
                    _scanState.value = ScanUiState.Error(
                        ProduceVisionAiService.formatUserFacingError(
                            throwable = err,
                            fallback = if (prefs.language == AppLanguage.MONGOLIAN) {
                                "Сонгосон зургуудаас жимс, хүнсний ногоо таньж чадсангүй."
                            } else {
                                "Couldn't identify produce in the selected photos."
                            },
                            language = prefs.language
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Batch photo analysis failed", e)
                _scanState.value = ScanUiState.Error(
                    ProduceVisionAiService.formatUserFacingError(
                        throwable = e,
                        fallback = if (prefs.language == AppLanguage.MONGOLIAN) {
                            "Сонгосон зургийг уншиж чадсангүй."
                        } else {
                            "Couldn't read selected photos."
                        },
                        language = prefs.language
                    )
                )
            }
        }
    }

    fun analyzeSampleProduce(sample: SampleProduce) {
        if (_scanState.value is ScanUiState.Analyzing) return
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            val title = sample.localizedTitle(prefs.language)
            val label = if (prefs.language == AppLanguage.MONGOLIAN) "$title-г шинжилж байна…" else "Scanning $title…"
            _scanState.value = ScanUiState.Analyzing(
                label = label,
                completedCount = 0,
                totalCount = 1,
                sampleKey = sample.key
            )
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.loadScaledBitmapFromDrawable(getApplication(), sample.drawableRes, maxDim = 640)
                }
                runSingleProduceAnalysis(
                    bitmap = bitmap,
                    label = label,
                    shouldPersistBitmap = false,
                    sampleKey = sample.key
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sample scan failed", e)
                _scanState.value = ScanUiState.Error(
                    e.localizedMessage ?: if (prefs.language == AppLanguage.MONGOLIAN) {
                        "Жишээ бүтээгдэхүүнийг шинжилж чадсангүй."
                    } else {
                        "Couldn't analyze sample produce."
                    }
                )
            }
        }
    }

    private suspend fun runSingleProduceAnalysis(
        bitmap: Bitmap,
        label: String,
        shouldPersistBitmap: Boolean,
        sampleKey: String?
    ) {
        _scanState.value = ScanUiState.Analyzing(
            label = label,
            completedCount = 0,
            totalCount = 1,
            previewBitmap = bitmap,
            sampleKey = sampleKey
        )
        val prefs = preferencesRepository.userPreferences.first()
        var savedPath: String? = null
        try {
            val analyzed = aiService.analyzeProduce(
                bitmap = bitmap,
                preferences = prefs,
                hasHardwareDepthSensor = depthCapability.hasHardwareDepthSensor,
                localImagePath = null,
                sampleKey = sampleKey,
                onStatusUpdate = { updatedLabel ->
                    val current = _scanState.value
                    if (current is ScanUiState.Analyzing) {
                        _scanState.value = current.copy(label = updatedLabel)
                    }
                }
            )
            savedPath = if (shouldPersistBitmap) {
                withContext(Dispatchers.IO) {
                    ImageUtils.saveScanBitmap(getApplication(), bitmap)
                }
            } else {
                null
            }
            val result = analyzed.copy(localImagePath = savedPath)
            rememberTranslation(result)
            withContext(Dispatchers.IO) {
                historyRepository.saveScan(result)
                if (prefs.expiryRemindersEnabled) {
                    ExpiryReminderManager.schedulePeriodicCheck(getApplication())
                }
            }
            refreshStorageStats()
            loadOrPrepareChatForResult(result, prefs)
            _scanState.value = ScanUiState.Success(results = listOf(result), selectedIndex = 0)
        } catch (e: TimeoutCancellationException) {
            ImageUtils.deleteScanFile(savedPath)
            Log.e(TAG, "Produce analysis timed out", e)
            _scanState.value = ScanUiState.Error(
                if (prefs.language == AppLanguage.MONGOLIAN) {
                    "Сүлжээний хурднаас шалтгаалж хугацаа хэтэрлээ. Дахин оролдоно уу."
                } else {
                    "Analysis timed out on your network. Please check your connection and try again."
                }
            )
        } catch (e: CancellationException) {
            ImageUtils.deleteScanFile(savedPath)
            throw e
        } catch (e: Exception) {
            ImageUtils.deleteScanFile(savedPath)
            Log.e(TAG, "Produce vision error", e)
            _scanState.value = ScanUiState.Error(
                ProduceVisionAiService.formatUserFacingError(
                    throwable = e,
                    fallback = if (prefs.language == AppLanguage.MONGOLIAN) {
                        "Зургийн шинжилгээ амжилтгүй боллоо. Дахин оролдоно уу."
                    } else {
                        "Vision analysis failed. Please try again."
                    },
                    language = prefs.language
                )
            )
        }
    }

    fun selectBatchIndex(index: Int) {
        val current = _scanState.value as? ScanUiState.Success ?: return
        val safeIndex = index.coerceIn(0, current.results.lastIndex)
        if (current.selectedIndex == safeIndex) return
        translateJob?.cancel()
        val rawTarget = current.results[safeIndex]
        rememberTranslation(rawTarget)
        val prefs = userPreferences.value
        val cached = translationCache[cacheKey(rawTarget.id, prefs.language.code)]
        val effectiveTarget = cached ?: rawTarget
        val updatedResults = if (cached != null && cached != rawTarget) {
            current.results.toMutableList().apply { this[safeIndex] = cached }
        } else {
            current.results
        }
        _scanState.value = current.copy(
            results = updatedResults,
            selectedIndex = safeIndex,
            isTranslating = false,
            translationWarning = null
        )
        chatStreamJob?.cancel()
        chatLoadJob?.cancel()
        chatLoadJob = viewModelScope.launch {
            val latestPrefs = preferencesRepository.userPreferences.first()
            loadOrPrepareChatForResult(effectiveTarget, latestPrefs)
            if (effectiveTarget.languageCode != latestPrefs.language.code) {
                translateItemAt(safeIndex, effectiveTarget, latestPrefs)
            }
        }
    }

    fun openScanReport(result: ProduceAnalysisResult) {
        chatStreamJob?.cancel()
        chatLoadJob?.cancel()
        translateJob?.cancel()
        rememberTranslation(result)
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            val cached = translationCache[cacheKey(result.id, prefs.language.code)]
            val displayResult = cached ?: result
            val needsTranslation = displayResult.languageCode != prefs.language.code
            _scanState.value = ScanUiState.Success(
                results = listOf(displayResult),
                selectedIndex = 0,
                isTranslating = needsTranslation
            )
            loadOrPrepareChatForResult(displayResult, prefs, openSheet = false)
            if (needsTranslation) {
                translateItemAt(0, displayResult, prefs)
            }
        }
    }

    fun dismissTranslationWarning() {
        _scanState.update { state ->
            if (state is ScanUiState.Success) state.copy(translationWarning = null) else state
        }
    }

    private fun translateItemAt(
        index: Int,
        sourceResult: ProduceAnalysisResult,
        prefs: UserPreferences
    ) {
        rememberTranslation(sourceResult)
        val cached = translationCache[cacheKey(sourceResult.id, prefs.language.code)]
        if (cached != null) {
            _scanState.update { state ->
                if (state is ScanUiState.Success && index in state.results.indices &&
                    state.results[index].id == sourceResult.id
                ) {
                    val updatedList = state.results.toMutableList().apply {
                        this[index] = cached
                    }
                    state.copy(results = updatedList, isTranslating = false, translationWarning = null)
                } else if (state is ScanUiState.Success) {
                    state.copy(isTranslating = false, translationWarning = null)
                } else {
                    state
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                historyRepository.saveScan(cached)
            }
            if (activeResultForChat?.id == cached.id) {
                viewModelScope.launch {
                    loadOrPrepareChatForResult(cached, prefs)
                }
            }
            return
        }

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _scanState.update { state ->
                if (state is ScanUiState.Success) {
                    state.copy(isTranslating = true, translationWarning = null)
                } else {
                    state
                }
            }
            try {
                val translated = aiService.translateAnalysisResult(
                    result = sourceResult,
                    targetLanguage = prefs.language,
                    hasHardwareDepthSensor = depthCapability.hasHardwareDepthSensor,
                    spatialModeEnabled = prefs.spatialModeEnabled
                )
                rememberTranslation(translated)
                withContext(Dispatchers.IO) {
                    historyRepository.saveScan(translated)
                }
                _scanState.update { state ->
                    if (state is ScanUiState.Success && index in state.results.indices &&
                        state.results[index].id == sourceResult.id
                    ) {
                        val updatedList = state.results.toMutableList().apply {
                            this[index] = translated
                        }
                        state.copy(results = updatedList, isTranslating = false, translationWarning = null)
                    } else if (state is ScanUiState.Success) {
                        state.copy(isTranslating = false)
                    } else {
                        state
                    }
                }
                if (activeResultForChat?.id == translated.id) {
                    loadOrPrepareChatForResult(translated, prefs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Language translation failed", e)
                val warning = ProduceVisionAiService.formatUserFacingError(
                    throwable = e,
                    fallback = stringsFor(prefs.language).translationWarning,
                    language = prefs.language
                )
                _scanState.update { state ->
                    if (state is ScanUiState.Success) {
                        state.copy(isTranslating = false, translationWarning = warning)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun reanalyzeActiveScan() {
        val current = _scanState.value as? ScanUiState.Success ?: return
        if (current.isTranslating) return
        val active = current.currentResult
        val activeIndex = current.selectedIndex

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            _scanState.update { state ->
                if (state is ScanUiState.Success) {
                    state.copy(isTranslating = true, translationWarning = null)
                } else {
                    state
                }
            }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.loadBitmapFromFilePath(active.localImagePath, maxDim = 640)
                        ?: SampleProduceCatalog.drawableForKey(active.sampleKey)?.let { resId ->
                            ImageUtils.loadScaledBitmapFromDrawable(getApplication(), resId, maxDim = 640)
                        }
                }
                if (bitmap == null) {
                    _scanState.update { state ->
                        if (state is ScanUiState.Success) {
                            state.copy(
                                isTranslating = false,
                                translationWarning = stringsFor(prefs.language).translationWarning
                            )
                        } else {
                            state
                        }
                    }
                    return@launch
                }

                val refreshed = aiService.analyzeProduce(
                    bitmap = bitmap,
                    preferences = prefs,
                    hasHardwareDepthSensor = depthCapability.hasHardwareDepthSensor,
                    localImagePath = active.localImagePath,
                    sampleKey = active.sampleKey,
                    existingId = active.id
                ).copy(scannedAtMillis = active.scannedAtMillis)

                invalidateScanCache(active.id)
                rememberTranslation(refreshed)
                withContext(Dispatchers.IO) {
                    historyRepository.saveScan(refreshed)
                }
                _scanState.update { state ->
                    if (state is ScanUiState.Success && activeIndex in state.results.indices &&
                        state.results[activeIndex].id == active.id
                    ) {
                        val updated = state.results.toMutableList().apply {
                            this[activeIndex] = refreshed
                        }
                        state.copy(results = updated, isTranslating = false, translationWarning = null)
                    } else if (state is ScanUiState.Success) {
                        state.copy(isTranslating = false)
                    } else {
                        state
                    }
                }
                if (activeResultForChat?.id == refreshed.id) {
                    loadOrPrepareChatForResult(refreshed, prefs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Profile refresh scan failed", e)
                val warning = ProduceVisionAiService.formatUserFacingError(
                    throwable = e,
                    fallback = stringsFor(prefs.language).translationWarning,
                    language = prefs.language
                )
                _scanState.update { state ->
                    if (state is ScanUiState.Success) {
                        state.copy(isTranslating = false, translationWarning = warning)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun resetToScanner() {
        chatStreamJob?.cancel()
        chatLoadJob?.cancel()
        translateJob?.cancel()
        _chatState.update { it.copy(isSheetOpen = false, isStreaming = false) }
        _scanState.value = ScanUiState.Idle
    }

    fun dismissError() {
        _scanState.value = ScanUiState.Idle
    }

    fun setChatSheetOpen(open: Boolean) {
        _chatState.update { it.copy(isSheetOpen = open, errorMessage = null) }
        val currentResult = (_scanState.value as? ScanUiState.Success)?.currentResult
        if (open && currentResult != null && activeResultForChat?.id != currentResult.id) {
            chatLoadJob?.cancel()
            chatLoadJob = viewModelScope.launch {
                val prefs = preferencesRepository.userPreferences.first()
                loadOrPrepareChatForResult(currentResult, prefs, openSheet = true)
            }
        }
    }

    private suspend fun loadOrPrepareChatForResult(
        result: ProduceAnalysisResult,
        preferences: UserPreferences,
        openSheet: Boolean = _chatState.value.isSheetOpen
    ) {
        val savedHistory = withContext(Dispatchers.IO) {
            historyRepository.getChatMessages(result.id)
        }
        activeResultForChat = result
        activeChatSession = aiService.startProduceChatSession(
            analysis = result,
            preferences = preferences,
            historyMessages = savedHistory
        )
        val greeting = ChatMessage(
            id = "greeting_${result.id}",
            scanId = result.id,
            isUser = false,
            text = aiService.buildInitialGreeting(result, preferences)
        )
        _chatState.value = ChatUiState(
            messages = listOf(greeting) + savedHistory,
            isStreaming = false,
            errorMessage = null,
            isSheetOpen = openSheet
        )
    }

    fun sendChatMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _chatState.value.isStreaming) return

        val currentResult = (_scanState.value as? ScanUiState.Success)?.currentResult
            ?: activeResultForChat
            ?: return

        chatStreamJob?.cancel()
        chatStreamJob = viewModelScope.launch {
            chatLoadJob?.join()
            val prefs = preferencesRepository.userPreferences.first()
            if (activeResultForChat?.id != currentResult.id || activeChatSession == null) {
                loadOrPrepareChatForResult(currentResult, prefs, openSheet = true)
            }
            val historyBeforeQuestion = _chatState.value.messages
            val session = activeChatSession ?: aiService.startProduceChatSession(
                analysis = currentResult,
                preferences = prefs,
                historyMessages = historyBeforeQuestion
            ).also { chat ->
                activeChatSession = chat
            }

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                scanId = currentResult.id,
                isUser = true,
                text = prompt
            )
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantPlaceholder = ChatMessage(
                id = assistantMsgId,
                scanId = currentResult.id,
                isUser = false,
                text = "",
                isStreaming = true
            )

            _chatState.update { state ->
                state.copy(
                    messages = state.messages + userMsg + assistantPlaceholder,
                    isStreaming = true,
                    errorMessage = null,
                    isSheetOpen = true
                )
            }

            try {
                var finalAnswerText = ""
                aiService.sendMessageStreamWithFallback(
                    analysis = currentResult,
                    preferences = prefs,
                    historyBeforeQuestion = historyBeforeQuestion,
                    currentChat = session,
                    message = prompt,
                    onSessionReplaced = { fallbackChat -> activeChatSession = fallbackChat }
                ).collect { partialText ->
                    finalAnswerText = partialText
                    _chatState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = partialText, isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                }
                val completedMsg = ChatMessage(
                    id = assistantMsgId,
                    scanId = currentResult.id,
                    isUser = false,
                    text = finalAnswerText,
                    isStreaming = false
                )
                withContext(Dispatchers.IO) {
                    historyRepository.saveChatMessage(userMsg)
                    historyRepository.saveChatMessage(completedMsg)
                }
                _chatState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                        },
                        isStreaming = false
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                handleChatFailure(
                    assistantMsgId,
                    if (prefs.language == AppLanguage.MONGOLIAN) {
                        "Хариултын хугацаа хэтэрлээ. Дахин оролдоно уу."
                    } else {
                        "Response timed out. Please try again."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat error", e)
                handleChatFailure(
                    assistantMsgId,
                    ProduceVisionAiService.formatUserFacingError(
                        throwable = e,
                        fallback = if (prefs.language == AppLanguage.MONGOLIAN) {
                            "RipeWise AI-тай холбогдож чадсангүй. Дахин оролдоно уу."
                        } else {
                            "Unable to reach RipeWise AI right now."
                        },
                        language = prefs.language
                    )
                )
            }
        }
    }

    private fun handleChatFailure(assistantMsgId: String, errorText: String) {
        _chatState.update { state ->
            state.copy(
                messages = state.messages.filterNot { it.id == assistantMsgId && it.text.isBlank() }
                    .map { msg -> if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg },
                isStreaming = false,
                errorMessage = errorText
            )
        }
    }

    fun clearChatError() {
        _chatState.update { it.copy(errorMessage = null) }
    }

    fun deleteScan(id: String) {
        invalidateScanCache(id)
        if (activeResultForChat?.id == id) {
            chatStreamJob?.cancel()
            chatLoadJob?.cancel()
            activeResultForChat = null
            activeChatSession = null
            _chatState.value = ChatUiState()
        }
        _scanState.update { state ->
            if (state is ScanUiState.Success && state.results.any { it.id == id }) {
                val remaining = state.results.filterNot { it.id == id }
                if (remaining.isEmpty()) {
                    ScanUiState.Idle
                } else {
                    state.copy(
                        results = remaining,
                        selectedIndex = state.selectedIndex.coerceIn(0, remaining.lastIndex)
                    )
                }
            } else {
                state
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.deleteScan(id)
            _scanStorageStats.value = ImageUtils.getScanStorageStats(getApplication())
        }
    }

    fun clearAllHistory() {
        chatStreamJob?.cancel()
        chatLoadJob?.cancel()
        translateJob?.cancel()
        pantryTranslateJob?.cancel()
        translationCache.clear()
        activeResultForChat = null
        activeChatSession = null
        _chatState.value = ChatUiState()
        if (_scanState.value is ScanUiState.Success) {
            _scanState.value = ScanUiState.Idle
        }
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.clearAllHistory(getApplication())
            _scanStorageStats.value = ImageUtils.getScanStorageStats(getApplication())
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(language)
            val updatedPrefs = userPreferences.value.copy(language = language)
            val currentSuccess = _scanState.value as? ScanUiState.Success
            if (currentSuccess != null) {
                val mappedResults = currentSuccess.results.map { item ->
                    rememberTranslation(item)
                    translationCache[cacheKey(item.id, language.code)] ?: item
                }
                val active = mappedResults[currentSuccess.selectedIndex.coerceIn(0, mappedResults.lastIndex)]
                _scanState.value = currentSuccess.copy(
                    results = mappedResults,
                    translationWarning = null
                )
                if (active.languageCode != language.code) {
                    translateItemAt(currentSuccess.selectedIndex, active, updatedPrefs)
                } else {
                    translateJob?.cancel()
                    _scanState.update { state ->
                        if (state is ScanUiState.Success) {
                            state.copy(isTranslating = false, translationWarning = null)
                        } else {
                            state
                        }
                    }
                    withContext(Dispatchers.IO) {
                        historyRepository.saveScan(active)
                    }
                }
            }
            val activeChatItem = (_scanState.value as? ScanUiState.Success)?.currentResult ?: activeResultForChat
            if (activeChatItem != null && !_chatState.value.isStreaming) {
                val savedHistory = withContext(Dispatchers.IO) {
                    historyRepository.getChatMessages(activeChatItem.id)
                }
                activeChatSession = aiService.startProduceChatSession(
                    analysis = activeChatItem,
                    preferences = updatedPrefs,
                    historyMessages = savedHistory
                )
                val greeting = ChatMessage(
                    id = "greeting_${activeChatItem.id}",
                    scanId = activeChatItem.id,
                    isUser = false,
                    text = aiService.buildInitialGreeting(activeChatItem, updatedPrefs)
                )
                _chatState.update { it.copy(messages = listOf(greeting) + savedHistory) }
            }
            localizePantryScans(updatedPrefs)
        }
    }

    private fun localizePantryScans(prefs: UserPreferences) {
        pantryTranslateJob?.cancel()
        pantryTranslateJob = viewModelScope.launch {
            val activeId = (_scanState.value as? ScanUiState.Success)?.currentResult?.id
            val pending = historyRepository.recentScans.first()
                .filter { it.languageCode != prefs.language.code && it.id != activeId }
                .take(8)
            for (item in pending) {
                rememberTranslation(item)
                val cached = translationCache[cacheKey(item.id, prefs.language.code)]
                if (cached != null) {
                    withContext(Dispatchers.IO) { historyRepository.saveScan(cached) }
                    continue
                }
                try {
                    val translated = aiService.translateAnalysisResult(
                        result = item,
                        targetLanguage = prefs.language,
                        hasHardwareDepthSensor = depthCapability.hasHardwareDepthSensor,
                        spatialModeEnabled = prefs.spatialModeEnabled
                    )
                    rememberTranslation(translated)
                    withContext(Dispatchers.IO) {
                        historyRepository.saveScan(translated)
                    }
                    delay(200)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Background pantry item localization skipped for ${item.id}", e)
                    break
                }
            }
        }
    }

    fun setReportSectionOrder(order: List<ReportSectionType>) {
        viewModelScope.launch {
            preferencesRepository.setReportSectionOrder(order)
        }
    }

    fun toggleDietaryGoal(goal: String) {
        viewModelScope.launch {
            preferencesRepository.toggleDietaryGoal(goal)
        }
    }

    fun toggleNutrientNeed(need: String) {
        viewModelScope.launch {
            preferencesRepository.toggleNutrientNeed(need)
        }
    }

    fun toggleHealthFocus(focus: String) {
        viewModelScope.launch {
            preferencesRepository.toggleHealthFocus(focus)
        }
    }

    fun toggleCookingTool(tool: String) {
        viewModelScope.launch {
            preferencesRepository.toggleCookingTool(tool)
        }
    }

    fun setLightingCalibration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setLightingCalibration(enabled)
        }
    }

    fun setSpatialMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSpatialMode(enabled)
        }
    }

    fun setSpatialScaleHint(hint: SpatialScaleHint) {
        viewModelScope.launch {
            preferencesRepository.setSpatialScaleHint(hint)
        }
    }

    fun setExpiryRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setExpiryRemindersEnabled(enabled)
            val updatedPrefs = userPreferences.value.copy(expiryRemindersEnabled = enabled)
            withContext(Dispatchers.IO) {
                if (enabled) {
                    ExpiryReminderManager.schedulePeriodicCheck(getApplication())
                    ExpiryReminderManager.checkAndNotifyIfNeeded(
                        context = getApplication(),
                        scans = recentScans.value,
                        language = updatedPrefs.language
                    )
                } else {
                    ExpiryReminderManager.cancelPeriodicCheck(getApplication())
                }
            }
        }
    }

    fun triggerExpiryCheckNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.expiryRemindersEnabled) {
                ExpiryReminderManager.schedulePeriodicCheck(getApplication())
                ExpiryReminderManager.checkAndNotifyIfNeeded(
                    context = getApplication(),
                    scans = recentScans.value,
                    language = prefs.language,
                    forceSameDay = true
                )
            }
        }
    }
}
