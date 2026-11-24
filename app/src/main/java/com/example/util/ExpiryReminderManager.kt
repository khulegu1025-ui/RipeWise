package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.MainActivity
import com.example.R
import com.example.model.AppLanguage
import com.example.model.ProduceAnalysisResult
import com.example.ui.i18n.stringsFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExpiryReminderManager {

    private const val TAG = "ExpiryReminderManager"
    const val CHANNEL_ID = "ripewise_expiry_reminders"
    private const val JOB_ID = 4107
    private const val NOTIFICATION_ID = 9001
    private const val PREFS_NAME = "ripewise_expiry_prefs"
    private const val KEY_LAST_SIGNATURE = "last_notified_signature"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val strings = stringsFor(language)
            val channel = NotificationChannel(
                CHANNEL_ID,
                strings.expiryNotifChannelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = strings.expiryRemindersSubtitle
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun schedulePeriodicCheck(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val alreadyScheduled = scheduler.allPendingJobs.any { it.id == JOB_ID }
            if (alreadyScheduled) return
            val component = ComponentName(context, ExpiryCheckJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(12L * 60L * 60L * 1000L)
                .setPersisted(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setRequiresBatteryNotLow(true)
            }
            scheduler.schedule(builder.build())
        }.onFailure { err ->
            Log.w(TAG, "Unable to schedule periodic expiry job", err)
        }
    }

    fun cancelPeriodicCheck(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(JOB_ID)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }.onFailure { err ->
            Log.w(TAG, "Unable to cancel periodic expiry job", err)
        }
    }

    @SuppressLint("MissingPermission")
    fun checkAndNotifyIfNeeded(
        context: Context,
        scans: List<ProduceAnalysisResult>,
        language: AppLanguage,
        nowMillis: Long = System.currentTimeMillis(),
        forceSameDay: Boolean = false
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context, language)

        val urgentItems = scans.mapNotNull { item ->
            val projection = FreshnessTracker.evaluate(item, nowMillis)
            if (projection.urgency != ExpiryUrgency.FRESH) item to projection else null
        }

        if (urgentItems.isEmpty()) {
            runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
            return false
        }

        val dayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val signature = "$dayStamp:" + urgentItems.joinToString(",") { (item, proj) ->
            "${item.id}:${proj.urgency.name}"
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!forceSameDay && prefs.getString(KEY_LAST_SIGNATURE, null) == signature) {
            return false
        }

        val strings = stringsFor(language)
        val previewNames = urgentItems.take(3).joinToString(", ") { it.first.produceName }
        val title = strings.expiryNotifTitle(urgentItems.size)
        val body = strings.expiryNotifBody(previewNames)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit { putString(KEY_LAST_SIGNATURE, signature) }
            true
        }.getOrElse { err ->
            Log.w(TAG, "Failed to post expiry reminder notification", err)
            false
        }
    }
}
