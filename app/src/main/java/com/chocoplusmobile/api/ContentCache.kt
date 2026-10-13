package com.chocoplusmobile.api

/** What HomeScreen fetches on mount — bundled into one cache slot rather
 * than three, since they're always fetched (and invalidated) together. */
data class HomeContent(val news: List<News>, val licenses: List<License>, val selections: List<Selection>)

/** What BrowseScreen (Films or Séries) fetches on mount — one slot per
 * mediaType, since Films and Séries are genuinely different data, not the
 * same screen instance switching content. */
data class BrowseContent(val hero: Media, val running: NewsVideoRunning?, val selections: List<Selection>)

/**
 * Home/Films/Séries each land on a fresh network fetch every time their
 * composable re-enters composition — including a plain tab switch, since
 * Compose Navigation only preserves rememberSaveable-backed UI state across
 * that, not a screen's own `remember { ApiData() }` — so switching Accueil →
 * Films → Accueil used to mean two extra round-trips just to redraw
 * something already fetched seconds earlier. Caching the result here for
 * the rest of the process (same idea as JobFilterCache, just three slots
 * rather than one) means a screen only ever re-fetches when explicitly
 * asked to (pull-to-refresh, the error retry button) — see how HomeScreen/
 * BrowseScreen wrap rememberApiData's own refresh() to clear the relevant
 * slot first, so a manual refresh still forces a genuine refetch instead of
 * just handing back what's already cached.
 */
object ContentCache {
    @Volatile var home: HomeContent? = null
    @Volatile var films: BrowseContent? = null
    @Volatile var series: BrowseContent? = null
}
