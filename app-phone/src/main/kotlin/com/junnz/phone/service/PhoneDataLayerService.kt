package com.junnz.phone.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.domain.UserAction
import com.junnz.shared.ipc.IpcProtocol
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PhoneDataLayerService : WearableListenerService() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var watchSync: WatchSyncManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("PhoneDataLayerService received: ${event.path}")
        if (!event.path.startsWith(IpcProtocol.PATH_ACTION)) return
        scope.launch {
            try {
                handleAction(event)
            } catch (e: Exception) {
                Timber.e(e, "Error handling action ${event.path}")
            }
        }
    }

    private suspend fun handleAction(event: MessageEvent) {
        val payload = JSONObject(String(event.data, Charsets.UTF_8))
        val reminderId = payload.getString(IpcProtocol.KEY_REMINDER_ID)
        val action = UserAction.Action.valueOf(payload.getString(IpcProtocol.KEY_ACTION))

        Timber.d("Watch action: $action on $reminderId")
        when (action) {
            UserAction.Action.COMPLETE,
            UserAction.Action.DISMISS -> reminderRepository.dismiss(reminderId)

            UserAction.Action.SNOOZE_5M,
            UserAction.Action.SNOOZE_1H,
            UserAction.Action.SNOOZE_TOMORROW ->
                reminderRepository.updateStatus(reminderId, ReminderStatus.SNOOZED)
        }
        watchSync.syncReminders()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
