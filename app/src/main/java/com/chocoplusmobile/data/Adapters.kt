package com.chocoplusmobile.data

import com.chocoplusmobile.api.Episode
import com.chocoplusmobile.api.License
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaCredit
import com.chocoplusmobile.api.MediaInfo
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.StatState
import com.chocoplusmobile.i18n.AppLocale
import com.chocoplusmobile.i18n.ContentTranslations
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.text

/** Mirrors the RN app's api/adapters.ts one-for-one — maps the real API
 * responses (api/Types.kt) into the UI-facing view-models above. */
object Adapters {

    private fun firstOf(list: List<String>?): String? = list?.firstOrNull()

    fun posterUri(media: Media): String? {
        val raw = firstOf(media.srcPoster.normal) ?: firstOf(media.srcPoster.special)
        return ImageQuality.withSize(raw, ImageCategory.POSTER_VERTICAL)
    }

    /** The "special" vertical poster variant — used for SPECIAL_POSTER
     * selections instead of the normal one. Falls back to normal if a title
     * in a special selection doesn't actually have a special poster. */
    fun specialPosterUri(media: Media): String? {
        val raw = firstOf(media.srcPoster.special) ?: firstOf(media.srcPoster.normal)
        return ImageQuality.withSize(raw, ImageCategory.POSTER_VERTICAL)
    }

    /** The "license" vertical poster variant — used for LicenseDetailScreen's
     * "Contenu principal" row instead of the normal one. */
    fun licenseMediaPosterUri(media: Media): String? {
        val raw = firstOf(media.srcPoster.license) ?: firstOf(media.srcPoster.normal)
        return ImageQuality.withSize(raw, ImageCategory.POSTER_VERTICAL)
    }

    fun backdropUri(media: Media): String? {
        val raw = media.srcBackgroundImage ?: firstOf(media.srcPoster.horizontal)
        return ImageQuality.withSize(raw, ImageCategory.BACKDROP)
    }

    /** Every image in one PosterSrc array, resized — unlike posterUri()/
     * specialPosterUri() (which each pick a single one for list/grid
     * display), a title can have several normal/special/horizontal posters.
     * The metadata gallery surfaces all of them instead of just the first. */
    fun allPosterUris(media: Media, key: PosterKey): List<String> {
        val category = if (key == PosterKey.HORIZONTAL) ImageCategory.BACKDROP else ImageCategory.POSTER_VERTICAL
        val list = when (key) {
            PosterKey.NORMAL -> media.srcPoster.normal
            PosterKey.SPECIAL -> media.srcPoster.special
            PosterKey.HORIZONTAL -> media.srcPoster.horizontal
        }
        return list.mapNotNull { ImageQuality.withSize(it, category) }
    }

    enum class PosterKey { NORMAL, SPECIAL, HORIZONTAL }

    fun mediaBackgroundUri(media: Media): String? = ImageQuality.withSize(media.srcBackgroundImage, ImageCategory.BACKDROP)

    /** Same source as backdropUri, but for the large header banners —
     * governed by the separate "hero" quality setting. */
    fun heroBackdropUri(media: Media): String? {
        val raw = media.srcBackgroundImage ?: firstOf(media.srcPoster.horizontal)
        return ImageQuality.withSize(raw, ImageCategory.HERO_BACKDROP)
    }

    fun heroLogoUri(media: Media): String? = ImageQuality.withSize(media.srcLogo, ImageCategory.HERO_LOGO)

    fun logoUri(media: Media): String? = ImageQuality.withSize(media.srcLogo, ImageCategory.LOGO)

    fun episodePosterUri(episode: Episode): String? = ImageQuality.withSize(episode.srcPoster, ImageCategory.EPISODE_POSTER)

    /** `CategorySimple.translationKey` is the genre's plain uppercase name
     * (e.g. "ACTION", "SCIENCE FICTION") — looked up in ContentTranslations
     * for the current Settings > language, falling back to a best-effort
     * formatted version of the raw key for any genre that table doesn't
     * know yet. */
    fun humanizeKey(key: String): String {
        ContentTranslations.categories[key.trim().uppercase()]?.get(AppLocale.current)?.let { return it }
        val last = key.substringAfterLast('/').substringAfterLast('.')
        return last.replace(Regex("[_-]+"), " ")
            .split(" ")
            .joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
    }

    private fun creditsByJob(media: Media, job: String): List<String> =
        media.credits.filter { it.job == job }.map { it.fullName }

