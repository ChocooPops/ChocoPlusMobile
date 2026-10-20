@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.search

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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.PosterFormatUtils
import com.chocoplusmobile.data.UiLicence
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.state.PosterAlignment
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.LogoImage
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.rememberApiData
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.delay
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text
import com.chocoplusmobile.i18n.tCount

/** Mirrors the RN app's screens/Search/SearchScreen.tsx — its own
 * independent tab, reached only via the TopBar magnifying-glass icon (see
 * MainGraph's routing note). */
@Composable
fun SearchScreen(initialQuery: String = "") {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val format = prefs.formatByPage.search
    val alignment = prefs.alignmentByPage.current(FormatPage.SEARCH, format)
    val cols = PosterFormatUtils.gridColumns(format, alignment)

    var query by remember { mutableStateOf(initialQuery) }
    var recents by remember { mutableStateOf(listOf<String>()) }
    var results by remember { mutableStateOf(listOf<Media>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    // Opening this screen should drop the user straight into typing, same as
    // any dedicated search page — no extra tap on the input first.
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    val (licensesState, reloadLicenses) = rememberApiData(Unit) {
        NetworkModule.safeCall { NetworkModule.api.getResearchPageLicenses() }.map(Adapters::mapLicenseToUi)
    }

    LaunchedEffect(query, retryTick) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        searching = true
        searchError = null
        delay(350)
        try {
            val list = NetworkModule.safeCall { NetworkModule.api.researchMedia(trimmed) }
            results = list
            recents = (listOf(trimmed) + recents.filter { it.lowercase() != trimmed.lowercase() }).take(8)
        } catch (e: Exception) {
            searchError = e.message ?: Tr.NETWORK_ERROR.text()
        } finally {
            searching = false
        }
    }

    fun openMedia(media: Media) {
        NavCache.pendingMedia = media
        navController.navigate(if (media.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        TopBar(
            onSearch = {},
            searchActive = true,
            onToggleFormat = { appViewModel.toggleFormat(FormatPage.SEARCH) },
            formatIsVertical = format == PosterFormat.V,
            onToggleAlignment = { appViewModel.toggleAlignment(FormatPage.SEARCH, format) },
            alignmentIcon = PosterFormatUtils.nextAlignmentIcon(alignment),
            onAvatar = { navController.navigate("profile") },
            avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
            avatarUri = user?.profilPhoto,
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(ChocoColors.surfaceInput, RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(AppIcon.of("search"), contentDescription = null, tint = ChocoColors.textMuted, modifier = Modifier.size(21.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(ChocoColors.accent),
                    modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(t(Tr.SEARCH_PLACEHOLDER), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 15.sp)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(AppIcon.of("cancel"), contentDescription = null, tint = ChocoColors.textMuted, modifier = Modifier.size(19.dp).clickable { query = "" })
                }
            }

            if (query.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                    when {
                        searching -> LoadingState(compact = true)
                        searchError != null -> ErrorState(message = searchError!!, onRetry = { retryTick++ })
                        else -> {
                            Text(
                                tCount(Tr.SEARCH_RESULTS_ONE, Tr.SEARCH_RESULTS_MANY, results.size).uppercase(),
                                color = ChocoColors.textFaint,
                                fontFamily = ChocoFonts.bodyBold,
                                fontSize = 11.sp,
                                letterSpacing = 1.1.sp,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            if (results.isEmpty()) {
                                Text(t(Tr.SEARCH_NO_RESULTS, query), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            } else if (format == PosterFormat.V && cols == 1) {
                                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                    results.forEach { m -> SearchCompactRow(m, onClick = { openMedia(m) }) }
                                }
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    results.forEach { m -> SearchGridTile(m, format, cols, prefs.settings.mediaLabelStyle, onClick = { openMedia(m) }) }
                                }
                            }
                        }
                    }
                }
            } else {
                Column {
                    if (recents.isNotEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                            Text(t(Tr.SEARCH_RECENT).uppercase(), color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                recents.forEach { r ->
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0x0AFFFFFF), RoundedCornerShape(99.dp))
                                            .padding(horizontal = 13.dp, vertical = 8.dp)
                                            .clickable { query = r },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(AppIcon.of("history"), contentDescription = null, tint = Color(0x4DFFFFFF), modifier = Modifier.size(15.dp))
                                        Text(r, color = Color(0xBFFFFFFF), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 21.dp)) {
                        Text(t(Tr.SEARCH_BY_LICENSE), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 17.sp)
                        when {
                            licensesState.loading -> LoadingState(compact = true)
                            licensesState.error != null -> ErrorState(message = licensesState.error!!, onRetry = reloadLicenses)
                            else -> {
                                FlowRow(modifier = Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    (licensesState.data ?: emptyList()).forEach { l ->
                                        LicenceGridCard(l, onClick = { navController.navigate("licenseDetail/${l.id}?title=${java.net.URLEncoder.encode(l.title, "UTF-8")}") })
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

@Composable
private fun SearchCompactRow(media: Media, onClick: () -> Unit) {
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
private fun SearchGridTile(media: Media, format: PosterFormat, cols: Int, mediaLabelStyle: MediaLabelStyle, onClick: () -> Unit) {
    val ui = Adapters.mapMediaToUi(media)
    val widthPct = if (cols == 1) 1f else if (cols == 2) 0.487f else 0.314f
    val modifier = Modifier.fillMaxWidth(widthPct).clickable(onClick = onClick)
    if (format == PosterFormat.V) {
        Poster(title = ui.title, uri = ui.posterUrl, ratio = PosterRatio.R2_3, radius = 8.dp, titleSize = 10.5.sp, modifier = modifier)
    } else {
        val cardLogo = if (mediaLabelStyle == MediaLabelStyle.LOGO) Adapters.logoUri(media) else null
        Poster(title = ui.title, uri = ui.backdropUrl, ratio = PosterRatio.R16_9, radius = 10.dp, titleSize = if (cols == 1) 15.sp else 12.sp, modifier = modifier) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
                Column {
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

@Composable
private fun LicenceGridCard(license: UiLicence, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.487f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick),
    ) {
        Poster(title = license.title, uri = license.iconUrl, ratio = PosterRatio.R16_10, titleSize = 12.5.sp, radius = 6.dp)
    }
}
