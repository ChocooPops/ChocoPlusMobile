package com.chocoplusmobile.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chocoplusmobile.state.PersistedPrefs
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "chocoplus_store")

/** Mirrors the RN app's api/tokenStorage.ts (AsyncStorage → DataStore). */
class TokenStorage(private val context: Context) {
    private val key = stringPreferencesKey("chocoplus.auth.token")

    suspend fun read(): String? = try {
        context.dataStore.data.first()[key]
    } catch (e: Exception) {
        null
    }

    suspend fun write(token: String) {
        try {
            context.dataStore.edit { it[key] = token }
        } catch (e: Exception) {
            // best-effort; the in-memory copy (AuthTokenHolder) still works this session
        }
    }

    suspend fun clear() {
        try {
            context.dataStore.edit { it.remove(key) }
        } catch (e: Exception) {
            // ignore
        }
    }
}

/** Mirrors the RN app's AppStateContext local-UI-prefs persistence — format
 * toggles, alignment toggles, settings — all unrelated to the account, so
 * they persist regardless of who's logged in. Bumped to "v2" for the same
 * reason as the RN side: the imageQuality/heroImageQuality defaults changed
 * ('original' -> 'medium'/'high'), so an old blob must never shadow the new
 * defaults. */
class PrefsStorage(private val context: Context) {
    private val key = stringPreferencesKey("chocoplus.prefs.v2")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun read(): PersistedPrefs {
        val raw = try {
            context.dataStore.data.first()[key]
        } catch (e: Exception) {
            null
        } ?: return PersistedPrefs()
        return try {
            json.decodeFromString(PersistedPrefs.serializer(), raw)
        } catch (e: Exception) {
            PersistedPrefs()
        }
    }

    suspend fun write(prefs: PersistedPrefs) {
        try {
            val raw = json.encodeToString(PersistedPrefs.serializer(), prefs)
            context.dataStore.edit { it[key] = raw }
        } catch (e: Exception) {
            // best-effort, same as the RN side's .catch(() => {})
        }
    }
}
