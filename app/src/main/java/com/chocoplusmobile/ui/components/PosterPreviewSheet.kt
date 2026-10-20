package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Playback
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.icons.MetadataIconVector
import com.chocoplusmobile.ui.icons.MyListAddVector
import com.chocoplusmobile.ui.icons.MyListCheckVector
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text

/** Whichever image the tapped card was actually showing — special/normal/
 * license poster, vertical or horizontal — the preview enlarges *that*
 * image rather than re-deriving a different one. Mirrors the RN app's
 * PosterPreviewTarget. */
data class PosterPreviewTarget(val media: Media, val uri: String?, val ratio: Float, val title: String)

/**
 * Long-press quick actions for a poster in any selection row — mirrors the
 * RN app's components/PosterPreviewSheet.tsx. Enlarges the tapped artwork
 * and surfaces Lecture, the Ma liste toggle, and a metadata button, added
 * *alongside* the existing plain-tap navigation to MovieDetail/SerieDetail,
 * not replacing it.
 *
 * The metadata button fetches the full GET /movie/:id or /series/:id record
 * itself, lazily, only when pressed — "il y a énormément d'informations"
 * there is exactly why this isn't fetched up front for every card.
 */
@Composable
fun PosterPreviewSheet(target: PosterPreviewTarget?, onClose: () -> Unit) {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    var playing by remember { mutableStateOf(false) }
    var metaOpen by remember { mutableStateOf(false) }
    var metaLoading by remember { mutableStateOf(false) }
    var metaError by remember { mutableStateOf<String?>(null) }
    var metaFor by remember { mutableStateOf<Int?>(null) }
    var metaRecord by remember { mutableStateOf<Media?>(null) }

    val media = target?.media
    // Plain isInMyList() never subscribed this composable to My List changes
    // (a bare StateFlow.value read) — collectAsState() does, so toggling
    // actually flips the icon now instead of only the underlying data.
    val myListIds by appViewModel.myListIds.collectAsState()
    val inList = media?.let { it.id in myListIds } ?: false

    fun play() {
        if (media == null || playing) return
        playing = true
        scope.launch {
            try {
                val playerTarget = Playback.resolvePlayTarget(media)
                NavCache.pendingPlayerTarget = playerTarget
                onClose()
                navController.navigate("player")
            } catch (e: Exception) {
                // nothing playable yet — stay put, no error UI for a quick-preview button
            } finally {
                playing = false
            }
        }
    }

    fun openMeta() {
        if (media == null) return
        metaOpen = true
        if (metaFor == media.id) return // already fetched for this title
        metaLoading = true
        metaError = null
        scope.launch {
            try {
                val isSeries = media.mediaType == MediaType.SERIES
                val record = NetworkModule.safeCall {
                    if (isSeries) NetworkModule.api.getSeriesById(media.id) else NetworkModule.api.getMovieById(media.id)
                }
                metaRecord = record
                metaFor = media.id
            } catch (e: Exception) {
                metaError = e.message ?: Tr.NETWORK_ERROR.text()
            } finally {
                metaLoading = false
            }
        }
    }

    if (target != null) {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xC7000000))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ChocoColors.surfaceAlt)
                        .clickable(enabled = false) {}
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Poster(title = target.title, uri = target.uri, ratio = target.ratio, radius = 14.dp, titleSize = 15.sp, hideFallbackTitle = true)
                    Text(target.title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        PrimaryButton(label = t(Tr.PLAYBACK), icon = AppIcon.of("play_arrow"), onClick = ::play, loading = playing, height = 46.dp, modifier = Modifier.weight(1f))
                        SquareIconButton(
                            icon = if (inList) MyListCheckVector(ChocoColors.text) else MyListAddVector(ChocoColors.text),
                            size = 46.dp,
                            iconModifier = if (inList) Modifier.width(21.dp).height(15.dp) else Modifier.size(20.dp),
                            background = Color(0x1AFFFFFF),
                            onClick = { media?.let { appViewModel.toggleMyList(it.id) } },
                        )
                        SquareIconButton(icon = MetadataIconVector(ChocoColors.text), size = 46.dp, iconSize = 20.dp, background = Color(0x1AFFFFFF), onClick = ::openMeta)
                    }
                    Text(
                        t(Tr.CLOSE),
                        color = ChocoColors.textMuted,
                        fontFamily = ChocoFonts.bodySemiBold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(top = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    MediaMetadataSheet(
        visible = metaOpen,
        onClose = { metaOpen = false },
        loading = metaLoading,
        error = metaError,
        media = metaRecord,
        mediaType = media?.mediaType ?: MediaType.MOVIE,
    )
}
