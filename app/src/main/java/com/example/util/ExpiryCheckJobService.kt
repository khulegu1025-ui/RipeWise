package com.example.util

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.example.ai.ProduceVisionAiService
import com.example.data.PreferencesRepository
import com.example.data.RipeWiseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExpiryCheckJobService : JobService() {

    private var serviceScope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        scope.launch {
            try {
                val appContext = applicationContext
                val prefs = PreferencesRepository(appContext).userPreferences.first()
                if (!prefs.expiryRemindersEnabled) return@launch

                val entities = RipeWiseDatabase.getInstance(appContext)
                    .scanHistoryDao()
                    .observeAllScans()
                    .first()

                val scans = entities.mapNotNull { entity ->
                    runCatching {
                        ProduceVisionAiService.parseAnalysisJson(
                            jsonText = entity.analysisJson,
                            id = entity.id,
                            languageCode = prefs.language.code,
                            localImagePath = entity.localImagePath,
                            sampleKey = entity.sampleKey,
                            scannedAtMillis = entity.scannedAtMillis
                        )
                    }.getOrNull()
                }

                ExpiryReminderManager.checkAndNotifyIfNeeded(
                    context = appContext,
                    scans = scans,
                    language = prefs.language
                )
            } catch (e: Exception) {
                Log.w("ExpiryCheckJobService", "Background expiry check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope?.cancel()
        serviceScope = null
        return true
    }
}
