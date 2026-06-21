package com.junnz.phone.data.repository

import com.junnz.phone.data.db.ReminderDao
import com.junnz.phone.data.db.entities.ReminderEntity
import com.junnz.phone.geofence.GeofenceManager
import com.junnz.phone.scheduling.ReminderScheduler
import com.junnz.shared.matching.EmbeddingService
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.domain.Trigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val geofenceManager: GeofenceManager,
    private val embeddingService: EmbeddingService,
) {
    val allReminders: Flow<List<Reminder>> = dao.observeAll().map { it.map(::toDomain) }
    val activeReminders: Flow<List<Reminder>> = dao.observeActive().map { it.map(::toDomain) }
    val pendingReminders: Flow<List<Reminder>> = dao.observeByStatus(ReminderStatus.PENDING)
        .map { it.map(::toDomain) }

    suspend fun save(reminder: Reminder) {
        // Geocode location labels so the resolved coordinates are persisted.
        var resolved = geofenceManager.resolveGeofences(reminder)
        // Compute & persist semantic anchor embeddings so context matching works.
        resolved = resolveSemanticEmbeddings(resolved)
        dao.upsert(toEntity(resolved))
        // (Re)arm the alarm for any time trigger; no-op for other trigger types.
        scheduler.schedule(resolved)
        // Register location triggers; no-op when there are none / no permission.
        geofenceManager.register(resolved)
    }

    private suspend fun resolveSemanticEmbeddings(reminder: Reminder): Reminder {
        val needsEmbedding = reminder.triggers.any {
            it is Trigger.SemanticTrigger && it.embeddingVector.isEmpty() && it.anchorText.isNotBlank()
        }
        if (!needsEmbedding) return reminder
        val updated = reminder.triggers.map { trigger ->
            if (trigger is Trigger.SemanticTrigger &&
                trigger.embeddingVector.isEmpty() && trigger.anchorText.isNotBlank()
            ) {
                trigger.copy(embeddingVector = embeddingService.embed(trigger.anchorText).toList())
            } else {
                trigger
            }
        }
        return reminder.copy(triggers = updated)
    }

    suspend fun getById(id: String): Reminder? = dao.getById(id)?.let(::toDomain)

    suspend fun updateStatus(id: String, status: ReminderStatus) {
        dao.updateStatus(id, status)
        if (status == ReminderStatus.DISMISSED || status == ReminderStatus.FIRED) {
            scheduler.cancel(id)
            geofenceManager.remove(id)
        }
    }

    suspend fun complete(id: String) = updateStatus(id, ReminderStatus.DISMISSED)
    suspend fun dismiss(id: String) = updateStatus(id, ReminderStatus.DISMISSED)
    suspend fun markFired(id: String) = updateStatus(id, ReminderStatus.FIRED)

    private fun toDomain(entity: ReminderEntity): Reminder = Reminder(
        id = entity.id,
        text = entity.text,
        rawTranscript = entity.rawTranscript,
        createdAt = Instant.fromEpochMilliseconds(entity.createdAtMs),
        status = entity.status,
        tags = Json.decodeFromString(entity.tagsJson),
        triggers = Json.decodeFromString(entity.triggersJson),
        priority = entity.priority,
    )

    private fun toEntity(reminder: Reminder): ReminderEntity = ReminderEntity(
        id = reminder.id,
        text = reminder.text,
        rawTranscript = reminder.rawTranscript,
        createdAtMs = reminder.createdAt.toEpochMilliseconds(),
        status = reminder.status,
        tagsJson = Json.encodeToString(reminder.tags),
        triggersJson = Json.encodeToString(reminder.triggers),
        priority = reminder.priority,
    )
}
