package com.junnz.phone.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.junnz.phone.receiver.AlarmReceiver
import com.junnz.phone.ui.MainActivity
import com.junnz.shared.domain.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts the heads-up notification when a reminder fires. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notify(reminder: Reminder) {
        ensureChannel()
        if (!canPost()) return

        val notifId = reminder.id.hashCode()
        val openPi = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(reminder.text)
            .setContentText(reminderSubtitle(reminder))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "Done", actionPendingIntent(reminder.id, AlarmReceiver.ACTION_DONE, notifId + 1))
            .addAction(0, "Snooze 30m", actionPendingIntent(reminder.id, AlarmReceiver.ACTION_SNOOZE, notifId + 2))
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    fun cancel(reminderId: String) {
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
    }

    private fun reminderSubtitle(reminder: Reminder): String =
        reminder.tags.firstOrNull()?.replaceFirstChar { it.titlecase() }?.let { "$it · Junnz" }
            ?: "Junnz reminder"

    private fun actionPendingIntent(reminderId: String, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Junnz reminder alerts" },
            )
        }
    }

    private fun canPost(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    companion object {
        const val CHANNEL_ID = "junnz_reminders"
    }
}
