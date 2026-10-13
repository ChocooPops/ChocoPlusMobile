package com.chocoplusmobile.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

// ─────────────────────────────────────────────────────────────────────────
// Transcribed 1:1 from the RN app's src/api/types.ts, which itself mirrors
// "ChocoPlus API — Routes non-admin" (dto/*.interface.ts). Kept as plain
// @Serializable data classes so Retrofit's kotlinx.serialization converter
// can (de)serialize them directly — no manual parsing anywhere else in the
// app. The API's own `Poster` DTO is renamed `PosterSrc` to avoid clashing
// with the Poster composable, same as the RN side.
// ─────────────────────────────────────────────────────────────────────────

/** Some routes (e.g. `series/episode/:id`) return the raw MySQL row, where
 * a boolean column comes back as 0/1 instead of false/true — this accepts
 * either form (plus "0"/"1"/"true"/"false" strings) rather than failing
 * the whole response over one flag. */
object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeBoolean()
        val content = (element as? JsonPrimitive)?.content?.lowercase() ?: return false
        return content == "true" || (content.toDoubleOrNull()?.let { it != 0.0 } ?: false)
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

@Serializable
data class ReturnMessage(
    val id: Int = 0,
    val message: String = "",
    val state: Boolean = false,
)

@Serializable
enum class MediaType {
    MEDIA, MOVIE, SERIES, SEASON, EPISODE, CREDIT, SELECTION, LICENSE, CATEGORY, OTHER, ALL,
    // Added for Selection.createFrom — confirmed against the backend's own
    // media-type.enum.ts, which now has these two on top of the original set.
    HISTORIC_USER, LAST_RELEASE,
}

@Serializable
enum class StatState {
    NOT_WATCHED, IN_PROGRESS, FINISHED,
}

@Serializable
data class TranslationTitle(
    val title: String,
    @SerialName("iso_639_1") val iso6391: String,
)

@Serializable
data class CategorySimple(
    val id: Int,
    val tmdbId: Int = 0,
    val translationKey: String,
)

@Serializable
data class MediaCredit(
    val id: Int,
    val tmdbId: Int = 0,
    val fullName: String,
    val originalFullName: String = "",
    val character: String? = null,
    val srcPoster: String? = null,
    /** Free-form — the API only documents ~6 of the ~30+ values it actually
     * uses (the rest are TMDB's crew-department taxonomy verbatim). */
    val job: String,
    val episodeCount: Int? = null,
    val order: Int = 0,
)

/** The API's own `Poster` DTO — arrays of ready-to-use image URLs. */
@Serializable
data class PosterSrc(
    val normal: List<String> = emptyList(),
    val special: List<String> = emptyList(),
    val license: List<String> = emptyList(),
    val horizontal: List<String> = emptyList(),
)

/** Central entity: a movie OR a series — Movie/Series below just add a few
 * fields on top, mirroring the TS `interface Movie extends Media`. Retrofit
 * picks the concrete subtype per-endpoint (there's no polymorphic decoding
 * needed since the shape is known statically at each call site). */
@Serializable
data class Media(
    val id: Int,
    val title: String,
    val mediaLibraryId: String = "",
    val otherTitles: List<TranslationTitle> = emptyList(),
    val categories: List<CategorySimple> = emptyList(),
    val keyWord: List<String> = emptyList(),
    val description: String = "",
    val credits: List<MediaCredit> = emptyList(),
    val date: String = "",
    val startShow: String? = null,
    val endShow: String? = null,
    val srcPoster: PosterSrc = PosterSrc(),
    val srcLogo: String? = null,
    val srcBackgroundImage: String? = null,
    val mediaType: MediaType? = null,
    val path: String? = null,
    // Movie-only fields — absent (null) on a plain Media/Series payload.
    val resolution: String? = null,
    val duration: Long? = null,
    val watchProgress: Double? = null,
    val stateProgress: StatState? = null,
    // Series-only fields.
    val lastSeasonWatched: Int? = null,
    val seasons: List<Season>? = null,
)

@Serializable
data class Episode(
    val id: Int,
    val seriesId: Int = 0,
    val seasonId: Int = 0,
    val mediaLibraryId: String = "",
    val name: String = "",
    val episodeNumber: Int = 0,
    val description: String = "",
    val date: String = "",
    val duration: Long = 0,
    val resolution: String = "",
    val srcPoster: String? = null,
    @Serializable(with = LenientBooleanSerializer::class) val isRecent: Boolean = false,
    val watchProgress: Double = 0.0,
    val stateProgress: StatState? = null,
    val path: String? = null,
)

