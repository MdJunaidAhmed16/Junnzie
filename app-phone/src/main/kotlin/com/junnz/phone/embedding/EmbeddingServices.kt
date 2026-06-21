package com.junnz.phone.embedding

import com.junnz.shared.matching.EmbeddingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.sqrt

/**
 * Deterministic, offline lexical embedding via signed feature hashing of words
 * plus character trigrams. No model file or network — always available.
 *
 * Captures word/stem overlap (cosine rises with shared vocabulary), which is
 * enough for "is this notification about the thing I anchored?" matching. It is
 * NOT deep-semantic: true synonyms ("flight"/"plane") won't align unless they
 * share trigrams. Configure [com.junnz.phone.BuildConfig.GOOGLE_EMBEDDING_API_KEY]
 * to swap in [GoogleEmbeddingService] for real semantic vectors.
 */
class LocalEmbeddingService(private val dim: Int = 256) : EmbeddingService {

    override suspend fun embed(text: String): FloatArray {
        val vec = FloatArray(dim)
        val tokens = TOKEN.findAll(text.lowercase())
            .map { it.value }
            .filter { it !in STOP_WORDS }
            .toList()

        for (token in tokens) {
            add(vec, token, 1f)
            val padded = "#$token#"
            var i = 0
            while (i + 3 <= padded.length) {
                add(vec, padded.substring(i, i + 3), 0.5f)
                i++
            }
        }

        val norm = sqrt(vec.fold(0.0) { acc, v -> acc + v * v }).toFloat()
        if (norm > 0f) for (i in vec.indices) vec[i] /= norm
        return vec
    }

    private fun add(vec: FloatArray, key: String, weight: Float) {
        val h = key.hashCode()
        val idx = ((h % dim) + dim) % dim
        val sign = if (((h shr 16) and 1) == 0) 1f else -1f
        vec[idx] += weight * sign
    }

    private companion object {
        val TOKEN = Regex("[a-z0-9]+")
        val STOP_WORDS = setOf(
            "the", "a", "an", "to", "of", "and", "or", "in", "on", "at", "is", "are",
            "i", "me", "my", "you", "your", "when", "for", "with", "it", "this", "that",
        )
    }
}

/**
 * Real semantic embeddings via the Google Generative Language API
 * (text-embedding-004). Same HTTP style as the Cloud Speech ASR service.
 */
class GoogleEmbeddingService(private val apiKey: String) : EmbeddingService {

    override suspend fun embed(text: String): FloatArray {
        if (text.isBlank()) return FloatArray(0)

        val body = JSONObject().apply {
            put("model", "models/text-embedding-004")
            put("content", JSONObject().apply {
                put("parts", listOf(JSONObject().apply { put("text", text) })
                    .let { arr -> org.json.JSONArray().apply { arr.forEach { put(it) } } })
            })
        }.toString()

        return withContext(Dispatchers.IO) {
            val key = URLEncoder.encode(apiKey, "UTF-8")
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/" +
                    "models/text-embedding-004:embedContent?key=$key",
            )
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
                    Timber.e("Embedding HTTP $code: $err")
                    return@withContext FloatArray(0)
                }
                parseEmbedding(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            } catch (e: Exception) {
                Timber.e(e, "Embedding request failed")
                FloatArray(0)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parseEmbedding(json: String): FloatArray = try {
        val values = JSONObject(json).getJSONObject("embedding").getJSONArray("values")
        FloatArray(values.length()) { values.getDouble(it).toFloat() }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse embedding response")
        FloatArray(0)
    }
}
