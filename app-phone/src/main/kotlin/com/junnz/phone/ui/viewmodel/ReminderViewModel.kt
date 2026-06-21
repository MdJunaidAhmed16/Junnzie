package com.junnz.phone.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.service.WatchSyncManager
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val watchSync: WatchSyncManager,
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = reminderRepository.activeReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Every reminder regardless of status — drives the Activity history timeline. */
    val allReminders: StateFlow<List<Reminder>> = reminderRepository.allReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun complete(reminderId: String) = viewModelScope.launch {
        reminderRepository.complete(reminderId)
        watchSync.syncReminders()
    }

    fun dismiss(reminderId: String) = viewModelScope.launch {
        reminderRepository.dismiss(reminderId)
        watchSync.syncReminders()
    }

    fun save(reminder: Reminder) = viewModelScope.launch {
        reminderRepository.save(reminder)
        watchSync.syncReminders()
    }
}
