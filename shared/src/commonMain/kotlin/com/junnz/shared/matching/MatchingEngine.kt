package com.junnz.shared.matching

import com.junnz.shared.domain.ContextEvent
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.Trigger
import kotlinx.datetime.Clock
import kotlin.math.sqrt

data class FireDecision(
    val reminder: Reminder,
    val score: Float,
    val triggerType: String,
    val reason: String,
)

interface ReminderQuery {
    suspend fun getActiveReminders(): List<Reminder>
}

interface EmbeddingService {
    suspend fun embed(text: String): FloatArray
}

interface CooldownStore {
    suspend fun isOnCooldown(reminderId: String): Boolean
    suspend fun markFired(reminderId: String)
}

data class MatchingConfig(
    val semanticThreshold: Float = 0.75f,
    val cooldownMinutes: Int = 30,
    val maxFiresPerScan: Int = 3,
)

class MatchingEngine(
    private val reminders: ReminderQuery,
    private val embeddings: EmbeddingService,
    private val cooldownStore: CooldownStore,
    private val config: MatchingConfig = MatchingConfig(),
    private val clock: Clock = Clock.System,
) {
    suspend fun evaluate(event: ContextEvent): List<FireDecision> {
        val active = reminders.getActiveReminders()
        if (active.isEmpty()) return emptyList()

        val eventEmbedding: FloatArray? = when (event) {
            is ContextEvent.VoiceContext -> embeddings.embed(event.transcript)
            is ContextEvent.AppNotification -> embeddings.embed(event.contextPhrase)
            is ContextEvent.AppForegrounded -> embeddings.embed(event.packageName)
            is ContextEvent.GeofenceEntered -> null // handled by GeofenceMatcher
        }

        val decisions = mutableListOf<FireDecision>()

        for (reminder in active) {
            if (cooldownStore.isOnCooldown(reminder.id)) continue

            for (trigger in reminder.triggers) {
                val decision = evaluateTrigger(reminder, trigger, event, eventEmbedding)
                if (decision != null) {
                    decisions.add(decision)
                    break // first matching trigger wins per reminder
                }
            }
        }

        val top = decisions.sortedByDescending { it.score }.take(config.maxFiresPerScan)
        top.forEach { cooldownStore.markFired(it.reminder.id) }
        return top
    }

    private suspend fun evaluateTrigger(
        reminder: Reminder,
        trigger: Trigger,
        event: ContextEvent,
        eventEmbedding: FloatArray?,
    ): FireDecision? {
        return when {
            trigger is Trigger.SemanticTrigger && eventEmbedding != null -> {
                val anchorVec = trigger.embeddingVector.toFloatArray()
                if (anchorVec.isEmpty()) null
                else {
                    val score = cosineSimilarity(eventEmbedding, anchorVec)
                    if (score >= trigger.threshold) {
                        FireDecision(reminder, score, "SEMANTIC", "cos=${"%.2f".format(score)}")
                    } else null
                }
            }

            trigger is Trigger.AppContextTrigger && event is ContextEvent.AppForegrounded -> {
                if (trigger.packageNames.contains(event.packageName)) {
                    FireDecision(reminder, 1f, "APP_CONTEXT", "pkg=${event.packageName}")
                } else null
            }

            trigger is Trigger.AppContextTrigger && event is ContextEvent.AppNotification -> {
                if (trigger.packageNames.contains(event.packageName)) {
                    FireDecision(reminder, 0.9f, "APP_NOTIFICATION", "pkg=${event.packageName}")
                } else null
            }

            trigger is Trigger.GeofenceTrigger && event is ContextEvent.GeofenceEntered -> {
                FireDecision(reminder, 1f, "GEOFENCE", "geofenceId=${event.geofenceId}")
            }

            else -> null
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0f || normB == 0f) 0f
        else dot / (sqrt(normA) * sqrt(normB))
    }
}
