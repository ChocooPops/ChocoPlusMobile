package com.chocoplusmobile.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clip
import com.chocoplusmobile.ui.components.ChocoChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.PlayerTarget
import com.chocoplusmobile.api.WatchProgressStore
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.PosterFormatUtils
import com.chocoplusmobile.download.DownloadEntry
import com.chocoplusmobile.download.DownloadKind
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.DownloadStatus
import com.chocoplusmobile.download.canDownload
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.screens.mylist.MediaCollection
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.ui.components.ChocoBottomSheet
import com.chocoplusmobile.ui.components.ChocoProgressBar
import com.chocoplusmobile.ui.components.DownloadButton
import com.chocoplusmobile.ui.components.EmptyState
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.components.formatBytes
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/** One title on the Downloads screen: a movie, or a series with however
 * many of its episodes are downloaded (or downloading). */
private data class DownloadedTitle(val media: Media, val kind: DownloadKind, val entries: List<DownloadEntry>)

/**
 * Every downloaded movie/episode, laid out exactly like Ma liste (same
 * shared MediaCollection, same format/alignment toggles) — but built
 * entirely from DownloadManager's local index and files, no network at all,
 * so it's also the screen the app opens on when started offline. Tapping a
 * title opens a sheet with its video(s): play, progress, cancel, delete.
 */
