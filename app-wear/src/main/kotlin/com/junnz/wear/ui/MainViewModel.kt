package com.junnz.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.UserAction
import com.junnz.shared.ipc.IpcProtocol
import com.junnz.wear.sync.WatchReminderCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val reminderCache: WatchReminderCache,
) : AndroidViewModel(app) {

    val reminders: StateFlow<List<Reminder>> = reminderCache.reminders
    val latestFire = reminderCache.latestFire

    fun sendAction(reminderId: String, action: UserAction.Action) {
        viewModelScope.launch {
            try {
                val nodeId = getPhoneNodeId() ?: return@launch
                val payload = JSONObject().apply {
                    put(IpcProtocol.KEY_SESSION_ID, UUID.randomUUID().toString())
                    put(IpcProtocol.KEY_REMINDER_ID, reminderId)
                    put(IpcProtocol.KEY_ACTION, action.name)
                    put(IpcProtocol.KEY_PROTOCOL_VERSION, IpcProtocol.VERSION)
                }.toString().toByteArray(Charsets.UTF_8)

                Wearable.getMessageClient(getApplication<Application>())
                    .sendMessage(nodeId, IpcProtocol.PATH_ACTION + reminderId, payload)
                    .await()
            } catch (e: Exception) {
                Timber.e(e, "Failed to send action $action for $reminderId")
            }
        }
    }

    private suspend fun getPhoneNodeId(): String? {
        return try {
            Wearable.getNodeClient(getApplication<Application>())
                .connectedNodes.await()
                .firstOrNull()?.id
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }
}
