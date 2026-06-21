package com.junnz.phone.service

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.ipc.IpcProtocol
import com.junnz.shared.parsing.ReminderParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearCaptureHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val watchSync: WatchSyncManager,
    private val asrService: AsrService,
) : MessageClient.OnMessageReceivedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioBuffers = mutableMapOf<String, ByteArrayOutputStream>()
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val parser = ReminderParser()

    init {
        messageClient.addListener(this)
            .addOnSuccessListener { Timber.d("WearCaptureHandler: MessageClient listener registered") }
            .addOnFailureListener { e -> Timber.e(e, "WearCaptureHandler: listener registration failed") }
    }

    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        Timber.d("WearCaptureHandler received: ${event.path}")
        scope.launch {
            try {
                when {
                    event.path == IpcProtocol.PATH_CAPTURE_START ->
                        handleCaptureStart(event)
                    event.path.startsWith(IpcProtocol.PATH_CAPTURE_AUDIO) ->
                        handleAudioChunk(event)
                    event.path.startsWith(IpcProtocol.PATH_CAPTURE_END) ->
                        handleCaptureEnd(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "WearCaptureHandler error for ${event.path}")
            }
        }
    }

    private fun handleCaptureStart(event: com.google.android.gms.wearable.MessageEvent) {
        val sessionId = JSONObject(String(event.data, Charsets.UTF_8))
            .getString(IpcProtocol.KEY_SESSION_ID)
        audioBuffers[sessionId] = ByteArrayOutputStream()
        Timber.d("Capture session started: $sessionId")
    }

    private fun handleAudioChunk(event: com.google.android.gms.wearable.MessageEvent) {
        val sessionId = event.path.removePrefix(IpcProtocol.PATH_CAPTURE_AUDIO)
        audioBuffers[sessionId]?.write(event.data)
        Timber.v("PCM chunk: ${event.data.size}B → session $sessionId")
    }

    private suspend fun handleCaptureEnd(event: com.google.android.gms.wearable.MessageEvent) {
        val payload = JSONObject(String(event.data, Charsets.UTF_8))
        val sessionId = payload.getString(IpcProtocol.KEY_SESSION_ID)
        val sourceNodeId = event.sourceNodeId

        val pcmData = audioBuffers.remove(sessionId)?.toByteArray() ?: ByteArray(0)
        Timber.d("Capture ended: session=$sessionId pcm=${pcmData.size}B")

        val transcript = asrService.transcribe(pcmData)
        Timber.d("Transcript: \"$transcript\"")

        val intent = classifyIntent(transcript)
        val outcome = processIntent(intent, transcript)

        val response = JSONObject().apply {
            put(IpcProtocol.KEY_SESSION_ID, sessionId)
            put(IpcProtocol.KEY_TRANSCRIPT, transcript)
            put(IpcProtocol.KEY_INTENT, intent)
            put(IpcProtocol.KEY_OUTCOME, outcome)
            put(IpcProtocol.KEY_PROTOCOL_VERSION, IpcProtocol.VERSION)
        }.toString().toByteArray(Charsets.UTF_8)

        messageClient
            .sendMessage(sourceNodeId, IpcProtocol.PATH_CAPTURE_TRANSCRIPT + sessionId, response)
            .await()
        Timber.d("Transcript response sent to $sourceNodeId")
    }

    private fun classifyIntent(transcript: String): String {
        val t = transcript.lowercase()
        return when {
            t.contains("remind") && (t.contains("me") || t.contains("to")) ->
                IpcProtocol.INTENT_CREATE
            t.startsWith("what") || t.startsWith("show") || t.startsWith("list") ->
                IpcProtocol.INTENT_QUERY
            t.startsWith("done") || t.startsWith("completed") || t.startsWith("dismiss") ->
                IpcProtocol.INTENT_DISMISS
            else ->
                IpcProtocol.INTENT_CONTEXT_ANNOUNCE
        }
    }

    private suspend fun processIntent(intent: String, transcript: String): String {
        return when (intent) {
            IpcProtocol.INTENT_CREATE -> {
                reminderRepository.save(buildReminder(transcript))
                watchSync.syncReminders()
                IpcProtocol.OUTCOME_SAVED
            }
            else -> IpcProtocol.OUTCOME_NO_MATCH
        }
    }

    private fun buildReminder(transcript: String): Reminder {
        val parsed = parser.parse(transcript)
        return Reminder(
            id = UUID.randomUUID().toString(),
            text = parsed.text.ifBlank { transcript },
            rawTranscript = transcript,
            createdAt = Clock.System.now(),
            status = ReminderStatus.PENDING,
            triggers = parsed.triggers,
        )
    }
}
