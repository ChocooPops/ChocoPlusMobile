@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.data.ImageQualityLevel
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.state.AppLanguage
import com.chocoplusmobile.state.FormatPage
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.MediaLabelStyle
import com.chocoplusmobile.state.PosterFormat
import com.chocoplusmobile.state.StartPage
import com.chocoplusmobile.state.VideoDecoding
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

// Language names are shown as autonyms (each in its own language, e.g.
// "English" even while the app itself is in French) regardless of the
// currently active app language — same convention every OS/app language
// picker uses, so a reader who picked the wrong one by mistake can still
// find their way back. Not run through t().
private val LANGUAGES = listOf(AppLanguage.FR to "Français", AppLanguage.EN to "English", AppLanguage.JA to "日本語")

private val FORMAT_PAGES_ORDER = listOf(FormatPage.HOME, FormatPage.FILMS, FormatPage.SERIES, FormatPage.CATALOGUE, FormatPage.SEARCH, FormatPage.MYLIST)

@Composable
private fun formatPageLabel(page: FormatPage): String = when (page) {
    FormatPage.HOME -> t(Tr.PAGE_HOME)
    FormatPage.FILMS -> t(Tr.PAGE_FILMS)
    FormatPage.SERIES -> t(Tr.PAGE_SERIES)
    FormatPage.CATALOGUE -> t(Tr.PAGE_CATALOGUE)
    FormatPage.SEARCH -> t(Tr.PAGE_SEARCH)
    FormatPage.MYLIST -> t(Tr.PAGE_MYLIST)
    FormatPage.LICENSE_DETAIL -> "" // never listed here
}

private data class ToggleDef(val label: Tr, val desc: Tr, val get: (com.chocoplusmobile.state.SettingsToggles) -> Boolean, val toggle: (com.chocoplusmobile.state.AppViewModel) -> Unit)
private val TOGGLES = listOf(
    ToggleDef(Tr.SETTINGS_AUTOPLAY_LABEL, Tr.SETTINGS_AUTOPLAY_DESC, { it.autoplay }, { it.toggleAutoplay() }),
)

private val START_PAGES = listOf(StartPage.ACCUEIL, StartPage.FILMS, StartPage.SERIES, StartPage.CATALOGUE, StartPage.MA_LISTE, StartPage.PROFIL, StartPage.RECHERCHE)

@Composable
private fun startPageLabel(page: StartPage): String = when (page) {
    StartPage.ACCUEIL -> t(Tr.PAGE_HOME)
    StartPage.FILMS -> t(Tr.PAGE_FILMS)
    StartPage.SERIES -> t(Tr.PAGE_SERIES)
    StartPage.CATALOGUE -> t(Tr.PAGE_CATALOGUE)
    StartPage.MA_LISTE -> t(Tr.PAGE_MYLIST)
    StartPage.PROFIL -> t(Tr.PAGE_PROFILE)
    StartPage.RECHERCHE -> t(Tr.PAGE_SEARCH)
}

private data class QualityDef(val level: ImageQualityLevel, val label: Tr, val desc: Tr)
private val IMAGE_QUALITY_LEVELS = listOf(
    QualityDef(ImageQualityLevel.LOW, Tr.QUALITY_LOW, Tr.QUALITY_LOW_DESC),
    QualityDef(ImageQualityLevel.MEDIUM, Tr.QUALITY_MEDIUM, Tr.QUALITY_MEDIUM_DESC),
    QualityDef(ImageQualityLevel.HIGH, Tr.QUALITY_HIGH, Tr.QUALITY_HIGH_DESC),
    QualityDef(ImageQualityLevel.ORIGINAL, Tr.QUALITY_ORIGINAL, Tr.QUALITY_ORIGINAL_DESC),
)

