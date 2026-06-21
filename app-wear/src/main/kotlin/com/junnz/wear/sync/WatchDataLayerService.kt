package com.junnz.wear.sync

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.junnz.shared.domain.Reminder
import com.junnz.shared.ipc.IpcProtocol
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WatchDataLayerService : WearableListenerService() {

    @Inject
    lateinit var reminderCache: WatchReminderCache

    @Inject
    lateinit var captureResultBus: CaptureResultBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            val path = event.dataItem.uri.path ?: continue
            Timber.d("DataChanged: $path")
            when (path) {
                IpcProtocol.DATA_PATH_REMINDERS -> handleReminderDataItem(event.dataItem.data)
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        Timber.d("MessageReceived: $path from ${event.sourceNodeId}")
        when {
            path.startsWith(IpcProtocol.PATH_CAPTURE_TRANSCRIPT) -> {
                handleTranscriptMessage(event.data)
            }
            path == IpcProtocol.PATH_FIRE -> {
                handleFireMessage(event.data)
            }
        }
    }

    private fun handleReminderDataItem(data: ByteArray?) {
        if (data == null) return
        scope.launch {
            try {
                val json = String(data, Charsets.UTF_8)
                val payload = JSONObject(json)
                val listJson = payload.getString(IpcProtocol.KEY_REMINDER_LIST)
                val reminders = Json.decodeFromString<List<Reminder>>(listJson)
                reminderCache.update(reminders)
                Timber.d("Updated ${reminders.size} reminders from phone")
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse reminders DataItem")
            }
        }
    }

    private fun handleTranscriptMessage(data: ByteArray) {
        scope.launch {
            try {
                val json = String(data, Charsets.UTF_8)
                val payload = JSONObject(json)
                val sessionId = payload.getString(IpcProtocol.KEY_SESSION_ID)
                val transcript = payload.optString(IpcProtocol.KEY_TRANSCRIPT, "")
                val intent = payload.optString(IpcProtocol.KEY_INTENT, IpcProtocol.INTENT_UNKNOWN)
                val outcome = payload.optString(IpcProtocol.KEY_OUTCOME, IpcProtocol.OUTCOME_ERROR)
                captureResultBus.post(CaptureResult(sessionId, transcript, intent, outcome))
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse transcript message")
            }
        }
    }

    private fun handleFireMessage(data: ByteArray) {
        scope.launch {
            try {
                val json = String(data, Charsets.UTF_8)
                val payload = JSONObject(json)
                val reminderId = payload.getString(IpcProtocol.KEY_REMINDER_ID)
                val reason = payload.optString(IpcProtocol.KEY_REASON, "")
                reminderCache.markFired(reminderId, reason)
                Timber.d("Fire received for reminder $reminderId: $reason")
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse fire message")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
