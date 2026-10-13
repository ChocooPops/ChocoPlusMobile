package com.chocoplusmobile.data

import kotlinx.serialization.Serializable

/**
 * Mirrors the RN app's utils/imageQuality.ts. The API serves every uploaded
 * image at its original resolution by default
 * (".../uploads/movie/2001192/144709.webp"), but also accepts a size segment
 * inserted right before the filename (".../2001192/300h/144709.webp") to get
 * a pre-resized variant. Vertical images (posters) use an "h" suffix,
 * horizontal ones (backgrounds, logos, license art, episode thumbnails) use
 * "w" — and only specific breakpoints exist per image category, so picking
 * an arbitrary pixel value would 404.
 *
 * Two independent quality levels exist: 'standard' for posters/backdrops
 * shown at list/grid size everywhere, and 'hero' for the much larger header
 * images — governed separately since they're displayed far bigger. Both are
 * kept in AppViewModel (persisted) and mirrored here in-memory (same
 * AuthTokenHolder-style pattern used for the auth token) so Adapters.kt can
 * build URLs synchronously without threading the setting through every call
 * site.
 */
@Serializable
enum class ImageQualityLevel { LOW, MEDIUM, HIGH, ORIGINAL }

enum class ImageQualityGroup { STANDARD, HERO }

enum class ImageCategory(val suffix: Char, val sizes: List<Int>, val group: ImageQualityGroup) {
    // Movie/series posters (srcPoster.normal / .special) — vertical affiches,
    // shown at list/grid size.
    POSTER_VERTICAL('h', listOf(100, 300, 350, 600, 900, 1400, 1920), ImageQualityGroup.STANDARD),
    // Movie/series backgrounds, srcPoster.horizontal, and license
    // icons/logos/backgrounds all share this breakpoint set.
    BACKDROP('w', listOf(100, 300, 350, 600, 900, 1400, 1920), ImageQualityGroup.STANDARD),
    // Movie/series logo art (Media.srcLogo).
    LOGO('w', listOf(200, 300, 500, 700), ImageQualityGroup.STANDARD),
    // Episode thumbnails (Episode.srcPoster).
    EPISODE_POSTER('w', listOf(300, 600, 900), ImageQualityGroup.STANDARD),
    // Cast/crew headshots — reverse-engineered breakpoint set (see the RN
    // source comment: 100h/300h/600h/900h returned 200, everything else 404).
    CREDIT_PHOTO('h', listOf(100, 300, 600, 900), ImageQualityGroup.STANDARD),
    // Same breakpoints/URL shape as BACKDROP, but only for the large header
    // banners — governed by the separate "hero" quality level.
    HERO_BACKDROP('w', listOf(100, 300, 350, 600, 900, 1400, 1920), ImageQualityGroup.HERO),
    // Same as LOGO, but for the Home/Films/Séries hero banners.
    HERO_LOGO('w', listOf(200, 300, 500, 700), ImageQualityGroup.HERO),
}

object ImageQuality {
    @Volatile
    var standardLevel: ImageQualityLevel = ImageQualityLevel.MEDIUM

    @Volatile
    var heroLevel: ImageQualityLevel = ImageQualityLevel.HIGH

    private fun pickSize(sizes: List<Int>, level: ImageQualityLevel): Int = when (level) {
        ImageQualityLevel.LOW -> sizes.first()
        ImageQualityLevel.HIGH -> sizes.last()
        // medium: middle breakpoint of the list.
        else -> sizes[(sizes.size - 1) / 2]
    }

    /** Rewrites a raw API image URL to the size matching the current quality
     * level for that category's group. Returns the URL untouched at
     * ORIGINAL (the un-resized image is always available and needs no path
     * change), and passes through null input so call sites can chain it
     * directly onto optional fields. */
    fun withSize(url: String?, category: ImageCategory): String? {
        if (url.isNullOrEmpty()) return null
        // Images stored locally by the download feature are already sized —
        // a size segment inserted into a file path would just break it.
        if (url.startsWith("file:")) return url
        val level = if (category.group == ImageQualityGroup.HERO) heroLevel else standardLevel
        if (level == ImageQualityLevel.ORIGINAL) return url
        val lastSlash = url.lastIndexOf('/')
        if (lastSlash == -1) return url
        val size = pickSize(category.sizes, level)
        return "${url.substring(0, lastSlash)}/$size${category.suffix}/${url.substring(lastSlash + 1)}"
    }
}
