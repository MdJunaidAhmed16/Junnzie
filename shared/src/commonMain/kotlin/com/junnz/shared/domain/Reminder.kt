package com.junnz.shared.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val id: String,
    val text: String,
    val rawTranscript: String,
    val createdAt: Instant,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val triggers: List<Trigger> = emptyList(),
    val tags: List<String> = emptyList(),
    val priority: Int = 0,
)

@Serializable
enum class ReminderStatus { PENDING, FIRED, DISMISSED, SNOOZED, EXPIRED }

@Serializable
sealed class Trigger {

    @Serializable
    data class TimeTrigger(
        val fireAt: Instant,
        val recurrence: Recurrence? = null,
    ) : Trigger()

    @Serializable
    data class GeofenceTrigger(
        val lat: Double,
        val lng: Double,
        val radiusMeters: Float = 200f,
        val label: String = "",
        val onEnter: Boolean = true,
        val onDwell: Boolean = false,
    ) : Trigger()

    @Serializable
    data class SemanticTrigger(
        val anchorText: String,
        val embeddingVector: List<Float> = emptyList(),
        val threshold: Float = 0.75f,
    ) : Trigger()

    @Serializable
    data class AppContextTrigger(
        val packageNames: List<String>,
        val label: String = "",
    ) : Trigger()
}

@Serializable
data class Recurrence(
    val rule: String,
    val until: Instant? = null,
    val count: Int? = null,
)
