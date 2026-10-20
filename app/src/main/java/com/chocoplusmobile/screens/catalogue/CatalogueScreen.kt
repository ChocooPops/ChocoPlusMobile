@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.CatalogFilter
import com.chocoplusmobile.api.CatalogFilterValue
import com.chocoplusmobile.api.CategorySimple
import com.chocoplusmobile.api.FilterType
import com.chocoplusmobile.api.wireValue
import com.chocoplusmobile.api.JobFilterCache
import com.chocoplusmobile.api.LogicalOperator
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Operation
import com.chocoplusmobile.api.SortCatalog
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.PosterFormatUtils
import com.chocoplusmobile.data.UiMediaType
import com.chocoplusmobile.navigation.CatalogueEntryFilter
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.ui.components.ButtonVariant
import com.chocoplusmobile.ui.components.ChocoBottomSheet
import com.chocoplusmobile.ui.components.ChocoChip
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.LogoImage
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text
import com.chocoplusmobile.i18n.tCount

/** The media-type filter — kept as a value (not its label) so the
 * filtering logic doesn't depend on the display language. */
private enum class MediaFilter(val label: Tr) { ALL(Tr.CATALOGUE_MEDIA_ALL), MOVIES(Tr.PAGE_FILMS), SERIES(Tr.PAGE_SERIES) }
// No "Toutes" entry — with multi-select, an empty selection already means
// every decade.
private val DEC_OPTIONS = listOf("1920", "1940", "1960", "1980", "1990", "2000", "2010", "2020")
/** `ascendingDefault` is only the direction pre-selected when the user picks
 * this sort — the actual direction sent is the separate `sortAscending`
 * state below, which the user can flip independently for whichever sort is
 * active. Either way it becomes the literal `orderDirection` query string
 * the backend expects ("true"/"false" — anything else is treated as true
 * server-side). */
private data class SortOption(val label: Tr, val value: SortCatalog, val ascendingDefault: Boolean)
private val SORTS = listOf(
    SortOption(Tr.CATALOGUE_SORT_ADDED, SortCatalog.ADDED_DATE, false),
    SortOption(Tr.CATALOGUE_SORT_TITLE, SortCatalog.TITLE, true),
    SortOption(Tr.CATALOGUE_SORT_RELEASE, SortCatalog.RELEASE_DATE, false),
    SortOption(Tr.CATALOGUE_SORT_SHUFFLE, SortCatalog.SHUFFLE, true),
    SortOption(Tr.CATALOGUE_SORT_WATCHED, SortCatalog.ALREADY_WATCHED, false),
)
private const val PAGE_SIZE = 24

/** The generic "any other FilterType" picker in the filter sheet — Media,
 * Catégorie and Décennie already get curated pickers above it, so this list
 * covers the rest of FilterType plus the Job codes fetched from
 * credit/job-filters (`filterType = null`), since the backend's Job enum
 * isn't mirrored client-side (see CatalogFilter's own doc in
 * ApiService.kt). */
private data class AdvancedFilterTypeOption(val label: Tr, val filterType: FilterType?)
private val ADVANCED_FILTER_TYPES = listOf(
    AdvancedFilterTypeOption(Tr.CATALOGUE_FILTER_JOB, null),
    AdvancedFilterTypeOption(Tr.CATALOGUE_FILTER_KEYWORD, FilterType.KEY_WORD),
    AdvancedFilterTypeOption(Tr.CATALOGUE_FILTER_YEAR, FilterType.YEAR),
    AdvancedFilterTypeOption(Tr.CATALOGUE_FILTER_SELECTION, FilterType.SELECTION),
    AdvancedFilterTypeOption(Tr.CATALOGUE_FILTER_LICENSE, FilterType.LICENSE),
)
/** `displayValue` is what shows after `displayLabel` in the applied-filters
 * chip (e.g. "Année : 1994") — defaults to `value` for the advanced-filter
 * sheet, where the typed value is worth echoing back. Staff entries pass
 * `null` instead: `displayLabel` already reads as "Réalisateur : John Doe",
 * and `value` there is a raw credit id, meaningless to show. */
private data class CustomFilterEntry(val typeData: String, val displayLabel: String, val value: String, val displayValue: String? = value)