    /** A single credit can hold several jobs joined with "\" (e.g. "DIRECTOR
     * \ SCREENPLAY") — the API's job strings are TMDB's crew-department
     * taxonomy verbatim (e.g. "ORIGINAL MUSIC COMPOSER"), covered by
     * ContentTranslations.jobs for the current Settings > language; falls
     * back to a Title Case version of the raw string for any job that table
     * doesn't know yet.
     *
     * Public since detail screens need it too — to label a catalog filter
     * built from tapping a crew member's chip (their `role` is already
     * humanized, but for cast it's the character name, not the job). */
    fun humanizeJob(job: String): String =
        job.split("\\").joinToString(" · ") { part ->
            val key = part.trim().uppercase()
            ContentTranslations.jobs[key]?.get(AppLocale.current)
                ?: key.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::titlecase) }
        }

    private fun mapCredit(c: MediaCredit): UiCreditPerson = UiCreditPerson(
        id = c.id,
        name = c.fullName,
        role = if (c.job == "ACTOR") c.character ?: "" else humanizeJob(c.job),
        job = c.job,
        photoUrl = ImageQuality.withSize(c.srcPoster, ImageCategory.CREDIT_PHOTO),
    )

    private fun yearOf(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        Regex("""^(\d{4})""").find(dateStr)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    /** Movie.duration/Episode.duration come back in milliseconds (e.g.
     * 4692032 ≈ 78 min), not minutes — confirmed against real API responses. */
    private fun formatDuration(durationMs: Long?): String? {
        if (durationMs == null) return null
        val totalMinutes = Math.round(durationMs / 60000.0)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) Tr.DURATION_HM.text(h, m.toString().padStart(2, '0')) else Tr.DURATION_M.text(m)
    }

    /** Maps a base Media, a Movie, or a Series (all share the one Media
     * shape in Kotlin — see api/Types.kt's comment) to the app's UiMedia
     * view-model. */
    fun mapMediaToUi(media: Media): UiMedia {
        val isSeries = media.mediaType == MediaType.SERIES || media.seasons != null
        return UiMedia(
            id = media.id,
            title = media.title,
            year = yearOf(media.date),
            category = media.categories.firstOrNull()?.let { humanizeKey(it.translationKey) } ?: "",
            type = if (isSeries) UiMediaType.SERIE else UiMediaType.FILM,
            posterUrl = posterUri(media),
            backdropUrl = backdropUri(media),
            progress = media.watchProgress,
            synopsis = media.description,
            director = creditsByJob(media, "DIRECTOR").joinToString(", ").ifEmpty { null },
            creators = creditsByJob(media, "CREATOR").joinToString(", ").ifEmpty { null },
            genres = media.categories.map { UiTag(it.id, humanizeKey(it.translationKey)) },
            keywords = media.keyWord.map { UiTag(null, it) },
            cast = media.credits.filter { it.job == "ACTOR" }.map(::mapCredit),
            crew = media.credits.filter { it.job != "ACTOR" }.map(::mapCredit),
            duration = formatDuration(media.duration),
            quality = media.resolution,
            seasonsCount = media.seasons?.size,
            seasons = media.seasons?.sortedBy { it.seasonNumber }?.map {
                UiSeason(it.id, it.seasonNumber, it.name, ImageQuality.withSize(it.srcPoster, ImageCategory.POSTER_VERTICAL))
            },
        )
    }

    /** Merges in the richer casts/crews/keywords from GET /media/media-info/:id
     * (list endpoints often omit these to keep payloads light). */
    fun applyMediaInfo(base: UiMedia, info: MediaInfo): UiMedia {
        val directors = info.crews.filter { it.job == "DIRECTOR" }.map { it.fullName }
        val creators = info.crews.filter { it.job == "CREATOR" }.map { it.fullName }
        return base.copy(
            cast = info.casts.map(::mapCredit),
            crew = info.crews.map(::mapCredit),
            director = directors.joinToString(", ").ifEmpty { base.director },
            creators = creators.joinToString(", ").ifEmpty { base.creators },
            genres = info.categories.map { UiTag(it.id, humanizeKey(it.translationKey)) },
            keywords = info.keyWords.map { UiTag(null, it) },
        )
    }

    private fun humanizeStateProgress(state: StatState): String = when (state) {
        StatState.NOT_WATCHED -> Tr.WATCH_NOT_WATCHED.text()
        StatState.IN_PROGRESS -> Tr.WATCH_IN_PROGRESS.text()
        StatState.FINISHED -> Tr.WATCH_FINISHED.text()
    }

    /** Fields common to GET /movie/:id and /series/:id — MediaMetadataSheet's
     * "toutes les métadonnées" button. otherTitles/cast/crew are deliberately
     * NOT included here even though the endpoint returns them on this same
     * object — MediaMetadataSheet renders those itself as dedicated sections
     * (a "TITRES ALTERNATIFS" list, a square PersonCard per credit) instead
     * of a plain comma-joined row, so putting them here too would just
     * duplicate the same data in a worse format. */
    private fun baseMetadataRows(media: Media): MutableList<MetadataRow> {
        val rows = mutableListOf(
            MetadataRow(Tr.TITLE_LABEL.text(), media.title),
            MetadataRow(Tr.META_ID.text(), media.mediaLibraryId),
        )
        if (media.categories.isNotEmpty()) {
            rows += MetadataRow(Tr.META_CATEGORIES.text(), media.categories.joinToString(", ") { humanizeKey(it.translationKey) })
        }
        if (media.keyWord.isNotEmpty()) {
            rows += MetadataRow(Tr.DETAIL_KEYWORDS.text(), media.keyWord.joinToString(", "))
        }
        if (media.description.isNotEmpty()) {
            rows += MetadataRow(Tr.META_SYNOPSIS.text(), media.description)
        }
        if (media.date.isNotBlank()) rows += MetadataRow(Tr.META_RELEASE_DATE.text(), media.date)
        media.startShow?.let { rows += MetadataRow(Tr.META_AIR_START.text(), it) }
        media.endShow?.let { rows += MetadataRow(Tr.META_AIR_END.text(), it) }
        media.path?.takeIf { it.isNotBlank() }?.let { rows += MetadataRow(Tr.META_PATH.text(), it) }
        return rows
    }

    fun movieMetadataRows(movie: Media): List<MetadataRow> {
        val rows = baseMetadataRows(movie)
        movie.resolution?.let { rows += MetadataRow(Tr.META_RESOLUTION.text(), it) }
        formatDuration(movie.duration)?.let { rows += MetadataRow(Tr.META_DURATION.text(), it) }
        if ((movie.watchProgress ?: 0.0) > 0) {
            rows += MetadataRow(Tr.META_PROGRESS.text(), Tr.PERCENT.text(movie.watchProgress!!.toInt()))
        }
        movie.stateProgress?.let { rows += MetadataRow(Tr.META_WATCH_STATUS.text(), humanizeStateProgress(it)) }
        return rows
    }

    fun seriesMetadataRows(series: Media): List<MetadataRow> {
        val rows = baseMetadataRows(series)
        series.seasons?.let { if (it.isNotEmpty()) rows += MetadataRow(Tr.META_SEASONS.text(), it.size.toString()) }
        if ((series.lastSeasonWatched ?: 0) > 0) {
            rows += MetadataRow(Tr.META_LAST_SEASON.text(), series.lastSeasonWatched.toString())
        }
        return rows
    }

    // Built per call: follows the app language (pattern and month names).
    private fun airDateFormatter() = DateTimeFormatter.ofPattern(Tr.DATE_PATTERN_LONG.text(), AppLocale.javaLocale)

    fun mapEpisodeToUi(episode: Episode): UiEpisode {
        val airDate = episode.date.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                LocalDate.parse(raw.take(10)).format(airDateFormatter())
            } catch (e: DateTimeParseException) {
                ""
            }
        } ?: ""
        return UiEpisode(
            id = episode.id,
            seasonId = episode.seasonId,
            seriesId = episode.seriesId,
            number = episode.episodeNumber,
            title = episode.name,
            description = episode.description,
            duration = formatDuration(episode.duration) ?: "",
            airDate = airDate,
            progress = episode.watchProgress,
            posterUrl = episodePosterUri(episode),
        )
    }

    /** Full-bleed fixed background on LicenseDetailScreen — "hero" tier.
     * License image assets (icon/logo/background alike) share the BACKDROP
     * category's breakpoints, not LOGO/HERO_LOGO (those are sized for
     * per-title srcLogo, a different asset pipeline). */
    fun licenseBackdropUri(license: License): String? = ImageQuality.withSize(license.srcBackground, ImageCategory.HERO_BACKDROP)

    fun licenseLogoUri(license: License): String? = ImageQuality.withSize(license.srcLogo, ImageCategory.HERO_BACKDROP)

    fun mapLicenseToUi(license: License): UiLicence = UiLicence(
        id = license.id,
        title = license.name,
        count = license.mediaList.size,
        backdropUrl = ImageQuality.withSize(license.srcBackground, ImageCategory.BACKDROP),
        logoUrl = ImageQuality.withSize(license.srcLogo, ImageCategory.BACKDROP),
        iconUrl = ImageQuality.withSize(license.srcIcon, ImageCategory.BACKDROP),
    )
}
