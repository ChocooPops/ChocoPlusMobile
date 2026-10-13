package com.chocoplusmobile.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chocoplusmobile.api.AuthTokenHolder
import com.chocoplusmobile.api.Episode
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.PlayerTarget
import com.chocoplusmobile.api.Season
import com.chocoplusmobile.api.Stream
import com.chocoplusmobile.api.WatchProgressStore
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.Palette
import com.chocoplusmobile.download.DownloadKind
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.DownloadStatus
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.AudioSubtitlePreference
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ChocoBottomSheet
import com.chocoplusmobile.ui.components.ChocoChip
import com.chocoplusmobile.ui.components.FadeInImage
import com.chocoplusmobile.ui.components.IconCircleButton
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.VlcVideoSurface
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import com.chocoplusmobile.video.VlcPlayer
import com.chocoplusmobile.video.rememberCastController
import com.chocoplusmobile.video.rememberVlcPlayer
import org.videolan.libvlc.MediaPlayer
import androidx.mediarouter.app.MediaRouteChooserDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** How long the controls stay up after the last interaction before fading
 * out on their own, while playing — same convention as YouTube/Netflix/VLC
 * itself. Tapping the screen toggles them instantly either way. */
private const val AUTO_HIDE_DELAY_MS = 4000L

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)
private const val NO_SUBTITLES_TRACK_ID = -1

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Best-effort language match against a libVLC track name — real-world
 * naming is inconsistent (full names, English names, ISO-639 codes) so this
 * just checks for the handful of forms French tracks actually show up as,
 * rather than trying to parse it properly. */
private fun isFrenchTrackName(name: String): Boolean {
    val n = name.trim().lowercase()
    return n == "fr" || n == "fre" || n == "fra" || n.contains("french") || n.contains("français") || n.contains("francais")
}

private fun hms(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return (if (h > 0) "$h:" else "") + "%02d:%02d".format(m, s)
}

/**
 * Mirrors the RN app's screens/Player/PlayerScreen.tsx — ChocoPlayer, a
 * fully custom control surface, now driven by libVLC (see video/VlcPlayer.kt
 * for why) instead of ExoPlayer. Reads the target passed via NavCache.
 */
