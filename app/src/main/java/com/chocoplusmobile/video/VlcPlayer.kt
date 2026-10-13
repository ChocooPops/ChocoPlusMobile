package com.chocoplusmobile.video

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.chocoplusmobile.state.VideoDecoding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Backs the full-screen ChocoPlayer (PlayerScreen) — libVLC instead of
 * Media3/ExoPlayer (which still drives the Films/Séries hero teaser, see
 * StreamPlayer.kt) for broader codec/container tolerance and real
 * audio/subtitle track switching, at the cost of ~20-30MB of bundled native
 * decoders. The stream endpoints take the JWT as a `?token=` query param
 * (see api/Stream.kt) — no custom HTTP header is required for the stream
 * routes specifically (confirmed live: a plain VLC desktop open of the same
 * URL, with no secret header at all, plays fine), so there's nothing extra
 * to attach to the Media here.
 */
object VlcPlayer {
    fun buildLibVLC(context: Context): LibVLC {
        val options = arrayListOf(
            "--no-sub-autodetect-file",
            "--network-caching=3000",
        )
        return LibVLC(context, options)
    }

    /**
     * `decoding` picks where the actual decode work happens — see
     * [VideoDecoding]'s doc for the color-consistency-vs-battery tradeoff
     * behind the two options. Exposed as a user setting (Paramètres >
     * Lecture) rather than hardcoded, since the right choice depends on the
     * device and the source files someone actually watches.
     */
    fun media(libVLC: LibVLC, url: String, decoding: VideoDecoding): Media {
        val media = Media(libVLC, Uri.parse(url))
        media.setHWDecoderEnabled(decoding == VideoDecoding.HARDWARE, false)
        return media
    }
}

/** Holds both the LibVLC engine handle and the MediaPlayer built on top of
 * it — `MediaPlayer` doesn't expose its LibVLC back out, and building a new
 * `Media` (see VlcPlayer.media()) needs one, so callers get both instead of
 * having to stash the engine handle separately. */
class VlcPlayerHandle(val libVLC: LibVLC, val mediaPlayer: MediaPlayer)

/** Builds a LibVLC + MediaPlayer pair scoped to this composable's
 * lifecycle — released automatically when it leaves composition. Mirrors
 * video/StreamPlayer.kt's rememberChocoPlayer() for the ExoPlayer side. */
@Composable
fun rememberVlcPlayer(): VlcPlayerHandle {
    val context = LocalContext.current
    val libVLC = remember { VlcPlayer.buildLibVLC(context) }
    val mediaPlayer = remember(libVLC) { MediaPlayer(libVLC) }
    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }
    return remember(libVLC, mediaPlayer) { VlcPlayerHandle(libVLC, mediaPlayer) }
}
