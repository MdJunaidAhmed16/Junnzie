package com.junnz.wear.sync

import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FiredReminder(val reminderId: String, val reason: String)

@Singleton
class WatchReminderCache @Inject constructor() {

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _latestFire = MutableStateFlow<FiredReminder?>(null)
    val latestFire: StateFlow<FiredReminder?> = _latestFire.asStateFlow()

    fun update(newReminders: List<Reminder>) {
        _reminders.value = newReminders
    }

    fun markFired(reminderId: String, reason: String) {
        _latestFire.value = FiredReminder(reminderId, reason)
    }

    fun pendingCount(): Int = _reminders.value.count { it.status == ReminderStatus.PENDING }
}
