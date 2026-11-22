package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.CookingSuggestion
import com.example.model.HealthBenefit
import com.example.model.NutritionHighlight
import com.example.model.ProduceAnalysisResult
import com.example.model.RipenessStage
import com.example.model.UserPreferences
import com.example.ui.i18n.localizePreferenceOption
import com.example.util.ImageUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ProduceVisionAiService"
private val MODEL_CANDIDATES = listOf(
    "gemini-3.5-flash-lite",
    "gemini-3.8-flash"
)
private const val VISION_TIMEOUT_MS = 35_000L
private const val CHAT_TIMEOUT_MS = 50_000L
private const val RATE_LIMIT_COOLDOWN_MS = 60_000L

class ProduceVisionAiService {

    private val permanentlyUnavailableModels = ConcurrentHashMap<String, Boolean>()
    private val rateLimitedUntilMillis = ConcurrentHashMap<String, Long>()

    private fun isModelTemporarilyBlocked(modelName: String): Boolean {
        if (permanentlyUnavailableModels[modelName] == true) return true
        val blockedUntil = rateLimitedUntilMillis[modelName] ?: return false
        return System.currentTimeMillis() < blockedUntil
    }

    private fun recordModelFailure(modelName: String, error: Throwable) {
        if (isModelUnavailableError(error)) {
            permanentlyUnavailableModels[modelName] = true
        } else if (isQuotaOrRateLimitError(error)) {
            rateLimitedUntilMillis[modelName] = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
        }
    }

