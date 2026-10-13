package com.chocoplusmobile.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import androidx.mediarouter.media.MediaRouteSelector

/**
 * A thin, Compose-friendly wrapper around the Cast SDK's session/remote-
 * media plumbing (none of which is Flow/Compose-shaped on its own) —
 * PlayerScreen reads [isConnected]/[deviceName]/[remotePositionMs]/
 * [remoteDurationMs]/[remoteIsPlaying] to know when and how to hand
 * playback off to the TV, and calls [loadMedia]/[play]/[pause]/[seekTo]
 * instead of driving the local libVLC MediaPlayer while connected.
 *
 * `CastContext.getSharedInstance(Context)` (the synchronous overload) only
 * succeeds once the Cast framework's Play Services dynamic module is
 * already installed on the device — on a fresh install it's frequently NOT
 * yet there, and this throws `IllegalStateException` (a device with no
 * Google Play Services at all — some tablets, most emulators without the
 * Play Store image — fails the same way). Falling back to the async
 * `getSharedInstance(Context, Executor)` overload here triggers that module
 * install and resolves once it lands, so casting becomes available a few
 * seconds after first launch instead of never — without this fallback,
 * [isAvailable] stayed permanently false on such a device (making the Cast
 * button vanish/never work) with no way to recover short of reinstalling
 * after the module happened to get pulled down by something else.
 */
class CastController(context: Context) {
    private var castContext: CastContext? by mutableStateOf(null)

    val isAvailable: Boolean get() = castContext != null

    /** For the caller to show Android's own device-picker dialog
     * (MediaRouteChooserDialog) without needing to touch CastContext
     * itself — null when Cast isn't available on this device at all. */
    fun routeSelector(): MediaRouteSelector? = castContext?.mergedSelector

    var isConnected by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf<String?>(null)
        private set

    // Mirrors the connected RemoteMediaClient's own reported state — there's
    // no Flow/State-based API for this, only the callback below, so this is
    // polled indirectly (onStatusUpdated fires on every state change the
    // receiver reports, not on a fixed timer).
    var remotePositionMs by mutableLongStateOf(0L)
        private set
    var remoteDurationMs by mutableLongStateOf(0L)
        private set
    var remoteIsPlaying by mutableStateOf(false)
        private set

    private var session: CastSession? = null
    private val remoteClient: RemoteMediaClient? get() = session?.remoteMediaClient

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = remoteClient ?: return
            remotePositionMs = client.approximateStreamPosition
            remoteDurationMs = client.streamDuration
            remoteIsPlaying = client.isPlaying
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = attach(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attach(s)
        override fun onSessionEnded(s: CastSession, error: Int) = detach()
        override fun onSessionSuspended(s: CastSession, reason: Int) = detach()
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStartFailed(s: CastSession, error: Int) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) {}
    }

    private fun attach(s: CastSession) {
        session = s
        isConnected = true
        deviceName = s.castDevice?.friendlyName
        s.remoteMediaClient?.registerCallback(remoteCallback)
    }

    private fun detach() {
        session?.remoteMediaClient?.unregisterCallback(remoteCallback)
        session = null
        isConnected = false
        deviceName = null
        remotePositionMs = 0L
        remoteDurationMs = 0L
        remoteIsPlaying = false
    }

    private fun bindContext(ctx: CastContext) {
        castContext = ctx
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        // A session can already be connected when this screen opens (cast
        // started from elsewhere, or survives navigation) — pick it up
        // immediately instead of waiting for the next state change.
        ctx.sessionManager.currentCastSession?.let(::attach)
    }

    init {
        runCatching { CastContext.getSharedInstance(context) }
            .onSuccess(::bindContext)
            .onFailure { first ->
                Log.w("CastController", "Cast not ready synchronously (${first.message}), waiting for module install")
                runCatching { CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context)) }
                    .onSuccess { task ->
                        task.addOnSuccessListener(::bindContext)
                        task.addOnFailureListener { Log.w("CastController", "Cast unavailable: ${it.message}") }
                    }
                    .onFailure { Log.w("CastController", "Cast unavailable: ${it.message}") }
            }
    }

    fun dispose() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        remoteClient?.unregisterCallback(remoteCallback)
    }

    /** Starts playback on the connected receiver from [startPositionMs] —
     * the same stream URL PlayerScreen already built for libVLC, since its
     * token-as-query-param shape works for any plain HTTP client. */
    fun loadMedia(streamUrl: String, title: String, posterUrl: String?, startPositionMs: Long) {
        val client = remoteClient ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(startPositionMs)
            .build()
        client.load(request)
    }

    fun play() {
        remoteClient?.play()
    }

    fun pause() {
        remoteClient?.pause()
    }

    fun seekTo(ms: Long) {
        remoteClient?.seek(MediaSeekOptions.Builder().setPosition(ms).build())
    }
}

/** Lives for exactly as long as the composable that requests it — dispose()
 * unregisters the session listener so it doesn't keep firing into a
 * disposed PlayerScreen after leaving the player. */
@Composable
fun rememberCastController(): CastController {
    val context = LocalContext.current
    val controller = remember { CastController(context) }
    DisposableEffect(Unit) { onDispose { controller.dispose() } }
    return controller
}
