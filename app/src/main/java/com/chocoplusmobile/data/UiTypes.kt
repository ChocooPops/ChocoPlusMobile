package com.chocoplusmobile.data

// UI-facing view-models. Screens render these, never raw API DTOs directly —
// Adapters.kt maps the real API responses (api/Types.kt) into this shape,
// so a change in the API's field names only touches the adapter. Mirrors
// the RN app's src/data/types.ts one-for-one.

enum class UiMediaType { FILM, SERIE }

/** A cast or crew member — same shape either way, `role` just means
 * different things (character name for cast, job title for crew). `job` is
 * the raw API job code (e.g. "ACTOR", "DIRECTOR") kept alongside the
 * already-humanized `role`, needed to build an exact job+person catalog
 * filter when tapping this person on a detail page. */
data class UiCreditPerson(
    val id: Int,
    val name: String,
    val role: String,
    val job: String,
    val photoUrl: String?,
)

/** A genre/keyword chip on a detail page — `id` is only ever present for
 * genres (categories); the API never returns an id for keywords (Media/
 * MediaInfo only carry their names), so tapping a keyword chip falls back
 * to a name-match catalog filter instead of an exact id one. */
data class UiTag(val id: Int?, val name: String)

data class UiSeason(
    val id: Int,
    val seasonNumber: Int,
    val name: String,
    val posterUrl: String?,
)

data class UiMedia(
    val id: Int,
    val title: String,
    val year: Int?,
    val category: String,
    val type: UiMediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    /** Resume-watching progress, 0-100 — only movies carry this from the API. */
    val progress: Double?,
    val synopsis: String?,
    val director: String?,
    val creators: String?,
    val genres: List<UiTag>,
    val keywords: List<UiTag>,
    val cast: List<UiCreditPerson>,
    val crew: List<UiCreditPerson>,
    val duration: String?,
    val quality: String?,
    val seasonsCount: Int?,
    val seasons: List<UiSeason>?,
)

data class UiEpisode(
    val id: Int,
    val seasonId: Int,
    val seriesId: Int,
    val number: Int,
    val title: String,
    val description: String,
    val duration: String,
    val airDate: String,
    val progress: Double,
    val posterUrl: String?,
)

/** A single key/value line in MetadataSheet — shared between Adapters.kt
 * (which builds them from the full movie/:id · series/:id record) and the
 * composable itself. */
data class MetadataRow(val k: String, val v: String)

data class UiLicence(
    val id: Int,
    val title: String,
    val count: Int,
    val backdropUrl: String?,
    val logoUrl: String?,
    /** Some licenses (e.g. the Home page's curated "directed by" set) only
     * have this populated — backdrop/logo come back null. */
    val iconUrl: String?,
)
