package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hermes_chat_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val KEY_SPEN_MODE = booleanPreferencesKey("spen_mode_enabled")
        val KEY_UI_DENSITY_SCALE = floatPreferencesKey("ui_density_scale")

        const val DEFAULT_SERVER_URL = "http://127.0.0.1:9120/"
        const val DEFAULT_CUSTOM_ENDPOINT = "AUTO"
        const val DEFAULT_MODEL = "hermes-agent"
        const val DEFAULT_SYSTEM_PROMPT = "Tu és o Hermes, um modelo de inteligência artificial de elite a correr localmente no dispositivo via Termux."
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_UI_DENSITY_SCALE = 1.0f
    }

    val settingsFlow: Flow<HermesSettings> = context.dataStore.data.map { preferences ->
        HermesSettings(
            serverUrl = preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL,
            customEndpoint = preferences[KEY_CUSTOM_ENDPOINT] ?: DEFAULT_CUSTOM_ENDPOINT,
            modelName = preferences[KEY_MODEL_NAME] ?: DEFAULT_MODEL,
            systemPrompt = preferences[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
            temperature = preferences[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE,
            maxTokens = preferences[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
            hapticEnabled = preferences[KEY_HAPTIC_ENABLED] ?: true,
            sPenModeEnabled = preferences[KEY_SPEN_MODE] ?: true,
            uiDensityScale = preferences[KEY_UI_DENSITY_SCALE] ?: DEFAULT_UI_DENSITY_SCALE
        )
    }

    suspend fun updateServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url.trim()
        }
    }

    suspend fun updateCustomEndpoint(endpoint: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_ENDPOINT] = endpoint.trim()
        }
    }

    suspend fun updateModelName(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MODEL_NAME] = model.trim()
        }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun updateTemperature(temp: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TEMPERATURE] = temp
        }
    }

    suspend fun updateMaxTokens(tokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MAX_TOKENS] = tokens
        }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun updateSPenModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SPEN_MODE] = enabled
        }
    }

    suspend fun updateUiDensityScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_UI_DENSITY_SCALE] = scale
        }
    }

    suspend fun resetDefaults() {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = DEFAULT_SERVER_URL
            preferences[KEY_MODEL_NAME] = DEFAULT_MODEL
            preferences[KEY_SYSTEM_PROMPT] = DEFAULT_SYSTEM_PROMPT
            preferences[KEY_TEMPERATURE] = DEFAULT_TEMPERATURE
            preferences[KEY_MAX_TOKENS] = DEFAULT_MAX_TOKENS
            preferences[KEY_HAPTIC_ENABLED] = true
            preferences[KEY_SPEN_MODE] = true
            preferences[KEY_UI_DENSITY_SCALE] = DEFAULT_UI_DENSITY_SCALE
        }
    }
}
