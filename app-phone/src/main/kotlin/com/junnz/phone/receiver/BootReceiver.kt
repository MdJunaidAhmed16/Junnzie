package com.junnz.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.scheduling.ReminderScheduler
import com.junnz.phone.service.JunnzForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Timber.d("Boot completed - restarting service and rescheduling alarms")
        JunnzForegroundService.start(context)

        val pending = goAsync()
        scope.launch {
            try {
                // Exact alarms don't survive reboot — re-arm every active reminder.
                repository.activeReminders.first().forEach { scheduler.schedule(it) }
            } catch (e: Exception) {
                Timber.e(e, "BootReceiver: failed to reschedule alarms")
            } finally {
                pending.finish()
            }
        }
    }
}