/** Mirrors the RN app's screens/Catalogue/CatalogueScreen.tsx. */
@Composable
fun CatalogueScreen() {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val format = prefs.formatByPage.catalogue
    val alignment = prefs.alignmentByPage.current(FormatPage.CATALOGUE, format)
    val cols = PosterFormatUtils.gridColumns(format, alignment)
    val mediaLabelStyle = prefs.settings.mediaLabelStyle
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf(listOf<CategorySimple>()) }
    var jobOptions by remember { mutableStateOf(listOf<String>()) }
    var media by remember { mutableStateOf(MediaFilter.ALL) }
    var selectedCatIds by remember { mutableStateOf(setOf<Int>()) }
    var selectedDecs by remember { mutableStateOf(setOf<String>()) }
    var sortValue by remember { mutableStateOf(SortCatalog.ADDED_DATE) }
    var sortAscending by remember { mutableStateOf(false) }
    var customFilters by remember { mutableStateOf(listOf<CustomFilterEntry>()) }
    var advancedTypeIdx by remember { mutableStateOf(0) }
    var selectedJob by remember { mutableStateOf<String?>(null) }
    var advancedValueText by remember { mutableStateOf("") }
    var filtersOpen by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf(listOf<Media>()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // A genre/keyword/staff chip tapped on a detail page pre-seeds the
    // corresponding filter here — read once and cleared immediately so
    // navigating back into Catalogue "fresh" later doesn't reapply it.
    remember {
        fun applySort(value: SortCatalog) {
            SORTS.find { it.value == value }?.let { sortValue = it.value; sortAscending = it.ascendingDefault }
        }
        when (val entry = NavCache.pendingCatalogueFilter) {
            is CatalogueEntryFilter.Category -> selectedCatIds = setOf(entry.id)
            is CatalogueEntryFilter.Keyword -> customFilters = listOf(CustomFilterEntry(FilterType.KEY_WORD.wireValue, Tr.CATALOGUE_FILTER_KEYWORD.text(), entry.name))
            is CatalogueEntryFilter.Staff -> customFilters = listOf(CustomFilterEntry(entry.job, Tr.LABEL_VALUE.text(entry.jobLabel, entry.name), entry.creditId.toString(), displayValue = null))
            is CatalogueEntryFilter.SelectionSeeAll -> {
                customFilters = listOf(CustomFilterEntry(FilterType.SELECTION.wireValue, entry.name, entry.selectionId.toString(), displayValue = null))
                applySort(SortCatalog.SHUFFLE)
            }
            is CatalogueEntryFilter.LicenseSeeAll -> {
                customFilters = listOf(CustomFilterEntry(FilterType.LICENSE.wireValue, entry.name, entry.licenseId.toString(), displayValue = null))
                applySort(SortCatalog.SHUFFLE)
            }
            is CatalogueEntryFilter.CategorySeeAll -> {
                selectedCatIds = setOf(entry.categoryId)
                applySort(SortCatalog.SHUFFLE)
            }
            // media/selectedCatIds/selectedDecs/customFilters all already
            // start empty — "supprime tous les filtres" needs nothing here
            // beyond just not setting any of them.
            CatalogueEntryFilter.LatestReleases -> applySort(SortCatalog.ADDED_DATE)
            CatalogueEntryFilter.AlreadyWatched -> applySort(SortCatalog.ALREADY_WATCHED)
            null -> {}
        }
        NavCache.pendingCatalogueFilter = null
    }

    LaunchedEffect(Unit) {
        try {
            categories = NetworkModule.safeCall { NetworkModule.api.getAllCategories() }
        } catch (e: Exception) { /* ignore, matches RN's .catch(() => {}) */ }
    }
    LaunchedEffect(Unit) {
        try {
            jobOptions = JobFilterCache.get()
        } catch (e: Exception) { /* ignore — the job filter picker just stays empty */ }
    }

    val sort = SORTS.find { it.value == sortValue } ?: SORTS[0]

    // Blocks with no explicit `logic` default to AND server-side (see
    // filters.interface.ts / getMediaByCatalogFilters) — exactly "media type
    // AND category-group AND decade-group AND every custom filter" as wanted
    // here. Within the category/decade groups themselves, `valueLogic = OR`
    // means "any of the selected ones" — the natural reading of a
    // multi-select (a title only ever carries one or two categories, so
    // requiring ALL selected ones to match would almost always return
    // nothing).
    fun buildFilters(): List<CatalogFilter> {
        val filters = mutableListOf<CatalogFilter>()
        var nextId = 1
        fun add(typeData: String, values: List<String>, orAmongValues: Boolean = false, logic: LogicalOperator? = null) {
            filters += CatalogFilter(
                id = nextId++,
                typeData = typeData,
                operation = Operation.CONTAIN,
                logic = logic,
                valueLogic = if (orAmongValues && values.size > 1) LogicalOperator.OR else null,
                value = values.map { CatalogFilterValue(it) },
            )
        }
        if (media == MediaFilter.MOVIES) add(FilterType.MEDIA.wireValue, listOf(MediaType.MOVIE.name))
        if (media == MediaFilter.SERIES) add(FilterType.MEDIA.wireValue, listOf(MediaType.SERIES.name))
        // Category id (numeric) rather than translationKey — an exact
        // `categoryId = ?` match server-side instead of a LIKE, and lets
        // several selected categories share one filter block cleanly.
        if (selectedCatIds.isNotEmpty()) add(FilterType.CATEGORY.wireValue, selectedCatIds.map { it.toString() }, orAmongValues = true)
        if (selectedDecs.isNotEmpty()) add(FilterType.DECADE.wireValue, selectedDecs.toList(), orAmongValues = true)
        customFilters.forEach { c ->
            // A single credit can hold several jobs joined with "\" (e.g.
            // "DIRECTOR \ SCREENPLAY" — see Adapters.humanizeJob) — one
            // block per job, all but the first marked `logic = OR` so the
            // backend ORs them together into one group instead of ANDing
            // them (see media-substitution.service.ts's block-grouping
            // loop), since no single Media_Credit row ever carries both.
            val jobs = c.typeData.split("\\").map { it.trim() }.filter { it.isNotEmpty() }
            jobs.forEachIndexed { idx, job -> add(job, listOf(c.value), logic = if (idx > 0) LogicalOperator.OR else null) }
        }
        return filters
    }

    fun load(offset: Int, append: Boolean) {
        if (append) loadingMore = true else { loading = true; error = null }
        scope.launch {
            try {
                val res = NetworkModule.safeCall {
                    NetworkModule.api.getCatalog(buildFilters(), sort.value.name, sortAscending.toString(), PAGE_SIZE, offset)
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

    LaunchedEffect(media, selectedCatIds, selectedDecs, customFilters, sortValue, sortAscending) { load(0, false) }

    fun openMedia(m: Media) {
        NavCache.pendingMedia = m
        navController.navigate(if (m.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    val chips = buildList {
        if (media != MediaFilter.ALL) add(Triple("media", t(Tr.LABEL_VALUE, t(Tr.CATALOGUE_CHIP_MEDIA), t(media.label))) { media = MediaFilter.ALL })
        selectedCatIds.forEach { id ->
            val label = categories.find { it.id == id }?.let { Adapters.humanizeKey(it.translationKey) } ?: "#$id"
            add(Triple("cat-$id", t(Tr.LABEL_VALUE, t(Tr.CATALOGUE_CHIP_CATEGORY), label)) { selectedCatIds = selectedCatIds - id })
        }
        selectedDecs.forEach { d -> add(Triple("dec-$d", t(Tr.LABEL_VALUE, t(Tr.CATALOGUE_CHIP_DECADE), t(Tr.CATALOGUE_DECADE_VALUE, d))) { selectedDecs = selectedDecs - d }) }
        customFilters.forEachIndexed { idx, c ->
            val label = if (c.displayValue != null) t(Tr.LABEL_VALUE, c.displayLabel, c.displayValue) else c.displayLabel
            add(Triple("custom-$idx", label) { customFilters = customFilters.filterIndexed { i, _ -> i != idx } })
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        TopBar(
            onSearch = { navController.navigate("search") },
            onToggleFormat = { appViewModel.toggleFormat(FormatPage.CATALOGUE) },
            formatIsVertical = format == PosterFormat.V,
            onToggleAlignment = { appViewModel.toggleAlignment(FormatPage.CATALOGUE, format) },
            alignmentIcon = PosterFormatUtils.nextAlignmentIcon(alignment),
            onAvatar = { navController.navigate("profile") },
            avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
            avatarUri = user?.profilPhoto,
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(t(Tr.PAGE_CATALOGUE), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 24.sp, letterSpacing = (-0.6).sp)
                    Text(tCount(Tr.TITLES_COUNT_ONE, Tr.TITLES_COUNT_MANY, total), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Row(
                    modifier = Modifier
                        .background(ChocoColors.accentSoft, RoundedCornerShape(10.dp))
                        .clickable { filtersOpen = true }
                        .padding(horizontal = 14.dp)
                        .height(38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(AppIcon.of("tune"), contentDescription = null, tint = ChocoColors.accent, modifier = androidx.compose.ui.Modifier.size(18.dp))
                    Text(t(Tr.CATALOGUE_FILTER_BUTTON), color = ChocoColors.accent, fontFamily = ChocoFonts.bodyBold, fontSize = 13.sp)
                }
            }

            if (chips.isNotEmpty()) {
                FlowRow(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    chips.forEach { (key, label, clear) ->
                        Row(
                            modifier = Modifier
                                .background(Color(0x14FFD301), RoundedCornerShape(99.dp))
                                .clickable(onClick = clear)
                                .padding(start = 12.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(label, color = ChocoColors.accent, fontFamily = ChocoFonts.bodySemiBold, fontSize = 11.5.sp)
                            Icon(AppIcon.of("close"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // A plain Row here used to force every sort chip onto one line —
            // fine with 4 of them, but a narrow phone can't fit "TRI" + 5
            // chips + the direction icon side by side anymore now that
            // there's a 5th ("Déjà vus") — FlowRow wraps onto a second line
            // instead of clipping/squeezing them.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(t(Tr.CATALOGUE_SORT_LABEL), color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp))
                SORTS.forEach { s -> ChocoChip(label = t(s.label), active = sortValue == s.value, white = true, small = true, onClick = { sortValue = s.value; sortAscending = s.ascendingDefault }) }
                Icon(
                    AppIcon.of(if (sortAscending) "expand_less" else "expand_more"),
                    contentDescription = t(if (sortAscending) Tr.SORT_ASCENDING else Tr.SORT_DESCENDING),
                    tint = ChocoColors.accent,
                    modifier = Modifier.padding(top = 3.dp).size(22.dp).clickable { sortAscending = !sortAscending },
                )
            }

            when {
                loading -> LoadingState()
                error != null -> ErrorState(message = error!!, onRetry = { load(0, false) })
                else -> {
                    // Same layout rules as MyListScreen/SearchScreen: a
                    // compact list row for Vertical+Single, a 1/2/3-column
                    // FlowRow grid otherwise, so switching between Catalogue,
                    // Ma liste and Recherche never feels like a different
                    // app.
                    if (format == PosterFormat.V && cols == 1) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            items.forEach { m -> CatalogueCompactRow(m, onClick = { openMedia(m) }) }
                        }
                    } else {
                        val widthPct = if (cols == 1) 1f else if (cols == 2) 0.487f else 0.314f
                        FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items.forEach { m ->
                                val ui = Adapters.mapMediaToUi(m)
                                val tileModifier = Modifier.fillMaxWidth(widthPct).clickable { openMedia(m) }
                                if (format == PosterFormat.V) {
                                    Poster(title = ui.title, uri = ui.posterUrl, ratio = PosterRatio.R2_3, radius = 8.dp, titleSize = 10.5.sp, badge = if (ui.type == UiMediaType.SERIE) t(Tr.BADGE_SERIES) else null, modifier = tileModifier)
                                } else {
                                    Poster(title = ui.title, uri = ui.backdropUrl, ratio = PosterRatio.R16_9, radius = 10.dp, titleSize = if (cols == 1) 15.sp else 12.sp, modifier = tileModifier) {
                                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
                                            Column {
                                                val cardLogo = if (mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.logoUri(m) else null
                                                if (cardLogo != null) {
                                                    LogoImage(uri = cardLogo, height = if (cols == 1) 28.dp else 20.dp, maxWidth = 220.dp)
                                                } else {
                                                    Text(ui.title.uppercase(), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = if (cols == 1) 15.sp else 12.5.sp, maxLines = 1)
                                                }
                                                if (ui.category.isNotEmpty()) {
                                                    Text(ui.category, color = Color(0x99FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (items.size < total) {
                        Row(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable(enabled = !loadingMore) { load(items.size, true) },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (loadingMore) CircularProgressIndicator(color = ChocoColors.accent) else Text(t(Tr.LOAD_MORE), color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    ChocoBottomSheet(visible = filtersOpen, onClose = { filtersOpen = false }) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t(Tr.CATALOGUE_FILTERS_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 19.sp)
            Text(
                t(Tr.CATALOGUE_CLEAR_ALL),
                color = ChocoColors.accent,
                fontFamily = ChocoFonts.bodyBold,
                fontSize = 12.5.sp,
                modifier = Modifier.clickable {
                    media = MediaFilter.ALL; selectedCatIds = emptySet(); selectedDecs = emptySet(); customFilters = emptyList()
                },
            )
        }

        FilterGroup(t(Tr.CATALOGUE_GROUP_MEDIA)) {
            MediaFilter.entries.forEach { o -> ChocoChip(label = t(o.label), active = media == o, onClick = { media = o }) }
        }
        FilterGroup(t(Tr.CATALOGUE_GROUP_CATEGORY)) {
            categories.forEach { c ->
                val active = selectedCatIds.contains(c.id)
                ChocoChip(label = Adapters.humanizeKey(c.translationKey), active = active, onClick = { selectedCatIds = if (active) selectedCatIds - c.id else selectedCatIds + c.id })
            }
        }
        FilterGroup(t(Tr.CATALOGUE_GROUP_DECADE)) {
            DEC_OPTIONS.forEach { o ->
                val active = selectedDecs.contains(o)
                ChocoChip(label = o, active = active, onClick = { selectedDecs = if (active) selectedDecs - o else selectedDecs + o })
            }
        }
        FilterGroup(t(Tr.CATALOGUE_GROUP_OTHER)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ADVANCED_FILTER_TYPES.forEachIndexed { idx, opt ->
                        ChocoChip(label = t(opt.label), active = advancedTypeIdx == idx, onClick = { advancedTypeIdx = idx })
                    }
                }
                val selectedOpt = ADVANCED_FILTER_TYPES[advancedTypeIdx]
                if (selectedOpt.filterType == null) {
                    JobDropdown(jobs = jobOptions, selected = selectedJob, onSelect = { selectedJob = it }, modifier = Modifier.padding(top = 10.dp))
                }
                AdvancedTextInput(value = advancedValueText, onValueChange = { advancedValueText = it }, placeholder = t(Tr.CATALOGUE_VALUE_PLACEHOLDER), modifier = Modifier.padding(top = 10.dp))
                PrimaryButton(
                    label = t(Tr.CATALOGUE_ADD_FILTER),
                    variant = ButtonVariant.OUTLINE,
                    height = 42.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    onClick = {
                        val typeData = selectedOpt.filterType?.wireValue ?: selectedJob
                        val value = advancedValueText.trim()
                        if (!typeData.isNullOrEmpty() && value.isNotEmpty()) {
                            val label = if (selectedOpt.filterType == null) Tr.CATALOGUE_FILTER_JOB_WITH.text(typeData) else selectedOpt.label.text()
                            customFilters = customFilters + CustomFilterEntry(typeData, label, value)
                            advancedValueText = ""
                        }
                    },
                )
            }
        }

        PrimaryButton(label = tCount(Tr.CATALOGUE_SEE_RESULTS_ONE, Tr.CATALOGUE_SEE_RESULTS_MANY, total), onClick = { filtersOpen = false }, height = 50.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** Mirrors MyListScreen's MyListCompactRow / SearchScreen's SearchCompactRow
 * — same compact list row for Vertical+Single, down to dropping the play
 * button and progress bar, so the three screens read as one consistent
 * layout system. */
@Composable
private fun CatalogueCompactRow(media: Media, onClick: () -> Unit) {
    val ui = Adapters.mapMediaToUi(media)
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Poster(title = ui.title, uri = ui.posterUrl, ratio = PosterRatio.R2_3, titleSize = 9.sp, modifier = Modifier.width(62.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ui.title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 14.5.sp, maxLines = 1)
            Text("${ui.year ?: ""} ${if (ui.category.isNotEmpty()) "· ${ui.category}" else ""}", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(AppIcon.of("chevron_right"), contentDescription = null, tint = ChocoColors.textDisabled, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilterGroup(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        Text(label, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
    }
}

/** Dropdown for picking a Job code (director, actor, …) fetched once from
 * credit/job-filters via JobFilterCache — a real select, not a free-text
 * field, since the valid codes are known and finite once fetched. */
@Composable
private fun JobDropdown(jobs: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChocoColors.surfaceAlt2, RoundedCornerShape(10.dp))
                .clickable(enabled = jobs.isNotEmpty()) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected ?: t(if (jobs.isEmpty()) Tr.CATALOGUE_JOBS_LOADING else Tr.CATALOGUE_PICK_JOB),
                color = if (selected != null) ChocoColors.text else ChocoColors.textMuted,
                fontFamily = ChocoFonts.bodyRegular,
                fontSize = 13.5.sp,
            )
            Icon(AppIcon.of("expand_more"), contentDescription = null, tint = ChocoColors.textMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            jobs.forEach { job ->
                DropdownMenuItem(
                    text = { Text(job, color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.5.sp) },
                    onClick = { onSelect(job); expanded = false },
                )
            }
        }
    }
}

/** A single-line free-text input for the "Autre filtre" section — styled
 * like SearchScreen's own search box (no shared component for this exists
 * yet), since the job code / filter value here can't be picked from a
 * curated list the way Média/Catégorie/Décennie are. */
@Composable
private fun AdvancedTextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ChocoColors.surfaceAlt2, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.5.sp),
            cursorBrush = SolidColor(ChocoColors.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.5.sp)
                inner()
            },
        )
    }
}
