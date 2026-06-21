package com.junnz.phone.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects when a watched app is resumed to the foreground, using
 * [UsageStatsManager] events. Requires the special "Usage access" grant
 * ([android.Manifest.permission.PACKAGE_USAGE_STATS]); without it
 * [hasUsageAccess] returns false and detection is skipped.
 */
@Singleton
class AppForegroundDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOps =
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    private var lastQueryTime = 0L

    /**
     * Returns the first watched package that resumed to the foreground since the
     * previous call, or null. Each app-open surfaces a single resume event, so a
     * package is reported once per open (no repeats while it stays foreground).
     */
    fun detectForeground(watched: Set<String>): String? {
        val now = System.currentTimeMillis()
        val begin = if (lastQueryTime == 0L) now - INITIAL_WINDOW_MS else lastQueryTime
        lastQueryTime = now

        val events = usageStatsManager.queryEvents(begin, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.packageName in watched
            ) {
                return event.packageName
            }
        }
        return null
    }

    fun hasUsageAccess(): Boolean = try {
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        Timber.w(e, "AppForegroundDetector: usage-access check failed")
        false
    }

    companion object {
        private const val INITIAL_WINDOW_MS = 10_000L
    }
}
