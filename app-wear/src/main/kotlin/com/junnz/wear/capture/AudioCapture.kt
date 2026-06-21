package com.junnz.wear.capture

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

data class AudioChunk(val pcm: ByteArray, val rmsLevel: Float)

object AudioCapture {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHUNK_DURATION_MS = 1000

    private val CHUNK_SIZE = SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000 // bytes per 1s chunk

    fun chunkFlow(): Flow<AudioChunk> = flow {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            CHUNK_SIZE,
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
        recorder.startRecording()
        try {
            val buffer = ByteArray(CHUNK_SIZE)
            while (coroutineContext.isActive) {
                var offset = 0
                while (offset < CHUNK_SIZE && coroutineContext.isActive) {
                    val read = recorder.read(buffer, offset, CHUNK_SIZE - offset)
                    if (read > 0) offset += read
                }
                if (offset > 0) {
                    val chunk = buffer.copyOf(offset)
                    emit(AudioChunk(chunk, rms(chunk)))
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun rms(pcm: ByteArray): Float {
        var sum = 0L
        val samples = pcm.size / 2
        for (i in 0 until samples) {
            val sample = (pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)
            sum += sample.toLong() * sample.toLong()
        }
        return if (samples == 0) 0f else kotlin.math.sqrt(sum.toDouble() / samples).toFloat() / 32768f
    }
}
