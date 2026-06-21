package com.junnz.shared.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class ContextEvent {
    abstract val occurredAt: Instant

    @Serializable
    data class VoiceContext(
        val transcript: String,
        val sessionId: String,
        override val occurredAt: Instant,
    ) : ContextEvent()

    @Serializable
    data class GeofenceEntered(
        val lat: Double,
        val lng: Double,
        val geofenceId: String,
        override val occurredAt: Instant,
    ) : ContextEvent()

    @Serializable
    data class AppForegrounded(
        val packageName: String,
        override val occurredAt: Instant,
    ) : ContextEvent()

    @Serializable
    data class AppNotification(
        val packageName: String,
        val contextPhrase: String,
        override val occurredAt: Instant,
    ) : ContextEvent()
}
