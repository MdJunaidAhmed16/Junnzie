package com.junnz.phone.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.service.WatchSyncManager
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.parsing.ReminderParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

sealed class PhoneCaptureState {
    object Idle : PhoneCaptureState()
    object Listening : PhoneCaptureState()
    data class Transcribing(val partial: String) : PhoneCaptureState()
    data class Done(val reminder: Reminder) : PhoneCaptureState()
    data class Error(val message: String) : PhoneCaptureState()
}

@HiltViewModel
class PhoneCaptureViewModel @Inject constructor(
    app: Application,
    private val reminderRepository: ReminderRepository,
    private val watchSync: WatchSyncManager,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<PhoneCaptureState>(PhoneCaptureState.Idle)
    val state: StateFlow<PhoneCaptureState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private val parser = ReminderParser()

    /**
     * Must be called from the main thread (e.g. LaunchedEffect with Dispatchers.Main).
     * Starts listening and streams partial results into state until a final result arrives.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _state.value = PhoneCaptureState.Error("Speech recognition not available on this device")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = PhoneCaptureState.Listening
                }

                override fun onPartialResults(partial: Bundle?) {
                    val text = partial
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    if (text.isNotEmpty()) _state.value = PhoneCaptureState.Transcribing(text)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    _state.value = if (text.isNotEmpty()) {
                        PhoneCaptureState.Done(buildReminder(text))
                    } else {
                        PhoneCaptureState.Error("No speech detected — try again")
                    }
                }

                override fun onError(error: Int) {
                    Timber.w("PhoneCapture SpeechRecognizer error $error")
                    _state.value = PhoneCaptureState.Error(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — try again"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unavailable for recognition"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again"
                            else -> "Recognition failed"
                        }
                    )
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            })
        }
    }

    /** Explicitly stop listening; onResults fires shortly after with whatever was captured. */
    fun stopListening() {
        recognizer?.stopListening()
    }

    /** Set error state when RECORD_AUDIO permission is denied. */
    fun onPermissionDenied() {
        _state.value = PhoneCaptureState.Error("Microphone permission required to capture reminders")
    }

    /**
     * Persist the completed reminder and sync to watch.
     * Safe to call then immediately navigate away — save runs in viewModelScope.
     */
    fun saveReminder() {
        val done = _state.value as? PhoneCaptureState.Done ?: return
        viewModelScope.launch {
            reminderRepository.save(done.reminder)
            watchSync.syncReminders()
        }
    }

    fun reset() {
        recognizer?.destroy()
        recognizer = null
        _state.value = PhoneCaptureState.Idle
    }

    override fun onCleared() {
        recognizer?.destroy()
        recognizer = null
        super.onCleared()
    }

    private fun buildReminder(text: String): Reminder {
        val parsed = parser.parse(text)
        return Reminder(
            id = UUID.randomUUID().toString(),
            text = parsed.text.ifBlank { text },
            rawTranscript = text,
            createdAt = Clock.System.now(),
            status = ReminderStatus.PENDING,
            triggers = parsed.triggers,
        )
    }
}
