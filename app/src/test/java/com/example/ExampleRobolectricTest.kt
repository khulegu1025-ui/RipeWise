package com.example

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ProduceVisionAiService
import com.example.data.ChatMessageEntity
import com.example.data.RipeWiseDatabase
import com.example.data.ScanHistoryEntity
import com.example.model.AppLanguage
import com.example.model.RipenessStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches RipeWise`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RipeWise", appName)
    }

    @Test
    fun `produce analysis JSON with 3D size, vitamins, and nutrients parses and serializes cleanly`() {
        val sampleJson = """
            {
              "isProduce": true,
              "produceName": "Hass Avocado",
              "variety": "Hass",
              "category": "Fruit",
              "ripenessStage": "PERFECTLY_RIPE",
              "ripenessScore": 86,
              "confidencePercent": 96,
              "estimatedLengthCm": 9.4,
              "estimatedWidthCm": 6.8,
              "estimatedWeightGrams": 175,
              "servingWeightSummary": "~175g whole (~280 kcal)",
              "lightingNote": "Calibrated for warm indoor lighting",
              "visualCues": ["Deep purple-black pebbled skin", "Clean dry stem cap"],
              "bestTimeToConsume": "Eat within 1-2 days for peak creaminess",
              "daysRemainingMin": 1,
              "daysRemainingMax": 2,
              "storageTitle": "Crisper Drawer if Cut",
              "storageInstructions": "Keep whole at room temperature if eating today; refrigerate once halved with lemon.",
              "storageAvoidTip": "Avoid freezing whole without mashing first.",
              "nutrientPreservationRule": "Serve raw or add after cooking to protect Vitamin E and folate.",
              "vitamins": [
                {"name": "Vitamin E", "amount": "14% DV", "benefit": "Protects skin barrier"},
                {"name": "Folate (B9)", "amount": "20% DV", "benefit": "Supports cellular renewal"}
              ],
              "nutrients": [
                {"name": "Dietary Fiber", "amount": "10g", "benefit": "Promotes gut health & dieting satiety"},
                {"name": "Potassium", "amount": "15% DV", "benefit": "Supports electrolyte balance"}
              ],
              "healthBenefits": [
                {"category": "Skin Glow", "title": "Lipid barrier support", "detail": "Monounsaturated fats nourish skin elasticity."}
              ],
              "cookingSuggestions": [
                {"title": "Citrus Avocado Smash", "method": "No-Cook / Raw", "prepMinutes": 5, "nutrientTip": "Zero heat preserves delicate Vitamin E", "pairing": "Fresh lime & flaky sea salt"}
              ],
              "quickQuestions": [
                "How should I prep this avocado without losing nutrients?"
              ]
            }
        """.trimIndent()

        val parsed = ProduceVisionAiService.parseAnalysisJson(
            jsonText = sampleJson,
            id = "scan-1",
            languageCode = "en"
        )
        assertEquals("Hass Avocado", parsed.produceName)
        assertEquals(RipenessStage.PERFECTLY_RIPE, parsed.ripenessStage)
        assertEquals(86, parsed.ripenessScore)
        assertEquals(9.4, parsed.estimatedLengthCm, 0.01)
        assertEquals(6.8, parsed.estimatedWidthCm, 0.01)
        assertEquals(175, parsed.estimatedWeightGrams)
        assertEquals(2, parsed.vitamins.size)
        assertEquals(2, parsed.nutrients.size)
        assertTrue(parsed.healthBenefits.isNotEmpty())

        val roundTrip = ProduceVisionAiService.parseAnalysisJson(
            ProduceVisionAiService.serializeAnalysisToJson(parsed),
            id = "scan-1"
        )
        assertEquals(parsed.produceName, roundTrip.produceName)
        assertEquals(parsed.ripenessStage, roundTrip.ripenessStage)
        assertEquals(parsed.estimatedWeightGrams, roundTrip.estimatedWeightGrams)
        assertEquals(parsed.vitamins.first().name, roundTrip.vitamins.first().name)
        assertEquals(parsed.nutrients.first().name, roundTrip.nutrients.first().name)
    }

    @Test
    fun `legacy nutritionHighlights JSON remains backward compatible`() {
        val legacyJson = """
            {
              "isProduce": true,
              "produceName": "Golden Mango",
              "nutritionHighlights": [
                {"name": "Vitamin C", "amount": "67% DV", "benefit": "Collagen support"},
                {"name": "Dietary Fiber", "amount": "3g", "benefit": "Digestion"}
              ]
            }
        """.trimIndent()

        val parsed = ProduceVisionAiService.parseAnalysisJson(legacyJson)
        assertTrue(parsed.vitamins.any { it.name == "Vitamin C" })
        assertTrue(parsed.nutrients.any { it.name == "Dietary Fiber" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-produce image throws actionable error`() {
        val nonProduceJson = """{"isProduce": false, "produceName": "coffee mug"}"""
        ProduceVisionAiService.parseAnalysisJson(nonProduceJson)
    }

    @Test
    fun `daysRemainingMax is clamped to at least daysRemainingMin`() {
        val json = """{"isProduce": true, "produceName": "Mango", "daysRemainingMin": 4, "daysRemainingMax": 1}"""
        val parsed = ProduceVisionAiService.parseAnalysisJson(json)
        assertEquals(4, parsed.daysRemainingMin)
        assertEquals(4, parsed.daysRemainingMax)
    }

    @Test
    fun `quota error detection identifies Gemini free tier limit in English and Mongolian`() {
        val error = RuntimeException(
            "You exceeded your current quota, metric: generate_content_free_tier_requests, limit: 20, model: gemini-3.8-flash"
        )
        assertTrue(ProduceVisionAiService.isQuotaOrRateLimitError(error))
        val formattedEn = ProduceVisionAiService.formatUserFacingError(error, "Fallback", AppLanguage.ENGLISH)
        val formattedMn = ProduceVisionAiService.formatUserFacingError(error, "Fallback", AppLanguage.MONGOLIAN)
        assertTrue(formattedEn.contains("quota", ignoreCase = true))
        assertTrue(formattedMn.contains("квот", ignoreCase = true))
    }

    @Test
    fun `room database persists and restores chat history per scan`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = RipeWiseDatabase.getInstance(context)
        val scanId = "test-scan-chat-1"

        db.scanHistoryDao().insertScan(
            ScanHistoryEntity(
                id = scanId,
                produceName = "Avocado",
                ripenessStage = "PERFECTLY_RIPE",
                ripenessScore = 85,
                analysisJson = """{"isProduce":true,"produceName":"Avocado"}""",
                localImagePath = null,
                sampleKey = "avocado",
                scannedAtMillis = 1000L
            )
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "m1",
                scanId = scanId,
                isUser = true,
                text = "How should I eat this for iron absorption?",
                createdAtMillis = 1001L
            )
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "m2",
                scanId = scanId,
                isUser = false,
                text = "Pair with lemon juice and leafy greens.",
                createdAtMillis = 1002L
            )
        )

        val restored = db.chatMessageDao().getMessagesForScan(scanId)
        assertEquals(2, restored.size)
        assertEquals(true, restored[0].isUser)
        assertEquals(false, restored[1].isUser)

        db.chatMessageDao().deleteMessagesForScan(scanId)
        db.scanHistoryDao().deleteScanById(scanId)
    }

    @Test
    fun `mongolian parseAnalysisJson provides mongolian defaults and localizes preference labels`() {
        val minimalJson = """{"isProduce": true, "produceName": "Авокадо"}"""
        val parsed = ProduceVisionAiService.parseAnalysisJson(minimalJson, languageCode = "mn")
        assertEquals("mn", parsed.languageCode)
        assertTrue(parsed.quickQuestions.first().contains("Шим тэжээлийг"))
        assertTrue(parsed.spatialMethodNote.contains("AI 3D"))
        assertEquals(
            "Өндөр эслэг хэрэгтэй",
            com.example.ui.i18n.localizePreferenceOption("High Fiber Needed", AppLanguage.MONGOLIAN)
        )
        assertEquals(
            "Агаар шарагч",
            com.example.ui.i18n.localizePreferenceOption("Air Fryer", AppLanguage.MONGOLIAN)
        )
    }

    @Test
    fun `json explicit null fields never produce literal null strings`() {
        val jsonWithNulls = """
            {
              "isProduce": true,
              "produceName": null,
              "variety": "null",
              "servingWeightSummary": null,
              "vitamins": [
                {"name": null, "amount": "null", "benefit": null}
              ],
              "visualCues": [null, "Bright green skin"]
            }
        """.trimIndent()
        val parsed = ProduceVisionAiService.parseAnalysisJson(jsonWithNulls, languageCode = "en")
        assertEquals("Fresh Produce", parsed.produceName)
        assertEquals("Seasonal Variety", parsed.variety)
        assertTrue(!parsed.servingWeightSummary.contains("null", ignoreCase = true))
        assertEquals("Key Nutrient", parsed.vitamins.first().name)
        assertEquals("Rich source", parsed.vitamins.first().amount)
        assertEquals(listOf("Bright green skin"), parsed.visualCues)
    }

    @Test
    fun `depth sensor capability helper runs safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = com.example.util.DepthSensorHelper.inspectDeviceDepthCapability(context)
        // Simply verifying no SecurityException or NoSuchFieldError occurs
        assertTrue(info.hasHardwareDepthSensor || !info.hasHardwareDepthSensor)
    }

    @Test
    fun `model unavailable error detection catches 404 errors for fallback`() {
        val error = RuntimeException("HTTP 404 models/gemini-3.5-flash-lite is not found")
        assertTrue(ProduceVisionAiService.isModelUnavailableError(error))
    }

    @Test
    fun `deleteScanFile removes existing file and ignores null or missing paths`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scansDir = java.io.File(context.filesDir, "scans").apply { mkdirs() }
        val file = java.io.File(scansDir, "failed_scan_test.jpg").apply {
            writeText("test-image-bytes")
        }
        assertTrue(file.exists())

        com.example.util.ImageUtils.deleteScanFile(file.absolutePath)
        assertTrue(!file.exists())

        // Should safely no-op on null, blank, and missing paths
        com.example.util.ImageUtils.deleteScanFile(null)
        com.example.util.ImageUtils.deleteScanFile("")
        com.example.util.ImageUtils.deleteScanFile(file.absolutePath)
    }

    @Test
    fun `cleanupOrphanScanFiles removes stale untracked scans while preserving active and fresh files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scansDir = java.io.File(context.filesDir, "scans").apply { mkdirs() }
        scansDir.listFiles()?.forEach { it.delete() }

        val now = 1_000_000_000L
        val oldTime = now - 300_000L
        val recentTime = now - 10_000L

        val orphanOld = java.io.File(scansDir, "orphan_old.jpg").apply {
            writeText("orphan")
            setLastModified(oldTime)
        }
        val activeOld = java.io.File(scansDir, "active_old.jpg").apply {
            writeText("active")
            setLastModified(oldTime)
        }
        val freshUntracked = java.io.File(scansDir, "in_progress.jpg").apply {
            writeText("recent")
            setLastModified(recentTime)
        }

        val deletedCount = com.example.util.ImageUtils.cleanupOrphanScanFiles(
            context = context,
            activePaths = setOf(activeOld.absolutePath),
            minAgeMillis = 120_000L,
            nowMillis = now
        )

        assertEquals(1, deletedCount)
        assertTrue(!orphanOld.exists())
        assertTrue(activeOld.exists())
        assertTrue(freshUntracked.exists())

        scansDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `formatAnalysisForCopy outputs structured produce details in English and Mongolian`() {
        val sampleJson = """
            {
              "isProduce": true,
              "produceName": "Honeycrisp Apple",
              "variety": "Honeycrisp",
              "category": "Fruit",
              "ripenessStage": "PERFECTLY_RIPE",
              "ripenessScore": 91,
              "confidencePercent": 97,
              "estimatedLengthCm": 8.2,
              "estimatedWidthCm": 7.9,
              "estimatedWeightGrams": 210,
              "servingWeightSummary": "1 medium apple (~110 kcal)",
              "visualCues": ["Crisp crimson blush", "Tight unbruised skin"],
              "bestTimeToConsume": "Enjoy fresh today",
              "storageTitle": "Crisper Drawer",
              "storageInstructions": "Store chilled at 2-4°C away from onions.",
              "storageAvoidTip": "Avoid warm sunny countertops.",
              "nutrientPreservationRule": "Keep the skin on for maximum quercetin and fiber.",
              "vitamins": [{"name": "Vitamin C", "amount": "14% DV", "benefit": "Antioxidant immunity"}],
              "nutrients": [{"name": "Pectin Fiber", "amount": "4.4g", "benefit": "Prebiotic gut satiety"}],
              "healthBenefits": [{"category": "Dieting", "title": "Low-calorie crunch", "detail": "High water and fiber aid fullness."}],
              "cookingSuggestions": [{"title": "Cinnamon Apple Slices", "method": "Raw", "prepMinutes": 3, "nutrientTip": "No heat keeps Vitamin C intact", "pairing": "Walnuts"}]
            }
        """.trimIndent()

        val parsed = ProduceVisionAiService.parseAnalysisJson(sampleJson, languageCode = "en")

        val copiedEn = ProduceVisionAiService.formatAnalysisForCopy(parsed, AppLanguage.ENGLISH)
        assertTrue(copiedEn.contains("Honeycrisp Apple"))
        assertTrue(copiedEn.contains("Ripeness:"))
        assertTrue(copiedEn.contains("210g"))
        assertTrue(copiedEn.contains("Vitamin C"))
        assertTrue(copiedEn.contains("Pectin Fiber"))
        assertTrue(copiedEn.contains("Cinnamon Apple Slices"))

        val copiedMn = ProduceVisionAiService.formatAnalysisForCopy(parsed, AppLanguage.MONGOLIAN)
        assertTrue(copiedMn.contains("Honeycrisp Apple"))
        assertTrue(copiedMn.contains("Боловсролт:"))
        assertTrue(copiedMn.contains("210г"))
        assertTrue(copiedMn.contains("Витамин:"))
        assertTrue(copiedMn.contains("Шим тэжээл ба эрдэс:"))
        assertTrue(copiedMn.contains("Хоолонд ашиглах санаа:"))
    }

    @Test
    fun `localized delete descriptions and ripeness normalization handle edge cases`() {
        val enStrings = com.example.ui.i18n.stringsFor(AppLanguage.ENGLISH)
        val mnStrings = com.example.ui.i18n.stringsFor(AppLanguage.MONGOLIAN)
        assertEquals("Delete Hass Avocado", enStrings.deleteItemContentDesc("Hass Avocado"))
        assertEquals("Авокадо устгах", mnStrings.deleteItemContentDesc("Авокадо"))

        assertEquals(RipenessStage.UNDERRIPE, RipenessStage.fromApiValue("unripe"))
        assertEquals(RipenessStage.PERFECTLY_RIPE, RipenessStage.fromApiValue("peak"))
        assertEquals(RipenessStage.OVERRIPE, RipenessStage.fromApiValue("very ripe"))
        assertEquals(RipenessStage.DAMAGED, RipenessStage.fromApiValue("bruised"))
    }

    @Test
    fun `visual telemetry extracts color cast and jpeg compression stays compact`() {
        val bmp = android.graphics.Bitmap.createBitmap(120, 80, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(230, 190, 90))

        val telemetry = com.example.util.ImageUtils.extractVisualTelemetry(bmp)
        assertEquals(120, telemetry.width)
        assertEquals(80, telemetry.height)
        assertTrue(telemetry.warmCastScore > 20)
        assertTrue(telemetry.brightnessPercent in 50..90)
        assertTrue(telemetry.promptSummary.contains("120x80"))

        val jpeg = com.example.util.ImageUtils.bitmapToJpegBytes(bmp)
        assertTrue(jpeg.isNotEmpty())
        assertTrue(jpeg.size < 25_000)
    }

    @Test
    fun `storage stats track scan bytes and clearAllScanFiles removes everything`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scansDir = java.io.File(context.filesDir, "scans").apply { mkdirs() }
        scansDir.listFiles()?.forEach { it.delete() }

        java.io.File(scansDir, "a.jpg").writeBytes(ByteArray(2048) { 1 })
        java.io.File(scansDir, "b.jpg").writeBytes(ByteArray(4096) { 2 })

        val stats = com.example.util.ImageUtils.getScanStorageStats(context)
        assertEquals(2, stats.fileCount)
        assertEquals(6144L, stats.totalBytes)
        assertTrue(stats.formattedSize().contains("KB"))

        val cleared = com.example.util.ImageUtils.clearAllScanFiles(context)
        assertEquals(2, cleared)
        assertEquals(0, com.example.util.ImageUtils.getScanStorageStats(context).fileCount)
    }

    @Test
    fun `duplicate vitamins and nutrients are deduplicated cleanly`() {
        val json = """
            {
              "isProduce": true,
              "produceName": "Spinach",
              "vitamins": [
                {"name": "Vitamin K", "amount": "180% DV", "benefit": "Bone"},
                {"name": "vitamin k", "amount": "180% DV", "benefit": "Dup"}
              ],
              "nutrients": [
                {"name": "Iron", "amount": "15% DV", "benefit": "Oxygen"},
                {"name": "Iron ", "amount": "15% DV", "benefit": "Dup"}
              ]
            }
        """.trimIndent()
        val parsed = ProduceVisionAiService.parseAnalysisJson(json)
        assertEquals(1, parsed.vitamins.size)
        assertEquals(1, parsed.nutrients.size)
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `pantry screen supports search filtering and clear all confirmation`() {
        val avocado = ProduceVisionAiService.parseAnalysisJson(
            """{"isProduce":true,"produceName":"Hass Avocado","variety":"Hass"}""",
            id = "id-avo"
        )
        val mango = ProduceVisionAiService.parseAnalysisJson(
            """{"isProduce":true,"produceName":"Golden Mango","variety":"Ataulfo"}""",
            id = "id-mango"
        )
        var clearedCalled = false

        composeRule.setContent {
            com.example.ui.theme.RipeWiseTheme {
                com.example.ui.screens.PantryHistoryScreen(
                    scans = listOf(avocado, mango),
                    storageStats = com.example.util.ScanStorageStats(fileCount = 2, totalBytes = 102_400L),
                    language = AppLanguage.ENGLISH,
                    onSelectLanguage = {},
                    onSelectScan = {},
                    onDeleteScan = {},
                    onClearAllScans = { clearedCalled = true },
                    onNavigateToScanner = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pantry_item_id-avo").assertExists()
        composeRule.onNodeWithTag("pantry_list")
            .performScrollToNode(hasTestTag("pantry_item_id-mango"))
        composeRule.onNodeWithTag("pantry_item_id-mango").assertExists()

        composeRule.onNodeWithTag("pantry_search_input")
            .performTextInput("Mango")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pantry_item_id-avo").assertDoesNotExist()
        composeRule.onNodeWithTag("pantry_item_id-mango").assertExists()

        composeRule.onNodeWithTag("clear_all_pantry_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_clear_pantry_button").performClick()
        composeRule.waitForIdle()

        assertTrue(clearedCalled)
    }

    @Test
    fun `report screen exposes copy share refresh and filter chips`() {
        val sample = ProduceVisionAiService.parseAnalysisJson(
            """
            {
              "isProduce": true,
              "produceName": "Vine Tomato",
              "vitamins": [{"name": "Vitamin C", "amount": "28% DV", "benefit": "Skin"}],
              "nutrients": [{"name": "Lycopene", "amount": "High", "benefit": "Antioxidant"}]
            }
            """.trimIndent(),
            id = "id-tom",
            sampleKey = "tomatoes"
        )

        composeRule.setContent {
            com.example.ui.theme.RipeWiseTheme {
                com.example.ui.screens.ProduceReportScreen(
                    results = listOf(sample),
                    selectedIndex = 0,
                    totalAttempted = 1,
                    isTranslating = false,
                    translationWarning = null,
                    preferences = com.example.model.UserPreferences(),
                    chatState = com.example.ui.viewmodel.ChatUiState(),
                    onSelectBatchIndex = {},
                    onSelectLanguage = {},
                    onRefreshForProfile = {},
                    onDismissTranslationWarning = {},
                    onBackToScanner = {},
                    onOpenChat = {},
                    onCloseChat = {},
                    onSendChatMessage = {},
                    onDismissChatError = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("copy_report_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("copied_confirmation_banner").assertExists()
        composeRule.onNodeWithTag("share_report_button").assertExists()
        composeRule.onNodeWithTag("refresh_report_button").assertExists()
        composeRule.onNodeWithTag("result_filter_vitamins").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `scanner screen wires camera permission request and permanently denied settings callbacks`() {
        var requestClicks = 0
        var settingsClicks = 0
        val permanentlyDenied = androidx.compose.runtime.mutableStateOf(false)

        composeRule.setContent {
            com.example.ui.theme.RipeWiseTheme {
                com.example.ui.screens.ScannerScreen(
                    scanState = com.example.ui.viewmodel.ScanUiState.Idle,
                    preferences = com.example.model.UserPreferences(),
                    hasHardwareDepthSensor = false,
                    recentCount = 0,
                    hasCameraPermission = false,
                    cameraPermissionDenied = true,
                    cameraPermissionPermanentlyDenied = permanentlyDenied.value,
                    onRequestCameraPermission = { requestClicks++ },
                    onDismissCameraDeniedNotice = {},
                    onOpenAppSettings = { settingsClicks++ },
                    onCaptureImageProxy = {},
                    onPickMultipleImages = {},
                    onAnalyzeSample = {},
                    onSelectLanguage = {},
                    onSetSpatialMode = {},
                    onSetSpatialScaleHint = {},
                    onOpenPantry = {},
                    onOpenPreferences = {},
                    onDismissError = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("enable_camera_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, requestClicks)
        assertEquals(0, settingsClicks)

        permanentlyDenied.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("enable_camera_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, requestClicks)
        assertEquals(1, settingsClicks)
    }

    @Test
    fun `ripenessScore is calibrated consistently within its ripenessStage band`() {
        val damagedHigh = ProduceVisionAiService.parseAnalysisJson(
            """{"isProduce":true,"produceName":"Peach","ripenessStage":"DAMAGED","ripenessScore":88}"""
        )
        assertEquals(RipenessStage.DAMAGED, damagedHigh.ripenessStage)
        assertTrue(damagedHigh.ripenessScore <= 45)

        val underripeHigh = ProduceVisionAiService.parseAnalysisJson(
            """{"isProduce":true,"produceName":"Banana","ripenessStage":"UNDERRIPE","ripenessScore":92}"""
        )
        assertEquals(RipenessStage.UNDERRIPE, underripeHigh.ripenessStage)
        assertTrue(underripeHigh.ripenessScore in 15..65)
    }

    @Test
    fun `report screen pinned back button returns to scanner`() {
        val sample = ProduceVisionAiService.parseAnalysisJson(
            """{"isProduce":true,"produceName":"Honeycrisp Apple"}""",
            id = "id-back-test",
            sampleKey = "apples"
        )
        var backClicked = false

        composeRule.setContent {
            com.example.ui.theme.RipeWiseTheme {
                com.example.ui.screens.ProduceReportScreen(
                    results = listOf(sample),
                    selectedIndex = 0,
                    totalAttempted = 1,
                    isTranslating = false,
                    translationWarning = null,
                    preferences = com.example.model.UserPreferences(),
                    chatState = com.example.ui.viewmodel.ChatUiState(),
                    onSelectBatchIndex = {},
                    onSelectLanguage = {},
                    onRefreshForProfile = {},
                    onDismissTranslationWarning = {},
                    onBackToScanner = { backClicked = true },
                    onOpenChat = {},
                    onCloseChat = {},
                    onSendChatMessage = {},
                    onDismissChatError = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("report_back_button").assertExists().performClick()
        composeRule.waitForIdle()
        assertTrue(backClicked)
    }

    @Test
    fun `personalization screen renders compact top-right language switch`() {
        var selectedLanguage = AppLanguage.ENGLISH

        composeRule.setContent {
            com.example.ui.theme.RipeWiseTheme {
                com.example.ui.screens.ProfilePreferencesScreen(
                    preferences = com.example.model.UserPreferences(language = AppLanguage.ENGLISH),
                    hasHardwareDepthSensor = false,
                    onSelectLanguage = { selectedLanguage = it },
                    onToggleNutrientNeed = {},
                    onToggleDietaryGoal = {},
                    onToggleHealthFocus = {},
                    onToggleCookingTool = {},
                    onSetLightingCalibration = {},
                    onSetSpatialMode = {},
                    onSetSpatialScaleHint = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_lang_en").assertExists()
        composeRule.onNodeWithTag("profile_lang_mn").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.MONGOLIAN, selectedLanguage)
    }
}



