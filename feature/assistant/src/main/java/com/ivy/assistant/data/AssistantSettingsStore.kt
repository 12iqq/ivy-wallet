package com.ivy.assistant.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Persists [AssistantSettings] as JSON in a private SharedPreferences file. */
@Singleton
class AssistantSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AssistantSettings> = _settings.asStateFlow()

    fun current(): AssistantSettings = _settings.value

    fun update(transform: (AssistantSettings) -> AssistantSettings) {
        _settings.update(transform)
        prefs.edit()
            .putString(KEY, json.encodeToString(AssistantSettings.serializer(), _settings.value))
            .apply()
    }

    private fun load(): AssistantSettings {
        val raw = prefs.getString(KEY, null) ?: return AssistantSettings()
        return try {
            json.decodeFromString(AssistantSettings.serializer(), raw)
        } catch (e: Exception) {
            Timber.e(e, "Corrupted assistant settings, resetting")
            AssistantSettings()
        }
    }

    private companion object {
        const val KEY = "settings"
    }
}