@Composable
fun DownloadsScreen() {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val offline by appViewModel.isOffline.collectAsState()
    val offlineSession by appViewModel.offlineSession.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val entries by DownloadManager.entries.collectAsState()
    val format = prefs.formatByPage.mylist
    val alignment = prefs.alignmentByPage.current(FormatPage.MYLIST, format)
    val cols = PosterFormatUtils.gridColumns(format, alignment)

    // Defensive — the entry points are already hidden for other roles.
    // The manual offline session has no account at all: reaching it at all
    // requires downloads on this device, which only ADMIN/FAMILY can make.
    if (!offlineSession && !user?.role.canDownload()) return

    // Entries whose metadata step hasn't run yet have no title/artwork to
    // show — they appear as soon as it has (a second or two in).
    val titles = remember(entries) {
        entries.values
            .filter { it.media != null }
            .groupBy { it.kind to it.mediaId }
            .map { (k, list) -> DownloadedTitle(list.first().media!!, k.first, list) }
            .sortedByDescending { t -> t.entries.maxOf { it.createdAt } }
    }
    val totalBytes = entries.values.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.totalBytes.coerceAtLeast(0) }
    var openedKey by remember { mutableStateOf<Pair<DownloadKind, Int>?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val opened = titles.firstOrNull { it.kind to it.media.id == openedKey }

    fun play(entry: DownloadEntry) {
        val media = entry.media ?: return
        NavCache.pendingPlayerTarget = when (entry.kind) {
            DownloadKind.MOVIE -> PlayerTarget.MovieTarget(
                movieId = entry.mediaId,
                title = media.title,
                posterUrl = Adapters.posterUri(media),
                progressPercent = media.watchProgress,
            )
            DownloadKind.EPISODE -> {
                val ep = entry.episode ?: return
                PlayerTarget.EpisodeTarget(
                    seasonId = ep.seasonId,
                    episodeId = ep.id,
                    seriesId = entry.mediaId,
                    title = "${media.title} · ${ep.name}",
                    posterUrl = Adapters.episodePosterUri(ep),
                    progressPercent = ep.watchProgress,
                )
            }
        }
        openedKey = null
        navController.navigate("player")
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        if (offlineSession) {
            OfflineSessionHeader(onExit = appViewModel::exitOfflineSession)
        } else TopBar(
            onSearch = { navController.navigate("search") },
            onToggleFormat = { appViewModel.toggleFormat(FormatPage.MYLIST) },
            formatIsVertical = format == PosterFormat.V,
            onToggleAlignment = { appViewModel.toggleAlignment(FormatPage.MYLIST, format) },
            alignmentIcon = PosterFormatUtils.nextAlignmentIcon(alignment),
            onAvatar = { navController.navigate("profile") },
            avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
            avatarUri = user?.profilPhoto,
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Téléchargements", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 24.sp, letterSpacing = (-0.6).sp)
                if (entries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmDeleteAll = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(AppIcon.of("delete_outline"), contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                        Text("Tout supprimer", color = Color(0xFFFF6B6B), fontFamily = ChocoFonts.bodyBold, fontSize = 12.sp)
                    }
                }
            }
            Text(
                "${titles.size} titre${if (titles.size > 1) "s" else ""}" + (if (totalBytes > 0) " · ${formatBytes(totalBytes)}" else "") + " · disponible hors ligne",
                color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp),
            )

            if (offline && !offlineSession) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).background(Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(AppIcon.of("wifi_off"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(20.dp))
                    Text("Mode hors ligne — seuls tes téléchargements sont disponibles.", color = ChocoColors.text, fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp)
                }
            }

            MediaCollection(
                items = titles.map { it.media },
                format = format,
                cols = cols,
                mediaLabelStyle = prefs.settings.mediaLabelStyle,
                onOpen = { m -> openedKey = titles.first { it.media === m }.let { it.kind to it.media.id } },
            )
            if (titles.isEmpty()) {
                EmptyState(
                    icon = AppIcon.of("download_for_offline"),
                    title = "Aucun téléchargement",
                    description = "Télécharge un film ou des épisodes depuis leur fiche pour les regarder sans connexion.",
                    ctaLabel = if (offline) null else "Parcourir le catalogue",
                    onPressCta = if (offline) null else ({ navController.navigate("catalogue") }),
                )
            }
        }
    }

    if (confirmDeleteAll) {
        val activeCount = entries.values.count { it.isActive }
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            containerColor = ChocoColors.surfaceAlt,
            title = { Text("Tout supprimer ?", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 17.sp) },
            text = {
                Text(
                    "Les ${titles.size} titre${if (titles.size > 1) "s" else ""} téléchargé${if (titles.size > 1) "s" else ""} ne seront plus disponibles hors ligne" +
                        (if (totalBytes > 0) " (${formatBytes(totalBytes)} libérés)" else "") + "." +
                        (if (activeCount > 0) " Les $activeCount téléchargement${if (activeCount > 1) "s" else ""} en cours ser${if (activeCount > 1) "ont" else "a"} annulé${if (activeCount > 1) "s" else ""}." else ""),
                    color = Color(0xB3FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openedKey = null
                    DownloadManager.removeAll()
                    confirmDeleteAll = false
                }) {
                    Text("Tout supprimer", color = Color(0xFFFF6B6B), fontFamily = ChocoFonts.bodyBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text("Annuler", color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold)
                }
            },
        )
    }

    ChocoBottomSheet(visible = opened != null, onClose = { openedKey = null }, maxHeightPct = 0.8f) {
        if (opened != null) DownloadedTitleSheet(title = opened, onPlay = ::play)
    }
}

/** Header of the manual offline session — no search/profile (both need
 * the backend), just the way back to the normal login/start flow. */