    private val produceAnalysisSchema = Schema.obj(
        properties = mapOf(
            "isProduce" to Schema.boolean(
                description = "True if image shows fruit, vegetable, or herb"
            ),
            "produceName" to Schema.string(
                description = "Common produce name in the requested output language"
            ),
            "variety" to Schema.string(
                description = "Cultivar/variety name in the requested output language"
            ),
            "category" to Schema.string(
                description = "Category (Fruit/Vegetable/Herb) translated into the requested output language"
            ),
            "ripenessStage" to Schema.enumeration(
                values = listOf("UNDERRIPE", "PERFECTLY_RIPE", "OVERRIPE", "DAMAGED"),
                description = "Exact ripeness stage enum"
            ),
            "ripenessScore" to Schema.integer(
                description = "0-100 ripeness score (70-90 ideal peak)"
            ),
            "confidencePercent" to Schema.integer(
                description = "Confidence 60-99"
            ),
            "estimatedLengthCm" to Schema.double(
                description = "Approximate length/height of visible produce in cm"
            ),
            "estimatedWidthCm" to Schema.double(
                description = "Approximate width/diameter in cm"
            ),
            "estimatedWeightGrams" to Schema.integer(
                description = "Approximate weight of the visible produce item(s) in grams"
            ),
            "servingWeightSummary" to Schema.string(
                description = "Short portion & calories note in the requested output language"
            ),
            "lightingNote" to Schema.string(
                description = "Brief lighting calibration note in the requested output language"
            ),
            "visualCues" to Schema.array(
                items = Schema.string(),
                description = "2-3 concise visual cues in the requested output language"
            ),
            "bestTimeToConsume" to Schema.string(
                description = "Actionable eat window in the requested output language"
            ),
            "daysRemainingMin" to Schema.integer(
                description = "Min days remaining (0 if today)"
            ),
            "daysRemainingMax" to Schema.integer(
                description = "Max days remaining"
            ),
            "storageTitle" to Schema.string(
                description = "Short storage location in the requested output language"
            ),
            "storageInstructions" to Schema.string(
                description = "1 concise storage instruction in the requested output language"
            ),
            "storageAvoidTip" to Schema.string(
                description = "1 storage mistake to avoid in the requested output language"
            ),
            "nutrientPreservationRule" to Schema.string(
                description = "1 practical rule to preserve vitamins in the requested output language"
            ),
            "vitamins" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(),
                        "amount" to Schema.string(),
                        "benefit" to Schema.string()
                    )
                ),
                description = "3 key vitamins in the requested output language"
            ),
            "nutrients" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(),
                        "amount" to Schema.string(),
                        "benefit" to Schema.string()
                    )
                ),
                description = "3 key nutrients/fiber/minerals in the requested output language"
            ),
            "healthBenefits" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "category" to Schema.string(),
                        "title" to Schema.string(),
                        "detail" to Schema.string()
                    )
                ),
                description = "2-3 targeted health benefits in the requested output language"
            ),
            "cookingSuggestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(),
                        "method" to Schema.string(),
                        "prepMinutes" to Schema.integer(),
                        "nutrientTip" to Schema.string(),
                        "pairing" to Schema.string()
                    )
                ),
                description = "2-3 personalized cooking suggestions in the requested output language"
            ),
            "quickQuestions" to Schema.array(
                items = Schema.string(),
                description = "3 short follow-up questions in the requested output language"
            )
        )
    )

    private val visionGenerationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = produceAnalysisSchema
        temperature = 0.15f
        maxOutputTokens = 3072
    }

    private fun visionModelFor(modelName: String) = Firebase.ai.generativeModel(
        modelName = modelName,
        generationConfig = visionGenerationConfig
    )

    suspend fun analyzeProduce(
        bitmap: Bitmap,
        preferences: UserPreferences,
        hasHardwareDepthSensor: Boolean = false,
        localImagePath: String? = null,
        sampleKey: String? = null,
        existingId: String? = null,
        onStatusUpdate: (String) -> Unit = {}
    ): ProduceAnalysisResult {
        val telemetry = withContext(Dispatchers.Default) {
            ImageUtils.extractVisualTelemetry(bitmap)
        }
        val jpegBytes = withContext(Dispatchers.IO) {
            ImageUtils.bitmapToJpegBytes(bitmap, maxDim = 512, quality = 72)
        }
        Log.d(TAG, "Prepared JPEG payload: ${jpegBytes.size / 1024} KB | ${telemetry.promptSummary}")

        val languageInstruction = when (preferences.language) {
            AppLanguage.MONGOLIAN ->
                """
                OUTPUT LANGUAGE (STRICT MONGOLIAN / КИРИЛЛ):
                Write EVERY single text field in natural, fluent Mongolian Cyrillic (Монгол хэл), with ZERO English words left untranslated.
                Translate categories ("Жимс", "Хүнсний ногоо"), cooking methods ("Уураар жигнэх", "Түүхийгээр", "Агаар шарагч"), benefit categories ("Арьсны эрүүл мэнд", "Дархлаа", "Хоол боловсруулалт"), vitamin names ("С витамин", "Д витамин", "Эслэг", "Төмөр"), and daily values ("20% ӨХХ") into Mongolian.
                Keep ONLY ripenessStage as the enum token (UNDERRIPE, PERFECTLY_RIPE, OVERRIPE, DAMAGED).
                """.trimIndent()
            AppLanguage.ENGLISH ->
                "OUTPUT LANGUAGE: Write all natural-language JSON string values in clear, warm English."
        }

        val calibrationPrompt = if (preferences.lightingCalibrationEnabled) {
            "Compensate for supermarket fluorescent/warm lighting color cast before judging ripeness."
        } else {
            "Judge ripeness under ambient lighting directly."
        }

        val spatialPrompt = if (preferences.spatialModeEnabled) {
            "3D SPATIAL SIZE & WEIGHT: ${preferences.spatialScaleHint.promptHint} Estimate realistic length (cm), width (cm), weight (grams), and calories/portion for the visible produce item."
        } else {
            "Estimate typical cultivar dimensions (cm) and weight (grams) for one average visible item."
        }

        val localizedGoals = preferences.dietaryGoals
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { localizePreferenceOption("Whole Foods", preferences.language) }
        val localizedNeeds = preferences.nutrientNeeds
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { if (preferences.language == AppLanguage.MONGOLIAN) "Тэнцвэртэй шим тэжээл" else "Balanced micronutrients" }
        val localizedFocus = preferences.healthFocuses
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { if (preferences.language == AppLanguage.MONGOLIAN) "Ерөнхий эрүүл мэнд" else "Wellness" }
        val localizedTools = preferences.cookingTools
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { if (preferences.language == AppLanguage.MONGOLIAN) "Энгийн гал тогоо" else "Basic kitchen" }

        val depthNoteForPrompt = if (hasHardwareDepthSensor) {
            "Hardware depth capability detected on device; refine volume-to-mass density carefully."
        } else {
            "Infer geometry from perspective foreshortening and cultivar proportions."
        }

        val prompt = """
            Analyze this produce photo as RipeWise, an expert botanist and clinical nutritionist.
            $languageInstruction
            CAMERA TELEMETRY: ${telemetry.promptSummary}. $calibrationPrompt
            $spatialPrompt $depthNoteForPrompt
            BOTANICAL & SCORING ACCURACY RULES:
            - Inspect stem/calyx dryness, lenticels, sugar speckling, peel gloss vs wrinkling, and bruising carefully.
            - Keep ripenessScore strictly aligned with ripenessStage: UNDERRIPE=25..62, PERFECTLY_RIPE=72..94, OVERRIPE=45..68, DAMAGED=5..38.
            - Use realistic USDA cultivar dimensions (cm), weight (g), and kcal in servingWeightSummary.
            - Keep every text value crisp (<= 18 words per field) for fast, scannable reading.
            USER PROFILE:
            - Dietary & Weight Goals: $localizedGoals
            - Specific Nutrient Needs: $localizedNeeds
            - Health Focus: $localizedFocus
            - Cooking Tools: $localizedTools
            If no fruit/vegetable/herb is visible, set isProduce=false.
        """.trimIndent()

        val requestContent = content {
            inlineData(jpegBytes, "image/jpeg")
            text(prompt)
        }

        val jsonText = executeVisionWithFallback(requestContent, preferences.language, onStatusUpdate)
        if (jsonText.isNullOrBlank()) {
            throw IllegalStateException(
                if (preferences.language == AppLanguage.MONGOLIAN) {
                    "AI хөдөлгүүрээс хоосон хариу ирлээ."
                } else {
                    "Vision model returned an empty response."
                }
            )
        }

        val spatialMethodNote = buildSpatialMethodNote(
            language = preferences.language,
            spatialModeEnabled = preferences.spatialModeEnabled,
            hasHardwareDepthSensor = hasHardwareDepthSensor
        )

        Log.d(TAG, "Vision JSON received (${jsonText.length} chars)")
        return parseAnalysisJson(
            jsonText = jsonText,
            id = existingId ?: UUID.randomUUID().toString(),
            languageCode = preferences.language.code,
            spatialMethodNote = spatialMethodNote,
            localImagePath = localImagePath,
            sampleKey = sampleKey
        )
    }

    suspend fun translateAnalysisResult(
        result: ProduceAnalysisResult,
        targetLanguage: AppLanguage,
        hasHardwareDepthSensor: Boolean = false,
        spatialModeEnabled: Boolean = true
    ): ProduceAnalysisResult {
        if (result.languageCode == targetLanguage.code) return result

        val sourceJson = serializeAnalysisToJson(result)
        val langDirective = when (targetLanguage) {
            AppLanguage.MONGOLIAN ->
                """
                Translate EVERY natural-language string value in this produce JSON into fluent, natural Mongolian Cyrillic (Монгол хэл) with ZERO leftover English words or Latin units.
                Convert "% DV" -> "% ӨХХ", "DV" -> "ӨХХ", "kcal" -> "ккал", "min" -> "мин", "g" -> "г", categories ("Жимс", "Хүнсний ногоо"), cooking methods, vitamin names ("С витамин", "Д витамин", "В витамин", "Эслэг", "Төмөр"), visual cues, storage tips, and quick questions into Mongolian.
                Keep ONLY numeric values, ripenessStage enum, and JSON property keys unchanged.
                """.trimIndent()
            AppLanguage.ENGLISH ->
                "Translate EVERY natural-language string value in this produce JSON into natural, concise English. Keep numbers, ripenessStage enum, and JSON keys identical."
        }

        val requestContent = content {
            text("$langDirective\n\nSOURCE JSON:\n$sourceJson")
        }

        val translatedJson = executeVisionWithFallback(requestContent, targetLanguage) {}
        if (translatedJson.isNullOrBlank()) {
            throw IllegalStateException("Translation returned empty JSON.")
        }

        val localizedSpatialNote = buildSpatialMethodNote(
            language = targetLanguage,
            spatialModeEnabled = spatialModeEnabled,
            hasHardwareDepthSensor = hasHardwareDepthSensor
        )

        return parseAnalysisJson(
            jsonText = translatedJson,
            id = result.id,
            languageCode = targetLanguage.code,
            spatialMethodNote = localizedSpatialNote,
            localImagePath = result.localImagePath,
            sampleKey = result.sampleKey,
            scannedAtMillis = result.scannedAtMillis
        )
    }

    private suspend fun executeVisionWithFallback(
        requestContent: Content,
        language: AppLanguage,
        onStatusUpdate: (String) -> Unit
    ): String? {
        val candidates = MODEL_CANDIDATES.filterNot { isModelTemporarilyBlocked(it) }
            .ifEmpty { MODEL_CANDIDATES.filterNot { permanentlyUnavailableModels[it] == true }.ifEmpty { MODEL_CANDIDATES } }

        var lastError: Exception? = null
        candidates.forEachIndexed { idx, modelName ->
            if (idx > 0) {
                onStatusUpdate(
                    if (language == AppLanguage.MONGOLIAN) "Нөөц AI хөдөлгүүр рүү шилжиж байна…"
                    else "Switching to backup vision engine…"
                )
            }
            try {
                return withContext(Dispatchers.IO) {
                    withTimeout(VISION_TIMEOUT_MS) {
                        visionModelFor(modelName).generateContent(requestContent).text
                    }
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Model $modelName timed out", e)
                    lastError = e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Model $modelName failed: ${e.message}", e)
                recordModelFailure(modelName, e)
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All vision models failed.")
    }

    private fun buildSpatialMethodNote(
        language: AppLanguage,
        spatialModeEnabled: Boolean,
        hasHardwareDepthSensor: Boolean
    ): String {
        return when (language) {
            AppLanguage.MONGOLIAN -> when {
                hasHardwareDepthSensor && spatialModeEnabled -> "Төхөөрөмжийн гүн мэдрэгч + AI 3D хэмжилт"
                spatialModeEnabled -> "AI 3D орон зайн баримжаа хэмжилт"
                else -> "Стандарт жингийн баримжаа"
            }
            AppLanguage.ENGLISH -> when {
                hasHardwareDepthSensor && spatialModeEnabled -> "Hardware Depth Sensor + AI 3D Estimate"
                spatialModeEnabled -> "AI 3D Spatial Depth Estimate"
                else -> "Standard Cultivar Weight Estimate"
            }
        }
    }

    fun startProduceChatSession(
        analysis: ProduceAnalysisResult,
        preferences: UserPreferences,
        historyMessages: List<ChatMessage> = emptyList(),
        preferredModelName: String = selectPreferredModelName()
    ): Chat {
        val systemPrompt = buildChatSystemPrompt(analysis, preferences)
        val chatModel = Firebase.ai.generativeModel(
            modelName = preferredModelName,
            systemInstruction = content { text(systemPrompt) }
        )
        val sdkHistory = buildAlternatingHistory(historyMessages)
        return chatModel.startChat(history = sdkHistory)
    }

    private fun selectPreferredModelName(): String {
        return MODEL_CANDIDATES.firstOrNull { !isModelTemporarilyBlocked(it) }
            ?: MODEL_CANDIDATES.firstOrNull { permanentlyUnavailableModels[it] != true }
            ?: MODEL_CANDIDATES.first()
    }

    private fun buildChatSystemPrompt(
        analysis: ProduceAnalysisResult,
        preferences: UserPreferences
    ): String {
        val replyLangRule = when (preferences.language) {
            AppLanguage.MONGOLIAN ->
                "Reply in fluent, warm Mongolian Cyrillic (Монгол хэл) with zero stray English words unless the user explicitly writes in English."
            AppLanguage.ENGLISH ->
                "Reply in clear, warm English unless the user writes in Mongolian, in which case reply in Mongolian."
        }
        val localizedGoals = preferences.dietaryGoals
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { localizePreferenceOption("Whole Foods", preferences.language) }
        val localizedNeeds = preferences.nutrientNeeds
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { if (preferences.language == AppLanguage.MONGOLIAN) "Тэнцвэртэй шим тэжээл" else "Balanced" }
        val localizedFocus = preferences.healthFocuses
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { if (preferences.language == AppLanguage.MONGOLIAN) "Эрүүл мэнд" else "Wellness" }
        val localizedTools = preferences.cookingTools
            .map { localizePreferenceOption(it, preferences.language) }
            .joinToString()
            .ifBlank { if (preferences.language == AppLanguage.MONGOLIAN) "Энгийн гал тогоо" else "Standard kitchen" }

        return """
            You are RipeWise AI — a warm, calm culinary nutritionist and produce expert.
            $replyLangRule
            SCANNED PRODUCE: ${analysis.produceName} (${analysis.variety}, ${analysis.category}), Stage: ${analysis.ripenessStage.localizedLabel(preferences.language)} (${analysis.ripenessScore}/100).
            Estimated 3D Size & Weight: ~${analysis.estimatedLengthCm} × ${analysis.estimatedWidthCm} cm, ~${analysis.estimatedWeightGrams}g (${analysis.servingWeightSummary}).
            Cues: ${analysis.visualCues.joinToString("; ")}.
            Window: ${analysis.bestTimeToConsume}. Storage: ${analysis.storageTitle} — ${analysis.storageInstructions} (Avoid: ${analysis.storageAvoidTip}).
            Nutrient Rule: ${analysis.nutrientPreservationRule}.
            USER PROFILE:
            - Dietary & Weight Goals: $localizedGoals
            - Specific Nutrient Needs: $localizedNeeds
            - Health Focus: $localizedFocus
            - Cooking Tools: $localizedTools
            Give practical, scannable advice tailored to their exact goals (e.g., fiber/satiety for dieting, Vitamin C + iron pairings, healthy fats for Vitamin D, nutrient-preserving prep times/temps).
        """.trimIndent()
    }

    private fun buildAlternatingHistory(messages: List<ChatMessage>): List<Content> {
        val filtered = messages
            .filter { !it.isStreaming && it.text.isNotBlank() && !it.id.startsWith("greeting_") }
        val result = mutableListOf<Content>()
        var expectingUser = true
        for (msg in filtered) {
            if (expectingUser && msg.isUser) {
                result.add(content("user") { text(msg.text) })
                expectingUser = false
            } else if (!expectingUser && !msg.isUser) {
                result.add(content("model") { text(msg.text) })
                expectingUser = true
            }
        }
        if (!expectingUser && result.isNotEmpty()) {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    fun buildInitialGreeting(
        analysis: ProduceAnalysisResult,
        preferences: UserPreferences
    ): String {
        return when (preferences.language) {
            AppLanguage.MONGOLIAN -> {
                val stageText = analysis.ripenessStage.localizedLabel(AppLanguage.MONGOLIAN).lowercase()
                "Би таны ${analysis.produceName} ($stageText, ~${analysis.estimatedWeightGrams}г)-ийг харлаа. " +
                    "Эслэг, Д витамин, төмрийн шингэц, жин барих хоол болон шим тэжээлийг хадгалж бэлтгэх талаар хүссэнээ асуугаарай."
            }
            AppLanguage.ENGLISH -> {
                val focusNote = preferences.nutrientNeeds.firstOrNull()?.let { " tailored for $it" }
                    ?: preferences.healthFocuses.firstOrNull()?.let { " tailored for $it" }
                    ?: ""
                "I have your ${analysis.produceName} (${analysis.ripenessStage.labelEn.lowercase()}, ~${analysis.estimatedWeightGrams}g) in view. " +
                    "Ask me anything about nutrient-saving prep$focusNote, iron/Vitamin D pairings, quick recipes, or storage tricks."
            }
        }
    }

    fun sendMessageStreamWithFallback(
        analysis: ProduceAnalysisResult,
        preferences: UserPreferences,
        historyBeforeQuestion: List<ChatMessage>,
        currentChat: Chat,
        message: String,
        onSessionReplaced: (Chat) -> Unit
    ): Flow<String> = flow {
        val candidates = MODEL_CANDIDATES.filterNot { isModelTemporarilyBlocked(it) }
            .ifEmpty { MODEL_CANDIDATES.filterNot { permanentlyUnavailableModels[it] == true }.ifEmpty { MODEL_CANDIDATES } }
        val primaryCandidate = candidates.first()

        try {
            streamFromChat(currentChat, message).collect { emit(it) }
            return@flow
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Chat timed out on $primaryCandidate; trying fallback", e)
            rateLimitedUntilMillis[primaryCandidate] = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
        } catch (e: Exception) {
            Log.w(TAG, "Chat failed on $primaryCandidate: ${e.message}", e)
            recordModelFailure(primaryCandidate, e)
            val remaining = candidates.drop(1)
            if (remaining.isEmpty()) throw e
        }

        val remainingCandidates = MODEL_CANDIDATES
            .filterNot { it == primaryCandidate || permanentlyUnavailableModels[it] == true }
        var lastError: Exception? = null
        for (fallbackModel in remainingCandidates) {
            try {
                val fallbackChat = startProduceChatSession(
                    analysis = analysis,
                    preferences = preferences,
                    historyMessages = historyBeforeQuestion,
                    preferredModelName = fallbackModel
                )
                onSessionReplaced(fallbackChat)
                streamFromChat(fallbackChat, message).collect { emit(it) }
                return@flow
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastError = e
            } catch (e: Exception) {
                recordModelFailure(fallbackModel, e)
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Chat request failed.")
    }

    private fun streamFromChat(chat: Chat, message: String): Flow<String> = flow {
        val accumulated = StringBuilder()
        withTimeout(CHAT_TIMEOUT_MS) {
            chat.sendMessageStream(message).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Assistant returned an empty response.")
        }
    }

    companion object {
        fun isModelUnavailableError(throwable: Throwable?): Boolean {
            var current: Throwable? = throwable
            while (current != null) {
                val msg = current.message.orEmpty().lowercase()
                if (
                    "404" in msg ||
                    "not found" in msg ||
                    "not supported" in msg ||
                    "unknown model" in msg
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun isQuotaOrRateLimitError(throwable: Throwable?): Boolean {
            var current: Throwable? = throwable
            while (current != null) {
                val msg = current.message.orEmpty().lowercase()
                val className = current::class.java.simpleName.lowercase()
                if (
                    "quota" in msg ||
                    "429" in msg ||
                    "rate limit" in msg ||
                    "resource_exhausted" in msg ||
                    "free_tier" in msg ||
                    "quota" in className
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun formatUserFacingError(
            throwable: Throwable,
            fallback: String,
            language: AppLanguage = AppLanguage.ENGLISH
        ): String {
            if (isQuotaOrRateLimitError(throwable)) {
                return if (language == AppLanguage.MONGOLIAN) {
                    "Өнөөдрийн үнэгүй AI квот түр дууссан байна. Хэсэг хүлээх эсвэл Google AI Studio / Firebase дээр эрхээ сунгана уу."
                } else {
                    "Daily free-tier AI quota is temporarily reached for this project. " +
                        "Please wait for the quota window to reset or enable billing in Google AI Studio / Firebase."
                }
            }
            return throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }

        fun formatAnalysisForCopy(
            analysis: ProduceAnalysisResult,
            language: AppLanguage
        ): String {
            val isMn = language == AppLanguage.MONGOLIAN
            val lengthUnit = if (isMn) "см" else "cm"
            val weightUnit = if (isMn) "г" else "g"
            val stageLabel = analysis.ripenessStage.localizedLabel(language)

            return buildString {
                appendLine("${analysis.produceName} (${analysis.variety}) — ${analysis.category}")
                if (isMn) {
                    appendLine("Боловсролт: $stageLabel (${analysis.ripenessScore}/100, ${analysis.confidencePercent}% таарц)")
                    appendLine("Ойролцоо 3D хэмжээ ба жин: ~${analysis.estimatedLengthCm} × ${analysis.estimatedWidthCm} $lengthUnit · ~${analysis.estimatedWeightGrams}$weightUnit (${analysis.servingWeightSummary})")
                    appendLine("Хэрэглэх хугацаа: ${analysis.bestTimeToConsume}")
                    if (analysis.visualCues.isNotEmpty()) {
                        appendLine("Илэрсэн шинж тэмдгүүд: ${analysis.visualCues.joinToString("; ")}")
                    }
                    appendLine("Зөв хадгалах (${analysis.storageTitle}): ${analysis.storageInstructions}")
                    appendLine("Зайлсхийх: ${analysis.storageAvoidTip}")
                    appendLine("Шим тэжээл хадгалах: ${analysis.nutrientPreservationRule}")
                    if (analysis.vitamins.isNotEmpty()) {
                        appendLine("Витамин:")
                        analysis.vitamins.forEach { appendLine("• ${it.name} (${it.amount}): ${it.benefit}") }
                    }
                    if (analysis.nutrients.isNotEmpty()) {
                        appendLine("Шим тэжээл ба эрдэс:")
                        analysis.nutrients.forEach { appendLine("• ${it.name} (${it.amount}): ${it.benefit}") }
                    }
                    if (analysis.healthBenefits.isNotEmpty()) {
                        appendLine("Эрүүл мэндийн ач тус:")
                        analysis.healthBenefits.forEach { appendLine("• [${it.category}] ${it.title}: ${it.detail}") }
                    }
                    if (analysis.cookingSuggestions.isNotEmpty()) {
                        appendLine("Хоолонд ашиглах санаа:")
                        analysis.cookingSuggestions.forEach {
                            appendLine("• ${it.title} (${it.method}, ${it.prepMinutes} мин) — ${it.nutrientTip}. Хослол: ${it.pairing}")
                        }
                    }
                } else {
                    appendLine("Ripeness: $stageLabel (${analysis.ripenessScore}/100, ${analysis.confidencePercent}% match)")
                    appendLine("3D Size & Weight: ~${analysis.estimatedLengthCm} × ${analysis.estimatedWidthCm} $lengthUnit · ~${analysis.estimatedWeightGrams}$weightUnit (${analysis.servingWeightSummary})")
                    appendLine("Best Time to Consume: ${analysis.bestTimeToConsume}")
                    if (analysis.visualCues.isNotEmpty()) {
                        appendLine("Visual Cues: ${analysis.visualCues.joinToString("; ")}")
                    }
                    appendLine("Smart Storage (${analysis.storageTitle}): ${analysis.storageInstructions}")
                    appendLine("Avoid: ${analysis.storageAvoidTip}")
                    appendLine("Nutrient Preservation: ${analysis.nutrientPreservationRule}")
                    if (analysis.vitamins.isNotEmpty()) {
                        appendLine("Vitamins:")
                        analysis.vitamins.forEach { appendLine("• ${it.name} (${it.amount}): ${it.benefit}") }
                    }
                    if (analysis.nutrients.isNotEmpty()) {
                        appendLine("Nutrients & Minerals:")
                        analysis.nutrients.forEach { appendLine("• ${it.name} (${it.amount}): ${it.benefit}") }
                    }
                    if (analysis.healthBenefits.isNotEmpty()) {
                        appendLine("Health Benefits:")
                        analysis.healthBenefits.forEach { appendLine("• [${it.category}] ${it.title}: ${it.detail}") }
                    }
                    if (analysis.cookingSuggestions.isNotEmpty()) {
                        appendLine("Cooking & Usage Ideas:")
                        analysis.cookingSuggestions.forEach {
                            appendLine("• ${it.title} (${it.method}, ${it.prepMinutes} min) — ${it.nutrientTip}. Pairing: ${it.pairing}")
                        }
                    }
                }
            }.trim()
        }

        fun parseAnalysisJson(
            jsonText: String,
            id: String = UUID.randomUUID().toString(),
            languageCode: String = "en",
            spatialMethodNote: String? = null,
            localImagePath: String? = null,
            sampleKey: String? = null,
            scannedAtMillis: Long = System.currentTimeMillis()
        ): ProduceAnalysisResult {
            val json = JSONObject(jsonText)
            val isMn = languageCode == AppLanguage.MONGOLIAN.code
            val isProduce = json.optBoolean("isProduce", true)
            if (!isProduce) {
                val seen = json.safeString("produceName", if (isMn) "бусад зүйл" else "non-produce item")
                throw IllegalArgumentException(
                    if (isMn) {
                        "Жимс, хүнсний ногоо тод харагдсангүй ($seen). Гэрэлтэй орчинд ойроос дахин авна уу."
                    } else {
                        "Couldn't spot a fruit or vegetable clearly (saw: $seen). Try framing a produce item in good light."
                    }
                )
            }

            val visualCues = json.optJSONArray("visualCues").toStringList()
            val legacyHighlights = json.optJSONArray("nutritionHighlights").toNutritionList(isMn)
            val parsedVitamins = json.optJSONArray("vitamins").toNutritionList(isMn)
            val parsedNutrients = json.optJSONArray("nutrients").toNutritionList(isMn)

            val finalVitamins = parsedVitamins.ifEmpty {
                legacyHighlights.filter {
                    it.name.contains("vitamin", ignoreCase = true) ||
                        it.name.contains("folate", ignoreCase = true) ||
                        it.name.contains("витамин", ignoreCase = true) ||
                        it.name.contains("фолат", ignoreCase = true)
                }.ifEmpty { legacyHighlights.take(2) }
            }.distinctBy { it.name.trim().lowercase() }
            val finalNutrients = parsedNutrients.ifEmpty {
                legacyHighlights.filterNot { it in finalVitamins }.ifEmpty { legacyHighlights }
            }.distinctBy { it.name.trim().lowercase() }

            val healthBenefits = json.optJSONArray("healthBenefits").toHealthBenefitList(isMn)
            val cookingSuggestions = json.optJSONArray("cookingSuggestions").toCookingList(isMn)
            val quickQuestions = json.optJSONArray("quickQuestions").toStringList()
            val minDays = json.optInt("daysRemainingMin", 1).coerceAtLeast(0)
            val maxDays = json.optInt("daysRemainingMax", minDays.coerceAtLeast(3)).coerceAtLeast(minDays)

            val lengthCm = json.optDouble("estimatedLengthCm", 9.5)
                .let { if (it.isNaN() || it <= 0.0) 9.5 else it }
            val widthCm = json.optDouble("estimatedWidthCm", 6.5)
                .let { if (it.isNaN() || it <= 0.0) 6.5 else it }
            val weightGrams = json.optInt("estimatedWeightGrams", 160).coerceIn(5, 10000)

            val defaultSpatialNote = spatialMethodNote ?: if (isMn) {
                "AI 3D орон зайн баримжаа хэмжилт"
            } else {
                "AI 3D Spatial Depth Estimate"
            }

            val resolvedSpatialNote = spatialMethodNote
                ?: json.safeString("spatialMethodNote", defaultSpatialNote)

            val resolvedLangCode = json.safeString("languageCode", languageCode)

            val stage = RipenessStage.fromApiValue(json.safeString("ripenessStage", "PERFECTLY_RIPE"))
            val rawScore = json.optInt("ripenessScore", 78).coerceIn(0, 100)
            val calibratedScore = when (stage) {
                RipenessStage.PERFECTLY_RIPE -> rawScore.coerceIn(70, 98)
                RipenessStage.UNDERRIPE -> rawScore.coerceIn(15, 65)
                RipenessStage.OVERRIPE -> rawScore.coerceIn(35, 69)
                RipenessStage.DAMAGED -> rawScore.coerceIn(5, 45)
            }

            return ProduceAnalysisResult(
                id = id,
                produceName = json.safeString("produceName", if (isMn) "Шинэ бүтээгдэхүүн" else "Fresh Produce"),
                variety = json.safeString("variety", if (isMn) "Улирлын сорт" else "Seasonal Variety"),
                category = json.safeString("category", if (isMn) "Жимс / Ногоо" else "Produce"),
                ripenessStage = stage,
                ripenessScore = calibratedScore,
                confidencePercent = json.optInt("confidencePercent", 92).coerceIn(50, 99),
                estimatedLengthCm = (Math.round(lengthCm * 10.0) / 10.0),
                estimatedWidthCm = (Math.round(widthCm * 10.0) / 10.0),
                estimatedWeightGrams = weightGrams,
                servingWeightSummary = json.safeString(
                    "servingWeightSummary",
                    if (isMn) "~1 дунд хэмжээ (~${weightGrams}г)" else "~1 medium portion (~${weightGrams}g)"
                ),
                spatialMethodNote = resolvedSpatialNote,
                lightingNote = json.safeString(
                    "lightingNote",
                    if (isMn) "Өрөөний гэрэлд тохируулсан" else "Balanced for ambient lighting"
                ),
                visualCues = visualCues.ifEmpty {
                    listOf(
                        if (isMn) "Хальсны өнгө ба гадаргуугийн бүтцийг шинжиллээ"
                        else "Natural skin tone and surface texture analyzed"
                    )
                },
                bestTimeToConsume = json.safeString(
                    "bestTimeToConsume",
                    if (isMn) "2–3 хоногийн дотор хэрэглэвэл тохиромжтой" else "Enjoy within 2-3 days"
                ),
                daysRemainingMin = minDays,
                daysRemainingMax = maxDays,
                storageTitle = json.safeString(
                    "storageTitle",
                    if (isMn) "Сэрүүн, хуурай газар" else "Cool, Dry Spot"
                ),
                storageInstructions = json.safeString(
                    "storageInstructions",
                    if (isMn) "Нарны шууд тусгалаас хол сэрүүн орчинд хадгална уу." else "Store away from direct sunlight."
                ),
                storageAvoidTip = json.safeString(
                    "storageAvoidTip",
                    if (isMn) "Чийгтэй үед нь битүү уутанд хийж хадгалахаас зайлсхий." else "Avoid sealing tightly while damp."
                ),
                nutrientPreservationRule = json.safeString(
                    "nutrientPreservationRule",
                    if (isMn) "Эмзэг витаминуудыг хамгаалахын тулд зөөлөн дулаанаар бэлтгэнэ үү."
                    else "Keep heat gentle to preserve delicate vitamins."
                ),
                vitamins = finalVitamins,
                nutrients = finalNutrients,
                healthBenefits = healthBenefits,
                cookingSuggestions = cookingSuggestions,
                quickQuestions = quickQuestions.ifEmpty {
                    if (isMn) {
                        listOf(
                            "Шим тэжээлийг алдахгүй яаж бэлтгэх вэ?",
                            "Төмөр болон Д витаминтай хэрхэн хослуулах вэ?",
                            "15 минутын эрүүл жор санал болгооч"
                        )
                    } else {
                        listOf(
                            "How should I prep this without losing nutrients?",
                            "How can I pair this for iron or Vitamin D?",
                            "Give me a 15-minute healthy recipe"
                        )
                    }
                },
                languageCode = resolvedLangCode,
                localImagePath = localImagePath,
                sampleKey = sampleKey,
                scannedAtMillis = scannedAtMillis
            )
        }

        fun serializeAnalysisToJson(result: ProduceAnalysisResult): String {
            val root = JSONObject().apply {
                put("isProduce", true)
                put("produceName", result.produceName)
                put("variety", result.variety)
                put("category", result.category)
                put("ripenessStage", result.ripenessStage.name)
                put("ripenessScore", result.ripenessScore)
                put("confidencePercent", result.confidencePercent)
                put("estimatedLengthCm", result.estimatedLengthCm)
                put("estimatedWidthCm", result.estimatedWidthCm)
                put("estimatedWeightGrams", result.estimatedWeightGrams)
                put("servingWeightSummary", result.servingWeightSummary)
                put("spatialMethodNote", result.spatialMethodNote)
                put("lightingNote", result.lightingNote)
                put("visualCues", JSONArray(result.visualCues))
                put("bestTimeToConsume", result.bestTimeToConsume)
                put("daysRemainingMin", result.daysRemainingMin)
                put("daysRemainingMax", result.daysRemainingMax)
                put("storageTitle", result.storageTitle)
                put("storageInstructions", result.storageInstructions)
                put("storageAvoidTip", result.storageAvoidTip)
                put("nutrientPreservationRule", result.nutrientPreservationRule)
                put("vitamins", JSONArray().apply {
                    result.vitamins.forEach { item ->
                        put(JSONObject().apply {
                            put("name", item.name)
                            put("amount", item.amount)
                            put("benefit", item.benefit)
                        })
                    }
                })
                put("nutrients", JSONArray().apply {
                    result.nutrients.forEach { item ->
                        put(JSONObject().apply {
                            put("name", item.name)
                            put("amount", item.amount)
                            put("benefit", item.benefit)
                        })
                    }
                })
                put("healthBenefits", JSONArray().apply {
                    result.healthBenefits.forEach { item ->
                        put(JSONObject().apply {
                            put("category", item.category)
                            put("title", item.title)
                            put("detail", item.detail)
                        })
                    }
                })
                put("cookingSuggestions", JSONArray().apply {
                    result.cookingSuggestions.forEach { item ->
                        put(JSONObject().apply {
                            put("title", item.title)
                            put("method", item.method)
                            put("prepMinutes", item.prepMinutes)
                            put("nutrientTip", item.nutrientTip)
                            put("pairing", item.pairing)
                        })
                    }
                })
                put("quickQuestions", JSONArray(result.quickQuestions))
                put("languageCode", result.languageCode)
            }
            return root.toString()
        }
    }
}

private fun JSONObject.safeString(key: String, fallback: String): String {
    if (!has(key) || isNull(key)) return fallback
    val raw = optString(key, "").trim()
    return if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) fallback else raw
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { idx ->
        if (isNull(idx)) return@mapNotNull null
        val raw = optString(idx, "").trim()
        raw.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}

private fun JSONArray?.toNutritionList(isMn: Boolean): List<NutritionHighlight> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { idx ->
        val obj = optJSONObject(idx) ?: return@mapNotNull null
        NutritionHighlight(
            name = obj.safeString("name", if (isMn) "Гол шим тэжээл" else "Key Nutrient"),
            amount = obj.safeString("amount", if (isMn) "Баялаг эх үүсвэр" else "Rich source"),
            benefit = obj.safeString("benefit", if (isMn) "Өдрийн эрч хүчийг дэмжинэ" else "Supports daily vitality")
        )
    }
}

private fun JSONArray?.toHealthBenefitList(isMn: Boolean): List<HealthBenefit> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { idx ->
        val obj = optJSONObject(idx) ?: return@mapNotNull null
        HealthBenefit(
            category = obj.safeString("category", if (isMn) "Эрүүл мэнд" else "Wellness"),
            title = obj.safeString("title", if (isMn) "Өдрийн дэмжлэг" else "Daily Support"),
            detail = obj.safeString("detail", "")
        )
    }
}

private fun JSONArray?.toCookingList(isMn: Boolean): List<CookingSuggestion> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { idx ->
        val obj = optJSONObject(idx) ?: return@mapNotNull null
        CookingSuggestion(
            title = obj.safeString("title", if (isMn) "Энгийн бэлтгэл" else "Simple Preparation"),
            method = obj.safeString("method", if (isMn) "Зөөлөн болгох" else "Gentle Prep"),
            prepMinutes = obj.optInt("prepMinutes", 10).coerceAtLeast(1),
            nutrientTip = obj.safeString("nutrientTip", if (isMn) "Витамин алдагдлыг багасгана" else "Minimizes heat nutrient loss"),
            pairing = obj.safeString("pairing", if (isMn) "Шинэ ногоо ба нимбэг" else "Fresh herbs & citrus")
        )
    }
}
