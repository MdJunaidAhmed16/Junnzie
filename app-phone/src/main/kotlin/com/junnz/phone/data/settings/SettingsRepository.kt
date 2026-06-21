package com.junnz.phone.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class JunnzSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "English",
    val contextNudgesEnabled: Boolean = true,
)

private val Context.settingsDataStore by preferencesDataStore(name = "junnz_settings")

/** DataStore-backed user preferences that survive process death and restarts. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<JunnzSettings> = dataStore.data.map { prefs ->
        JunnzSettings(
            themeMode = prefs[KEY_THEME]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            language = prefs[KEY_LANGUAGE] ?: "English",
            contextNudgesEnabled = prefs[KEY_CONTEXT_NUDGES] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { it[KEY_LANGUAGE] = language }
    }

    suspend fun setContextNudgesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CONTEXT_NUDGES] = enabled }
    }

    /** One-shot read used by background components (e.g. the context firer). */
    suspend fun contextNudgesEnabled(): Boolean = settings.first().contextNudgesEnabled

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_CONTEXT_NUDGES = booleanPreferencesKey("context_nudges_enabled")
    }
}
