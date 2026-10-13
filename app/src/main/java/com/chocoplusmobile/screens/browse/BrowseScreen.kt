package com.chocoplusmobile.screens.browse

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.R
import com.chocoplusmobile.api.AuthTokenHolder
import com.chocoplusmobile.api.BrowseContent
import com.chocoplusmobile.api.ContentCache
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Playback
import com.chocoplusmobile.api.SelectionType
import com.chocoplusmobile.api.Stream
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.ImageCategory
import com.chocoplusmobile.data.ImageQuality
import com.chocoplusmobile.data.UiMediaType
import com.chocoplusmobile.navigation.CatalogueEntryFilter
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.screens.home.HorizontalCardOverlay
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.FadeInImage
import com.chocoplusmobile.ui.components.IconCircleButton
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.LogoImage
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterPreviewSheet
import com.chocoplusmobile.ui.components.PosterPreviewTarget
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.PosterRow
import com.chocoplusmobile.ui.components.SectionHeader
import com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.components.VlcVideoSurface
import com.chocoplusmobile.ui.components.longPressable
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.icons.CutStreamVector
import com.chocoplusmobile.ui.icons.DetailIconVector
import com.chocoplusmobile.ui.icons.RestreamVector
import com.chocoplusmobile.ui.icons.VolumeMuteVector
import com.chocoplusmobile.ui.icons.VolumeVector
import com.chocoplusmobile.ui.rememberApiData
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import com.chocoplusmobile.video.VlcPlayer
import com.chocoplusmobile.video.rememberVlcPlayer
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer

private data class FilteredRow(val id: Int, val title: String, val isSpecial: Boolean, val items: List<Media>, val createFrom: MediaType?)

/** Mirrors the RN app's screens/Browse/BrowseScreen.tsx (shared by
 * FilmsScreen/SeriesScreen there via a `mediaType` prop, same here). */
