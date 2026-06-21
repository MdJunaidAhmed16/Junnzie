package com.junnz.phone.matching

import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.data.settings.SettingsRepository
import com.junnz.phone.notification.ReminderNotifier
import com.junnz.shared.domain.ContextEvent
import com.junnz.shared.domain.Trigger
import com.junnz.shared.matching.MatchingEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for runtime context signals (app foregrounded, app
 * notification). Runs the shared [MatchingEngine] — which handles app-context
 * package matches *and* semantic embedding matches — and posts a nudge for each
 * fired reminder. Cooldown and scoring live in the engine.
 */
@Singleton
class ContextEventDispatcher @Inject constructor(
    @Suppress("unused") @ApplicationContext context: Context,
    private val matchingEngine: MatchingEngine,
    private val notifier: ReminderNotifier,
    private val settingsRepository: SettingsRepository,
    reminderRepository: ReminderRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cheap short-circuit: skip all work when nothing semantic/app-context is active. */
    @Volatile
    private var hasContextReminders = false

    init {
        scope.launch {
            reminderRepository.activeReminders.collect { reminders ->
                hasContextReminders = reminders.any { r ->
                    r.triggers.any { it is Trigger.SemanticTrigger || it is Trigger.AppContextTrigger }
                }
            }
        }
    }

    suspend fun dispatch(event: ContextEvent) {
        if (!hasContextReminders) return
        if (!settingsRepository.contextNudgesEnabled()) return
        val decisions = matchingEngine.evaluate(event)
        for (decision in decisions) {
            Timber.d("ContextEventDispatcher: ${decision.triggerType} fired ${decision.reminder.id} (${decision.reason})")
            notifier.notify(decision.reminder)
        }
    }
}
