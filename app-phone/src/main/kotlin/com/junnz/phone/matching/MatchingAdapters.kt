package com.junnz.phone.matching

import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.shared.domain.Reminder
import com.junnz.shared.matching.CooldownStore
import com.junnz.shared.matching.ReminderQuery
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/** Feeds active reminders from Room into the shared [com.junnz.shared.matching.MatchingEngine]. */
class RepositoryReminderQuery(
    private val repository: ReminderRepository,
) : ReminderQuery {
    override suspend fun getActiveReminders(): List<Reminder> =
        repository.activeReminders.first()
}

/**
 * Process-lifetime cooldown so a context reminder isn't re-fired on every
 * notification / app-open within the window. Cleared on process death — that's
 * fine; a reminder firing once after a cold start is acceptable.
 */
class InMemoryCooldownStore(
    private val cooldownMs: Long = 30 * 60 * 1000L,
) : CooldownStore {
    private val firedUntil = ConcurrentHashMap<String, Long>()

    override suspend fun isOnCooldown(reminderId: String): Boolean =
        (firedUntil[reminderId] ?: 0L) > System.currentTimeMillis()

    override suspend fun markFired(reminderId: String) {
        firedUntil[reminderId] = System.currentTimeMillis() + cooldownMs
    }
}