/** Mirrors the RN app's screens/Profile/SettingsScreen.tsx. */
@Composable
fun SettingsScreen() {
    val navController = LocalNavController.current
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val settings = prefs.settings

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
        ScreenHeaderBack(title = t(Tr.SETTINGS_TITLE), onBack = { navController.popBackStack() })

        SectionLabel(t(Tr.SETTINGS_SECTION_LANGUAGE), topPadding = 20.dp)
        SettingsCard(padded = true) {
            Text(t(Tr.SETTINGS_LANGUAGE_APP_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
            Text(t(Tr.SETTINGS_LANGUAGE_APP_DESC), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LANGUAGES.forEach { (lang, label) ->
                    FormatOption(label = label, icon = AppIcon.of("translate"), active = settings.language == lang, onClick = { appViewModel.setLanguage(lang) }, modifier = Modifier.weight(1f))
                }
            }
        }

        SectionLabel(t(Tr.SETTINGS_SECTION_DISPLAY), topPadding = 20.dp)
        SettingsCard {
            SettingsRow {
                Text(t(Tr.SETTINGS_FORMAT_PAGE_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                Text(t(Tr.SETTINGS_FORMAT_PAGE_DESC), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                Column(modifier = Modifier.padding(top = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FORMAT_PAGES_ORDER.forEach { page ->
                        val fmt = prefs.formatByPage.get(page)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatPageLabel(page), color = Color(0xBFFFFFFF), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.5.sp, modifier = Modifier.padding(end = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                FormatOption(t(Tr.FORMAT_VERTICAL), AppIcon.of("crop_portrait"), fmt == PosterFormat.V, { if (fmt != PosterFormat.V) appViewModel.toggleFormat(page) }, Modifier.weight(1f))
                                FormatOption(t(Tr.FORMAT_HORIZONTAL), AppIcon.of("crop_landscape"), fmt == PosterFormat.H, { if (fmt != PosterFormat.H) appViewModel.toggleFormat(page) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            SettingsRow {
                Text(t(Tr.SETTINGS_IMAGE_QUALITY_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                Text(t(Tr.SETTINGS_IMAGE_QUALITY_DESC), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                QualityPicker(settings.imageQuality) { appViewModel.setImageQuality(it) }
            }
            SettingsRow {
                Text(t(Tr.SETTINGS_HERO_QUALITY_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                Text(t(Tr.SETTINGS_HERO_QUALITY_DESC), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                QualityPicker(settings.heroImageQuality) { appViewModel.setHeroImageQuality(it) }
            }
            SettingsRow {
                Text(t(Tr.SETTINGS_LOGO_OR_TITLE_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                Text(t(Tr.SETTINGS_LOGO_OR_TITLE_DESC), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormatOption(t(Tr.LOGO), AppIcon.of("image"), settings.mediaLabelStyle == MediaLabelStyle.LOGO, { appViewModel.setMediaLabelStyle(MediaLabelStyle.LOGO) }, Modifier.weight(1f))
                    FormatOption(t(Tr.TITLE_LABEL), AppIcon.of("title"), settings.mediaLabelStyle == MediaLabelStyle.TITLE, { appViewModel.setMediaLabelStyle(MediaLabelStyle.TITLE) }, Modifier.weight(1f))
                }
            }
            TOGGLES.forEach { toggleDef ->
                val on = toggleDef.get(settings.toggles)
                SettingsRow(onClick = { toggleDef.toggle(appViewModel) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t(toggleDef.label), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                            Text(t(toggleDef.desc), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Row(modifier = Modifier.width(44.dp).height(26.dp).background(if (on) ChocoColors.accent else Color(0x24FFFFFF), RoundedCornerShape(99.dp)).padding(3.dp)) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(20.dp).background(if (on) ChocoColors.onAccent else Color(0xB3FFFFFF), androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                    }
                }
            }
        }

        SectionLabel(t(Tr.SETTINGS_SECTION_START), topPadding = 20.dp)
        SettingsCard(padded = true) {
            Text(t(Tr.SETTINGS_START_PAGE_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
            FlowRow(modifier = Modifier.padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                START_PAGES.forEach { p ->
                    val active = settings.startPage == p
                    Row(
                        modifier = Modifier
                            .background(if (active) ChocoColors.accent else Color.Transparent, RoundedCornerShape(99.dp))
                            .clickable { appViewModel.setStartPage(p) }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Text(startPageLabel(p), color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 12.sp)
                    }
                }
            }
        }

        SectionLabel(t(Tr.SETTINGS_SECTION_PLAYBACK), topPadding = 20.dp)
        SettingsCard {
            SettingsRow {
                Text(t(Tr.SETTINGS_DECODING_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                Text(
                    t(Tr.SETTINGS_DECODING_DESC),
                    color = ChocoColors.textMuted,
                    fontFamily = ChocoFonts.bodyRegular,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormatOption(t(Tr.DECODING_SOFTWARE), AppIcon.of("memory"), settings.videoDecoding == VideoDecoding.SOFTWARE, { appViewModel.setVideoDecoding(VideoDecoding.SOFTWARE) }, Modifier.weight(1f))
                    FormatOption(t(Tr.DECODING_HARDWARE), AppIcon.of("bolt"), settings.videoDecoding == VideoDecoding.HARDWARE, { appViewModel.setVideoDecoding(VideoDecoding.HARDWARE) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(text, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = topPadding, bottom = 9.dp))
}

@Composable
private fun SettingsCard(padded: Boolean = false, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ChocoColors.surfaceAlt, RoundedCornerShape(14.dp)).then(if (padded) Modifier.padding(15.dp) else Modifier)) { content() }
}

@Composable
private fun SettingsRow(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(15.dp)
            .background(Color.Transparent),
    ) { content() }
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ChocoColors.divider))
}

@Composable
private fun FormatOption(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(32.dp)
            .background(if (active) ChocoColors.accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, modifier = Modifier.size(16.dp))
        Text(label, color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, fontFamily = ChocoFonts.bodySemiBold, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun QualityPicker(value: ImageQualityLevel, onChange: (ImageQualityLevel) -> Unit) {
    Column(modifier = Modifier.padding(top = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IMAGE_QUALITY_LEVELS.forEach { lvl ->
            val active = value == lvl.level
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onChange(lvl.level) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                RadioRing(active)
                Column {
                    Text(t(lvl.label), color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.sp)
                    Text(t(lvl.desc), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun RadioRing(active: Boolean) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(19.dp)) {
        drawCircle(color = if (active) ChocoColors.accent else ChocoColors.borderStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f), radius = size.minDimension / 2 - 2f)
        if (active) drawCircle(color = ChocoColors.accent, radius = size.minDimension / 2 - 7f)
    }
}
