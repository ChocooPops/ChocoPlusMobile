@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.PosterFormatUtils
import com.chocoplusmobile.navigation.NavCache
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.ui.components.EmptyState
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.LogoImage
import com.chocoplusmobile.ui.components.Poster
import com.chocoplusmobile.ui.components.PosterRatio
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch

/**
 * Deliberately mirrors Recherche's results list — same format/alignment
 * system, same compact row for v+single, same logo-or-title-bottom-left/
 * no-date treatment for horizontal tiles, down to dropping the delete and
 * play buttons and the progress bar. Mirrors the RN app's
 * screens/MyList/MyListScreen.tsx.
 */
@Composable
fun MyListScreen() {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val prefs by appViewModel.prefs.collectAsState()
    val format = prefs.formatByPage.mylist
    val alignment = prefs.alignmentByPage.current(FormatPage.MYLIST, format)
    val cols = PosterFormatUtils.gridColumns(format, alignment)
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(listOf<Media>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                items = NetworkModule.safeCall { NetworkModule.api.getMyList() }
            } catch (e: Exception) {
                error = e.message ?: "Erreur réseau"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun openMedia(m: Media) {
        NavCache.pendingMedia = m
        navController.navigate(if (m.mediaType == MediaType.SERIES) "serieDetail" else "movieDetail")
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        TopBar(
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
            Text("Ma liste", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 24.sp, letterSpacing = (-0.6).sp)
            Text("${items.size} titre${if (items.size > 1) "s" else ""} · visible de toi seul", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))

            when {
                loading -> LoadingState()
                error != null -> ErrorState(message = error!!, onRetry = ::load)
                else -> {
                    MediaCollection(items = items, format = format, cols = cols, mediaLabelStyle = prefs.settings.mediaLabelStyle, onOpen = ::openMedia)
                    if (items.isEmpty()) {
                        EmptyState(
                            icon = AppIcon.of("bookmark_add"),
                            title = "Rien à grignoter ici",
                            description = "Ajoute un film ou une série depuis sa fiche pour la garder sous la main.",
                            ctaLabel = "Parcourir le catalogue",
                            onPressCta = { navController.navigate("catalogue") },
                        )
                    }
                }
            }
        }
    }
}

/** Ma liste's grid/list rendering of a set of titles, following the page's
 * format/alignment toggles — shared with the Downloads screen, which shows
 * its (offline) titles exactly the same way. */
@Composable
fun MediaCollection(items: List<Media>, format: PosterFormat, cols: Int, mediaLabelStyle: MediaLabelStyle, onOpen: (Media) -> Unit) {
    if (format == PosterFormat.V && cols == 1) {
        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            items.forEach { m -> MyListCompactRow(m, onClick = { onOpen(m) }) }
        }
    } else {
        val widthPct = if (cols == 1) 1f else if (cols == 2) 0.487f else 0.314f
        FlowRow(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items.forEach { m ->
                val ui = Adapters.mapMediaToUi(m)
                val tileModifier = Modifier.fillMaxWidth(widthPct).clickable { onOpen(m) }
                if (format == PosterFormat.V) {
                    Poster(title = ui.title, uri = ui.posterUrl, ratio = PosterRatio.R2_3, radius = 8.dp, titleSize = 10.5.sp, badge = if (ui.type == com.chocoplusmobile.data.UiMediaType.SERIE) "SÉRIE" else null, modifier = tileModifier)
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
}

@Composable
private fun MyListCompactRow(media: Media, onClick: () -> Unit) {
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