@Composable
fun PlayerScreen() {
    val navController = LocalNavController.current
    var target by remember { mutableStateOf(NavCache.pendingPlayerTarget) }
    val currentTarget = target

    if (currentTarget == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    // Samsung (and most OEMs) keep drawing the status bar's clock/battery/
    // signal icons over any full-screen video unless the app explicitly
    // hides them — a real device request (S25+), not visible on every
    // emulator skin. Swiping down from the top still reveals them
    // momentarily, same as YouTube/Netflix; restored on leaving the screen.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }

    // Switching episode from the in-player episode list remounts everything
    // below fresh (new libVLC player, all playback state reset) rather than
    // hand-resetting every individually-`remember`ed value — simpler than
    // chasing every place a resume-position/track-list/etc. could otherwise
    // leak from one episode into the next.
    // The locally recorded progress (see WatchProgressStore) wins over the
    // possibly-stale one the calling screen passed in — resolved once per
    // video, so the store being updated mid-playback (on backgrounding)
    // doesn't change params under the running player.
    key(currentTarget) {
        val params = remember { WatchProgressStore.applyTo(currentTarget) }
        PlayerContent(params = params, onSwitchEpisode = { target = it })
    }
}

@Composable
private fun PlayerContent(params: PlayerTarget, onSwitchEpisode: (PlayerTarget) -> Unit) {
    val navController = LocalNavController.current
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val context = LocalContext.current
    val castController = rememberCastController()

    val token = AuthTokenHolder.token
    val remoteStreamUrl = token?.let {
        when (params) {
            is PlayerTarget.MovieTarget -> Stream.movieUrl(params.movieId, it)
            is PlayerTarget.EpisodeTarget -> Stream.episodeUrl(params.seasonId, params.episodeId, it)
        }
    }
    // A downloaded copy always wins — no bandwidth used, and it's the only
    // thing that plays offline. Casting still needs the remote URL though:
    // the TV can't read a file off this phone.
    val streamUrl = remember { DownloadManager.localVideoUri(params) } ?: remoteStreamUrl

    // Opens Android's own device-picker dialog (the same one a
    // MediaRouteButton would) — Stream.kt's URLs already carry auth as a
    // `?token=` query param instead of a header, exactly so a plain HTTP
    // client like a Cast receiver can play them with no changes needed.
    fun openCastPicker() {
        val activity = context.findActivity() ?: return
        val selector = castController.routeSelector() ?: return
        runCatching { MediaRouteChooserDialog(activity).apply { routeSelector = selector }.show() }
            .onFailure { android.util.Log.w("PlayerScreen", "Could not open the cast picker", it) }
    }

    val vlc = rememberVlcPlayer()
    val mediaPlayer = vlc.mediaPlayer
    var paused by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var errored by remember { mutableStateOf(false) }
    var duration by remember { mutableFloatStateOf(0f) }
    var currentTime by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var showResume by remember { mutableStateOf((params.progressPercent ?: 0.0) > 0) }
    var tracksOpen by remember { mutableStateOf(false) }
    var rateIndex by remember { mutableIntStateOf(0) }
    var resizeContain by remember { mutableStateOf(true) }
    var resumeApplied by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf(listOf<MediaPlayer.TrackDescription>()) }
    var subTracks by remember { mutableStateOf(listOf<MediaPlayer.TrackDescription>()) }
    var currentAudioTrack by remember { mutableIntStateOf(-1) }
    var currentSubTrack by remember { mutableIntStateOf(NO_SUBTITLES_TRACK_ID) }
    var audioSubPrefApplied by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }

    // Episode list (series only) — replaces the "Diffuser" action. Defaults
    // to the season of the episode actually playing; switching season only
    // re-fetches that season's episode list, not the series itself.
    var episodesSheetOpen by remember { mutableStateOf(false) }
    var seriesTitle by remember { mutableStateOf(params.title.substringBefore(" · ")) }
    var seasons by remember { mutableStateOf(listOf<Season>()) }
    var selectedSeasonId by remember { mutableStateOf((params as? PlayerTarget.EpisodeTarget)?.seasonId) }
    var seasonEpisodes by remember { mutableStateOf(listOf<Episode>()) }
    var episodesLoading by remember { mutableStateOf(false) }

    // Online, the episode list comes from the backend (every episode, each
    // one then played from its local file if downloaded — see streamUrl);
    // offline, it's built from this series' downloaded episodes only, with
    // no network call at all.
    val offline by appViewModel.isOffline.collectAsState()
    val downloads by DownloadManager.entries.collectAsState()

    if (params is PlayerTarget.EpisodeTarget && offline) {
        LaunchedEffect(params.seriesId, selectedSeasonId, downloads) {
            val downloaded = downloads.values.filter {
                it.kind == DownloadKind.EPISODE && it.mediaId == params.seriesId && it.status == DownloadStatus.COMPLETED && it.episode != null
            }
            downloaded.firstOrNull()?.media?.title?.let { seriesTitle = it }
            val seasonIds = downloaded.mapNotNull { it.seasonId ?: it.episode?.seasonId }.toSet()
            val known = downloaded.firstOrNull()?.media?.seasons.orEmpty()
            seasons = seasonIds.map { id -> known.firstOrNull { it.id == id } ?: Season(id = id, seriesId = params.seriesId) }.sortedBy { it.seasonNumber }
            seasonEpisodes = downloaded.mapNotNull { it.episode }.filter { it.seasonId == selectedSeasonId }.sortedBy { it.episodeNumber }
        }
    } else if (params is PlayerTarget.EpisodeTarget) {
        LaunchedEffect(params.seriesId) {
            try {
                val series = NetworkModule.safeCall { NetworkModule.api.getSeriesById(params.seriesId) }
                seriesTitle = series.title
                seasons = series.seasons?.sortedBy { it.seasonNumber } ?: emptyList()
            } catch (e: Exception) { /* ignore — season picker just stays empty */ }
        }
        LaunchedEffect(params.seriesId, selectedSeasonId) {
            val seasonId = selectedSeasonId ?: return@LaunchedEffect
            episodesLoading = true
            try {
                seasonEpisodes = NetworkModule.safeCall { NetworkModule.api.getEpisodes(params.seriesId, seasonId) }
            } catch (e: Exception) {
                seasonEpisodes = emptyList()
            } finally {
                episodesLoading = false
            }
        }
    }

    fun selectEpisode(episode: Episode) {
        episodesSheetOpen = false
        onSwitchEpisode(
            PlayerTarget.EpisodeTarget(
                seasonId = episode.seasonId,
                episodeId = episode.id,
                seriesId = (params as PlayerTarget.EpisodeTarget).seriesId,
                title = "$seriesTitle · ${episode.name}",
                posterUrl = Adapters.episodePosterUri(episode),
                progressPercent = episode.watchProgress,
            ),
        )
    }

    // Tap the video to toggle every control on/off, fading either way — the
    // rest of the app never hides UI like this, so this is scoped to just
    // this screen rather than a shared component.
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    fun showControls() {
        controlsVisible = true
        interactionTick++
    }
    fun toggleControls() {
        if (controlsVisible) controlsVisible = false else showControls()
    }

    // Auto-hides while actually playing, same as any modern player — every
    // real interaction (not just the toggle tap) restarts the countdown via
    // interactionTick, since re-setting controlsVisible to the same `true`
    // value wouldn't otherwise restart this effect.
    LaunchedEffect(interactionTick, controlsVisible, paused, loaded, tracksOpen) {
        if (controlsVisible && loaded && !paused && !tracksOpen) {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    val resumeSeconds = if ((params.progressPercent ?: 0.0) > 0 && duration > 0) ((params.progressPercent!! / 100.0) * duration).roundToLong() else 0L

    // The resume banner is a one-shot notice, not an ordinary control — it
    // shows once right after the resume-seek actually happens, stays up on
    // its own for a few seconds, then hides itself for good, regardless of
    // controlsVisible or of scrubbing back near that timestamp later.
    LaunchedEffect(resumeApplied) {
        if (resumeApplied) {
            delay(4000)
            showResume = false
        }
    }

    // Applies the last VF/VO combination the viewer actually picked (see
    // recordAudioSubtitleChoice below) the first time this playback session
    // has tracks to choose from — a one-shot default, not a lock: the track
    // sheet below still lets it be overridden per video. VF picks a French
    // audio track (falling back to whatever's first if none is tagged
    // French) and turns subtitles off; VO keeps the first non-French audio
    // track and turns on French subtitles when one exists.
    fun applyAudioSubtitlePreference() {
        if (audioSubPrefApplied || audioTracks.isEmpty()) return
        audioSubPrefApplied = true
        val frAudio = audioTracks.firstOrNull { isFrenchTrackName(it.name) }
        when (prefs.settings.audioSubtitlePreference) {
            AudioSubtitlePreference.VF -> {
                val target = frAudio ?: audioTracks.first()
                mediaPlayer.setAudioTrack(target.id)
                currentAudioTrack = target.id
                mediaPlayer.setSpuTrack(NO_SUBTITLES_TRACK_ID)
                currentSubTrack = NO_SUBTITLES_TRACK_ID
            }
            AudioSubtitlePreference.VO -> {
                val target = audioTracks.firstOrNull { !isFrenchTrackName(it.name) } ?: audioTracks.first()
                mediaPlayer.setAudioTrack(target.id)
                currentAudioTrack = target.id
                val frSub = subTracks.firstOrNull { isFrenchTrackName(it.name) }
                if (frSub != null) {
                    mediaPlayer.setSpuTrack(frSub.id)
                    currentSubTrack = frSub.id
                }
            }
        }
    }

    // libVLC lists a pseudo "Disable" entry (id -1) first in both track
    // lists — it has to be filtered out, otherwise the preference above
    // picks it as "the first audio track" and silently mutes the video
    // (and the subtitle sheet would show it next to our own "Aucun").
    fun refreshTracks() {
        audioTracks = mediaPlayer.audioTracks?.filter { it.id >= 0 } ?: emptyList()
        subTracks = mediaPlayer.spuTracks?.filter { it.id >= 0 } ?: emptyList()
        currentAudioTrack = mediaPlayer.audioTrack
        currentSubTrack = mediaPlayer.spuTrack
        applyAudioSubtitlePreference()
    }

    // Called from the track sheet below whenever the viewer manually changes
    // the audio or subtitle track — not on the automatic apply above, which
    // would just be writing back the value it was itself given. Reduces
    // whatever combination results to the same VF/VO shape Settings used to
    // expose directly: a French-leaning audio track with subtitles off reads
    // as "VF"; anything else (original audio, or subtitles left on) reads as
    // "VO" — matching exactly how applyAudioSubtitlePreference interprets it.
    fun recordAudioSubtitleChoice(audioTrackId: Int, subTrackId: Int) {
        val audioIsFrench = audioTracks.firstOrNull { it.id == audioTrackId }?.let { isFrenchTrackName(it.name) } ?: false
        val subsOff = subTrackId == NO_SUBTITLES_TRACK_ID
        val inferred = if (audioIsFrench && subsOff) AudioSubtitlePreference.VF else AudioSubtitlePreference.VO
        appViewModel.setAudioSubtitlePreference(inferred)
    }

    // Forcing the decoded frame's assumed aspect ratio to match the (full-
    // screen) surface's own ratio makes libVLC scale-to-fill instead of
    // letterboxing — a real crop/zoom, not just an approximation, as long as
    // VlcVideoSurface below actually fills the screen (it does).
    LaunchedEffect(resizeContain) {
        mediaPlayer.aspectRatio = if (resizeContain) null else "$screenWidthDp:$screenHeightDp"
    }

    val videoDecoding = prefs.settings.videoDecoding
    LaunchedEffect(streamUrl, videoDecoding) {
        if (streamUrl != null) {
            val media = VlcPlayer.media(vlc.libVLC, streamUrl, videoDecoding)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        }
    }

    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    ended = false
                    loaded = true
                    refreshTracks()
                }
                MediaPlayer.Event.LengthChanged -> {
                    duration = event.lengthChanged / 1000f
                    if (!resumeApplied && (params.progressPercent ?: 0.0) > 0 && duration > 0) {
                        resumeApplied = true
                        val targetMs = ((params.progressPercent!! / 100.0) * duration * 1000).toLong()
                        mediaPlayer.time = targetMs
                        currentTime = targetMs / 1000f
                    }
                }
                MediaPlayer.Event.TimeChanged -> {
                    if (!scrubbing) currentTime = event.timeChanged / 1000f
                }
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> refreshTracks()
                MediaPlayer.Event.EncounteredError -> errored = true
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> ended = true
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose { mediaPlayer.setEventListener(null) }
    }

    // While casting, the local libVLC player stays paused throughout — only
    // the TV should ever actually be playing — so this now branches instead
    // of always driving mediaPlayer directly.
    LaunchedEffect(paused, loaded, castController.isConnected) {
        if (castController.isConnected) {
            if (paused) castController.pause() else castController.play()
        } else if (loaded) {
            if (paused) mediaPlayer.pause() else mediaPlayer.play()
        }
    }

    // Cast handoff: connecting pauses the local player and starts the same
    // stream on the receiver from wherever local playback was; disconnecting
    // (TV turned off, user picked "Stop casting", session dropped) seeks the
    // local player to wherever the TV had gotten to and resumes there —
    // `castWasActive` is what tells the disconnect branch apart from this
    // effect's own very first run (isConnected starts false on every screen,
    // that first false is not a disconnect).
    var castWasActive by remember { mutableStateOf(false) }
    LaunchedEffect(castController.isConnected) {
        if (castController.isConnected) {
            castWasActive = true
            if (remoteStreamUrl != null) {
                mediaPlayer.pause()
                castController.loadMedia(remoteStreamUrl, params.title, params.posterUrl, (currentTime * 1000).toLong())
            }
        } else if (castWasActive) {
            castWasActive = false
            val resumeMs = castController.remotePositionMs
            if (resumeMs > 0) mediaPlayer.time = resumeMs
            if (!paused) mediaPlayer.play()
        }
    }

    // Mirrors the receiver's own reported position/duration/play-state back
    // into the same vars the UI (slider, time labels, play/pause icon)
    // already reads — so none of that UI needs to know it's now looking at
    // remote state instead of the local MediaPlayer's.
    LaunchedEffect(castController.isConnected, castController.remotePositionMs, castController.remoteDurationMs, castController.remoteIsPlaying) {
        if (castController.isConnected) {
            currentTime = castController.remotePositionMs / 1000f
            duration = castController.remoteDurationMs / 1000f
            paused = !castController.remoteIsPlaying
        }
    }

    LaunchedEffect(rateIndex) { mediaPlayer.setRate(SPEEDS[rateIndex]) }

    // Keeps the screen from sleeping only while a video is actually playing
    // here — paused, finished, errored, casting (the TV is the one playing)
    // or leaving the player all hand control back to the normal timeout.
    val playerView = LocalView.current
    val keepAwake = loaded && !paused && !ended && !errored && !castController.isConnected
    DisposableEffect(playerView, keepAwake) {
        playerView.keepScreenOn = keepAwake
        onDispose { playerView.keepScreenOn = false }
    }

    // Sends currentTime / duration * 100 to the backend — on leaving the
    // player (back, or switching episode, both dispose this composable) and
    // also whenever the app is backgrounded, since the process may well be
    // killed from there without onDispose ever running.
    fun saveProgress() {
        if (!loaded || errored || duration <= 0f) return
        appViewModel.saveWatchProgress(params, currentTime / duration * 100.0)
    }

    // Backgrounding pauses local playback (the surface is gone anyway, see
    // VlcVideoSurface) and leaves it paused on return, like Netflix/YouTube.
    // Not while casting — the TV should keep playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveProgress()
                if (!castController.isConnected && loaded) {
                    mediaPlayer.pause()
                    paused = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveProgress()
        }
    }

    fun seekBy(deltaSeconds: Float) {
        showControls()
        val target = (currentTime + deltaSeconds).coerceIn(0f, if (duration > 0) duration else Float.MAX_VALUE)
        if (castController.isConnected) {
            castController.seekTo((target * 1000).toLong())
        } else {
            mediaPlayer.time = (target * 1000).toLong()
        }
        currentTime = target
    }

    // Double-tap left/right half of the screen to seek ±10s, same gesture
    // as YouTube — independent of controlsVisible (works whether the
    // control bar is shown or not, like the real thing), with a brief
    // icon+label flash on whichever side was tapped as feedback.
    var seekFlashSide by remember { mutableIntStateOf(0) } // -1 left, 1 right
    var seekFlashVisible by remember { mutableStateOf(false) }
    var seekFlashTick by remember { mutableIntStateOf(0) }
    fun flashSeek(side: Int) {
        seekFlashSide = side
        seekFlashVisible = true
        seekFlashTick++
    }
    LaunchedEffect(seekFlashTick) {
        if (seekFlashTick > 0) {
            delay(500)
            seekFlashVisible = false
        }
    }

    val displayedTime = if (scrubbing) scrubValue else currentTime
    val (g1, g2, g3) = Palette.posterGradient(params.title)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (castController.isConnected) {
            // Nothing plays locally while casting — the TV has the actual
            // video. Same "poster behind a dim scrim" treatment used
            // elsewhere for a stream that hasn't started rendering yet.
            if (params.posterUrl != null) {
                FadeInImage(uri = params.posterUrl!!, title = params.title, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(g1, g2, g3))))
            }
            Box(modifier = Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcon.of("cast"), contentDescription = null, tint = ChocoColors.text, modifier = Modifier.size(52.dp))
                    Text(
                        "Diffusion sur ${castController.deviceName ?: "l'appareil"}",
                        color = ChocoColors.text,
                        fontFamily = ChocoFonts.bodyBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        } else if (streamUrl != null) {
            VlcVideoSurface(mediaPlayer = mediaPlayer, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(g1, g2, g3))))
        }

        // Tap anywhere to toggle every control below, double-tap left/right
        // half to seek ±10s (YouTube's gesture) — sits above the video but
        // below the controls themselves (added later, further down this
        // Box), so a tap that actually lands on a button still hits that
        // button instead of this layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { toggleControls() },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) {
                                seekBy(-10f)
                                flashSeek(-1)
                            } else {
                                seekBy(10f)
                                flashSeek(1)
                            }
                        },
                    )
                },
        )

        AnimatedVisibility(
            visible = seekFlashVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(if (seekFlashSide < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 44.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(Color(0x66000000), androidx.compose.foundation.shape.CircleShape).padding(20.dp),
            ) {
                Icon(AppIcon.of(if (seekFlashSide < 0) "replay_10" else "forward_10"), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text(if (seekFlashSide < 0) "-10s" else "+10s", color = Color.White, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x8C000000), 0.4f to Color(0x0D000000), 1f to Color(0xB3000000))))
        }

        if (!loaded && !errored) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ChocoColors.accent)
            }
        }
        if (errored || streamUrl == null) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(AppIcon.of("error_outline"), contentDescription = null, tint = ChocoColors.textMuted, modifier = Modifier.size(34.dp))
                Text("Impossible de lire cette vidéo", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyMedium, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp).padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircleButton(icon = AppIcon.of("keyboard_arrow_down"), onClick = { navController.popBackStack() })
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(params.title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 14.sp, maxLines = 1)
                    Text("ChocoPlayer", color = Color(0x8CFFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (castController.isAvailable) {
                        IconCircleButton(
                            icon = AppIcon.of("cast"),
                            color = if (castController.isConnected) ChocoColors.accent else ChocoColors.text,
                            onClick = { showControls(); openCastPicker() },
                        )
                    }
                    IconCircleButton(icon = AppIcon.of("subtitles"), onClick = { showControls(); tracksOpen = true })
                }
            }
        }

        if (loaded && !errored) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcon.of("replay_10"), contentDescription = "Reculer 10s", tint = ChocoColors.text, modifier = Modifier.size(38.dp).clickable { seekBy(-10f) })
                        Box(
                            modifier = Modifier.size(76.dp).background(Color(0x29FFFFFF), androidx.compose.foundation.shape.CircleShape).clickable { showControls(); paused = !paused },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcon.of(if (paused) "play_arrow" else "pause"), contentDescription = null, tint = ChocoColors.text, modifier = Modifier.size(44.dp))
                        }
                        Icon(AppIcon.of("forward_10"), contentDescription = "Avancer 10s", tint = ChocoColors.text, modifier = Modifier.size(38.dp).clickable { seekBy(10f) })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showResume,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 118.dp)
                    .background(Color(0xEB141414), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcon.of("history_toggle_off"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reprise à ${hms(resumeSeconds)}", color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 13.sp)
                    Text("Position restaurée depuis ton historique", color = Color(0x80FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    modifier = Modifier
                        .background(Color(0x1FFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable {
                            showControls()
                            if (castController.isConnected) castController.seekTo(0L) else mediaPlayer.time = 0L
                            currentTime = 0f
                            showResume = false
                        }
                        .padding(horizontal = 12.dp)
                        .height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reprendre à zéro", color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 11.5.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hms(displayedTime.toLong()), color = Color(0xBFFFFFFF), fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp)
                    Slider(
                        value = displayedTime,
                        onValueChange = { showControls(); scrubbing = true; scrubValue = it },
                        onValueChangeFinished = {
                            if (castController.isConnected) {
                                castController.seekTo((scrubValue * 1000).toLong())
                            } else {
                                mediaPlayer.time = (scrubValue * 1000).toLong()
                            }
                            currentTime = scrubValue
                            scrubbing = false
                        },
                        valueRange = 0f..(duration.takeIf { it > 0 } ?: 1f),
                        colors = SliderDefaults.colors(thumbColor = ChocoColors.accent, activeTrackColor = ChocoColors.accent, inactiveTrackColor = Color(0x4DFFFFFF)),
                        modifier = Modifier.weight(1f).height(32.dp),
                    )
                    Text("-${hms((duration - displayedTime).toLong())}", color = Color(0x99FFFFFF), fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    PlayerActionButton(icon = "speed", label = "Vitesse ${SPEEDS[rateIndex]}×", onClick = { showControls(); rateIndex = (rateIndex + 1) % SPEEDS.size })
                    PlayerActionButton(icon = "subtitles", label = "Pistes", onClick = { showControls(); tracksOpen = true })
                    if (params is PlayerTarget.EpisodeTarget) {
                        PlayerActionButton(icon = "video_library", label = "Épisodes", onClick = { showControls(); episodesSheetOpen = true })
                    }
                    if (castController.isAvailable) {
                        PlayerActionButton(icon = "cast", label = "Diffuser", onClick = { showControls(); openCastPicker() })
                    }
                    PlayerActionButton(icon = "aspect_ratio", label = "Cadrage", onClick = { showControls(); resizeContain = !resizeContain })
                }
            }
        }
    }

    ChocoBottomSheet(visible = tracksOpen, onClose = { tracksOpen = false }, maxHeightPct = 0.6f) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TrackGroup(
                label = "AUDIO",
                tracks = audioTracks.map { it.id to it.name },
                selectedId = currentAudioTrack,
                onSelect = { id -> mediaPlayer.setAudioTrack(id); currentAudioTrack = id; recordAudioSubtitleChoice(id, currentSubTrack) },
                modifier = Modifier.weight(1f),
            )
            TrackGroup(
                label = "SOUS-TITRES",
                tracks = listOf(NO_SUBTITLES_TRACK_ID to "Aucun") + subTracks.map { it.id to it.name },
                selectedId = currentSubTrack,
                onSelect = { id -> mediaPlayer.setSpuTrack(id); currentSubTrack = id; recordAudioSubtitleChoice(currentAudioTrack, id) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (params is PlayerTarget.EpisodeTarget) {
        ChocoBottomSheet(visible = episodesSheetOpen, onClose = { episodesSheetOpen = false }, maxHeightPct = 0.75f) {
            Text("ÉPISODES", color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 14.dp))
            if (seasons.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    items(seasons.size) { idx ->
                        val s = seasons[idx]
                        ChocoChip(label = s.name.ifBlank { "Saison ${s.seasonNumber}" }, active = s.id == selectedSeasonId, onClick = { selectedSeasonId = s.id }, small = true)
                    }
                }
            }
            if (episodesLoading) {
                LoadingState(compact = true)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    seasonEpisodes.forEach { episode ->
                        PlayerEpisodeRow(
                            episode = episode,
                            playing = episode.id == params.episodeId,
                            downloaded = downloads[DownloadManager.episodeKey(episode.id)]?.status == DownloadStatus.COMPLETED,
                            onClick = { selectEpisode(episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionButton(icon: String, label: String, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(AppIcon.of(icon), contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(22.dp))
        Text(label, color = Color(0xCCFFFFFF), fontFamily = ChocoFonts.bodySemiBold, fontSize = 9.5.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

/** `tracks` is a list of (trackId, displayName) — real libVLC track ids now,
 * not the placeholder string list this used to be under ExoPlayer (which
 * had no track selection wired up at all). */
@Composable
private fun TrackGroup(label: String, tracks: List<Pair<Int, String>>, selectedId: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 12.dp))
        tracks.forEach { (id, name) ->
            val active = id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (active) Color(0x1FFFD301) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                    .clickable { onSelect(id) }
                    .padding(vertical = 11.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (active) Icon(AppIcon.of("check"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(17.dp))
                else Box(modifier = Modifier.size(17.dp))
                Text(name, color = if (active) ChocoColors.accent else Color(0xA6FFFFFF), fontFamily = ChocoFonts.bodyMedium, fontSize = 13.sp)
            }
        }
    }
}

/** Mirrors SerieDetailScreen's own EpisodeRow, with one addition: `playing`
 * marks the episode actually loaded in ChocoPlayer right now — an accent
 * background/border plus an equalizer-ish icon instead of the episode
 * number, so it reads clearly as "this one" among the rest of the list. */
@Composable
private fun PlayerEpisodeRow(episode: Episode, playing: Boolean, downloaded: Boolean, onClick: () -> Unit) {
    val eUi = remember(episode) { Adapters.mapEpisodeToUi(episode) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (playing) Color(0x1FFFD301) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(enabled = !playing, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(26.dp).padding(top = 14.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                Icon(AppIcon.of("play_arrow"), contentDescription = "En cours de lecture", tint = ChocoColors.accent, modifier = Modifier.size(18.dp))
            } else {
                Text("${eUi.number}", color = ChocoColors.textFaint, fontFamily = ChocoFonts.headingExtraBold, fontSize = 16.sp)
            }
        }
        Poster(title = eUi.title, uri = eUi.posterUrl, ratio = PosterRatio.R16_9, radius = 7.dp, titleSize = 8.sp, progress = eUi.progress.takeIf { it > 0 }, modifier = Modifier.width(96.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(eUi.title, color = if (playing) ChocoColors.accent else ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
            val meta = listOfNotNull(eUi.duration.ifBlank { null }, eUi.airDate.ifBlank { null }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodySemiBold, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            // Tells which episodes will play from the phone rather than stream.
            if (downloaded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Icon(AppIcon.of("download_done"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(13.dp))
                    Text("Téléchargé", color = ChocoColors.accent, fontFamily = ChocoFonts.bodySemiBold, fontSize = 10.sp)
                }
            }
        }
    }
}
