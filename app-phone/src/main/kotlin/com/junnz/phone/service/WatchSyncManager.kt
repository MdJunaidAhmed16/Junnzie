package com.junnz.phone.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.shared.domain.Reminder
import com.junnz.shared.ipc.IpcProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
) {
    /**
     * Push the full reminder list to the watch via DataClient.
     * Watch reads this via [WatchDataLayerService].
     */
    suspend fun syncReminders() {
        try {
            val reminders = reminderRepository.activeReminders.first()
            val payload = buildPayload(reminders)

            val request = PutDataMapRequest.create(IpcProtocol.DATA_PATH_REMINDERS).apply {
                dataMap.putString(IpcProtocol.KEY_REMINDER_LIST, Json.encodeToString(reminders))
                dataMap.putInt(IpcProtocol.KEY_PROTOCOL_VERSION, IpcProtocol.VERSION)
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }

            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
                .await()

            Timber.d("Synced ${reminders.size} reminders to watch")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync reminders to watch")
        }
    }

    /**
     * Push a fire event to the watch via MessageClient (broadcast to all nodes).
     */
    suspend fun sendFireToWatch(reminderId: String, reason: String) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val payload = JSONObject().apply {
                put(IpcProtocol.KEY_REMINDER_ID, reminderId)
                put(IpcProtocol.KEY_REASON, reason)
                put(IpcProtocol.KEY_PROTOCOL_VERSION, IpcProtocol.VERSION)
            }.toString().toByteArray(Charsets.UTF_8)

            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, IpcProtocol.PATH_FIRE, payload)
                    .await()
            }
            Timber.d("Sent fire for $reminderId to ${nodes.size} watch(es)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send fire event to watch")
        }
    }

    private fun buildPayload(reminders: List<Reminder>): ByteArray {
        return JSONObject().apply {
            put(IpcProtocol.KEY_REMINDER_LIST, Json.encodeToString(reminders))
            put(IpcProtocol.KEY_PROTOCOL_VERSION, IpcProtocol.VERSION)
        }.toString().toByteArray(Charsets.UTF_8)
    }
}
