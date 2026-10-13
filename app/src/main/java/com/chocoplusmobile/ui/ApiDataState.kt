package com.chocoplusmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chocoplusmobile.api.ApiException

/** Mirrors the RN app's hooks/useApiData.ts — every screen in the app
 * follows the same "loading / error / data" shape against the real API, so
 * this avoids repeating the same three mutableStateOf()s everywhere.
 * `key` restarts the fetch when it changes (same idea as useApiData's
 * `deps` array — pass Unit for a fetch-once-on-mount screen). */
class ApiData<T> {
    var data by mutableStateOf<T?>(null)
        internal set
    var loading by mutableStateOf(true)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
}

@Composable
fun <T> rememberApiData(key: Any?, fetcher: suspend () -> T): Pair<ApiData<T>, () -> Unit> {
    val state = remember(key) { ApiData<T>() }
    var tick by remember(key) { mutableIntStateOf(0) }

    LaunchedEffect(key, tick) {
        state.loading = true
        state.error = null
        try {
            state.data = fetcher()
        } catch (e: Exception) {
            state.error = (e as? ApiException)?.message ?: e.message ?: "Erreur réseau"
        } finally {
            state.loading = false
        }
    }

    return state to { tick++ }
}
