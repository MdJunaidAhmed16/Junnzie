package com.junnz.shared.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class FireEvent(
    val id: String,
    val reminderId: String,
    val firedAt: Instant,
    val triggerType: String,
    val score: Float = 1f,
    val reason: String = "",
    val acknowledged: Boolean = false,
)

@Serializable
data class UserAction(
    val reminderId: String,
    val action: Action,
    val sessionId: String,
    val occurredAt: Instant,
) {
    @Serializable
    enum class Action { COMPLETE, DISMISS, SNOOZE_5M, SNOOZE_1H, SNOOZE_TOMORROW }
}
