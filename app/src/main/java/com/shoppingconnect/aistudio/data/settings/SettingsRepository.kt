package com.shoppingconnect.aistudio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.json.AppJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun reset()
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private val key = stringPreferencesKey("settings_json")

    override val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> AppLog.e("Settings", "read failed", e); emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> decode(prefs[key]) }

    private fun decode(json: String?): AppSettings =
        json?.let { runCatching { AppJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val next = transform(decode(prefs[key]))
            prefs[key] = AppJson.encodeToString(AppSettings.serializer(), next)
        }
    }

    override suspend fun reset() {
        context.settingsStore.edit { it.clear() }
    }
}

/** Test double. */
class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
    override suspend fun reset() { state.value = AppSettings() }
}
