package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.CategoryTimeStat
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.TopMediaResponse
import com.chocoplusmobile.api.UserCategoryPreferences
import com.chocoplusmobile.api.WatchTimeStats
import com.chocoplusmobile.api.WatchingStatsResponse
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ChocoProgressBar
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

private val MEDIA_FILTERS = listOf("Tout" to "all", "Films" to "MOVIE", "Séries" to "SERIES")
private val DONUT_COLORS = listOf(ChocoColors.accent, Color(0xFFC8A11A), Color(0x80FFFFFF), Color(0x4DFFFFFF), Color(0x29FFFFFF))

/** The 3 ways the backend can break down "which categories does this user
 * watch most" — same category set, different weighting. The user picks
 * which one the donut reflects. */
private enum class CategoryStatMode(val label: String) {
    COUNT("Titres"),
    WEIGHTED("Pondéré"),
    TIME("Temps"),
}

private data class CategorySegment(val name: String, val percentage: Double)

private data class StatsBundle(
    val watchTime: WatchTimeStats,
    val history: WatchingStatsResponse,
    val topMedia: TopMediaResponse,
    val preferencesCount: UserCategoryPreferences,
    val preferencesWeighted: UserCategoryPreferences,
    val preferencesTime: List<CategoryTimeStat>,
)

