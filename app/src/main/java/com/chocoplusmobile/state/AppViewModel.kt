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
import com.chocoplusmobile.api.User
import com.chocoplusmobile.api.WatchProgressPayload
import com.chocoplusmobile.api.WatchProgressStore
import com.chocoplusmobile.data.ImageQuality
import com.chocoplusmobile.data.ImageQualityLevel
import com.chocoplusmobile.i18n.AppLocale
import com.chocoplusmobile.storage.PrefsStorage
import com.chocoplusmobile.storage.TokenStorage
import com.chocoplusmobile.storage.UserStorage
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.canDownload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val userStorage = UserStorage(app)

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

    /** True when the app started without connectivity and restored the
     * cached user instead of a server-validated one — only ever for
     * ADMIN/FAMILY accounts, so they can reach their offline downloads.
     * Navigation lands on the Downloads screen in that case. */
    private val _offlineMode = MutableStateFlow(false)
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    /** Offline session entered by hand from the start screens (Login/
     * splash) — no account, no backend call at all, only the Downloads
     * screen and the player on local files. Takes over the whole UI (see
     * NavGraph) until exitOfflineSession(). */
    private val _offlineSession = MutableStateFlow(false)
    val offlineSession: StateFlow<Boolean> = _offlineSession.asStateFlow()

    /** Either offline flavour — screens use it to skip network calls. */
    val isOffline: StateFlow<Boolean> =
        combine(_offlineMode, _offlineSession) { a, b -> a || b }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun enterOfflineSession() {
        _offlineSession.value = true
    }

    fun exitOfflineSession() {
        _offlineSession.value = false
    }

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
            val token = tokenStorage.read()
            if (token == null) {
                _authChecked.value = true
                return@launch
            }
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
                val status = (e as? ApiException)?.status
                // No connectivity (no HTTP status at all) with a cached
                // ADMIN/FAMILY user: open the app anyway, on the offline
                // Downloads screen — the token is kept, so everything else
                // simply works again once the network is back.
                val cached = if (status == null) userStorage.read() else null
                if (cached != null && cached.role.canDownload()) {
                    _user.value = cached
                    _offlineMode.value = true
                } else {
                    AuthTokenHolder.token = null
                    if (status == 401) {
                        tokenStorage.clear()
                        userStorage.clear()
                    }
                }
            } finally {
                _authChecked.value = true
            }
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

    /** A server-confirmed user — cached for offline starts, and lets any
     * download interrupted by a previous run carry on. */
    private fun onUserLoaded(me: User) {
        _user.value = me
        _offlineMode.value = false
        viewModelScope.launch { userStorage.write(me) }
        if (me.role.canDownload()) DownloadManager.resumePending()
    }

    fun logout() {
        AuthTokenHolder.token = null
        DownloadManager.pauseAll()
        _offlineMode.value = false
        viewModelScope.launch {
            tokenStorage.clear()
            userStorage.clear()
        }
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
                val message = (e as? ApiException)?.message ?: "Connexion impossible"
                _authError.value = message
                onError(message)
            }
        }
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
    fun setAudioSubtitlePreference(pref: AudioSubtitlePreference) = updatePrefs { it.copy(settings = it.settings.copy(audioSubtitlePreference = pref)) }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
        }
    }
}
