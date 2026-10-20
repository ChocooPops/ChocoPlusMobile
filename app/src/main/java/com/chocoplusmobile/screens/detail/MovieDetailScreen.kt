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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.WatchProgressStore
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.canDownload
import com.chocoplusmobile.ui.components.DownloadButton
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.UiMediaType
import com.chocoplusmobile.data.UiTag
import com.chocoplusmobile.navigation.CatalogueEntryFilter
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.ui.components.TimedProgress
import com.chocoplusmobile.ui.components.FadeInImage
import com.chocoplusmobile.ui.components.IconCircleButton
import com.chocoplusmobile.ui.components.LoadingState
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
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text

/** Mirrors the RN app's screens/Detail/MovieDetailScreen.tsx. Reads the full
 * Media object passed via NavCache (see its docstring) — every list
 * endpoint already returns it in full, so there's no re-fetch here. */
@Composable
fun MovieDetailScreen() {
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
    var playing by remember { mutableStateOf(false) }
    var metaOpen by remember { mutableStateOf(false) }
    // Same lazy-fetch-full-record pattern as PosterPreviewSheet's own
    // metadata button — this screen already fetches getMediaInfo (crew/cast/
    // similar), but MediaMetadataSheet wants the full Media record itself
    // (every poster variant, background, logo), which is a separate,
    // heavier endpoint not worth loading before the button is even pressed.
    var metaLoading by remember { mutableStateOf(false) }
    var metaError by remember { mutableStateOf<String?>(null) }
    var metaFor by remember { mutableStateOf<Int?>(null) }
    var metaRecord by remember { mutableStateOf<Media?>(null) }
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    val heroLogo = if (prefs.settings.mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.heroLogoUri(media) else null

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
    }

    // Was a plain isInMyList() call before — a snapshot read of a StateFlow's
    // .value that never subscribed Compose to it, so toggling My List here
    // never actually flipped the icon (or anything else reacting to it):
    // nothing told this composable to recompose. collectAsState() does.
    val myListIds by appViewModel.myListIds.collectAsState()
    val inList = media.id in myListIds

    // What the player recorded this session wins over media.watchProgress,
    // which comes from whatever (possibly cached) list this screen was
    // opened from — see WatchProgressStore.
    val localProgress by WatchProgressStore.state.collectAsState()
    val progress = localProgress.movie(media.id) ?: ui.progress

    // Download control — ADMIN/FAMILY only (the backend's FamilyUserGuard
    // rejects everyone else anyway); nothing at all is shown otherwise.
    val user by appViewModel.user.collectAsState()
    val canDownload = user?.role.canDownload()
    val downloads by DownloadManager.entries.collectAsState()
    val download = downloads[DownloadManager.movieKey(media.id)]

    fun openMeta() {
        metaOpen = true
        if (metaFor == media.id) return // already fetched for this title
        metaLoading = true
        metaError = null
        scope.launch {
            try {
                metaRecord = NetworkModule.safeCall { NetworkModule.api.getMovieById(media.id) }
                metaFor = media.id
            } catch (e: Exception) {
                metaError = e.message ?: Tr.NETWORK_ERROR.text()
            } finally {
                metaLoading = false
            }
        }
    }

    fun play() {
        if (playing) return
        playing = true
        scope.launch {
            try {
                NavCache.pendingPlayerTarget = com.chocoplusmobile.api.Playback.resolvePlayTarget(media)
                navController.navigate("player")
            } finally {
                playing = false
            }
        }
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
                // Same fix as BrowseScreen's hero: a fixed aspect ratio makes
                // the box taller than the actual landscape screen (width ×
                // 10/16 comfortably exceeds a phone's landscape height),
                // pushing the bottom-aligned logo below the fold — capping
                // the height to the real viewport in landscape keeps it
                // inside the first visible screen instead.
                val heroModifier = if (isLandscape) {
                    Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                }
                Box(modifier = heroModifier) {
                    // Poster (used before) bakes its own fixed aspectRatio(16:10)
                    // into every instance regardless of the caller's modifier —
                    // harmless in portrait where the Box above already matches
                    // that same ratio, but once the Box's height is capped to
                    // the real screen in landscape (a different, wider ratio),
                    // Poster kept demanding 16:10 anyway and overflowed past the
                    // Box's actual bounds. FadeInImage has no such opinion — it
                    // just crops to fill whatever size it's actually given.
                    val heroBackdrop = Adapters.heroBackdropUri(media)
                    if (heroBackdrop != null) {
                        FadeInImage(uri = heroBackdrop, title = ui.title, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(ChocoColors.surfaceAlt))
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 0.75f to Color(0x59141414), 1f to ChocoColors.surface)))
                    IconCircleButton(
                        icon = AppIcon.of("arrow_back"),
                        size = 38.dp,
                        iconSize = 22.dp,
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars).padding(start = 12.dp, top = 8.dp),
                    )
                    Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = if (isLandscape) 36.dp else 16.dp, end = 16.dp, bottom = if (isLandscape) 56.dp else 22.dp)) {
                        if (heroLogo != null) {
                            LogoImage(uri = heroLogo, height = if (isLandscape) 108.dp else 80.dp, maxWidth = if (isLandscape) 340.dp else 260.dp)
                        } else {
                            Text(ui.title.uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = if (isLandscape) 42.sp else 34.sp, lineHeight = if (isLandscape) 42.sp else 34.sp, letterSpacing = (-1.2).sp)
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ui.year?.let { Text(it.toString(), color = Color(0x8CFFFFFF), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp) }
                        ui.duration?.let { Text(it, color = Color(0x8CFFFFFF), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp) }
                        ui.quality?.let {
                            Box(modifier = Modifier.background(Color.Transparent).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                Text(it, color = Color(0xBFFFFFFF), fontFamily = ChocoFonts.bodyBold, fontSize = 10.sp)
                            }
                        }
                    }

                    if ((progress ?: 0.0) > 0) {
                        TimedProgress(
                            progress = progress!!,
                            durationMs = media.duration,
                            leading = t(Tr.DETAIL_RESUME_PLAYBACK),
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        PrimaryButton(label = t(if ((progress ?: 0.0) > 0) Tr.RESUME else Tr.PLAYBACK), icon = AppIcon.of("play_arrow"), onClick = ::play, loading = playing, height = 48.dp, modifier = Modifier.weight(1f))
                        SquareIconButton(
                            icon = if (inList) MyListCheckVector(ChocoColors.text) else MyListAddVector(ChocoColors.text),
                            size = 48.dp,
                            iconModifier = if (inList) Modifier.width(21.dp).height(15.dp) else Modifier.size(20.dp),
                            onClick = { appViewModel.toggleMyList(media.id) },
                        )
                        if (canDownload) {
                            DownloadButton(entry = download, onStart = { DownloadManager.downloadMovie(media.id) })
                        }
                        SquareIconButton(icon = MetadataIconVector(ChocoColors.text), size = 48.dp, iconSize = 20.dp, onClick = ::openMeta)
                    }

                    if (ui.synopsis?.isNotEmpty() == true) {
                        Text(ui.synopsis!!, color = Color(0xB8FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 13.5.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 18.dp))
                    }

                    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (ui.genres.isNotEmpty()) {
                            DetailMetaChipRow(t(Tr.DETAIL_GENRES), ui.genres, onClick = { tag -> tag.id?.let { openCatalogueFor(CatalogueEntryFilter.Category(it)) } })
                        }
                        if (ui.keywords.isNotEmpty()) {
                            DetailMetaChipRow(t(Tr.DETAIL_KEYWORDS), ui.keywords, dim = true, onClick = { tag -> openCatalogueFor(CatalogueEntryFilter.Keyword(tag.name)) })
                        }
                    }
                }
            }
            if (creditsLoading) {
                item { LoadingState(compact = true, modifier = Modifier.padding(top = 24.dp)) }
            } else {
                if (ui.cast.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            Text(t(Tr.DETAIL_CAST), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp))
                            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                }
                if (ui.crew.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            Text(t(Tr.DETAIL_CREW), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp))
                            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
            if (similarLoading) {
                item {
                    Column(modifier = Modifier.padding(top = 24.dp, bottom = 30.dp)) {
                        Text(t(Tr.DETAIL_SIMILAR), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp))
                        LoadingState(compact = true)
                    }
                }
            } else if (similar.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 24.dp, bottom = 30.dp)) {
                        Text(t(Tr.DETAIL_SIMILAR), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp))
                        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            similar.forEach { m ->
                                val simUi = Adapters.mapMediaToUi(m)
                                if (!isLandscape) {
                                    Poster(title = simUi.title, uri = simUi.posterUrl, ratio = PosterRatio.R2_3, radius = 8.dp, titleSize = 10.5.sp, modifier = Modifier.fillMaxWidth(0.314f).clickable { openMedia(m) })
                                } else {
                                    val cardLogo = if (prefs.settings.mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.logoUri(m) else null
                                    Poster(title = simUi.title, uri = simUi.backdropUrl, ratio = PosterRatio.R16_9, radius = 8.dp, titleSize = 11.sp, modifier = Modifier.fillMaxWidth(0.314f).clickable { openMedia(m) }) {
                                        Row(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp, start = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
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
            }
        }
    }

    MediaMetadataSheet(visible = metaOpen, onClose = { metaOpen = false }, loading = metaLoading, error = metaError, media = metaRecord, mediaType = MediaType.MOVIE)
}

@Composable
internal fun DetailMetaRow(label: String, value: String, dim: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, modifier = Modifier.width(84.dp))
        Text(value, color = if (dim) Color(0x99FFFFFF) else Color(0xD9FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/** Same left-label layout as DetailMetaRow, but the value side is a wrapped
 * row of clickable bubbles instead of plain joined text — tapping one opens
 * Catalogue pre-filtered on it (see CatalogueEntryFilter). */
@Composable
internal fun DetailMetaChipRow(label: String, tags: List<UiTag>, onClick: (UiTag) -> Unit, dim: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, modifier = Modifier.width(84.dp).padding(top = 3.dp))
        FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                Row(
                    modifier = Modifier
                        .background(Color(0x14FFFFFF), RoundedCornerShape(99.dp))
                        .clickable { onClick(tag) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(tag.name, color = if (dim) Color(0x99FFFFFF) else Color(0xD9FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 11.5.sp)
                }
            }
        }
    }
}
