package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.CatalogFilter
import com.chocoplusmobile.api.CatalogFilterValue
import com.chocoplusmobile.api.FilterType
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Operation
import com.chocoplusmobile.api.SortCatalog
import com.chocoplusmobile.api.wireValue
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.UiMediaType
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text

private val MEDIA_FILTERS = listOf(Tr.CATALOGUE_MEDIA_ALL to null, Tr.PAGE_FILMS to MediaType.MOVIE, Tr.PAGE_SERIES to MediaType.SERIES)

// Same "tranche" size for the first fetch and every load-more one.
private const val HISTORY_PAGE_SIZE = 30

// How close to the bottom (in px) of what's already loaded before the next
// slice is fetched — small enough that it only fires once actually near the
// end, large enough that the next 30 arrive before the reader hits the
// literal bottom and sees an empty gap.
private const val LOAD_MORE_THRESHOLD_PX = 600

/** Mirrors the RN app's screens/Profile/HistoryScreen.tsx — now backed by
 * POST media/catalog (filtered on ALREADY_WATCHED, sorted descending) rather
 * than the separate "top media" endpoint, so this reads as an actual
 * watch history (most recently watched first) instead of a most-watched
 * ranking. The backend attaches the ALREADY_WATCHED join purely from the
 * sort value — no extra filter is needed for that part, only the optional
 * Films/Séries media-type one below. Paginated in 30-media slices, the next
 * one fetched automatically once the scroll position nears the bottom of
 * what's already loaded (see the scrollState-keyed LaunchedEffect below) —
 * this screen is a plain Column+verticalScroll rather than a LazyColumn
 * (Catalogue's own infinite grid), so "near the bottom" is read straight off
 * ScrollState instead of a LazyListState's visible-item index. */
@Composable
fun HistoryScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var filter by remember { mutableStateOf<MediaType?>(null) }
    var items by remember { mutableStateOf(listOf<Media>()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    fun buildFilters(): List<CatalogFilter> = filter?.let {
        listOf(CatalogFilter(id = 1, typeData = FilterType.MEDIA.wireValue, operation = Operation.CONTAIN, value = listOf(CatalogFilterValue(it.name))))
    } ?: emptyList()

    fun load(offset: Int, append: Boolean) {
        if (append) loadingMore = true else { loading = true; error = null }
        scope.launch {
            try {
                val res = NetworkModule.safeCall {
                    NetworkModule.api.getCatalog(buildFilters(), SortCatalog.ALREADY_WATCHED.name, "false", HISTORY_PAGE_SIZE, offset)
                }
                items = if (append) items + res.medias else res.medias
                total = res.total
            } catch (e: Exception) {
                error = e.message ?: Tr.NETWORK_ERROR.text()
            } finally {
                loading = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(filter, retryTick) { load(0, false) }

    // Fires on every scroll-position change (and whenever a load actually
    // completes, since items/total change too) — a no-op unless it's
    // genuinely near the bottom with more left to fetch and nothing already
    // in flight, so this can't double-trigger a slice.
    LaunchedEffect(scrollState.value, items, total, loading, loadingMore) {
        val nearBottom = scrollState.value >= scrollState.maxValue - LOAD_MORE_THRESHOLD_PX
        if (nearBottom && !loading && !loadingMore && items.size < total) {
            load(items.size, true)
        }
    }

    fun openMedia(media: Media) {
        NavCache.pendingMedia = media
        navController.navigate(if (media.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp).verticalScroll(scrollState).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
        ScreenHeaderBack(title = t(Tr.PROFILE_HISTORY), onBack = { navController.popBackStack() })
        Text(t(Tr.HISTORY_INTRO), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        Row(modifier = Modifier.padding(top = 14.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MEDIA_FILTERS.forEach { (label, value) ->
                val active = filter == value
                Row(
                    modifier = Modifier
                        .background(if (active) ChocoColors.text else Color.Transparent, RoundedCornerShape(99.dp))
                        .clickable { filter = value }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(t(label), color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 12.sp)
                }
            }
        }

        when {
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!, onRetry = { retryTick++ })
            items.isEmpty() -> Text(t(Tr.HISTORY_EMPTY), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    items.forEach { media ->
                        val ui = remember(media) { Adapters.mapMediaToUi(media) }
                        val progress = ui.progress ?: 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openMedia(media) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Poster(
                                title = ui.title,
                                uri = ui.posterUrl,
                                ratio = PosterRatio.R2_3,
                                radius = 8.dp,
                                titleSize = 8.5.sp,
                                progress = ui.progress,
                                modifier = Modifier.width(62.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ui.title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 13.5.sp, maxLines = 1)
                                Text(
                                    t(if (ui.type == UiMediaType.SERIE) Tr.HISTORY_SERIES_PROGRESS else Tr.HISTORY_MOVIE_PROGRESS, progress.roundToInt()),
                                    color = ChocoColors.textMuted,
                                    fontFamily = ChocoFonts.bodyRegular,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            val done = progress >= 95
                            Row(modifier = Modifier.background(if (done) ChocoColors.accentSoft else Color(0x1AFFFFFF), RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(t(if (done) Tr.WATCH_FINISHED else Tr.WATCH_IN_PROGRESS), color = if (done) ChocoColors.accent else Color(0x99FFFFFF), fontFamily = ChocoFonts.bodyBold, fontSize = 10.sp, letterSpacing = 0.4.sp)
                            }
                        }
                    }
                }
                if (loadingMore) {
                    Row(modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = ChocoColors.accent)
                    }
                }
            }
        }
    }
}
