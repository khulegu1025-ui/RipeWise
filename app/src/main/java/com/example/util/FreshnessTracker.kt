package com.example.util

import com.example.model.AppLanguage
import com.example.model.ProduceAnalysisResult
import com.example.model.RipenessStage
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class ExpiryUrgency {
    FRESH,
    EXPIRING_SOON,
    USE_TODAY,
    EXPIRED
}

data class FreshnessProjection(
    val scannedAtMillis: Long,
    val elapsedDays: Int,
    val remainingDaysMin: Int,
    val remainingDaysMax: Int,
    val originalStage: RipenessStage,
    val projectedStage: RipenessStage,
    val originalScore: Int,
    val projectedScore: Int,
    val scoreDelta: Int,
    val urgency: ExpiryUrgency
) {
    val hasRipenessChanged: Boolean
        get() = elapsedDays > 0 && (projectedScore != originalScore || projectedStage != originalStage)
}

object FreshnessTracker {

    private const val ONE_DAY_MILLIS = 86_400_000L

    fun calendarDaysElapsed(
        scannedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        if (nowMillis <= scannedAtMillis) return 0
        val elapsedHours = ((nowMillis - scannedAtMillis) / 3_600_000L).toInt()
        val fullDays = ((nowMillis - scannedAtMillis) / ONE_DAY_MILLIS).toInt()

        val scanCal = Calendar.getInstance().apply {
            timeInMillis = scannedAtMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calDelta = ((nowCal.timeInMillis - scanCal.timeInMillis) / ONE_DAY_MILLIS)
            .toInt()
            .coerceAtLeast(0)

        // Treat a new calendar day with at least 6 hours elapsed as tomorrow, or >= 24h elapsed
        return if (calDelta >= 1 && elapsedHours >= 6) {
            maxOf(fullDays, calDelta)
        } else {
            fullDays
        }
    }

    fun evaluate(
        result: ProduceAnalysisResult,
        nowMillis: Long = System.currentTimeMillis()
    ): FreshnessProjection {
        val elapsedDays = calendarDaysElapsed(result.scannedAtMillis, nowMillis)
        val remainingMin = (result.daysRemainingMin - elapsedDays).coerceAtLeast(0)
        val remainingMax = (result.daysRemainingMax - elapsedDays).coerceAtLeast(0)
        val origStage = result.ripenessStage
        val origScore = result.ripenessScore.coerceIn(5, 99)

        val projectedStage: RipenessStage
        val projectedScore: Int

        if (elapsedDays == 0) {
            projectedStage = origStage
            projectedScore = origScore
        } else {
            when (origStage) {
                RipenessStage.UNDERRIPE -> {
                    val windowSpan = (result.daysRemainingMax - result.daysRemainingMin).coerceAtLeast(1)
                    if (elapsedDays > result.daysRemainingMax) {
                        val overdue = elapsedDays - result.daysRemainingMax
                        projectedStage = RipenessStage.OVERRIPE
                        projectedScore = (64 - overdue * 12).coerceIn(15, 66)
                    } else if (elapsedDays >= result.daysRemainingMin) {
                        val progress = ((elapsedDays - result.daysRemainingMin).toDouble() / windowSpan)
                            .coerceIn(0.0, 1.0)
                        projectedStage = RipenessStage.PERFECTLY_RIPE
                        projectedScore = (86 - (progress * 10.0).roundToInt()).coerceIn(72, 92)
                    } else {
                        val stepUp = (elapsedDays * 14).coerceAtMost(20)
                        projectedStage = RipenessStage.UNDERRIPE
                        projectedScore = (origScore + stepUp).coerceIn(20, 68)
                    }
                }

                RipenessStage.PERFECTLY_RIPE -> {
                    if (result.daysRemainingMax <= 0) {
                        projectedStage = RipenessStage.OVERRIPE
                        projectedScore = (origScore - elapsedDays * 20).coerceIn(15, 65)
                    } else if (elapsedDays > result.daysRemainingMax) {
                        val overdue = elapsedDays - result.daysRemainingMax
                        projectedStage = RipenessStage.OVERRIPE
                        projectedScore = (64 - overdue * 14).coerceIn(15, 66)
                    } else if (elapsedDays == result.daysRemainingMax) {
                        projectedStage = if (result.daysRemainingMax == 1) {
                            RipenessStage.OVERRIPE
                        } else {
                            RipenessStage.PERFECTLY_RIPE
                        }
                        val drop = if (result.daysRemainingMax == 1) {
                            maxOf(20, origScore - 64)
                        } else {
                            16
                        }
                        projectedScore = (origScore - drop).coerceIn(42, 74)
                    } else {
                        // Freshness visibly steps down each passing day after scan
                        val fractionElapsed = elapsedDays.toDouble() / result.daysRemainingMax.coerceAtLeast(1)
                        val drop = maxOf(8, (fractionElapsed * 22.0).roundToInt())
                        val nextScore = (origScore - drop).coerceIn(66, 94)
                        projectedStage = if (nextScore < 70) RipenessStage.OVERRIPE else RipenessStage.PERFECTLY_RIPE
                        projectedScore = nextScore
                    }
                }

                RipenessStage.OVERRIPE -> {
                    projectedStage = RipenessStage.OVERRIPE
                    projectedScore = (origScore - elapsedDays * 16).coerceIn(10, 64)
                }

                RipenessStage.DAMAGED -> {
                    projectedStage = RipenessStage.DAMAGED
                    projectedScore = (origScore - elapsedDays * 14).coerceIn(5, 42)
                }
            }
        }

        val urgency = when {
            origStage == RipenessStage.DAMAGED -> ExpiryUrgency.USE_TODAY
            elapsedDays > result.daysRemainingMax -> ExpiryUrgency.EXPIRED
            remainingMax == 0 || projectedStage == RipenessStage.OVERRIPE -> ExpiryUrgency.USE_TODAY
            remainingMax == 1 -> ExpiryUrgency.EXPIRING_SOON
            else -> ExpiryUrgency.FRESH
        }

        return FreshnessProjection(
            scannedAtMillis = result.scannedAtMillis,
            elapsedDays = elapsedDays,
            remainingDaysMin = remainingMin,
            remainingDaysMax = remainingMax,
            originalStage = origStage,
            projectedStage = projectedStage,
            originalScore = origScore,
            projectedScore = projectedScore,
            scoreDelta = projectedScore - origScore,
            urgency = urgency
        )
    }

    fun formatScanTimestamp(
        millis: Long,
        language: AppLanguage
    ): String {
        return if (language == AppLanguage.MONGOLIAN) {
            SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(millis))
        } else {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .format(Date(millis))
        }
    }
}