/** Mirrors the RN app's screens/Profile/StatsScreen.tsx. */
@Composable
fun StatsScreen() {
    val navController = LocalNavController.current
    var mediaFilter by remember { mutableStateOf("all") }
    var categoryMode by remember { mutableStateOf(CategoryStatMode.COUNT) }
    var data by remember { mutableStateOf<StatsBundle?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(mediaFilter, retryTick) {
        loading = true
        error = null
        try {
            val contentType = when (mediaFilter) { "MOVIE" -> "movies"; "SERIES" -> "series"; else -> "all" }
            // 14 days back so the "7 derniers jours" bar chart below always
            // has a full week of daily buckets to take, even ones with zero
            // activity on the most recent day or two.
            val startDate = java.time.LocalDate.now().minusDays(14).toString()
            coroutineScope {
                val wt = async { NetworkModule.safeCall { NetworkModule.api.getWatchTime() } }
                val hist = async { NetworkModule.safeCall { NetworkModule.api.getWatchingHistory(userId = 0, startDate = startDate, periodType = "day", contentType = contentType) } }
                val top = async { NetworkModule.safeCall { NetworkModule.api.getTopMedia(0, mediaFilter) } }
                val prefsCount = async { NetworkModule.safeCall { NetworkModule.api.getCategoryPreferences() } }
                val prefsWeighted = async { NetworkModule.safeCall { NetworkModule.api.getCategoryPreferencesWeighted() } }
                val prefsTime = async { NetworkModule.safeCall { NetworkModule.api.getCategoryPreferencesByTime() } }
                data = StatsBundle(wt.await(), hist.await(), top.await(), prefsCount.await(), prefsWeighted.await(), prefsTime.await())
            }
        } catch (e: Exception) {
            error = e.message ?: "Erreur réseau"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
        ScreenHeaderBack(title = "Statistiques", onBack = { navController.popBackStack() })

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MEDIA_FILTERS.forEach { (label, value) ->
                val active = mediaFilter == value
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(if (active) ChocoColors.accent else Color.Transparent, RoundedCornerShape(9.dp))
                        .clickable { mediaFilter = value },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 12.5.sp)
                }
            }
        }

        when {
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!, onRetry = { retryTick++ })
            data != null -> {
                val d = data!!
                val bars = d.history.data.takeLast(7)
                val maxBar = (bars.maxOfOrNull { it.hours } ?: 1.0).coerceAtLeast(1.0)

                StatCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Heures de visionnage", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 14.5.sp)
                        Text("7 derniers jours", color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp)
                    }
                    Text("${d.watchTime.total.totalHours} h ${d.watchTime.total.totalMinutes.toString().padStart(2, '0')}", color = ChocoColors.accent, fontFamily = ChocoFonts.headingExtraBold, fontSize = 32.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "${d.watchTime.movies.contentCount} films · ${d.watchTime.series.contentCount} séries · ${d.watchTime.series.episodeCount ?: 0} épisodes",
                        color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 16.dp),
                    )
                    if (bars.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().height(118.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                            bars.forEach { b ->
                                Column(modifier = Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Text("${"%.1f".format(b.hours)}h", color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 9.5.sp)
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 7.dp)
                                            .fillMaxWidth()
                                            .height((((b.hours / maxBar) * 88).roundToInt().coerceAtLeast(4)).dp)
                                            .background(if (b.hours >= maxBar * 0.6) ChocoColors.accent else Color(0x59FFD301), RoundedCornerShape(5.dp)),
                                    )
                                    Text(b.period, color = ChocoColors.textTertiary, fontFamily = ChocoFonts.bodySemiBold, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                                }
                            }
                        }
                    }
                }

                if (d.topMedia.topMedia.isNotEmpty()) {
                    StatCard {
                        Text("Top contenus par visionnages", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 14.5.sp, modifier = Modifier.padding(bottom = 14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            d.topMedia.topMedia.take(5).forEachIndexed { i, t ->
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(t.title, color = Color(0xD9FFFFFF), fontFamily = ChocoFonts.bodySemiBold, fontSize = 12.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                        Text("${t.watchCount}×", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodySemiBold, fontSize = 11.sp)
                                    }
                                    ChocoProgressBar(progress = t.avgProgress, height = 6.dp, fillColor = if (i == 0) ChocoColors.accent else Color(0x66FFD301))
                                }
                            }
                        }
                    }
                }

                // Same category set, 3 different weightings the user can
                // switch between — count of titles touched, weighted by how
                // much of each was actually watched, or real time spent.
                val rawSegments = when (categoryMode) {
                    CategoryStatMode.COUNT -> d.preferencesCount.categories.map { CategorySegment(it.categoryName, it.percentage) }
                    CategoryStatMode.WEIGHTED -> d.preferencesWeighted.categories.map { CategorySegment(it.categoryName, it.percentage) }
                    CategoryStatMode.TIME -> d.preferencesTime.map { CategorySegment(it.categoryName, it.percentage) }
                }.take(5)
                val donutSegments = run {
                    var acc = 0.0
                    rawSegments.mapIndexed { i, c ->
                        val seg = Triple(c, DONUT_COLORS[i % DONUT_COLORS.size], acc)
                        acc += c.percentage
                        seg
                    }
                }
                val (centerValue, centerCaption) = when (categoryMode) {
                    CategoryStatMode.COUNT -> d.preferencesCount.totalWatched.roundToInt().toString() to "titres"
                    CategoryStatMode.WEIGHTED -> d.preferencesWeighted.totalWatched.roundToInt().toString() to "score"
                    CategoryStatMode.TIME -> d.preferencesTime.sumOf { it.totalTimeHours }.roundToInt().toString() to "heures"
                }
                if (donutSegments.isNotEmpty()) {
                    StatCard {
                        Text("Catégories les plus visionnées", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 14.5.sp, modifier = Modifier.padding(bottom = 14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                            CategoryStatMode.entries.forEach { mode ->
                                val active = categoryMode == mode
                                Row(
                                    modifier = Modifier
                                        .background(if (active) ChocoColors.accent else Color(0x14FFFFFF), RoundedCornerShape(99.dp))
                                        .clickable { categoryMode = mode }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(mode.label, color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(118.dp), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(118.dp)) {
                                    val stroke = 15.dp.toPx()
                                    val radius = (size.minDimension - stroke) / 2
                                    val topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
                                    val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                                    drawArc(color = Color(0x14FFFFFF), startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                                    donutSegments.forEach { (c, color, accBefore) ->
                                        val sweep = (c.percentage / 100.0 * 360.0).toFloat()
                                        val start = -90f + (accBefore / 100.0 * 360.0).toFloat()
                                        drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(centerValue, color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 17.sp)
                                    Text(centerCaption, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 9.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                donutSegments.forEach { (c, color, _) ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
                                        Text(c.name, color = Color(0xB8FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                        Text("${c.percentage.roundToInt()} %", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyBold, fontSize = 11.sp)
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
private fun StatCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(ChocoColors.surfaceAlt, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        content()
    }
}
