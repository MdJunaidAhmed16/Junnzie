package com.junnz.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.junnz.phone.R
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.detection.AppForegroundDetector
import com.junnz.phone.matching.ContextEventDispatcher
import com.junnz.phone.ui.MainActivity
import com.junnz.shared.domain.ContextEvent
import com.junnz.shared.domain.Trigger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.datetime.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class JunnzForegroundService : Service() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var watchSync: WatchSyncManager

    @Inject
    lateinit var foregroundDetector: AppForegroundDetector

    @Inject
    lateinit var contextDispatcher: ContextEventDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Packages watched by active app-context reminders; drives the poll loop. */
    @Volatile
    private var watchedPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        Timber.d("JunnzForegroundService started")

        scope.launch {
            watchSync.syncReminders()

            reminderRepository.activeReminders.collect { reminders ->
                watchedPackages = reminders
                    .filter { it.status == com.junnz.shared.domain.ReminderStatus.PENDING }
                    .flatMap { r ->
                        r.triggers.filterIsInstance<Trigger.AppContextTrigger>()
                            .flatMap { it.packageNames }
                    }
                    .toSet()

                // Only this service detects app/semantic context; time & location
                // triggers run off alarms/geofences, so stand down when there's
                // no context work to watch.
                val hasContextTriggers = reminders.any { r ->
                    r.triggers.any { it is Trigger.SemanticTrigger || it is Trigger.AppContextTrigger }
                }
                if (!hasContextTriggers) {
                    Timber.d("No active context reminders - stopping service")
                    stopSelf()
                }
            }
        }

        scope.launch { foregroundPollLoop() }

        return START_STICKY
    }

    private suspend fun foregroundPollLoop() {
        while (scope.isActive) {
            val watched = watchedPackages
            if (watched.isNotEmpty() && foregroundDetector.hasUsageAccess()) {
                foregroundDetector.detectForeground(watched)?.let { pkg ->
                    contextDispatcher.dispatch(ContextEvent.AppForegrounded(pkg, Clock.System.now()))
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Junnz is active")
            .setContentText("Watching for context triggers")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(launchIntent)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Junnz Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Context matching is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "junnz_service"
        private const val POLL_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, JunnzForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JunnzForegroundService::class.java))
        }
    }
}
