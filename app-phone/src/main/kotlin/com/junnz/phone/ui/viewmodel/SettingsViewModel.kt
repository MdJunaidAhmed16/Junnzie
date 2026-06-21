package com.junnz.phone.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junnz.phone.data.settings.JunnzSettings
import com.junnz.phone.data.settings.SettingsRepository
import com.junnz.phone.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<JunnzSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JunnzSettings(),
    )

    fun cycleTheme() = viewModelScope.launch {
        val modes = ThemeMode.entries
        val next = modes[(modes.indexOf(settings.value.themeMode) + 1) % modes.size]
        repository.setThemeMode(next)
    }

    fun cycleLanguage() = viewModelScope.launch {
        val current = settings.value.language
        val next = LANGUAGES[(LANGUAGES.indexOf(current).coerceAtLeast(0) + 1) % LANGUAGES.size]
        repository.setLanguage(next)
    }

    fun setContextNudges(enabled: Boolean) = viewModelScope.launch {
        repository.setContextNudgesEnabled(enabled)
    }

    private companion object {
        val LANGUAGES = listOf("English", "हिन्दी", "Español", "Français")
    }
}