@Composable
private fun OfflineSessionHeader(onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcon.of("wifi_off"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(20.dp))
            Text("Hors ligne", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 15.sp)
        }
        Text(
            "Se connecter",
            color = ChocoColors.accent,
            fontFamily = ChocoFonts.bodyBold,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onExit).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DownloadedTitleSheet(title: DownloadedTitle, onPlay: (DownloadEntry) -> Unit) {
    val localProgress by WatchProgressStore.state.collectAsState()
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(title.media.title, color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 18.sp)
        if (title.kind == DownloadKind.MOVIE) {
            val entry = title.entries.first()
            val progress = localProgress.movie(title.media.id) ?: title.media.watchProgress
            DownloadedVideoRow(
                label = "Film",
                meta = Adapters.mapMediaToUi(title.media).duration,
                imageUri = Adapters.backdropUri(title.media),
                entry = entry,
                watchProgress = progress,
                onPlay = { onPlay(entry) },
            )
        } else {
            // Only seasons with at least one downloaded episode get a tab;
            // the list below shows just the selected season's episodes.
            val seasons = title.media.seasons.orEmpty()
            val bySeason = title.entries
                .groupBy { it.seasonId ?: it.episode?.seasonId }
                .toList()
                .sortedBy { (seasonId, _) -> seasons.firstOrNull { it.id == seasonId }?.seasonNumber ?: Int.MAX_VALUE }
            var selectedSeasonId by remember(title.media.id) { mutableStateOf(bySeason.firstOrNull()?.first) }
            // A season can vanish under us (its last episode deleted) —
            // fall back to the first one still there.
            val current = bySeason.firstOrNull { it.first == selectedSeasonId } ?: bySeason.firstOrNull()

            if (bySeason.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 16.dp)) {
                    items(bySeason.size) { idx ->
                        val (seasonId, list) = bySeason[idx]
                        val season = seasons.firstOrNull { it.id == seasonId }
                        val label = season?.let { "S${it.seasonNumber}" } ?: "Saison ${idx + 1}"
                        ChocoChip(label = "$label · ${list.size}", active = seasonId == current?.first, onClick = { selectedSeasonId = seasonId }, small = true)
                    }
                }
            }
            current?.let { (seasonId, list) ->
                val season = seasons.firstOrNull { it.id == seasonId }
                Text(
                    (season?.name?.ifBlank { null } ?: season?.let { "Saison ${it.seasonNumber}" } ?: "Épisodes").uppercase(),
                    color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
                list.sortedBy { it.episode?.episodeNumber ?: 0 }.forEach { entry ->
                    val ep = entry.episode
                    DownloadedVideoRow(
                        label = ep?.let { "${it.episodeNumber}. ${it.name}" } ?: "Épisode",
                        meta = ep?.let { Adapters.mapEpisodeToUi(it).duration },
                        imageUri = ep?.let { Adapters.episodePosterUri(it) },
                        entry = entry,
                        watchProgress = ep?.let { localProgress.episode(it.id) ?: it.watchProgress },
                        onPlay = { onPlay(entry) },
                    )
                }
            }
        }
    }
}

/** One video in the sheet — thumbnail (tap to play once downloaded),
 * title, then either watch progress + size or download progress, and the
 * shared DownloadButton for cancel/delete/retry. */
@Composable
private fun DownloadedVideoRow(label: String, meta: String?, imageUri: String?, entry: DownloadEntry, watchProgress: Double?, onPlay: () -> Unit) {
    val ready = entry.status == DownloadStatus.COMPLETED
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = ready, onClick = onPlay).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(120.dp)) {
            Poster(title = label, uri = imageUri, ratio = PosterRatio.R16_9, radius = 6.dp, titleSize = 8.sp, progress = watchProgress?.takeIf { it > 0 }, modifier = Modifier.fillMaxWidth()) {
                if (ready) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(32.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(AppIcon.of("play_arrow"), contentDescription = "Lire", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
            val status = when (entry.status) {
                DownloadStatus.COMPLETED -> listOfNotNull(meta?.ifBlank { null }, entry.totalBytes.takeIf { it > 0 }?.let(::formatBytes)).joinToString(" · ")
                DownloadStatus.QUEUED -> "En attente…"
                DownloadStatus.DOWNLOADING -> entry.progressPercent?.let { "Téléchargement… $it %" } ?: "Téléchargement…"
                DownloadStatus.FAILED -> "Échec du téléchargement"
                DownloadStatus.PAUSED -> entry.progressPercent?.let { "En pause · $it %" } ?: "En pause"
            }
            if (status.isNotEmpty()) {
                Text(
                    status,
                    color = if (entry.status == DownloadStatus.FAILED) Color(0xFFFF6B6B) else ChocoColors.textFaint,
                    fontFamily = ChocoFonts.bodySemiBold, fontSize = 10.5.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (entry.status == DownloadStatus.DOWNLOADING) {
                ChocoProgressBar(progress = (entry.progressPercent ?: 0).toDouble(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        DownloadButton(entry = entry, onStart = {}, size = 38.dp, iconSize = 21.dp, bordered = false)
    }
}
