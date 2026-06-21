package com.junnz.phone.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.notification.ReminderNotifier
import com.junnz.phone.service.WatchSyncManager
import com.junnz.shared.domain.ReminderStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var watchSync: WatchSyncManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Timber.e("GeofencingEvent error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            return
        }
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_DWELL
        ) return

        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        val pending = goAsync()
        scope.launch {
            try {
                for (reminderId in ids) {
                    val reminder = repository.getById(reminderId) ?: continue
                    if (reminder.status == ReminderStatus.DISMISSED) continue
                    Timber.d("Geofence fired for ${reminder.id}")
                    notifier.notify(reminder)
                    // markFired cancels the geofence + alarm via the repository.
                    repository.markFired(reminderId)
                }
                watchSync.syncReminders()
            } catch (e: Exception) {
                Timber.e(e, "GeofenceBroadcastReceiver failure")
            } finally {
                pending.finish()
            }
        }
    }
}
