package com.chocoplusmobile.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.License
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Selection
import com.chocoplusmobile.api.SelectionType
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.UiMediaType
import com.chocoplusmobile.navigation.CatalogueEntryFilter
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.screens.home.HorizontalCardOverlay
import com.chocoplusmobile.ui.components.FadeInImage
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.LogoImage
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterPreviewSheet
import com.chocoplusmobile.ui.components.PosterPreviewTarget
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.PosterRow
import com.chocoplusmobile.ui.components.SectionHeader
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.components.longPressable
import com.chocoplusmobile.ui.rememberApiData
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

private const val MEDIA_LIST_ITEM_WIDTH = 132

/** Mirrors the RN app's screens/Detail/LicenseDetailScreen.tsx. Grid
 * summaries (Home/Recherche) only carry srcIcon — the background, logo,
 * mediaList and selectionList this screen needs only come from the full
 * record, so it always re-fetches by id regardless of what the caller
 * already had. */
@Composable
fun LicenseDetailScreen(licenseId: Int, fallbackTitle: String) {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val format = prefs.formatByPage.licenseDetail

    val (state, refresh) = rememberApiData(licenseId) {
        NetworkModule.safeCall { NetworkModule.api.getLicenseById(licenseId) }
    }

    fun openMedia(media: Media) {
        NavCache.pendingMedia = media
        navController.navigate(if (media.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    fun seeAllSelection(selection: Selection) {
        val filter = CatalogueEntryFilter.forSelection(selection.id, selection.name, selection.createFrom) ?: return
        NavCache.pendingCatalogueFilter = filter
        navController.navigate("catalogue")
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val license = state.data

    Box(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        license?.let {
            val backdrop = Adapters.licenseBackdropUri(it)
            if (backdrop != null) {
                FadeInImage(uri = backdrop, title = it.name, modifier = Modifier.fillMaxSize())
            }
            // rgba(10,10,12, 0 / 0.1 / 0.3) — same stops as the RN version.
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x000A0A0C), 0.45f to Color(0x1A0A0A0C), 1f to Color(0x4D0A0A0C))))
        }

        var mediaPreview by remember { mutableStateOf<PosterPreviewTarget?>(null) }

        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(message = state.error!!, onRetry = refresh)
            license != null -> {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.55f), contentAlignment = Alignment.Center) {
                                // A small nudge down from dead-center — purely cosmetic.
                                Box(modifier = Modifier.padding(top = 180.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                                    val logo = Adapters.licenseLogoUri(license)
                                    if (logo != null) {
                                        LogoImage(uri = logo, height = 110.dp, maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.7f)
                                    } else {
                                        Text(
                                            license.name,
                                            color = ChocoColors.text,
                                            fontFamily = ChocoFonts.headingExtraBold,
                                            fontSize = 32.sp,
                                            lineHeight = 34.sp,
                                            maxLines = 3,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            letterSpacing = (-1).sp,
                                        )
                                    }
                                }
                            }

                            if (license.mediaList.isNotEmpty()) {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    SectionHeader(title = "Contenu principal", accentBar = true, onClick = {
                                        NavCache.pendingCatalogueFilter = CatalogueEntryFilter.LicenseSeeAll(licenseId, license.name)
                                        navController.navigate("catalogue")
                                    })
                                    PosterRow(data = license.mediaList, key = { m, i -> "${m.id}-$i" }) { media, _ ->
                                        val ui = Adapters.mapMediaToUi(media)
                                        val uri = Adapters.licenseMediaPosterUri(media)
                                        Poster(
                                            title = ui.title,
                                            uri = uri,
                                            ratio = PosterRatio.R2_3,
                                            titleSize = 13.sp,
                                            badge = if (ui.type == UiMediaType.SERIE) "SÉRIE" else null,
                                            modifier = Modifier.width(MEDIA_LIST_ITEM_WIDTH.dp).longPressable({ openMedia(media) }, { mediaPreview = PosterPreviewTarget(media, uri, PosterRatio.R2_3, ui.title) }),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(license.selectionList.size) { idx ->
                        LicenseSelectionRow(selection = license.selectionList[idx], format = format, onOpenMedia = ::openMedia, onSeeAll = { seeAllSelection(license.selectionList[idx]) })
                    }
                    item { Box(modifier = Modifier.height(com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) }
                }
                TopBar(
                    overlay = true,
                    listState = listState,
                    onSearch = { navController.navigate("search") },
                    onToggleFormat = { appViewModel.toggleFormat(FormatPage.LICENSE_DETAIL) },
                    formatIsVertical = format == PosterFormat.V,
                    onAvatar = { navController.navigate("profile") },
                    avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
                    avatarUri = user?.profilPhoto,
                )
                PosterPreviewSheet(target = mediaPreview, onClose = { mediaPreview = null })
            }
        }

        if (license == null && !state.loading && state.error == null) {
            TopBar(onSearch = { navController.navigate("search") }, onAvatar = { navController.navigate("profile") }, avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?", avatarUri = user?.profilPhoto)
        }
    }
}

/** Same rendering as HomeScreen's own selection row — SPECIAL selections
 * always vertical/bigger, NORMAL ones follow `format`. */
@Composable
private fun LicenseSelectionRow(selection: Selection, format: PosterFormat, onOpenMedia: (Media) -> Unit, onSeeAll: () -> Unit) {
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val mediaLabelStyle = prefs.settings.mediaLabelStyle
    val isSpecial = selection.selectionType == SelectionType.SPECIAL_POSTER
    var preview by remember { mutableStateOf<PosterPreviewTarget?>(null) }
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
                        badge = if (ui.type == UiMediaType.SERIE) "SÉRIE" else null,
                        modifier = Modifier.width(152.dp).longPressable({ onOpenMedia(media) }, { preview = PosterPreviewTarget(media, uri, PosterRatio.R2_3, ui.title) }),
                    )
                }
                format == PosterFormat.V -> {
                    Poster(
                        title = ui.title,
                        uri = ui.posterUrl,
                        ratio = PosterRatio.R2_3,
                        titleSize = 12.5.sp,
                        badge = if (ui.type == UiMediaType.SERIE) "SÉRIE" else null,
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
