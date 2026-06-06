/** Stores user preferences in DataStore. */
package it.unibo.equishare.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.unibo.equishare.ui.screens.profile.ThemeOption
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.userPreferences by preferencesDataStore(name = "user_preferences")

class UserPreferencesDataSource(private val context: Context) {

    private val themeKey                = stringPreferencesKey(KEY_THEME)
    private val languageTagKey          = stringPreferencesKey(KEY_LANGUAGE_TAG)
    private val notificationsEnabledKey = booleanPreferencesKey(KEY_NOTIFICATIONS_ENABLED)
    private val lastSeenActivityAtKey   = stringPreferencesKey(KEY_LAST_SEEN_ACTIVITY_AT)
    private val lastNotifiedAtKey       = stringPreferencesKey(KEY_LAST_NOTIFIED_AT)
    private val lastSyncAtKey           = longPreferencesKey(KEY_LAST_SYNC_AT)
    private val favoriteGroupIdsKey     = stringSetPreferencesKey(KEY_FAVORITE_GROUP_IDS)
    private val groupOrderIdsKey        = stringPreferencesKey(KEY_GROUP_ORDER_IDS)

    val theme: Flow<ThemeOption> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            when (prefs[themeKey]) {
                ThemeOption.LIGHT.name -> ThemeOption.LIGHT
                ThemeOption.DARK.name  -> ThemeOption.DARK
                else                   -> ThemeOption.SYSTEM
            }
        }

    val notificationsEnabled: Flow<Boolean> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[notificationsEnabledKey] ?: true }

    val languageTag: Flow<String?> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[languageTagKey]?.takeIf { it.isNotBlank() } }

    val lastSeenActivityAt: Flow<String?> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[lastSeenActivityAtKey] }

    val lastNotifiedAt: Flow<String?> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[lastNotifiedAtKey] }

    val lastSyncAt: Flow<Long?> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[lastSyncAtKey] }

    val groupOrderIds: Flow<List<String>> = context.userPreferences.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            prefs[groupOrderIdsKey]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    suspend fun setTheme(theme: ThemeOption) {
        context.userPreferences.edit { it[themeKey] = theme.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.userPreferences.edit { it[notificationsEnabledKey] = enabled }
    }

    suspend fun setLanguageTag(languageTag: String?) {
        context.userPreferences.edit { prefs ->
            if (languageTag.isNullOrBlank()) prefs.remove(languageTagKey)
            else prefs[languageTagKey] = languageTag
        }
    }

    suspend fun setLastSeenActivityAtIfNewer(timestamp: String) {
        val next = timestamp.toOffsetDateTimeOrNull()
        context.userPreferences.edit { prefs ->
            val current = prefs[lastSeenActivityAtKey]?.toOffsetDateTimeOrNull()
            if (next == null || current == null || next.isAfter(current)) {
                prefs[lastSeenActivityAtKey] = timestamp
            }
        }
    }

    suspend fun setLastNotifiedAt(timestamp: String) {
        context.userPreferences.edit { it[lastNotifiedAtKey] = timestamp }
    }

    suspend fun setLastSyncAt(timestampMs: Long) {
        context.userPreferences.edit { it[lastSyncAtKey] = timestampMs }
    }

    suspend fun setGroupOrder(groupIds: List<String>) {
        context.userPreferences.edit { prefs ->
            prefs[groupOrderIdsKey] = groupIds.joinToString("|")
        }
    }

    suspend fun clear() {
        context.userPreferences.edit { it.clear() }
    }

    private companion object {
        const val KEY_THEME                  = "theme"
        const val KEY_LANGUAGE_TAG           = "language_tag"
        const val KEY_NOTIFICATIONS_ENABLED  = "notifications_enabled"
        const val KEY_LAST_SEEN_ACTIVITY_AT  = "last_seen_activity_at"
        const val KEY_LAST_NOTIFIED_AT       = "last_notified_at"
        const val KEY_LAST_SYNC_AT           = "last_sync_at"
        const val KEY_FAVORITE_GROUP_IDS     = "favorite_group_ids_v2"
        const val KEY_GROUP_ORDER_IDS        = "group_order_ids"
    }
}

private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this) }.getOrNull()
