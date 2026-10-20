package com.chocoplusmobile.screens.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.ContentCache
import com.chocoplusmobile.api.HomeContent
import com.chocoplusmobile.api.License
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.News
import com.chocoplusmobile.api.Playback
import com.chocoplusmobile.api.Selection
import com.chocoplusmobile.api.SelectionType
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.ImageCategory
import com.chocoplusmobile.data.ImageQuality
import com.chocoplusmobile.data.Palette
import com.chocoplusmobile.data.UiMediaType
import com.chocoplusmobile.navigation.CatalogueEntryFilter
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.state.PosterFormat
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
import com.chocoplusmobile.ui.components.SquareIconButton
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.components.longPressable
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.icons.DetailIconVector
import com.chocoplusmobile.ui.icons.MyListAddVector
import com.chocoplusmobile.ui.icons.MyListCheckVector
import com.chocoplusmobile.ui.rememberApiData
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t

/** Mirrors the RN app's screens/Home/HomeScreen.tsx. */
@Composable
fun HomeScreen() {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val resumeFormat = prefs.formatByPage.home
    val scope = rememberCoroutineScope()

    // ContentCache.home survives leaving/re-entering this composable (a tab
    // switch away and back), unlike rememberApiData's own state — so
    // switching tabs never re-hits the network for data already fetched
    // this session. rawRefresh alone would just hand back the same cached
    // value, so the actual retry/pull-to-refresh path goes through
    // `refresh` below instead, which clears the slot first.
    val (state, rawRefresh) = rememberApiData(Unit) {
        ContentCache.home ?: HomeContent(
            news = NetworkModule.safeCall { NetworkModule.api.getNews() },
            licenses = NetworkModule.safeCall { NetworkModule.api.getHomePageLicenses() },
            selections = NetworkModule.safeCall { NetworkModule.api.getSelectionHome() },
        ).also { ContentCache.home = it }
    }
    fun refresh() {
        ContentCache.home = null
        rawRefresh()
    }

    fun openMedia(media: Media) {
        NavCache.pendingMedia = media
        navController.navigate(if (media.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    // "Tout voir" (SectionHeader's chevron) on a selection row — see
    // CatalogueEntryFilter.forSelection, shared with Browse/LicenseDetail's
    // own selection rows.
    fun seeAllSelection(selection: Selection) {
        val entry = CatalogueEntryFilter.forSelection(selection.id, selection.name, selection.createFrom) ?: return
        NavCache.pendingCatalogueFilter = entry
        navController.navigate("catalogue")
    }

    fun play(media: Media) {
        scope.launch {
            try {
                NavCache.pendingPlayerTarget = Playback.resolvePlayTarget(media)
                navController.navigate("player")
            } catch (e: Exception) {
                // no episode/movie to play yet — silently ignore, same as the RN version
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
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Column {
                            if (data.news.isNotEmpty()) {
                                HeroCarousel(news = data.news, onOpenMedia = ::openMedia, onPlay = ::play)
                            }
                            if (data.licenses.isNotEmpty()) {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    PosterRow(data = data.licenses, key = { l, i -> "${l.id}-$i" }) { license, _ ->
                                        val ui = Adapters.mapLicenseToUi(license)
                                        Poster(
                                            title = ui.title,
                                            uri = ui.iconUrl,
                                            ratio = PosterRatio.R16_10,
                                            titleSize = 12.5.sp,
                                            modifier = Modifier.width(160.dp).clickable {
                                                navController.navigate("licenseDetail/${license.id}?title=${java.net.URLEncoder.encode(license.name, "UTF-8")}")
                                            },
                                        )
                                    }
                                }
                            }
                            if (data.news.isEmpty() && data.licenses.isEmpty() && data.selections.isEmpty()) {
                                ErrorState(message = t(Tr.HOME_NO_CONTENT), onRetry = ::refresh)
                            }
                        }
                    }
                    items(data.selections.size) { idx ->
                        HomeSelectionRow(selection = data.selections[idx], format = resumeFormat, onOpenMedia = ::openMedia, onSeeAll = { seeAllSelection(data.selections[idx]) })
                    }
                    item { Box(modifier = Modifier.height(com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) }
                }
                TopBar(
                    overlay = true,
                    listState = listState,
                    onSearch = { navController.navigate("search") },
                    onToggleFormat = { appViewModel.toggleFormat(FormatPage.HOME) },
                    formatIsVertical = resumeFormat == PosterFormat.V,
                    onAvatar = { navController.navigate("profile") },
                    avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
                    avatarUri = user?.profilPhoto,
                )
            }
        }
    }
}

@Composable
private fun HeroCarousel(news: List<News>, onOpenMedia: (Media) -> Unit, onPlay: (Media) -> Unit) {
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val settings = prefs.settings
    val pagerState = rememberPagerState(pageCount = { news.size })
    val featIndex = pagerState.currentPage
    val featMedia = news.getOrNull(featIndex)?.media
    // Plain isInMyList() never subscribed this composable to My List changes
    // (a bare StateFlow.value read) — collectAsState() does, so toggling
    // actually flips the icon now instead of only the underlying data.
    val myListIds by appViewModel.myListIds.collectAsState()
    val inList = featMedia?.let { it.id in myListIds } ?: false
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth().height(470.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val n = news[page]
            val m = n.media
            val slideImage = ImageQuality.withSize(n.srcBackground, ImageCategory.HERO_BACKDROP) ?: m?.let { Adapters.heroBackdropUri(it) }
            val slideLogo = if (settings.mediaLabelStyle == MediaLabelStyle.LOGO) m?.let { Adapters.heroLogoUri(it) } else null
            val (g1, g2, g3) = Palette.posterGradient(m?.title ?: "")
            Box(modifier = Modifier.fillMaxSize()) {
                if (slideImage != null) {
                    FadeInImage(uri = slideImage, title = m?.title, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(g1, g2, g3))))
                }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, ChocoColors.bg))))
                if (m != null) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 114.dp).padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (slideLogo != null) {
                            LogoImage(uri = slideLogo, height = 90.dp, maxWidth = 300.dp)
                        } else {
                            Text(
                                m.title.uppercase(),
                                color = ChocoColors.text,
                                fontFamily = ChocoFonts.headingExtraBold,
                                fontSize = 34.sp,
                                lineHeight = 36.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                        Text(
                            m.categories.joinToString(" · ") { Adapters.humanizeKey(it.translationKey) },
                            color = Color(0xB3FFFFFF),
                            fontFamily = ChocoFonts.bodyMedium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 98.dp).background(Color(0x6B000000), RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(AppIcon.of("campaign"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(15.dp))
            Text(t(Tr.HOME_FEATURED).uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp)
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (news.size > 1) {
                // Driven directly by the pager's own live scroll position
                // (not just the settled page), so each dot morphs width and
                // colour in real time as a finger drags a news card across —
                // not just a snap once the swipe commits.
                val pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    news.indices.forEach { i ->
                        val fraction = (1f - kotlin.math.abs(pagerPosition - i)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(lerpDp(6.dp, 22.dp, fraction))
                                .clip(RoundedCornerShape(99.dp))
                                .background(lerpColor(Color(0x59FFFFFF), ChocoColors.accent, fraction))
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(ChocoColors.accent)
                        .clickable(enabled = featMedia != null) { featMedia?.let(onPlay) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcon.of("play_arrow"), contentDescription = null, tint = ChocoColors.onAccent, modifier = Modifier.size(16.dp))
                    Text(t(Tr.PLAYBACK), color = ChocoColors.onAccent, fontFamily = ChocoFonts.bodyBold, fontSize = 15.sp, modifier = Modifier.padding(start = 7.dp))
                }
                SquareIconButton(icon = DetailIconVector(ChocoColors.text), size = 48.dp, background = Color(0x21FFFFFF), onClick = { featMedia?.let(onOpenMedia) })
                SquareIconButton(
                    icon = if (inList) MyListCheckVector(ChocoColors.text) else MyListAddVector(ChocoColors.text),
                    size = 48.dp,
                    iconModifier = if (inList) Modifier.width(21.dp).height(15.dp) else Modifier.size(20.dp),
                    background = Color(0x21FFFFFF),
                    onClick = { featMedia?.let { appViewModel.toggleMyList(it.id) } },
                )
            }
        }
    }
}

@Composable
private fun HomeSelectionRow(selection: Selection, format: PosterFormat, onOpenMedia: (Media) -> Unit, onSeeAll: () -> Unit) {
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val mediaLabelStyle = prefs.settings.mediaLabelStyle
    val isSpecial = selection.selectionType == SelectionType.SPECIAL_POSTER
    var preview by remember { mutableStateOf<PosterPreviewTarget?>(null) }
    // No chevron/tap affordance at all for a createFrom this app doesn't
    // know how to build a Catalogue filter for — matches SectionHeader's
    // own onClick == null ? no-chevron behavior.
    val seeAllEnabled = CatalogueEntryFilter.forSelection(selection.id, selection.name, selection.createFrom) != null

    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        SectionHeader(title = selection.name, accentBar = true, onClick = if (seeAllEnabled) onSeeAll else null)
        PosterRow(data = selection.mediaList, key = { m, i -> "${m.id}-$i" }) { media, _ ->
            val ui = Adapters.mapMediaToUi(media)
            when {
                isSpecial -> {
                    val uri = Adapters.specialPosterUri(media)
                    Poster(
                        title = ui.title,
                        uri = uri,
                        ratio = PosterRatio.R2_3,
                        titleSize = 14.sp,
                        badge = if (ui.type == UiMediaType.SERIE) t(Tr.BADGE_SERIES) else null,
                        modifier = Modifier.width(152.dp).longPressable(onClick = { onOpenMedia(media) }, onLongPress = { preview = PosterPreviewTarget(media, uri, PosterRatio.R2_3, ui.title) }),
                    )
                }
                format == PosterFormat.V -> {
                    Poster(
                        title = ui.title,
                        uri = ui.posterUrl,
                        ratio = PosterRatio.R2_3,
                        titleSize = 12.5.sp,
                        badge = if (ui.type == UiMediaType.SERIE) t(Tr.BADGE_SERIES) else null,
                        modifier = Modifier.width(112.dp).longPressable(onClick = { onOpenMedia(media) }, onLongPress = { preview = PosterPreviewTarget(media, ui.posterUrl, PosterRatio.R2_3, ui.title) }),
                    )
                }
                else -> {
                    Poster(
                        title = ui.title,
                        uri = ui.backdropUrl,
                        ratio = PosterRatio.R16_9,
                        radius = 10.dp,
                        titleSize = 14.sp,
                        modifier = Modifier.width(196.dp).longPressable(onClick = { onOpenMedia(media) }, onLongPress = { preview = PosterPreviewTarget(media, ui.backdropUrl, PosterRatio.R16_9, ui.title) }),
                    ) {
                        HorizontalCardOverlay(media = media, category = ui.category, itemWidthDp = 196, mediaLabelStyle = mediaLabelStyle)
                    }
                }
            }
        }
        PosterPreviewSheet(target = preview, onClose = { preview = null })
    }
}

/** Bottom-left title/logo + category overlay used by every horizontal
 * poster card in the app (Home/Films/Séries/LicenseDetail selection rows). */
@Composable
internal fun HorizontalCardOverlay(media: Media, category: String, itemWidthDp: Int, mediaLabelStyle: MediaLabelStyle) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.BottomStart) {
        Column {
            val cardLogo = if (mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.logoUri(media) else null
            if (cardLogo != null) {
                LogoImage(uri = cardLogo, height = 28.dp, maxWidth = (itemWidthDp * 0.8f).dp)
            } else {
                Text(media.title.uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 14.sp, maxLines = 1)
            }
            if (category.isNotEmpty()) {
                Text(category, color = Color(0x99FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 10.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