@Composable
fun BrowseScreen(mediaType: MediaType) {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val isFilms = mediaType == MediaType.MOVIE
    val formatPage = if (isFilms) FormatPage.FILMS else FormatPage.SERIES
    val format = prefs.formatByPage.get(formatPage)
    var videoOn by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    // Settings > "Vidéo tournante automatique" — seeds the hero's initial
    // autoplay state once prefs are confirmed hydrated (a fresh
    // `remember { mutableStateOf(true) }` above would otherwise always win
    // the race against the async DataStore read). Only seeds it once: from
    // then on videoOn is free to flip on its own (video ends, errors out) or
    // via the hero's own toggle button without fighting the setting.
    val prefsHydrated by appViewModel.prefsHydrated.collectAsState()
    var autoplaySeeded by remember { mutableStateOf(false) }
    LaunchedEffect(prefsHydrated) {
        if (prefsHydrated && !autoplaySeeded) {
            autoplaySeeded = true
            videoOn = prefs.settings.toggles.autoplay
        }
    }

    // ContentCache.films/.series survive leaving/re-entering this composable
    // (a tab switch away and back), unlike rememberApiData's own state — see
    // HomeScreen's identical fix for the full reasoning. rawRefresh alone
    // would just hand back the same cached value, so retry/pull-to-refresh
    // goes through `refresh` below instead, which clears the slot first.
    val (state, rawRefresh) = rememberApiData(mediaType) {
        (if (isFilms) ContentCache.films else ContentCache.series) ?: BrowseContent(
            hero = NetworkModule.safeCall { if (isFilms) NetworkModule.api.getRandomMovie() else NetworkModule.api.getRandomSeries() },
            running = try {
                NetworkModule.safeCall { if (isFilms) NetworkModule.api.getNewsVideoRunningMovies() else NetworkModule.api.getNewsVideoRunningSeries() }
            } catch (e: Exception) {
                null
            },
            selections = NetworkModule.safeCall { NetworkModule.api.getRandomMediaSelectionByType(mediaType.name) },
        ).also { if (isFilms) ContentCache.films = it else ContentCache.series = it }
    }
    fun refresh() {
        if (isFilms) ContentCache.films = null else ContentCache.series = null
        rawRefresh()
    }

    fun openMedia(media: Media) {
        NavCache.pendingMedia = media
        navController.navigate(if (media.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    // "Tout voir" (SectionHeader's chevron) on a selection row — see
    // CatalogueEntryFilter.forSelection, shared with Home/LicenseDetail's
    // own selection rows. These are always createFrom = CATEGORY here
    // (confirmed against createMediaSelectionsByTypeFromCategoryByCount),
    // resolving to a CategorySeeAll (SHUFFLE, filtered on that category id).
    fun seeAllSelection(row: FilteredRow) {
        val entry = CatalogueEntryFilter.forSelection(row.id, row.title, row.createFrom) ?: return
        NavCache.pendingCatalogueFilter = entry
        navController.navigate("catalogue")
    }

    fun play(media: Media) {
        scope.launch {
            try {
                NavCache.pendingPlayerTarget = Playback.resolvePlayTarget(media)
                navController.navigate("player")
            } catch (e: Exception) {
                // ignore — nothing to play for this title yet
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        when {
            state.loading -> {
                LoadingState(modifier = Modifier.padding(top = 90.dp))
                TopBar(onSearch = { navController.navigate("search") }, onAvatar = { navController.navigate("profile") }, avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?", avatarUri = user?.profilPhoto)
            }
            state.error != null -> {
                ErrorState(message = state.error!!, onRetry = ::refresh, modifier = Modifier.padding(top = 90.dp))
                TopBar(onSearch = { navController.navigate("search") }, onAvatar = { navController.navigate("profile") }, avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?", avatarUri = user?.profilPhoto)
            }
            else -> {
                val data = state.data!!
                val heroMedia = data.running?.media ?: data.hero
                val heroImage = ImageQuality.withSize(data.running?.srcBackground, ImageCategory.HERO_BACKDROP) ?: Adapters.heroBackdropUri(heroMedia)
                val heroLogo = if (prefs.settings.mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.heroLogoUri(heroMedia) else null
                val newsId = data.running?.id
                val authToken = AuthTokenHolder.token
                val heroStreamUrl = if (newsId != null && videoOn && authToken != null) Stream.newsUrl(newsId, authToken) else null

                val filteredRows = remember(data.selections) {
                    data.selections.map { s ->
                        FilteredRow(
                            id = s.id,
                            title = s.name,
                            isSpecial = s.selectionType == SelectionType.SPECIAL_POSTER,
                            items = s.mediaList,
                            createFrom = s.createFrom,
                        )
                    }.filter { it.items.isNotEmpty() }
                }

                val vlc = rememberVlcPlayer()
                val player = vlc.mediaPlayer
                val videoDecoding = prefs.settings.videoDecoding
                // Stays false from the moment a stream starts loading until
                // libVLC actually starts producing frames — BrowseHero shows
                // the still backdrop (+ logo) the whole time, so there's
                // never a black flash of an empty video surface while the
                // stream buffers.
                var heroVideoReady by remember { mutableStateOf(false) }
                LaunchedEffect(heroStreamUrl, videoDecoding) {
                    if (heroStreamUrl != null) {
                        heroVideoReady = false
                        val media = VlcPlayer.media(vlc.libVLC, heroStreamUrl, videoDecoding)
                        player.media = media
                        media.release()
                        player.play()
                    } else {
                        player.stop()
                    }
                }
                LaunchedEffect(muted) { player.setVolume(if (muted) 0 else 100) }
                DisposableEffect(player) {
                    val listener = MediaPlayer.EventListener { event ->
                        when (event.type) {
                            MediaPlayer.Event.Playing -> heroVideoReady = true
                            MediaPlayer.Event.EncounteredError -> {
                                // Not the real player, just a background teaser —
                                // a stream that fails to load just falls back to
                                // the still background below instead of showing
                                // a broken video area.
                                Log.w("BrowseScreen", "hero stream error url=$heroStreamUrl")
                                videoOn = false
                            }
                            MediaPlayer.Event.EndReached -> {
                                // Plays once, then stops — falls back to the
                                // still backdrop below instead of looping.
                                videoOn = false
                            }
                        }
                    }
                    player.setEventListener(listener)
                    onDispose { player.setEventListener(null) }
                }

                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Column {
                            BrowseHero(
                                heroMedia = heroMedia,
                                heroImage = heroImage,
                                heroLogo = heroLogo,
                                heroStreamUrl = heroStreamUrl,
                                heroVideoReady = heroVideoReady,
                                newsId = newsId,
                                videoOn = videoOn,
                                muted = muted,
                                player = player,
                                onToggleVideoOn = { videoOn = it },
                                onToggleMuted = { muted = !muted },
                                onOpenMedia = ::openMedia,
                                onPlay = ::play,
                                isLandscape = isLandscape,
                            )
                            if (filteredRows.isEmpty()) {
                                ErrorState(message = "Aucun contenu à afficher pour le moment.", onRetry = ::refresh)
                            }
                        }
                    }
                    items(filteredRows.size) { idx ->
                        BrowseRow(row = filteredRows[idx], format = format, onOpenMedia = ::openMedia, onSeeAll = { seeAllSelection(filteredRows[idx]) })
                    }
                    item { Box(modifier = Modifier.height(TAB_BAR_CLEARANCE)) }
                }
                TopBar(
                    overlay = true,
                    listState = listState,
                    onSearch = { navController.navigate("search") },
                    onToggleFormat = { appViewModel.toggleFormat(formatPage) },
                    formatIsVertical = format == PosterFormat.V,
                    onAvatar = { navController.navigate("profile") },
                    avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
                    avatarUri = user?.profilPhoto,
                )
            }
        }
    }
}

@Composable
private fun BrowseHero(
    heroMedia: Media,
    heroImage: String?,
    heroLogo: String?,
    heroStreamUrl: String?,
    heroVideoReady: Boolean,
    newsId: Int?,
    videoOn: Boolean,
    muted: Boolean,
    player: MediaPlayer,
    onToggleVideoOn: (Boolean) -> Unit,
    onToggleMuted: () -> Unit,
    onOpenMedia: (Media) -> Unit,
    onPlay: (Media) -> Unit,
    isLandscape: Boolean,
) {
    // A fixed 16:9 aspect ratio works fine in portrait, but in landscape the
    // resulting height (screen width × 9/16) comfortably exceeds a phone's
    // actual landscape screen height — the backdrop still fills the visible
    // area (looks fine on its own), but the logo/categories/play button
    // pinned to the *bottom* of that oversized box end up positioned well
    // below the fold, invisible until scrolling down. Capping the hero's
    // height to the real viewport height in landscape keeps that bottom
    // content inside the very first screen, no scroll needed.
    val heroModifier = if (isLandscape) {
        Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp)
    } else {
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    }
    Box(modifier = heroModifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // The video surface is attached as soon as there's a stream to play
        // — libVLC needs somewhere to render into before it can ever reach
        // "Playing" — but the still backdrop (+ logo/title below) is drawn
        // on top of it the whole time the stream is loading, only removed
        // once heroVideoReady flips true, so there's never a black flash of
        // an empty player while it buffers.
        //
        // Some sources aren't truly 16:9 — they carry their own baked-in
        // letterbox bars — so the surface is scaled up 1.4x, centered
        // (contentAlignment above, plus scale()'s own default center
        // transform origin — belt and suspenders), and cropped by the
        // clipToBounds above, to push those bars out past the visible
        // frame evenly on all sides, per user request, rather than trying
        // to detect/measure them per title.
        if (heroStreamUrl != null) {
            VlcVideoSurface(mediaPlayer = player, modifier = Modifier.fillMaxSize().scale(1.4f))
        }
        if (heroStreamUrl == null || !heroVideoReady) {
            if (heroImage != null) {
                FadeInImage(uri = heroImage, title = heroMedia.title, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(ChocoColors.surfaceAlt))
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, ChocoColors.bg))))

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 14.dp).padding(top = 88.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                //modifier = Modifier.background(Color(0x73000000), RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                //verticalAlignment = Alignment.CenterVertically,
                //horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                //Box(modifier = Modifier.size(6.dp).background(ChocoColors.accent, RoundedCornerShape(99.dp)))
                //Text("EN VEDETTE", color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 10.sp, letterSpacing = 0.7.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconCircleButton(icon = DetailIconVector(ChocoColors.text), size = 34.dp, iconSize = 18.dp, onClick = { onOpenMedia(heroMedia) })
                // Nothing to activate/cancel without a running news stream —
                // the random-media fallback hero shows only the detail button.
                if (newsId != null) {
                    if (videoOn) {
                        IconCircleButton(icon = CutStreamVector(ChocoColors.text), size = 34.dp, iconSize = 18.dp, onClick = { onToggleVideoOn(false) })
                        IconCircleButton(icon = if (muted) VolumeMuteVector(ChocoColors.text) else VolumeVector(ChocoColors.text), size = 34.dp, iconSize = 18.dp, onClick = onToggleMuted)
                    } else {
                        IconCircleButton(icon = RestreamVector(ChocoColors.text), size = 34.dp, iconSize = 18.dp, onClick = { onToggleVideoOn(true) })
                    }
                }
            }
        }

        Row(
            // The floating BottomNavBar (see MainGraph) overlays this screen
            // at all times — in portrait there's a whole page of rows below
            // the hero to keep it clear of that bar, but in landscape the
            // hero now spans almost the entire viewport (see isLandscape's
            // height cap above), landing this row right where the bar
            // floats. Reusing TAB_BAR_CLEARANCE (the same convention every
            // scrollable screen already uses to keep its last row clear of
            // the bar) instead of a one-off value here.
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = if (isLandscape) TAB_BAR_CLEARANCE else 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (heroLogo != null) {
                    LogoImage(uri = heroLogo, height = if (isLandscape) 84.dp else 66.dp, maxWidth = if (isLandscape) 340.dp else 260.dp)
                } else {
                    Text(heroMedia.title.uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = if (isLandscape) 34.sp else 27.sp, lineHeight = if (isLandscape) 34.sp else 27.sp, maxLines = 2, letterSpacing = (-0.9).sp)
                }
                Text(
                    heroMedia.categories.joinToString(" · ") { Adapters.humanizeKey(it.translationKey) },
                    color = Color(0x99FFFFFF),
                    fontFamily = ChocoFonts.bodyRegular,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Box(
                modifier = Modifier.size(46.dp).background(ChocoColors.accent, RoundedCornerShape(99.dp)).clickable { onPlay(heroMedia) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcon.of("play_arrow"), contentDescription = null, tint = ChocoColors.onAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun BrowseRow(row: FilteredRow, format: PosterFormat, onOpenMedia: (Media) -> Unit, onSeeAll: () -> Unit) {
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val mediaLabelStyle = prefs.settings.mediaLabelStyle
    var preview by remember { mutableStateOf<PosterPreviewTarget?>(null) }
    val seeAllEnabled = CatalogueEntryFilter.forSelection(row.id, row.title, row.createFrom) != null

    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        SectionHeader(title = row.title, accentBar = true, onClick = if (seeAllEnabled) onSeeAll else null)
        PosterRow(data = row.items, key = { m, i -> "${m.id}-$i" }) { media, _ ->
            val ui = Adapters.mapMediaToUi(media)
            when {
                row.isSpecial -> {
                    val uri = Adapters.specialPosterUri(media)
                    Poster(
                        title = ui.title,
                        uri = uri,
                        ratio = PosterRatio.R2_3,
                        titleSize = 14.sp,
                        modifier = Modifier.width(152.dp).longPressable({ onOpenMedia(media) }, { preview = PosterPreviewTarget(media, uri, PosterRatio.R2_3, ui.title) }),
                    )
                }
                format == PosterFormat.V -> {
                    Poster(
                        title = ui.title,
                        uri = ui.posterUrl,
                        ratio = PosterRatio.R2_3,
                        titleSize = 12.5.sp,
                        modifier = Modifier.width(112.dp).longPressable({ onOpenMedia(media) }, { preview = PosterPreviewTarget(media, ui.posterUrl, PosterRatio.R2_3, ui.title) }),
                    )
                }
                else -> {
                    Poster(
                        title = ui.title,
                        uri = ui.backdropUrl,
                        ratio = PosterRatio.R16_9,
                        radius = 10.dp,
                        titleSize = 14.sp,
                        modifier = Modifier.width(196.dp).longPressable({ onOpenMedia(media) }, { preview = PosterPreviewTarget(media, ui.backdropUrl, PosterRatio.R16_9, ui.title) }),
                    ) {
                        HorizontalCardOverlay(media = media, category = ui.category, itemWidthDp = 196, mediaLabelStyle = mediaLabelStyle)
                    }
                }
            }
        }
        PosterPreviewSheet(target = preview, onClose = { preview = null })
    }
}
