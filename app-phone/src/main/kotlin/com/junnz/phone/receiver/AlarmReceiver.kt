package com.junnz.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.notification.ReminderNotifier
import com.junnz.phone.scheduling.ReminderScheduler
import com.junnz.phone.service.WatchSyncManager
import com.junnz.shared.domain.ReminderStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives exact-alarm wake-ups (and the Done/Snooze notification actions).
 * Uses [goAsync] so the short DB + notification work can run off the main
 * thread before the broadcast is released.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var watchSync: WatchSyncManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val action = intent.action ?: ACTION_FIRE
        Timber.d("AlarmReceiver $action for $reminderId")

        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_DONE -> {
                        repository.complete(reminderId)
                        notifier.cancel(reminderId)
                        watchSync.syncReminders()
                    }
                    ACTION_SNOOZE -> {
                        notifier.cancel(reminderId)
                        repository.updateStatus(reminderId, ReminderStatus.SNOOZED)
                        scheduler.scheduleAt(reminderId, System.currentTimeMillis() + SNOOZE_MS)
                    }
                    else -> { // ACTION_FIRE
                        val reminder = repository.getById(reminderId) ?: return@launch
                        if (reminder.status == ReminderStatus.DISMISSED) return@launch
                        notifier.notify(reminder)
                        repository.markFired(reminderId)
                        watchSync.syncReminders()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "AlarmReceiver failed for $reminderId")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminderId"
        const val ACTION_FIRE = "com.junnz.phone.action.FIRE"
        const val ACTION_DONE = "com.junnz.phone.action.DONE"
        const val ACTION_SNOOZE = "com.junnz.phone.action.SNOOZE"
        private const val SNOOZE_MS = 30 * 60 * 1000L
    }
}