@Serializable
data class Season(
    val id: Int,
    val seriesId: Int = 0,
    val mediaLibraryId: String = "",
    val name: String = "",
    val seasonNumber: Int = 0,
    val srcPoster: String? = null,
    @Serializable(with = LenientBooleanSerializer::class) val isRecent: Boolean = false,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
enum class SelectionType {
    NORMAL_POSTER, SPECIAL_POSTER,
}

@Serializable
data class Selection(
    val id: Int,
    val name: String,
    val selectionType: SelectionType,
    val mediaList: List<Media> = emptyList(),
    val createFrom: MediaType? = null,
)

@Serializable
data class License(
    val id: Int,
    val name: String,
    val order: Int = 0,
    val position: Boolean = false,
    val srcIcon: String? = null,
    val srcLogo: String? = null,
    val srcBackground: String? = null,
    val mediaList: List<Media> = emptyList(),
    val selectionList: List<Selection> = emptyList(),
)

@Serializable
data class News(
    val id: Int,
    val srcBackground: String? = null,
    val orientation: Int = 0,
    val media: Media,
)

@Serializable
data class NewsVideoRunning(
    val id: Int,
    val mediaLibraryId: String = "",
    val srcBackground: String? = null,
    val startShow: String? = null,
    val endShow: String? = null,
    val activated: Boolean? = null,
    val path: String? = null,
    val media: Media,
)

@Serializable
data class MediaInfo(
    val id: Int,
    val casts: List<MediaCredit> = emptyList(),
    val crews: List<MediaCredit> = emptyList(),
    val categories: List<CategorySimple> = emptyList(),
    val keyWords: List<String> = emptyList(),
)

@Serializable
data class Support(
    val id: Int,
    val subject: String,
    val areaConcerned: List<String> = emptyList(),
    val description: String? = null,
)

@Serializable
enum class Role {
    ADMIN, USER, FAMILY, NOT_ACTIVATE, SUSPENDED,
}

@Serializable
data class User(
    val id: Int,
    val pseudo: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: Role = Role.USER,
    val dateBorn: String = "",
    val profilPhoto: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ProfilPhoto(
    val id: Int,
    val name: String,
)

@Serializable
data class TokenModel(
    @SerialName("access_token") val accessToken: String,
)

// ── /stat-user ──────────────────────────────────────────────────────────

@Serializable
data class CategoryStats(
    val categoryId: Int,
    val categoryName: String,
    val count: Int,
    val percentage: Double,
)

/** Shared by both getUserPreferredCategories (plain counts) and its
 * -Weighted sibling — `totalWatched` is a whole title count for the former
 * but a weighted fractional score for the latter (`parseFloat(...)`
 * server-side), so it has to be a Double here to decode either one. */
@Serializable
data class UserCategoryPreferences(
    val userId: Int,
    val totalWatched: Double,
    val categories: List<CategoryStats> = emptyList(),
)

@Serializable
data class WatchTimeBucket(
    val totalHours: Int = 0,
    val totalMinutes: Int = 0,
    val totalSeconds: Int = 0,
    val contentCount: Int = 0,
    val episodeCount: Int? = null,
)

@Serializable
data class WatchTimeStats(
    val userId: Int,
    val movies: WatchTimeBucket,
    val series: WatchTimeBucket,
    val total: WatchTimeBucket,
)

@Serializable
data class DataPoint(
    val period: String,
    val hours: Double,
    val movies: Int = 0,
    val series: Int = 0,
    val episodeCount: Int = 0,
)

@Serializable
data class WatchingStatsResponse(
    val userId: Int,
    val startDate: String = "",
    val periodType: String = "",
    val contentType: String = "",
    val data: List<DataPoint> = emptyList(),
)

@Serializable
data class TopMedia(
    val rank: Int,
    val mediaId: Int,
    val title: String,
    val mediaType: String,
    val description: String? = null,
    val date: String = "",
    val resolution: String? = null,
    val posterName: String? = null,
    val posterType: String? = null,
    val watchCount: Int,
    val totalProgress: Double = 0.0,
    val avgProgress: Double = 0.0,
)

@Serializable
data class TopMediaResponse(
    val userId: Int,
    val mediaType: String = "all",
    val topMedia: List<TopMedia> = emptyList(),
)

/** `getUserPreferredCategoriesByTime` returns a bare array (untyped on the
 * backend, `Promise<any>`) of these — the snake_case field names are the
 * literal SQL column aliases (`ROUND(...) as total_time_hours` etc.),
 * carried straight through to JSON. */
@Serializable
data class CategoryTimeStat(
    val categoryId: Int,
    val categoryName: String,
    val count: Int,
    @SerialName("total_time_seconds") val totalTimeSeconds: Double = 0.0,
    @SerialName("total_time_minutes") val totalTimeMinutes: Double = 0.0,
    @SerialName("total_time_hours") val totalTimeHours: Double = 0.0,
    val percentage: Double = 0.0,
)
