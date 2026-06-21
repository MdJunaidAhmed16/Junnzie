package com.junnz.phone.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

interface AsrService {
    /** Transcribe raw 16 kHz, 16-bit, mono PCM bytes to text. */
    suspend fun transcribe(pcmData: ByteArray): String
}

/**
 * Sends watch-recorded PCM to Google Cloud Speech-to-Text (REST v1).
 *
 * Audio spec from AudioCapture on the watch: 16 000 Hz, LINEAR16, mono.
 * The API key must be restricted to this app's package in Cloud Console.
 */
class GoogleCloudSpeechAsrService(private val apiKey: String) : AsrService {

    override suspend fun transcribe(pcmData: ByteArray): String {
        if (pcmData.isEmpty()) return ""

        val audioB64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", 16_000)
                put("audioChannelCount", 1)
                put("languageCode", "en-US")
                // "latest_short" is optimised for voice commands under 60 s
                put("model", "latest_short")
                put("enableAutomaticPunctuation", false)
            })
            put("audio", JSONObject().apply {
                put("content", audioB64)
            })
        }.toString()

        return withContext(Dispatchers.IO) {
            val url = URL("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 12_000

                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                    Timber.e("Cloud Speech HTTP $code: $err")
                    return@withContext ""
                }

                val json = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                parseTranscript(json)
            } catch (e: Exception) {
                Timber.e(e, "Cloud Speech request failed")
                ""
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parseTranscript(json: String): String {
        return try {
            val results = JSONObject(json).optJSONArray("results") ?: return ""
            buildString {
                for (i in 0 until results.length()) {
                    val alt = results.getJSONObject(i)
                        .getJSONArray("alternatives")
                        .getJSONObject(0)
                    if (isNotEmpty()) append(" ")
                    append(alt.getString("transcript"))
                }
            }.trim()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Cloud Speech response")
            ""
        }
    }
}

/** Used when no API key is configured — returns a labelled placeholder. */
class StubAsrService : AsrService {
    override suspend fun transcribe(pcmData: ByteArray): String {
        val seconds = pcmData.size / (16_000 * 2)
        return "[stub — ${seconds}s of watch audio received, set GOOGLE_SPEECH_API_KEY to enable real ASR]"
    }
}
