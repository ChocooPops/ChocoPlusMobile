package com.chocoplusmobile.state

import com.chocoplusmobile.data.ImageQualityLevel
import kotlinx.serialization.Serializable

// Mirrors the RN app's state/AppStateContext.tsx type declarations —
// PersistedPrefs is what actually gets serialized into DataStore (see
// storage/PrefsStorage.kt), same PREFS_STORAGE_KEY versioning story (a v1
// blob predating today's imageQuality/heroImageQuality defaults must never
// shadow the current ones, hence the "v2" key there).

@Serializable
enum class PosterFormat { V, H }

enum class FormatPage { HOME, SEARCH, CATALOGUE, FILMS, SERIES, MYLIST, LICENSE_DETAIL }

/** How many posters sit on one line: SINGLE is one per line (unchanged
 * behaviour), GRID packs several — 3 for vertical posters, 2 for horizontal
 * ones (see data/PosterFormatUtils.kt's gridColumns()). Tracked separately
 * per poster format (not just per page) so switching between Vertical/
 * Horizontal on a page never disturbs the alignment already picked for the
 * other shape. */
@Serializable
enum class PosterAlignment { SINGLE, GRID }

@Serializable
data class AlignmentByFormat(val v: PosterAlignment, val h: PosterAlignment)

@Serializable
enum class AppLanguage { FR, JA, EN }

@Serializable
data class SettingsToggles(
    val autoplay: Boolean = true,
)

@Serializable
enum class MediaLabelStyle { LOGO, TITLE }

@Serializable
enum class StartPage { ACCUEIL, FILMS, SERIES, CATALOGUE, MA_LISTE, PROFIL, RECHERCHE }

/** A single VF/VO reading of "what the viewer actually wants", inferred
 * rather than chosen from a Settings screen — there's no dedicated UI for
 * this, it's derived from whatever audio/subtitle combination the viewer
 * last picked manually in the player's track sheet (see PlayerScreen's
 * recordAudioSubtitleChoice), then reapplied as the starting point on the
 * next video (applyAudioSubtitlePreference), by matching track names
 * against a small set of French-language hints. */
@Serializable
enum class AudioSubtitlePreference {
    /** Last seen as: a French-leaning audio track with subtitles off. */
    VF,
    /** Anything else — a non-French audio track, or subtitles left on. */
    VO,
}

/** Where video decoding happens — see video/VlcPlayer.kt. SOFTWARE (the
 * default) decodes entirely in libVLC's own bundled codecs: slower and
 * heavier on battery, but colors come out consistent across devices and
 * source files. HARDWARE hands decoding to the device's own chip (faster,
 * lighter on battery) but some SoCs get the color range/matrix wrong on
 * files with missing or inconsistent signaling, showing washed-out colors
 * on some titles — left as an opt-in for whoever wants the battery back. */
@Serializable
enum class VideoDecoding { SOFTWARE, HARDWARE }

@Serializable
data class Settings(
    /** Also sent as the Accept-Language header on every API request. */
    val language: AppLanguage = AppLanguage.FR,
    /** Poster/background/logo resolution requested from the API — ORIGINAL
     * keeps the un-resized image, the others trade quality for data usage. */
    val imageQuality: ImageQualityLevel = ImageQualityLevel.MEDIUM,
    /** Same idea, but only for the large header banners, shown much bigger
     * and often wanting a higher tier than list/grid posters. */
    val heroImageQuality: ImageQualityLevel = ImageQualityLevel.HIGH,
    /** Whether the news hero, the Films/Séries hero, and horizontal-format
     * poster cards show the media's logo artwork or its title as plain text.
     * Always falls back to text when a title has no logo. */
    val mediaLabelStyle: MediaLabelStyle = MediaLabelStyle.LOGO,
    val toggles: SettingsToggles = SettingsToggles(),
    val startPage: StartPage = StartPage.ACCUEIL,
    val videoDecoding: VideoDecoding = VideoDecoding.SOFTWARE,
    val audioSubtitlePreference: AudioSubtitlePreference = AudioSubtitlePreference.VF,
)

