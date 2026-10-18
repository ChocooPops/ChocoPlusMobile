@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.Episode
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Playback
import com.chocoplusmobile.api.WatchProgressStore
import com.chocoplusmobile.download.DownloadEntry
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.DownloadStatus
import com.chocoplusmobile.download.canDownload
import com.chocoplusmobile.ui.components.ChocoProgressBar
import com.chocoplusmobile.ui.components.DownloadButton
import com.chocoplusmobile.ui.components.rememberNotificationPermissionRequest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.UiSeason
import com.chocoplusmobile.navigation.CatalogueEntryFilter
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.ui.components.FadeInImage
import com.chocoplusmobile.ui.components.IconCircleButton
import com.chocoplusmobile.ui.components.LogoImage
import com.chocoplusmobile.ui.components.MediaMetadataSheet
import com.chocoplusmobile.ui.components.PersonCard
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.components.SquareIconButton
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.icons.MetadataIconVector
import com.chocoplusmobile.ui.icons.MyListAddVector
import com.chocoplusmobile.ui.icons.MyListCheckVector
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch

private enum class SerieTab { EPISODES, SIMILAR }

/** Mirrors the RN app's screens/Detail/SerieDetailScreen.tsx. */
@Composable
fun SerieDetailScreen() {
    val navController = LocalNavController.current
    val appViewModel = LocalAppViewModel.current
    val media = remember { NavCache.pendingMedia }

    if (media == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val prefs by appViewModel.prefs.collectAsState()
    val scope = rememberCoroutineScope()

    // First-open entrance animation: the whole page starts just below the
    // bottom edge and scrolls up into place — a one-shot effect on mount,
    // not tied to the LazyColumn's own scroll position.
    val enterOffset = remember { Animatable(160f) }
    LaunchedEffect(Unit) {
        enterOffset.animateTo(0f, animationSpec = tween(450, easing = FastOutSlowInEasing))
    }

    var ui by remember(media.id) { mutableStateOf(Adapters.mapMediaToUi(media)) }
    var similar by remember { mutableStateOf(listOf<Media>()) }
    var creditsLoading by remember { mutableStateOf(true) }
    var similarLoading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(SerieTab.EPISODES) }
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    val heroLogo = if (prefs.settings.mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.heroLogoUri(media) else null

    // media.seasons already carries the full list (id, name, poster) when
    // the source endpoint populates it — the picker renders from that.
    // Opens on the season last picked here while the app is running, else the
    // server's lastSeasonWatched (a season id), else the first season —
    // each only if it still matches one of this series' seasons.
    fun seasonNumberOf(seasonId: Int?): Int? = seasonId?.let { id -> ui.seasons?.find { it.id == id }?.seasonNumber }
    var season by remember(media.id) {
        mutableIntStateOf(
            seasonNumberOf(appViewModel.lastSeason(media.id))
                ?: seasonNumberOf(media.lastSeasonWatched)
                ?: ui.seasons?.firstOrNull()?.seasonNumber
                ?: 1
        )
    } // the season NUMBER shown/selected

    /** A season picked by hand — remembered for next time. */
    fun selectSeason(number: Int) {
        season = number
        ui.seasons?.find { it.seasonNumber == number }?.let { appViewModel.rememberLastSeason(media.id, it.id) }
    }
    var episodes by remember { mutableStateOf(listOf<Episode>()) }
    var episodesLoading by remember { mutableStateOf(true) }
    var episodesError by remember { mutableStateOf<String?>(null) }
    var resumeEpisode by remember { mutableStateOf<Episode?>(null) }

    // GET /series/episodes/:seriesId/:seasonId wants the season's real id
    // (e.g. 5000121), not its seasonNumber — resolved from the season list.
    val seasonIdForApi = ui.seasons?.find { it.seasonNumber == season }?.id ?: season

    LaunchedEffect(media.id) {
        creditsLoading = true
        similarLoading = true
        try {
            val info = NetworkModule.safeCall { NetworkModule.api.getMediaInfo(media.id) }
            ui = Adapters.applyMediaInfo(ui, info)
        } catch (e: Exception) { /* ignore */ } finally { creditsLoading = false }
        try {
            similar = NetworkModule.safeCall { NetworkModule.api.getSimilarTitles(media.id) }
        } catch (e: Exception) { /* ignore */ } finally { similarLoading = false }
        try {
            resumeEpisode = NetworkModule.safeCall { NetworkModule.api.getLastEpisodeWatched(media.id) }
        } catch (e: Exception) { /* ignore */ }
    }

    LaunchedEffect(media.id, seasonIdForApi) {
        episodesLoading = true
        episodesError = null
        try {
            episodes = NetworkModule.safeCall { NetworkModule.api.getEpisodes(media.id, seasonIdForApi) }
        } catch (e: Exception) {
            episodesError = e.message ?: "Erreur réseau"
        } finally {
            episodesLoading = false
        }
    }

    // See MovieDetailScreen's identical fix: isInMyList() alone is a plain
    // StateFlow.value read, invisible to Compose — collectAsState() is what
    // actually makes toggling My List update this icon.
    val myListIds by appViewModel.myListIds.collectAsState()
    val inList = media.id in myListIds
    var metaOpen by remember { mutableStateOf(false) }
    // Same lazy-fetch-full-record pattern as PosterPreviewSheet's own
    // metadata button — MediaMetadataSheet wants the full Media record
    // itself (every poster variant, background, logo, and every season's
    // full episode list), a separate, heavier endpoint from getMediaInfo
    // above, not worth loading before the button is even pressed.
    var metaLoading by remember { mutableStateOf(false) }
    var metaError by remember { mutableStateOf<String?>(null) }
    var metaFor by remember { mutableStateOf<Int?>(null) }
    var metaRecord by remember { mutableStateOf<Media?>(null) }

    fun openMeta() {
        metaOpen = true
        if (metaFor == media.id) return // already fetched for this title
        metaLoading = true
        metaError = null
        scope.launch {
            try {
                metaRecord = NetworkModule.safeCall { NetworkModule.api.getSeriesById(media.id) }
                metaFor = media.id
            } catch (e: Exception) {
                metaError = e.message ?: "Erreur réseau"
            } finally {
                metaLoading = false
            }
        }
    }

    // What the player recorded this session wins over the server values
    // fetched when this screen opened (see WatchProgressStore) — both for
    // each episode's progress bar and for which episode "Reprendre" picks.
    val localProgress by WatchProgressStore.state.collectAsState()
    fun Episode.withLocalProgress(): Episode = localProgress.episode(id)?.let { copy(watchProgress = it) } ?: this
    val shownEpisodes = episodes.map { it.withLocalProgress() }
    val lastLocalEpisode = localProgress.lastEpisodeBySeries[media.id]?.let { id -> shownEpisodes.find { it.id == id } }
    val nextUp = lastLocalEpisode ?: resumeEpisode?.withLocalProgress() ?: shownEpisodes.firstOrNull()

    // Download controls (per episode + whole season) — ADMIN/FAMILY only.
    val user by appViewModel.user.collectAsState()
    val canDownload = user?.role.canDownload()
    val downloads by DownloadManager.entries.collectAsState()

    fun playEpisode(episode: Episode) {
        NavCache.pendingPlayerTarget = com.chocoplusmobile.api.PlayerTarget.EpisodeTarget(
            seasonId = episode.seasonId,
            episodeId = episode.id,
            seriesId = media.id,
            title = "${media.title} · ${episode.name}",
            posterUrl = Adapters.episodePosterUri(episode),
            progressPercent = episode.watchProgress,
        )
        navController.navigate("player")
    }

    // Opens another title from "Titres similaires" — replaces the current
    // detail screen in the back stack instead of stacking on top of it, so
    // chaining through several similar titles still only takes one back
    // tap to get back to wherever the user actually came from (Browse,
    // Catalogue, …), not one tap per title visited along the way.
    fun openMedia(m: Media) {
        NavCache.pendingMedia = m
        val current = navController.currentBackStackEntry?.destination?.route
        navController.navigate(if (m.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail") {
            if (current != null) popUpTo(current) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun openCatalogueFor(entry: CatalogueEntryFilter) {
        NavCache.pendingCatalogueFilter = entry
        navController.navigate("catalogue")
    }

    Box(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        LazyColumn(modifier = Modifier.fillMaxSize().offset(y = enterOffset.value.dp)) {
            item {
                Column {
                    // Same fix as BrowseScreen's/MovieDetailScreen's hero: a
                    // fixed aspect ratio makes the box taller than the
                    // actual landscape screen, pushing the bottom-aligned
                    // logo below the fold — capping the height to the real
                    // viewport in landscape keeps it inside the first
                    // visible screen instead.
                    val heroModifier = if (isLandscape) {
                        Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(16f / 11f)
                    }
                    Box(modifier = heroModifier) {
                        // Poster (used before) bakes its own fixed aspectRatio
                        // (16:11) into every instance regardless of the caller's
                        // modifier — harmless in portrait where the Box above
                        // already matches that same ratio, but once the Box's
                        // height is capped to the real screen in landscape (a
                        // different, wider ratio), Poster kept demanding 16:11
                        // anyway and overflowed past the Box's actual bounds.
                        // FadeInImage has no such opinion — it just crops to
                        // fill whatever size it's actually given.
                        val heroBackdrop = Adapters.heroBackdropUri(media)
                        if (heroBackdrop != null) {
                            FadeInImage(uri = heroBackdrop, title = ui.title, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(ChocoColors.surfaceAlt))
                        }
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 0.75f to Color(0x4D141414), 1f to ChocoColors.surface)))
                        IconCircleButton(
                            icon = AppIcon.of("arrow_back"),
                            size = 38.dp,
                            iconSize = 22.dp,
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, top = 8.dp),
                        )
                        Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = if (isLandscape) 36.dp else 16.dp, end = 16.dp, bottom = if (isLandscape) 56.dp else 22.dp)) {
                            if (heroLogo != null) {
                                LogoImage(uri = heroLogo, height = if (isLandscape) 108.dp else 80.dp, maxWidth = if (isLandscape) 340.dp else 260.dp)
                            } else {
                                Text(ui.title.uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = if (isLandscape) 42.sp else 34.sp, lineHeight = if (isLandscape) 42.sp else 34.sp, letterSpacing = (-1.2).sp)
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
                        ui.year?.let { Text(it.toString(), color = Color(0x8CFFFFFF), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp) }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            PrimaryButton(
                                label = if (nextUp != null) "S$season · E${nextUp.episodeNumber}" else "Lecture",
                                icon = AppIcon.of("play_arrow"),
                                onClick = { nextUp?.let(::playEpisode) },
                                enabled = nextUp != null,
                                height = 48.dp,
                                modifier = Modifier.weight(1f),
                            )
                            SquareIconButton(
                                icon = if (inList) MyListCheckVector(ChocoColors.text) else MyListAddVector(ChocoColors.text),
                                size = 48.dp,
                                iconModifier = if (inList) Modifier.width(21.dp).height(15.dp) else Modifier.size(20.dp),
                                onClick = { appViewModel.toggleMyList(media.id) },
                            )
                            SquareIconButton(icon = MetadataIconVector(ChocoColors.text), size = 48.dp, iconSize = 20.dp, onClick = ::openMeta)
                        }

                        if (ui.synopsis?.isNotEmpty() == true) {
                            Text(ui.synopsis!!, color = Color(0xB8FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 13.5.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 18.dp))
                        }

                        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (ui.genres.isNotEmpty()) {
                                DetailMetaChipRow("Genres", ui.genres, onClick = { tag -> tag.id?.let { openCatalogueFor(CatalogueEntryFilter.Category(it)) } })
                            }
                            if (ui.keywords.isNotEmpty()) {
                                DetailMetaChipRow("Mots-clés", ui.keywords, dim = true, onClick = { tag -> openCatalogueFor(CatalogueEntryFilter.Keyword(tag.name)) })
                            }
                        }
                    }

                    if (creditsLoading) {
                        CircularProgressIndicator(color = ChocoColors.accent, modifier = Modifier.padding(top = 24.dp).padding(horizontal = 16.dp))
                    } else {
                        if (ui.cast.isNotEmpty()) {
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                Text("Distribution", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(ui.cast.size) { i ->
                                        val p = ui.cast[i]
                                        PersonCard(
                                            name = p.name,
                                            role = p.role,
                                            photoUrl = p.photoUrl,
                                            modifier = Modifier.clickable { openCatalogueFor(CatalogueEntryFilter.Staff(p.job, Adapters.humanizeJob(p.job), p.id, p.name)) },
                                        )
                                    }
                                }
                            }
                        }
                        if (ui.crew.isNotEmpty()) {
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                Text("Équipe technique", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(ui.crew.size) { i ->
                                        val p = ui.crew[i]
                                        PersonCard(
                                            name = p.name,
                                            role = p.role,
                                            photoUrl = p.photoUrl,
                                            modifier = Modifier.clickable { openCatalogueFor(CatalogueEntryFilter.Staff(p.job, Adapters.humanizeJob(p.job), p.id, p.name)) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.padding(top = 22.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        DetailTabButton("Épisodes", tab == SerieTab.EPISODES) { tab = SerieTab.EPISODES }
                        DetailTabButton("Titres similaires", tab == SerieTab.SIMILAR) { tab = SerieTab.SIMILAR }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ChocoColors.divider))

                    if (tab == SerieTab.EPISODES) {
                        val seasons = ui.seasons ?: emptyList()
                        if (seasons.isNotEmpty()) {
                            // Start scrolled to the selected season, which may
                            // be far down the row (see `season` above).
                            val seasonRowState = rememberLazyListState(
                                initialFirstVisibleItemIndex = seasons.indexOfFirst { it.seasonNumber == season }.coerceAtLeast(0),
                            )
                            LazyRow(state = seasonRowState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp, bottom = 15.dp)) {
                                items(seasons.size) { i ->
                                    val s = seasons[i]
                                    val active = s.seasonNumber == season
                                    Column(modifier = Modifier.width(88.dp).clickable { selectSeason(s.seasonNumber) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Poster(
                                            title = s.name,
                                            uri = s.posterUrl,
                                            ratio = PosterRatio.R2_3,
                                            radius = 4.dp,
                                            titleSize = 9.sp,
                                            modifier = if (active) Modifier.background(Color.Transparent) else Modifier,
                                        )
                                        Text(s.name, color = if (active) ChocoColors.accent else Color(0xB3FFFFFF), fontFamily = ChocoFonts.bodySemiBold, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                IconCircleButton(icon = AppIcon.of("chevron_left"), size = 34.dp, iconSize = 20.dp, background = Color(0x12FFFFFF), onClick = { selectSeason((season - 1).coerceAtLeast(1)) })
                                Text("Saison $season", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 15.sp, modifier = Modifier.width(90.dp).padding(horizontal = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                IconCircleButton(icon = AppIcon.of("chevron_right"), size = 34.dp, iconSize = 20.dp, background = Color(0x12FFFFFF), onClick = { selectSeason(season + 1) })
                            }
                        }
                    }
                }
            }

            if (tab == SerieTab.EPISODES) {
                if (episodesLoading) {
                    item { CircularProgressIndicator(color = ChocoColors.accent, modifier = Modifier.padding(top = 30.dp).padding(horizontal = 16.dp)) }
                } else if (episodes.isEmpty()) {
                    item {
                        Text(
                            episodesError ?: "Aucun épisode pour cette saison.",
                            color = ChocoColors.textMuted,
                            fontFamily = ChocoFonts.bodyRegular,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        )
                    }
                } else {
                    if (canDownload) {
                        item {
                            SeasonDownloadRow(
                                episodes = shownEpisodes,
                                downloads = shownEpisodes.mapNotNull { downloads[DownloadManager.episodeKey(it.id)] },
                            )
                        }
                    }
                    items(shownEpisodes.size) { idx ->
                        val episode = shownEpisodes[idx]
                        EpisodeRow(
                            episode = episode,
                            onClick = { playEpisode(episode) },
                            download = downloads[DownloadManager.episodeKey(episode.id)],
                            canDownload = canDownload,
                        )
                    }
                }
            } else {
                if (similarLoading) {
                    item { CircularProgressIndicator(color = ChocoColors.accent, modifier = Modifier.padding(top = 30.dp).padding(horizontal = 16.dp)) }
                } else if (similar.isEmpty()) {
                    item {
                        Text("Aucun titre similaire pour le moment.", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp))
                    }
                } else {
                    item {
                        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            similar.forEach { m ->
                                val simUi = Adapters.mapMediaToUi(m)
                                if (!isLandscape) {
                                    Poster(title = simUi.title, uri = simUi.posterUrl, ratio = PosterRatio.R2_3, radius = 8.dp, titleSize = 10.5.sp, modifier = Modifier.fillMaxWidth(0.314f).clickable { openMedia(m) })
                                } else {
                                    val cardLogo = if (prefs.settings.mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.logoUri(m) else null
                                    Poster(title = simUi.title, uri = simUi.backdropUrl, ratio = PosterRatio.R16_9, radius = 8.dp, titleSize = 11.sp, modifier = Modifier.fillMaxWidth(0.314f).clickable { openMedia(m) }) {
                                        Column(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp, start = 14.dp), verticalArrangement = Arrangement.Bottom) {
                                            if (cardLogo != null) LogoImage(uri = cardLogo, height = 60.dp, maxWidth = 120.dp)
                                            else Text(simUi.title.uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, maxLines = 2)
                                            if (simUi.category.isNotEmpty()) Text(simUi.category, color = Color(0x99FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 8.5.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Box(modifier = Modifier.height(30.dp)) }
        }
    }

    MediaMetadataSheet(visible = metaOpen, onClose = { metaOpen = false }, loading = metaLoading, error = metaError, media = metaRecord, mediaType = MediaType.SERIES)
}

@Composable
private fun DetailTabButton(label: String, active: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick).padding(bottom = 11.dp)) {
        Text(label, color = if (active) ChocoColors.text else ChocoColors.textFaint, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp)
        if (active) Box(modifier = Modifier.padding(top = 2.dp).height(3.dp).width(8.dp * label.length).background(ChocoColors.accent))
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit, download: DownloadEntry?, canDownload: Boolean) {
    val eUi = remember(episode) { Adapters.mapEpisodeToUi(episode) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${eUi.number}", color = ChocoColors.textFaint, fontFamily = ChocoFonts.headingExtraBold, fontSize = 14.sp, modifier = Modifier.width(24.dp).padding(top = 32.dp))
        Poster(title = eUi.title, uri = eUi.posterUrl, ratio = PosterRatio.R16_9, radius = 4.dp, titleSize = 8.5.sp, progress = eUi.progress.takeIf { it > 0 }, modifier = Modifier.width(152.dp)) {
            if (eUi.progress >= 100) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(17.dp).background(ChocoColors.accent, androidx.compose.foundation.shape.RoundedCornerShape(99.dp)), contentAlignment = Alignment.Center) {
                    Icon(AppIcon.of("check"), contentDescription = null, tint = ChocoColors.onAccent, modifier = Modifier.size(12.dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(eUi.title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 13.5.sp, lineHeight = 17.sp, maxLines = 2)
            Text(eUi.description, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, lineHeight = 15.5.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            val meta = listOfNotNull(eUi.duration.ifBlank { null }, eUi.airDate.ifBlank { null }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodySemiBold, fontSize = 10.5.sp, modifier = Modifier.padding(top = 5.dp))
        }
        if (canDownload) {
            DownloadButton(
                entry = download,
                onStart = { DownloadManager.downloadEpisode(episode) },
                size = 38.dp,
                iconSize = 21.dp,
                bordered = false,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
    }
}

/**
 * "Download the whole season" in one tap, above the episode list — also
 * shows the season's overall state: how many episodes are done, aggregate
 * progress while some are downloading, and lets the whole batch be
 * cancelled or deleted at once (each episode row keeps its own control too).
 */
@Composable
private fun SeasonDownloadRow(episodes: List<Episode>, downloads: List<DownloadEntry>) {
    var confirm by remember { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()
    val done = downloads.count { it.status == DownloadStatus.COMPLETED }
    val active = downloads.filter { it.isActive }
    val allDone = done == episodes.size
    val remaining = episodes.size - done - active.size

    val (icon, label) = when {
        active.isNotEmpty() -> "close" to "Téléchargement de la saison… $done/${episodes.size}"
        allDone -> "download_done" to "Saison téléchargée"
        done > 0 -> "download" to "Télécharger les $remaining épisodes restants"
        else -> "download" to "Télécharger la saison (${episodes.size} épisode${if (episodes.size > 1) "s" else ""})"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 6.dp)
            .background(Color(0x0FFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable {
                if (active.isEmpty() && !allDone) {
                    requestNotifications()
                    DownloadManager.downloadEpisodes(episodes)
                } else {
                    confirm = true
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcon.of(icon), contentDescription = null, tint = if (allDone) ChocoColors.accent else ChocoColors.text, modifier = Modifier.size(20.dp))
            Text(label, color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 12.5.sp)
        }
        if (active.isNotEmpty()) {
            val known = active.filter { it.totalBytes > 0 }
            // Finished episodes count as 100 %, queued ones as 0 %.
            val percent = (done * 100.0 + known.sumOf { (it.progressPercent ?: 0).toDouble() }) / episodes.size
            ChocoProgressBar(progress = percent, modifier = Modifier.fillMaxWidth().padding(top = 9.dp))
        }
    }

    if (confirm) {
        val cancelling = active.isNotEmpty()
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = ChocoColors.surfaceAlt,
            title = {
                Text(
                    if (cancelling) "Annuler les téléchargements ?" else "Supprimer la saison ?",
                    color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 17.sp,
                )
            },
            text = {
                Text(
                    if (cancelling) "Les ${active.size} épisode${if (active.size > 1) "s" else ""} en cours ou en attente seront annulés. Les épisodes déjà téléchargés sont conservés."
                    else "Les $done épisodes téléchargés de cette saison ne seront plus disponibles hors ligne.",
                    color = Color(0xB3FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    (if (cancelling) active else downloads).forEach { DownloadManager.remove(it.key) }
                    confirm = false
                }) {
                    Text(if (cancelling) "Annuler les téléchargements" else "Supprimer", color = Color(0xFFFF6B6B), fontFamily = ChocoFonts.bodyBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text("Fermer", color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold)
                }
            },
        )
    }
}
