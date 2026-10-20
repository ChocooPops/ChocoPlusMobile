package com.chocoplusmobile.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chocoplusmobile.api.ApiException
import com.chocoplusmobile.api.AuthTokenHolder
import com.chocoplusmobile.api.LoginPayload
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.PlayerTarget
import com.chocoplusmobile.api.ServerConfigHolder
import com.chocoplusmobile.api.User
import com.chocoplusmobile.api.WatchProgressPayload
import com.chocoplusmobile.api.WatchProgressStore
import com.chocoplusmobile.api.isServerUnavailable
import com.chocoplusmobile.data.ImageQuality
import com.chocoplusmobile.data.ImageQualityLevel
import com.chocoplusmobile.i18n.AppLocale
import com.chocoplusmobile.storage.PrefsStorage
import com.chocoplusmobile.storage.TokenStorage
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.canDownload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.text

/**
 * Mirrors the RN app's state/AppStateContext.tsx — the one shared source of
 * truth for auth, the "welcome back" gate, My List, and every persisted UI
 * preference (poster format/alignment per page, image quality, language…).
 * Held at the Activity/NavHost root and read via LocalAppViewModel, the
 * Compose equivalent of useAppState().
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val tokenStorage = TokenStorage(app)
    private val prefsStorage = PrefsStorage(app)

    private val _prefs = MutableStateFlow(PersistedPrefs())
    val prefs: StateFlow<PersistedPrefs> = _prefs.asStateFlow()

    // Public (not just an internal flag) so navigation can wait on it before
    // trusting prefs.settings.startPage — a local DataStore read, but still
    // async, and NavHost's startDestination can't itself be reactive.
    private val _prefsHydrated = MutableStateFlow(false)
    val prefsHydrated: StateFlow<Boolean> = _prefsHydrated.asStateFlow()

    private val _authChecked = MutableStateFlow(false)
    val authChecked: StateFlow<Boolean> = _authChecked.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()
    val isAuthenticated: StateFlow<Boolean> =
        _user.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Offline session entered by hand from the start screens (Login/
     * splash) — no account, no backend call at all, only the Downloads
     * screen and the player on local files. Takes over the whole UI (see
     * NavGraph) until exitOfflineSession(). */
    private val _offlineSession = MutableStateFlow(false)
    val offlineSession: StateFlow<Boolean> = _offlineSession.asStateFlow()

    /** Screens use it to skip network calls. */
    val isOffline: StateFlow<Boolean> = offlineSession

    fun enterOfflineSession() {
        _offlineSession.value = true
    }

    fun exitOfflineSession() {
        _offlineSession.value = false
    }

    /** True while the backend is known to be unreachable (no connectivity,
     * timeout, 5xx) — Login then offers the downloads, and a background
     * probe (watchServerRecovery) brings the app back to normal once the
     * server answers again. */
    private val _serverUnavailable = MutableStateFlow(false)
    val serverUnavailable: StateFlow<Boolean> = _serverUnavailable.asStateFlow()

    private val _checkingServer = MutableStateFlow(false)
    val checkingServer: StateFlow<Boolean> = _checkingServer.asStateFlow()

    private var recoveryJob: Job? = null
    private val recoveryLock = Mutex()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    /** True only right after a stored token was silently restored at app
     * launch (not after an interactive login() submission) — gates the
     * "welcome back, tap your photo to continue" screen. */
    private val _welcomeGatePending = MutableStateFlow(false)
    val welcomeGatePending: StateFlow<Boolean> = _welcomeGatePending.asStateFlow()

    fun dismissWelcomeGate() {
        _welcomeGatePending.value = false
    }

    private val _myListIds = MutableStateFlow<Set<Int>>(emptySet())
    val myListIds: StateFlow<Set<Int>> = _myListIds.asStateFlow()

    private val _myListLoading = MutableStateFlow(false)
    val myListLoading: StateFlow<Boolean> = _myListLoading.asStateFlow()

    init {
        // ── Local UI prefs (format toggles, settings) — unrelated to the
        // account, fine to persist regardless of who's logged in. ──
        viewModelScope.launch {
            _prefs.value = prefsStorage.read()
            _prefsHydrated.value = true
            applyPrefsSideEffects(_prefs.value.settings)
        }

        AuthTokenHolder.unauthorizedHandler = { logout() }

        // ── Auth boot: read the stored token, validate it against the server. ──
        viewModelScope.launch {
            try {
                restoreSession()
            } finally {
                _authChecked.value = true
            }
        }
    }

    /** Validates the stored token against the server — at launch, and again
     * by recoverServer once an unreachable server answers again. */
    private suspend fun restoreSession() {
        // No server yet: the app only shows the server form (see NavGraph).
        if (!ServerConfigHolder.isConfigured) return
        val token = tokenStorage.read() ?: return
        AuthTokenHolder.token = token
        try {
            val me = NetworkModule.safeCall { NetworkModule.api.getCurrentUser() }
            onUserLoaded(me)
            // Only the silent restore path gates on the welcome screen —
            // an interactive login() below never touches this flag.
            _welcomeGatePending.value = true
            refreshMyList()
        } catch (e: Exception) {
            // Only forget the stored token when the server itself looked
            // at it and rejected it (401) — that's the one case that
            // actually says the token is bad. Any other failure (no
            // connectivity, a DNS hiccup, a timeout, a 5xx) says nothing
            // about the token at all; it just means this one validation
            // attempt couldn't complete. Wiping storage on those too used
            // to mean: open the app once with a flaky connection, and a
            // perfectly good token was gone for good — forced back to
            // Login next time even once the network was fine again.
            // This session still shows Login either way (we genuinely
            // couldn't confirm who's logged in), but the persisted token
            // now only gets erased when it's actually confirmed invalid.
            //
            // Server unreachable (no connectivity, timeout, 5xx, wrong URL):
            // land on Login — never straight into the offline mode — so the
            // server form (gear) and the offline button both stay at hand.
            // The token is kept, and recoverServer revalidates it once the
            // server answers again.
            AuthTokenHolder.token = null
            if ((e as? ApiException)?.status == 401) tokenStorage.clear()
            if (e.isServerUnavailable()) markServerUnavailable()
        }
    }

    private fun applyPrefsSideEffects(settings: Settings) {
        // Mirrors the persisted quality/language settings into the
        // in-memory caches ImageQuality/AuthTokenHolder read synchronously
        // (URL building, the Accept-Language header) — same reasoning as
        // the RN app's own module-level mirrors.
        ImageQuality.standardLevel = settings.imageQuality
        ImageQuality.heroLevel = settings.heroImageQuality
        AuthTokenHolder.language = settings.language.name.lowercase()
        AppLocale.current = settings.language
    }

    private fun updatePrefs(block: (PersistedPrefs) -> PersistedPrefs) {
        _prefs.update(block)
        applyPrefsSideEffects(_prefs.value.settings)
        if (_prefsHydrated.value) {
            viewModelScope.launch { prefsStorage.write(_prefs.value) }
        }
    }

    /** A server-confirmed user — lets any download interrupted by a
     * previous run carry on. */
    private fun onUserLoaded(me: User) {
        _user.value = me
        _serverUnavailable.value = false
        if (me.role.canDownload()) DownloadManager.resumePending()
    }

    fun logout() {
        AuthTokenHolder.token = null
        DownloadManager.pauseAll()
        viewModelScope.launch { tokenStorage.clear() }
        _user.value = null
        _myListIds.value = emptySet()
        _welcomeGatePending.value = false
    }

    fun login(email: String, password: String, onError: (String) -> Unit, onSuccess: () -> Unit) {
        _authError.value = null
        viewModelScope.launch {
            try {
                val token = NetworkModule.safeCall { NetworkModule.api.login(LoginPayload(email, password)) }
                AuthTokenHolder.token = token.accessToken
                tokenStorage.write(token.accessToken)
                val me = NetworkModule.safeCall { NetworkModule.api.getCurrentUser() }
                onUserLoaded(me)
                refreshMyList()
                onSuccess()
            } catch (e: Exception) {
                AuthTokenHolder.token = null
                // Server down/unreachable is not a credentials problem: flag
                // it (Login shows its own banner + the downloads) instead of
                // the usual error. A 401/403 still gets the usual message.
                val unavailable = e.isServerUnavailable()
                if (unavailable) markServerUnavailable()
                val message = if (unavailable) Tr.LOGIN_ERROR_SERVER_UNREACHABLE.text()
                else (e as? ApiException)?.message ?: Tr.LOGIN_ERROR_GENERIC.text()
                _authError.value = message
                onError(message)
            }
        }
    }

    /** Probes the backend now (Login on open, its "Réessayer" button):
     * flags it unavailable, or — if it was and answers again — recovers. */
    fun checkServer() {
        if (_checkingServer.value || !ServerConfigHolder.isConfigured) return
        _checkingServer.value = true
        viewModelScope.launch {
            try {
                if (!NetworkModule.isServerReachable()) {
                    markServerUnavailable()
                } else {
                    recoverServer()
                }
            } finally {
                _checkingServer.value = false
            }
        }
    }

    private fun markServerUnavailable() {
        _serverUnavailable.value = true
        if (recoveryJob?.isActive == true) return
        recoveryJob = viewModelScope.launch { watchServerRecovery() }
    }

    /** Re-probes every few seconds until the server answers again, then
     * returns to normal operation (see recoverServer). */
    private suspend fun watchServerRecovery() {
        while (_serverUnavailable.value) {
            delay(SERVER_RETRY_MS)
            if (NetworkModule.isServerReachable()) recoverServer()
        }
    }

    /** The server answers again: clear the flag and, if we're still
     * logged out with a stored token, revalidate it — restoreSession re-flags the server if that fails.
     * Locked so checkServer and the background watch never revalidate
     * twice at once. */
    private suspend fun recoverServer() = recoveryLock.withLock {
        if (!_serverUnavailable.value) return@withLock
        _serverUnavailable.value = false
        if (_user.value == null) restoreSession()
    }

    fun refreshUser() {
        viewModelScope.launch {
            try {
                onUserLoaded(NetworkModule.safeCall { NetworkModule.api.getCurrentUser() })
            } catch (e: Exception) {
                // ignore — a 401 here is already handled by the global interceptor
            }
        }
    }

    fun refreshMyList() {
        viewModelScope.launch {
            _myListLoading.value = true
            try {
                val list = NetworkModule.safeCall { NetworkModule.api.getMyList() }
                _myListIds.value = list.map { it.id }.toSet()
            } catch (e: Exception) {
                // leave the previous snapshot in place on transient failure
            } finally {
                _myListLoading.value = false
            }
        }
    }

    fun toggleMyList(mediaId: Int) {
        val wasIn = _myListIds.value.contains(mediaId)
        _myListIds.update { if (wasIn) it - mediaId else it + mediaId }
        viewModelScope.launch {
            try {
                NetworkModule.safeCall { NetworkModule.api.toggleIntoMyList(mediaId) }
            } catch (e: Exception) {
                // roll back on failure
                _myListIds.update { if (wasIn) it + mediaId else it - mediaId }
            }
        }
    }

    fun isInMyList(mediaId: Int): Boolean = _myListIds.value.contains(mediaId)

    /** Called by PlayerScreen when leaving a video (or backgrounding the
     * app) — launched here rather than in the player's own scope, which is
     * already cancelled by the time its onDispose runs. */
    fun saveWatchProgress(target: PlayerTarget, percent: Double) {
        val payload = WatchProgressPayload(percent.coerceIn(0.0, 100.0))
        WatchProgressStore.record(target, payload.watchProgress)
        if (isOffline.value) return
        viewModelScope.launch {
            try {
                NetworkModule.safeCall {
                    when (target) {
                        is PlayerTarget.MovieTarget -> NetworkModule.api.saveMovieWatchProgress(target.movieId, payload)
                        is PlayerTarget.EpisodeTarget -> NetworkModule.api.saveEpisodeWatchProgress(target.episodeId, payload)
                    }
                }
            } catch (e: Exception) {
                // ignore — losing one progress update isn't worth surfacing
            }
        }
    }

    fun toggleFormat(page: FormatPage) {
        updatePrefs { p ->
            val next = if (p.formatByPage.get(page) == PosterFormat.V) PosterFormat.H else PosterFormat.V
            p.copy(formatByPage = p.formatByPage.with(page, next))
        }
    }

    fun toggleAlignment(page: FormatPage, format: PosterFormat) {
        updatePrefs { p ->
            val current = p.alignmentByPage.current(page, format)
            val next = if (current == PosterAlignment.SINGLE) PosterAlignment.GRID else PosterAlignment.SINGLE
            p.copy(alignmentByPage = p.alignmentByPage.with(page, format, next))
        }
    }

    fun setLanguage(language: AppLanguage) = updatePrefs { it.copy(settings = it.settings.copy(language = language)) }

    fun setImageQuality(level: ImageQualityLevel) = updatePrefs { it.copy(settings = it.settings.copy(imageQuality = level)) }

    fun setHeroImageQuality(level: ImageQualityLevel) = updatePrefs { it.copy(settings = it.settings.copy(heroImageQuality = level)) }

    fun setMediaLabelStyle(style: MediaLabelStyle) = updatePrefs { it.copy(settings = it.settings.copy(mediaLabelStyle = style)) }

    fun toggleAutoplay() = updatePrefs { p -> p.copy(settings = p.settings.copy(toggles = p.settings.toggles.copy(autoplay = !p.settings.toggles.autoplay))) }

    fun setStartPage(page: StartPage) = updatePrefs { it.copy(settings = it.settings.copy(startPage = page)) }

    fun setVideoDecoding(decoding: VideoDecoding) = updatePrefs { it.copy(settings = it.settings.copy(videoDecoding = decoding)) }

    /** Not driven by any Settings UI — PlayerScreen calls this whenever the
     * viewer manually changes an audio or subtitle track, inferring VF/VO
     * from the resulting combination so the next video starts the same way. */
    /** seriesId → id of the season last picked in SerieDetailScreen —
     * takes priority over the server's lastSeasonWatched. Deliberately in
     * memory only: once the app is closed, lastSeasonWatched is back in
     * charge. */
    private val lastSeasonBySeries = mutableMapOf<Int, Int>()

    fun lastSeason(seriesId: Int): Int? = lastSeasonBySeries[seriesId]

    fun rememberLastSeason(seriesId: Int, seasonId: Int) {
        lastSeasonBySeries[seriesId] = seasonId
    }

    fun setAudioSubtitlePreference(pref: AudioSubtitlePreference) = updatePrefs { it.copy(settings = it.settings.copy(audioSubtitlePreference = pref)) }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
        }
    }
}

private const val SERVER_RETRY_MS = 10_000L
