package com.sami.livetv.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sami_settings")

enum class AppLanguage {
    Arabic,
    English,
}

data class SyncedLibraryBaseline(
    val favoriteIds: Set<String>,
    val watchHistory: List<String>,
)

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val isDarkTheme: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY] ?: true
    }

    val language: Flow<AppLanguage> = dataStore.data.map { preferences ->
        when (preferences[LANGUAGE_KEY]) {
            LANGUAGE_ENGLISH -> AppLanguage.English
            else -> AppLanguage.Arabic
        }
    }

    val favoriteIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[FAVORITES_KEY] ?: emptySet()
    }

    val watchHistory: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[HISTORY_KEY]
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = enabled
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] =
                if (language == AppLanguage.English) LANGUAGE_ENGLISH else LANGUAGE_ARABIC
        }
    }

    suspend fun setFavorite(channelId: String, favorite: Boolean) {
        require(channelId.isNotBlank()) { "Channel id must not be blank." }
        dataStore.edit { preferences ->
            val current = preferences[FAVORITES_KEY].orEmpty()
            preferences[FAVORITES_KEY] =
                if (favorite) current + channelId else current - channelId
        }
    }

    suspend fun recordWatch(channelId: String, limit: Int = DEFAULT_HISTORY_LIMIT) {
        require(channelId.isNotBlank()) { "Channel id must not be blank." }
        require(limit > 0) { "History limit must be greater than zero." }

        dataStore.edit { preferences ->
            val existing = preferences[HISTORY_KEY]
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
            val updated = (listOf(channelId) + existing.filterNot { it == channelId })
                .take(limit)
            preferences[HISTORY_KEY] = updated.joinToString(separator = "\n")
        }
    }

    suspend fun replaceLibrary(
        favoriteIds: Set<String>,
        watchHistory: List<String>,
    ) {
        val cleanFavorites = favoriteIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val cleanHistory = watchHistory
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(DEFAULT_HISTORY_LIMIT)
            .toList()

        dataStore.edit { preferences ->
            preferences[FAVORITES_KEY] = cleanFavorites
            preferences[HISTORY_KEY] = cleanHistory.joinToString(separator = "\n")
        }
    }

    suspend fun loadSyncedLibraryBaseline(accountId: String): SyncedLibraryBaseline? {
        val suffix = accountKeySuffix(accountId)
        val favoritesKey = stringSetPreferencesKey("cloud_favorites_$suffix")
        val historyKey = stringPreferencesKey("cloud_history_$suffix")
        val preferences = dataStore.data.first()
        val favorites = preferences[favoritesKey] ?: return null
        val history = preferences[historyKey] ?: return null
        return SyncedLibraryBaseline(
            favoriteIds = favorites,
            watchHistory = history.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList(),
        )
    }

    suspend fun saveSyncedLibraryBaseline(
        accountId: String,
        favoriteIds: Set<String>,
        watchHistory: List<String>,
    ) {
        val suffix = accountKeySuffix(accountId)
        val favoritesKey = stringSetPreferencesKey("cloud_favorites_$suffix")
        val historyKey = stringPreferencesKey("cloud_history_$suffix")
        val cleanFavorites = favoriteIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val cleanHistory = watchHistory
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(DEFAULT_HISTORY_LIMIT)
            .toList()
        dataStore.edit { preferences ->
            preferences[favoritesKey] = cleanFavorites
            preferences[historyKey] = cleanHistory.joinToString(separator = "\n")
        }
    }

    private fun accountKeySuffix(accountId: String): String {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        return accountId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .also { require(it.isNotEmpty()) { "Account id is not valid." } }
    }

    private companion object {
        const val LANGUAGE_ARABIC = "ar"
        const val LANGUAGE_ENGLISH = "en"
        const val DEFAULT_HISTORY_LIMIT = 10

        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val FAVORITES_KEY = stringSetPreferencesKey("favorite_channel_ids")
        val HISTORY_KEY = stringPreferencesKey("watch_history")
    }
}
