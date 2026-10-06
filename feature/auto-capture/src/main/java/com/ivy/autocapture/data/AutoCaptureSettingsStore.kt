package com.ivy.autocapture.data

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

/** Persists [AutoCaptureConfig] as JSON in a private SharedPreferences file. */
@Singleton
class AutoCaptureSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("auto_capture", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<AutoCaptureConfig> = _config.asStateFlow()

    fun current(): AutoCaptureConfig = _config.value

    fun update(transform: (AutoCaptureConfig) -> AutoCaptureConfig) {
        _config.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(AutoCaptureConfig.serializer(), _config.value))
            .apply()
    }

    private fun load(): AutoCaptureConfig {
        val raw = prefs.getString(KEY, null) ?: return AutoCaptureConfig()
        return try {
            json.decodeFromString(AutoCaptureConfig.serializer(), raw)
        } catch (e: Exception) {
            Timber.e(e, "Corrupted auto-capture settings, resetting")
            AutoCaptureConfig()
        }
    }

    private companion object {
        const val KEY = "config"
    }
}
