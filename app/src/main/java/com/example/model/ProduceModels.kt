package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

enum class AppLanguage(val code: String, val shortLabel: String, val displayName: String) {
    ENGLISH("en", "EN", "English"),
    MONGOLIAN("mn", "МОН", "Монгол")
}

enum class SpatialScaleHint(val labelEn: String, val labelMn: String, val promptHint: String) {
    AUTO("Auto Scale", "Автомат", "Estimate real-world cultivar dimensions automatically."),
    PALM_SIZE("Hand / Palm (~9cm)", "Алган хэмжээ (~9см)", "Item is roughly hand-held / palm scale (~8-11 cm reference)."),
    PLATE_VIEW("Plate / Bunch (~22cm)", "Таваг / багц (~22см)", "Frame spans plate or bunch scale (~20-25 cm across).")
}

enum class ResultFilterTab {
    ALL,
    RIPENESS_SIZE,
    VITAMINS,
    NUTRIENTS,
    USAGE
}

enum class RipenessStage(val labelEn: String, val labelMn: String) {
    UNDERRIPE("Underripe", "Түүхийвтэр"),
    PERFECTLY_RIPE("Perfectly Ripe", "Яг боловсорсон"),
    OVERRIPE("Overripe", "Хэт боловсорсон"),
    DAMAGED("Damaged", "Гэмтэлтэй");

    fun localizedLabel(language: AppLanguage): String {
        return when (language) {
            AppLanguage.ENGLISH -> labelEn
            AppLanguage.MONGOLIAN -> labelMn
        }
    }

    companion object {
        fun fromApiValue(raw: String): RipenessStage {
            return when (raw.trim().uppercase().replace(" ", "_")) {
                "UNDERRIPE", "UNRIPE" -> UNDERRIPE
                "PERFECTLY_RIPE", "RIPE", "PEAK" -> PERFECTLY_RIPE
                "OVERRIPE", "VERY_RIPE" -> OVERRIPE
                "DAMAGED", "SPOILED", "BRUISED" -> DAMAGED
                else -> PERFECTLY_RIPE
            }
        }
    }
}

data class NutritionHighlight(
    val name: String,
    val amount: String,
    val benefit: String
)

data class HealthBenefit(
    val category: String,
    val title: String,
    val detail: String
)

data class CookingSuggestion(
    val title: String,
    val method: String,
    val prepMinutes: Int,
    val nutrientTip: String,
    val pairing: String
)

data class ProduceAnalysisResult(
    val id: String,
    val produceName: String,
    val variety: String,
    val category: String,
    val ripenessStage: RipenessStage,
    val ripenessScore: Int,
    val confidencePercent: Int,
    val estimatedLengthCm: Double,
    val estimatedWidthCm: Double,
    val estimatedWeightGrams: Int,
    val servingWeightSummary: String,
    val spatialMethodNote: String,
    val lightingNote: String,
    val visualCues: List<String>,
    val bestTimeToConsume: String,
    val daysRemainingMin: Int,
    val daysRemainingMax: Int,
    val storageTitle: String,
    val storageInstructions: String,
    val storageAvoidTip: String,
    val nutrientPreservationRule: String,
    val vitamins: List<NutritionHighlight>,
    val nutrients: List<NutritionHighlight>,
    val healthBenefits: List<HealthBenefit>,
    val cookingSuggestions: List<CookingSuggestion>,
    val quickQuestions: List<String>,
    val languageCode: String = "en",
    val localImagePath: String? = null,
    val sampleKey: String? = null,
    val scannedAtMillis: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val scanId: String = "",
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class UserPreferences(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val dietaryGoals: Set<String> = setOf("Plant-Forward", "Whole Foods"),
    val nutrientNeeds: Set<String> = setOf("High Fiber Needed", "Vitamin D Support", "Iron Boost"),
    val healthFocuses: Set<String> = setOf("Skin Glow", "Immunity", "Gut & Digestion"),
    val cookingTools: Set<String> = setOf("Air Fryer", "Steamer", "High-Speed Blender"),
    val lightingCalibrationEnabled: Boolean = true,
    val spatialModeEnabled: Boolean = true,
    val spatialScaleHint: SpatialScaleHint = SpatialScaleHint.AUTO
) {
    companion object {
        val ALL_DIETARY_GOALS = listOf(
            "Weight Loss / Dieting",
            "Low Sugar / Diabetic",
            "Low Carb / Keto",
            "Plant-Forward",
            "Whole Foods",
            "Anti-Inflammatory",
            "High Protein",
            "Heart-Healthy"
        )
        val ALL_NUTRIENT_NEEDS = listOf(
            "High Fiber Needed",
            "Vitamin D Support",
            "Iron Boost",
            "Vitamin C & Collagen",
            "Potassium & Hydration",
            "Calcium & Bone",
            "Folate & B-Vitamins"
        )
        val ALL_HEALTH_FOCUSES = listOf(
            "Skin Glow",
            "Immunity",
            "Gut & Digestion",
            "Sustained Energy",
            "Brain & Mood",
            "Muscle Recovery"
        )
        val ALL_COOKING_TOOLS = listOf(
            "Air Fryer",
            "Steamer",
            "High-Speed Blender",
            "Cast Iron Skillet",
            "Sheet Pan / Oven",
            "Instant Pot",
            "No-Cook / Raw"
        )
    }
}

data class SampleProduce(
    val key: String,
    val titleEn: String,
    val titleMn: String,
    val subtitleEn: String,
    val subtitleMn: String,
    @DrawableRes val drawableRes: Int
) {
    fun localizedTitle(language: AppLanguage): String =
        if (language == AppLanguage.MONGOLIAN) titleMn else titleEn

    fun localizedSubtitle(language: AppLanguage): String =
        if (language == AppLanguage.MONGOLIAN) subtitleMn else subtitleEn
}

object SampleProduceCatalog {
    val samples = listOf(
        SampleProduce(
            key = "avocado",
            titleEn = "Hass Avocado",
            titleMn = "Авокадо",
            subtitleEn = "Creamy & ripe",
            subtitleMn = "Зөөлөн боловсорсон",
            drawableRes = R.drawable.img_sample_avocado_1795260014977
        ),
        SampleProduce(
            key = "mango",
            titleEn = "Golden Mango",
            titleMn = "Алтан Манго",
            subtitleEn = "Sweet aroma",
            subtitleMn = "Чихэрлэг үнэртэй",
            drawableRes = R.drawable.img_sample_mango_1795260015295
        ),
        SampleProduce(
            key = "banana",
            titleEn = "Spotted Bananas",
            titleMn = "Толботой Гадил",
            subtitleEn = "Sugar speckles",
            subtitleMn = "Чихэрлэг толботой",
            drawableRes = R.drawable.img_sample_banana_1795260015875
        ),
        SampleProduce(
            key = "tomatoes",
            titleEn = "Vine Tomatoes",
            titleMn = "Улаан Лооль",
            subtitleEn = "Glossy heirloom",
            subtitleMn = "Шинэ гялалзсан",
            drawableRes = R.drawable.img_sample_tomatoes_1795260015565
        )
    )

    fun drawableForKey(key: String?): Int? {
        return samples.firstOrNull { it.key == key }?.drawableRes
    }
}
