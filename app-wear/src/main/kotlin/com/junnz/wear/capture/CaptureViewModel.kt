package com.junnz.wear.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.junnz.shared.ipc.IpcProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

sealed class CaptureState {
    object Idle : CaptureState()
    object Connecting : CaptureState()
    data class Recording(val rmsLevel: Float, val chunksCount: Int) : CaptureState()
    object Processing : CaptureState()
    data class Preview(val transcript: String) : CaptureState()
    object Saved : CaptureState()
    data class Error(val message: String) : CaptureState()
    object NoPhone : CaptureState()
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    app: Application,
) : AndroidViewModel(app) {

    private val messageClient: MessageClient = Wearable.getMessageClient(app)

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private var captureJob: Job? = null
    private var sessionId: String = ""

    private val transcriptListener = MessageClient.OnMessageReceivedListener { event ->
        if (!event.path.startsWith(IpcProtocol.PATH_CAPTURE_TRANSCRIPT)) return@OnMessageReceivedListener
        try {
            val payload = JSONObject(String(event.data, Charsets.UTF_8))
            val sid = payload.optString(IpcProtocol.KEY_SESSION_ID)
            if (sid == sessionId && _state.value is CaptureState.Processing) {
                val transcript = payload.optString(IpcProtocol.KEY_TRANSCRIPT, "")
                val outcome = payload.optString(IpcProtocol.KEY_OUTCOME, IpcProtocol.OUTCOME_ERROR)
                Timber.d("Transcript received: \"$transcript\" outcome=$outcome")
                _state.value = if (outcome == IpcProtocol.OUTCOME_SAVED) {
                    CaptureState.Preview(transcript)
                } else {
                    CaptureState.Error(
                        if (outcome == IpcProtocol.OUTCOME_NO_MATCH) "No reminder detected — try again"
                        else "Something went wrong"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse transcript message")
            _state.value = CaptureState.Error("Failed to read response")
        }
    }

    init {
        messageClient.addListener(transcriptListener)
    }

    fun startCapture() {
        if (_state.value is CaptureState.Recording) return
        sessionId = UUID.randomUUID().toString()
        captureJob = viewModelScope.launch {
            try {
                val nodeId = getPhoneNodeId()
                if (nodeId == null) {
                    _state.value = CaptureState.NoPhone
                    return@launch
                }
                sendMessage(nodeId, IpcProtocol.PATH_CAPTURE_START, buildSessionPayload())
                _state.value = CaptureState.Recording(0f, 0)

                var chunkIndex = 0
                AudioCapture.chunkFlow().collect { chunk ->
                    sendBinaryMessage(nodeId, IpcProtocol.PATH_CAPTURE_AUDIO + sessionId, chunk.pcm)
                    chunkIndex++
                    _state.value = CaptureState.Recording(chunk.rmsLevel, chunkIndex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Capture failed")
                _state.value = CaptureState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        viewModelScope.launch {
            try {
                val nodeId = getPhoneNodeId() ?: run {
                    _state.value = CaptureState.Error("Phone not connected")
                    return@launch
                }
                sendMessage(nodeId, IpcProtocol.PATH_CAPTURE_END + sessionId, buildSessionPayload())
                Timber.d("Sent CAPTURE_END for session $sessionId")
                _state.value = CaptureState.Processing
            } catch (e: Exception) {
                Timber.e(e, "Stop capture failed")
                _state.value = CaptureState.Error(e.message ?: "Unknown error")
            }
        }
        viewModelScope.launch {
            delay(15_000)
            if (_state.value is CaptureState.Processing) {
                Timber.e("Processing timeout — phone did not respond within 15s")
                _state.value = CaptureState.Error("Phone didn't respond")
            }
        }
    }

    fun confirmSave() {
        _state.value = CaptureState.Saved
    }

    fun reset() {
        captureJob?.cancel()
        captureJob = null
        _state.value = CaptureState.Idle
    }

    override fun onCleared() {
        messageClient.removeListener(transcriptListener)
        captureJob?.cancel()
        super.onCleared()
    }

    private suspend fun getPhoneNodeId(): String? {
        return try {
            val nodes = Wearable.getNodeClient(getApplication<Application>())
                .connectedNodes.await()
            nodes.firstOrNull()?.id
        } catch (e: Exception) {
            Timber.e(e, "Failed to get phone node")
            null
        }
    }

    private suspend fun sendMessage(nodeId: String, path: String, payload: ByteArray) {
        messageClient.sendMessage(nodeId, path, payload).await()
    }

    private suspend fun sendBinaryMessage(nodeId: String, path: String, data: ByteArray) {
        messageClient.sendMessage(nodeId, path, data).await()
    }

    private fun buildSessionPayload(): ByteArray {
        return JSONObject().apply {
            put(IpcProtocol.KEY_SESSION_ID, sessionId)
            put(IpcProtocol.KEY_PROTOCOL_VERSION, IpcProtocol.VERSION)
        }.toString().toByteArray(Charsets.UTF_8)
    }
}
