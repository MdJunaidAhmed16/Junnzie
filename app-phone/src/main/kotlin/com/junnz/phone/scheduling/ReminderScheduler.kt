package com.junnz.phone.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.junnz.phone.receiver.AlarmReceiver
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.Trigger
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exact alarms for a reminder's earliest [Trigger.TimeTrigger].
 * Falls back to an inexact, doze-friendly alarm when the OS withholds the
 * exact-alarm capability (Android 12+ without the user grant).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Schedule (or reschedule) the soonest future time trigger for this reminder. */
    fun schedule(reminder: Reminder) {
        val fireAt = reminder.triggers
            .filterIsInstance<Trigger.TimeTrigger>()
            .minByOrNull { it.fireAt }
            ?.fireAt ?: return
        val triggerMs = fireAt.toEpochMilliseconds()
        if (triggerMs <= System.currentTimeMillis()) {
            Timber.d("ReminderScheduler: ${reminder.id} fireAt in past, not scheduling")
            return
        }
        scheduleAt(reminder.id, triggerMs)
        Timber.d("ReminderScheduler: scheduled ${reminder.id} for $fireAt")
    }

    /** Schedule a single wake-up for [reminderId] at [triggerMs] (used for snooze too). */
    fun scheduleAt(reminderId: String, triggerMs: Long) {
        val pi = firePendingIntent(reminderId)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (e: SecurityException) {
            // Exact permission revoked between check and call — degrade gracefully.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            Timber.w(e, "ReminderScheduler: exact denied, scheduled inexact for $reminderId")
        }
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(firePendingIntent(reminderId))
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun firePendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
