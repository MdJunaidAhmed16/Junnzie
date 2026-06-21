package com.junnz.phone.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SpeechRecognizer for watch-initiated captures.
 *
 * When the watch signals /capture/start, call startListening(sessionId).
 * The phone mic starts capturing in parallel with the watch recording.
 * When the watch signals /capture/end, call stopAndAwait(sessionId) to
 * finalize recognition and get the transcript.
 *
 * Only one session is active at a time — a new startListening() cancels any prior session.
 */
@Singleton
class PhoneMicAsrManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var activeSessionId: String? = null
    @Volatile private var activeDeferred: CompletableDeferred<String>? = null
    @Volatile private var activeRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean {
        val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val hasService = SpeechRecognizer.isRecognitionAvailable(context)
        return hasPermission && hasService
    }

    /**
     * Start phone-mic recognition for the given capture session.
     * Safe to call from any thread.
     */
    fun startListening(sessionId: String) {
        val deferred = CompletableDeferred<String>()

        // Replace any previous session
        val prev = activeDeferred
        if (prev != null && !prev.isCompleted) prev.complete("")

        activeDeferred = deferred
        activeSessionId = sessionId

        if (!isAvailable()) {
            Timber.w("ASR[$sessionId]: not available — missing permission or service")
            deferred.complete("")
            return
        }

        mainHandler.post {
            activeRecognizer?.destroy()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            activeRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partial: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    Timber.d("ASR[$sessionId]: result=\"$text\"")
                    if (!deferred.isCompleted) deferred.complete(text)
                    destroyRecognizer()
                }

                override fun onError(error: Int) {
                    Timber.w("ASR[$sessionId]: error=${errorName(error)}")
                    if (!deferred.isCompleted) deferred.complete("")
                    destroyRecognizer()
                }
            })

            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    // Finalize after 2 s of silence
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
                }
            )
        }
    }

    /**
     * Signal end of recording, then suspend until the transcript is ready.
     * Returns empty string on timeout or if ASR was unavailable.
     */
    suspend fun stopAndAwait(sessionId: String, timeoutMs: Long = 30_000L): String {
        // Tell recognizer to stop and finalize — onResults fires shortly after
        mainHandler.post { activeRecognizer?.stopListening() }

        val deferred = activeDeferred ?: return ""
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            Timber.w("ASR[$sessionId]: timed out")
            if (!deferred.isCompleted) deferred.complete("")
            ""
        } finally {
            if (activeSessionId == sessionId) {
                activeSessionId = null
                activeDeferred = null
            }
        }
    }

    private fun destroyRecognizer() {
        mainHandler.post {
            activeRecognizer?.destroy()
            activeRecognizer = null
        }
    }

    private fun errorName(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "NO_PERMISSION"
        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
        SpeechRecognizer.ERROR_SERVER -> "SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        else -> "UNKNOWN($code)"
    }
}