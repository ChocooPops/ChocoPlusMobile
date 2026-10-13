package com.chocoplusmobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Video surface for the libVLC-backed ChocoPlayer (PlayerScreen) — the
 * `VLCVideoLayout` helper view handles the actual SurfaceView/TextureView
 * plumbing libVLC needs internally. The MediaPlayer itself, and its
 * release, is owned by rememberVlcPlayer().
 *
 * Views are attached on ON_START and detached on ON_STOP rather than just
 * once: Android destroys the underlying Surface when the app goes to the
 * background, and libVLC never re-binds to the new one on its own — the
 * video kept decoding into the dead surface, so coming back showed a black
 * screen while playback carried on (see attach() below for the seek that
 * also has to follow the re-attach). */
@Composable
fun VlcVideoSurface(mediaPlayer: MediaPlayer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val layout = remember { VLCVideoLayout(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(modifier = modifier, factory = { layout })

    DisposableEffect(mediaPlayer, lifecycleOwner) {
        var needsDecoderResync = false
        fun attach() {
            if (mediaPlayer.vlcVout.areViewsAttached()) return
            mediaPlayer.attachViews(layout, null, false, false)
            // Re-attaching alone isn't enough: the decoder lost its reference
            // frames along with the old surface, so it resumes on delta
            // frames with nothing to apply them to — grey, blocky, saturated
            // picture until the next keyframe. Seeking in place (precise, not
            // fast) flushes it and restarts decoding from the previous
            // keyframe, same as what scrubbing manually used to fix.
            if (needsDecoderResync) {
                needsDecoderResync = false
                val time = mediaPlayer.time
                if (time > 0) mediaPlayer.setTime(time, false)
            }
        }
        fun detach() {
            if (!mediaPlayer.vlcVout.areViewsAttached()) return
            needsDecoderResync = mediaPlayer.hasMedia()
            mediaPlayer.detachViews()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> attach()
                Lifecycle.Event.ON_STOP -> detach()
                else -> Unit
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) attach()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detach()
        }
    }
}
