package com.nutritiontracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AppSettings(
    /** Личный ключ USDA FoodData Central; пустой — используется общий DEMO_KEY. */
    val usdaApiKey: String = "",
    val onlineSearchEnabled: Boolean = true,
    val usdaEnabled: Boolean = true,
    val openFoodFactsEnabled: Boolean = true,
    /** Фоновое обновление сохранённых продуктов из источников при наличии сети. */
    val autoRefreshEnabled: Boolean = true,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val usdaKey = stringPreferencesKey("usda_api_key")
        val online = booleanPreferencesKey("online_search")
        val usda = booleanPreferencesKey("usda_enabled")
        val off = booleanPreferencesKey("off_enabled")
        val autoRefresh = booleanPreferencesKey("auto_refresh")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                usdaApiKey = p[Keys.usdaKey] ?: "",
                onlineSearchEnabled = p[Keys.online] ?: true,
                usdaEnabled = p[Keys.usda] ?: true,
                openFoodFactsEnabled = p[Keys.off] ?: true,
                autoRefreshEnabled = p[Keys.autoRefresh] ?: true,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val old = AppSettings(
                usdaApiKey = p[Keys.usdaKey] ?: "",
                onlineSearchEnabled = p[Keys.online] ?: true,
                usdaEnabled = p[Keys.usda] ?: true,
                openFoodFactsEnabled = p[Keys.off] ?: true,
                autoRefreshEnabled = p[Keys.autoRefresh] ?: true,
            )
            val new = transform(old)
            p[Keys.usdaKey] = new.usdaApiKey.trim()
            p[Keys.online] = new.onlineSearchEnabled
            p[Keys.usda] = new.usdaEnabled
            p[Keys.off] = new.openFoodFactsEnabled
            p[Keys.autoRefresh] = new.autoRefreshEnabled
        }
    }
}