@Serializable
data class FormatByPage(
    val home: PosterFormat = PosterFormat.H,
    val search: PosterFormat = PosterFormat.V,
    val catalogue: PosterFormat = PosterFormat.V,
    val films: PosterFormat = PosterFormat.V,
    val series: PosterFormat = PosterFormat.V,
    val mylist: PosterFormat = PosterFormat.H,
    val licenseDetail: PosterFormat = PosterFormat.V,
) {
    fun get(page: FormatPage): PosterFormat = when (page) {
        FormatPage.HOME -> home
        FormatPage.SEARCH -> search
        FormatPage.CATALOGUE -> catalogue
        FormatPage.FILMS -> films
        FormatPage.SERIES -> series
        FormatPage.MYLIST -> mylist
        FormatPage.LICENSE_DETAIL -> licenseDetail
    }

    fun with(page: FormatPage, format: PosterFormat): FormatByPage = when (page) {
        FormatPage.HOME -> copy(home = format)
        FormatPage.SEARCH -> copy(search = format)
        FormatPage.CATALOGUE -> copy(catalogue = format)
        FormatPage.FILMS -> copy(films = format)
        FormatPage.SERIES -> copy(series = format)
        FormatPage.MYLIST -> copy(mylist = format)
        FormatPage.LICENSE_DETAIL -> copy(licenseDetail = format)
    }
}

private val SINGLE_BOTH = AlignmentByFormat(PosterAlignment.SINGLE, PosterAlignment.SINGLE)

// Catalogue/Ma liste already shipped as an implicit 3-per-line grid for
// Vertical and a single column for Horizontal — baked in as the default so
// this feature is purely additive.
private val GRID_V_SINGLE_H = AlignmentByFormat(PosterAlignment.GRID, PosterAlignment.SINGLE)

@Serializable
data class AlignmentByPage(
    val home: AlignmentByFormat = SINGLE_BOTH,
    val search: AlignmentByFormat = SINGLE_BOTH,
    val catalogue: AlignmentByFormat = GRID_V_SINGLE_H,
    val films: AlignmentByFormat = SINGLE_BOTH,
    val series: AlignmentByFormat = SINGLE_BOTH,
    val mylist: AlignmentByFormat = GRID_V_SINGLE_H,
    // Unused — LicenseDetail only exposes the format (v/h) toggle, not the
    // alignment (single/grid) one, same as Home/Films/Séries.
    val licenseDetail: AlignmentByFormat = SINGLE_BOTH,
) {
    fun get(page: FormatPage): AlignmentByFormat = when (page) {
        FormatPage.HOME -> home
        FormatPage.SEARCH -> search
        FormatPage.CATALOGUE -> catalogue
        FormatPage.FILMS -> films
        FormatPage.SERIES -> series
        FormatPage.MYLIST -> mylist
        FormatPage.LICENSE_DETAIL -> licenseDetail
    }

    fun with(page: FormatPage, format: PosterFormat, alignment: PosterAlignment): AlignmentByPage {
        val current = get(page)
        val next = if (format == PosterFormat.V) current.copy(v = alignment) else current.copy(h = alignment)
        return when (page) {
            FormatPage.HOME -> copy(home = next)
            FormatPage.SEARCH -> copy(search = next)
            FormatPage.CATALOGUE -> copy(catalogue = next)
            FormatPage.FILMS -> copy(films = next)
            FormatPage.SERIES -> copy(series = next)
            FormatPage.MYLIST -> copy(mylist = next)
            FormatPage.LICENSE_DETAIL -> copy(licenseDetail = next)
        }
    }

    fun current(page: FormatPage, format: PosterFormat): PosterAlignment {
        val pair = get(page)
        return if (format == PosterFormat.V) pair.v else pair.h
    }
}

@Serializable
data class PersistedPrefs(
    val formatByPage: FormatByPage = FormatByPage(),
    val alignmentByPage: AlignmentByPage = AlignmentByPage(),
    val settings: Settings = Settings(),
)
