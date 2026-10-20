@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.Episode
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.data.Adapters
import com.chocoplusmobile.data.ImageQuality
import com.chocoplusmobile.data.ImageCategory
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.tCount

/**
 * "Toutes les métadonnées" for PosterPreviewSheet's info button (and now
 * MovieDetailScreen/SerieDetailScreen's own metadata button — see their
 * openMeta()) — mirrors the RN app's components/MediaMetadataSheet.tsx, then
 * extended per explicit request to actually surface everything GET
 * /movie/:id and /series/:id return: every image tied to the title (logo,
 * every normal/special/horizontal poster at a larger size, background),
 * every alternate-language title, the full cast and crew (each as its own
 * square PersonCard, not just a comma-joined name list), and — for a series
 * — a season switcher with that season's full episode list, on top of the
 * plain key/value rows MetadataSheet already renders elsewhere. Everything
 * here comes from the one full record PosterPreviewSheet/the detail screens
 * already fetch — no extra requests.
 */
@Composable
fun MediaMetadataSheet(
    visible: Boolean,
    onClose: () -> Unit,
    loading: Boolean,
    error: String?,
    media: Media?,
    mediaType: MediaType,
) {
    var seasonIdx by remember(media?.id) { mutableIntStateOf(0) }
    val isSeries = mediaType == MediaType.SERIES
    val seasons = remember(media) { media?.seasons?.sortedBy { it.seasonNumber } ?: emptyList() }
    val selectedSeason = seasons.getOrNull(seasonIdx.coerceIn(0, (seasons.size - 1).coerceAtLeast(0)))

    ChocoBottomSheet(visible = visible, onClose = onClose, maxHeightPct = 0.88f, scrollable = false) {
        when {
            loading -> Column {
                MetaSheetTitle()
                CircularProgressIndicator(color = ChocoColors.accent, modifier = Modifier.padding(vertical = 24.dp))
            }
            error != null -> Column {
                MetaSheetTitle()
                Text(error, color = androidx.compose.ui.graphics.Color(0xFFFF9C9C), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 12.dp))
                PrimaryButton(label = t(Tr.CLOSE), variant = ButtonVariant.OUTLINE, onClick = onClose, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            media != null -> {
                val logo = Adapters.logoUri(media)
                val background = Adapters.mediaBackgroundUri(media)
                val normalPosters = Adapters.allPosterUris(media, Adapters.PosterKey.NORMAL)
                val specialPosters = Adapters.allPosterUris(media, Adapters.PosterKey.SPECIAL)
                val horizontalPosters = Adapters.allPosterUris(media, Adapters.PosterKey.HORIZONTAL)
                val rows = if (isSeries) Adapters.seriesMetadataRows(media) else Adapters.movieMetadataRows(media)
                val episodes = selectedSeason?.episodes ?: emptyList()

                LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp)) {
                    item {
                        Column {
                            MetaSheetTitle()
                            if (logo != null) {
                                MetaSection(t(Tr.LOGO).uppercase()) {
                                    Box(modifier = Modifier.background(ChocoColors.surfaceAlt2, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                                        LogoImage(uri = logo, height = 54.dp, maxWidth = 200.dp)
                                    }
                                }
                            }
                            if (media.otherTitles.isNotEmpty()) {
                                MetaSection(t(Tr.META_ALT_TITLES).uppercase()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        media.otherTitles.forEach { t ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(t.iso6391.uppercase(), color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp, modifier = Modifier.width(28.dp))
                                                Text(t.title, color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            // Enlarged per explicit request — these used to be
                            // the same thumbnail size as a catalogue grid tile,
                            // too small to actually judge a poster/background by.
                            ImageGalleryRow(t(Tr.META_POSTERS), normalPosters, ratio = PosterRatio.R2_3, itemWidth = 150.dp)
                            ImageGalleryRow(t(Tr.META_SPECIAL_POSTERS), specialPosters, ratio = PosterRatio.R2_3, itemWidth = 150.dp)
                            ImageGalleryRow(t(Tr.META_HORIZONTAL_POSTERS), horizontalPosters, ratio = PosterRatio.R16_9, itemWidth = 260.dp)
                            ImageGalleryRow(t(Tr.META_BACKGROUND), background?.let { listOf(it) } ?: emptyList(), ratio = PosterRatio.R16_9, itemWidth = 260.dp)

                            val cast = media.credits.filter { it.job == "ACTOR" }
                            val crew = media.credits.filter { it.job != "ACTOR" }
                            if (cast.isNotEmpty()) {
                                MetaSection(t(Tr.META_CAST).uppercase()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        cast.forEach { c ->
                                            PersonCard(name = c.fullName, role = c.character, photoUrl = ImageQuality.withSize(c.srcPoster, ImageCategory.CREDIT_PHOTO))
                                        }
                                    }
                                }
                            }
                            if (crew.isNotEmpty()) {
                                MetaSection(t(Tr.DETAIL_CREW).uppercase()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        crew.forEach { c ->
                                            PersonCard(name = c.fullName, role = Adapters.humanizeJob(c.job), photoUrl = ImageQuality.withSize(c.srcPoster, ImageCategory.CREDIT_PHOTO))
                                        }
                                    }
                                }
                            }

                            if (isSeries && seasons.isNotEmpty()) {
                                MetaSection(t(Tr.META_SEASONS).uppercase()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        items(seasons.size) { idx ->
                                            val s = seasons[idx]
                                            ChocoChip(label = s.name.ifBlank { t(Tr.SEASON_N, s.seasonNumber) }, active = idx == seasonIdx, onClick = { seasonIdx = idx }, small = true)
                                        }
                                    }
                                    selectedSeason?.let { season ->
                                        Row(modifier = Modifier.padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Poster(
                                                title = season.name,
                                                uri = ImageQuality.withSize(season.srcPoster, ImageCategory.POSTER_VERTICAL),
                                                ratio = PosterRatio.R2_3,
                                                radius = 8.dp,
                                                titleSize = 10.sp,
                                                modifier = Modifier.width(78.dp),
                                            )
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                                Text(season.name, color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 13.5.sp)
                                                Text(
                                                    tCount(Tr.EPISODES_COUNT_ONE, Tr.EPISODES_COUNT_MANY, season.episodes.size),
                                                    color = ChocoColors.textMuted,
                                                    fontFamily = ChocoFonts.bodyRegular,
                                                    fontSize = 11.5.sp,
                                                    modifier = Modifier.padding(top = 3.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    t(Tr.EPISODES).uppercase(),
                                    color = ChocoColors.textFaint,
                                    fontFamily = ChocoFonts.bodyBold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 1.1.sp,
                                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp).padding(bottom = 9.dp, top = 6.dp),
                                )
                            }
                        }
                    }

                    items(episodes.size) { idx -> EpisodeCard(episodes[idx]) }

                    item {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                rows.forEach { r -> MetadataRowLine(r) }
                            }
                            PrimaryButton(label = t(Tr.CLOSE), variant = ButtonVariant.OUTLINE, onClick = onClose, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaSheetTitle() {
    Text(t(Tr.META_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 19.sp)
    Text(
        t(Tr.META_ADMIN_ONLY),
        color = ChocoColors.textMuted,
        fontFamily = ChocoFonts.bodyRegular,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
    )
}

@Composable
private fun MetaSection(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(label, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 9.dp))
        content()
    }
}

@Composable
private fun ImageGalleryRow(label: String, uris: List<String>, ratio: Float, itemWidth: androidx.compose.ui.unit.Dp) {
    if (uris.isEmpty()) return
    MetaSection(label) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(uris.size) { idx ->
                Poster(title = "", uri = uris[idx], ratio = ratio, radius = 8.dp, hideFallbackTitle = true, modifier = Modifier.width(itemWidth))
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode) {
    val ui = remember(episode) { Adapters.mapEpisodeToUi(episode) }
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Poster(title = ui.title, uri = ui.posterUrl, ratio = PosterRatio.R16_9, radius = 7.dp, titleSize = 8.5.sp, modifier = Modifier.width(118.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${ui.number}. ${ui.title}", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 13.5.sp, lineHeight = 17.sp)
            val meta = listOfNotNull(ui.duration.ifBlank { null }, ui.airDate.ifBlank { null }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodySemiBold, fontSize = 10.5.sp, modifier = Modifier.padding(top = 5.dp))
            }
            if (ui.description.isNotEmpty()) {
                // Full, untruncated description — per "toutes ses méta données".
                Text(ui.description, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, lineHeight = 15.5.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
