package com.chocoplusmobile.navigation

import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.api.PlayerTarget

/**
 * Navigation-Compose routes only carry primitive args cleanly — the RN app
 * passes a full `Media` object straight through `navigation.navigate()`
 * (every list endpoint already returns it in full, so there's no "get media
 * by id" round trip). This is the equivalent here: the calling screen sets
 * the field right before navigating, the destination screen reads it once
 * on first composition. Not restoration-safe across process death (a rare
 * edge case even React Navigation's own state persistence wouldn't help
 * with here, since the RN side re-fetches nothing either), which is an
 * accepted trade-off for this migration's scope.
 */
/**
 * A filter to pre-apply when CatalogueScreen is the navigation target —
 * from tapping a genre/keyword/cast-or-crew chip on a detail page. Category
 * has a real id to filter on exactly; Keyword doesn't carry one from the
 * API at all (Media/MediaInfo only ever return keyword names), so it falls
 * back to a name match. Staff reuses the catalog's own generic
 * job+creditId filter (the same shape CatalogueScreen's "Métier" advanced
 * filter already builds) for an exact "this person, in this specific role"
 * match rather than the broader "this person, any role" one.
 */
sealed class CatalogueEntryFilter {
    data class Category(val id: Int) : CatalogueEntryFilter()
    data class Keyword(val name: String) : CatalogueEntryFilter()
    data class Staff(val job: String, val jobLabel: String, val creditId: Int, val name: String) : CatalogueEntryFilter()

    // A Selection's "Tout voir" chevron (SectionHeader's onClick) — how it's
    // built depends on Selection.createFrom, since that field also tells you
    // what `Selection.id` actually means: a real Selection/License id for
    // SELECTION/LICENSE, but just the current userId for LAST_RELEASE/
    // HISTORIC_USER (confirmed against selection.service.ts/
    // stat-user.service.ts — not a filterable value at all for those two).
    data class SelectionSeeAll(val selectionId: Int, val name: String) : CatalogueEntryFilter()
    data class LicenseSeeAll(val licenseId: Int, val name: String) : CatalogueEntryFilter()
    object LatestReleases : CatalogueEntryFilter()
    object AlreadyWatched : CatalogueEntryFilter()

    // Distinct from the plain `Category(id)` above (used by genre chips on
    // detail pages, which must keep its existing default-sort behavior
    // untouched) — this one is specifically the Films/Séries pages' own
    // selection rows (createFrom = CATEGORY there, id = the category's own
    // id — confirmed against createMediaSelectionsByTypeFromCategoryByCount),
    // which "Tout voir" defaults to SHUFFLE the same as Selection/License.
    data class CategorySeeAll(val categoryId: Int) : CatalogueEntryFilter()

    companion object {
        /** Maps a Selection's own (id, name, createFrom) to the right "Tout
         * voir" entry — shared by every screen that renders a Selection row
         * (Home, Browse, LicenseDetail) instead of duplicating this per
         * screen. Takes the three fields directly rather than a whole
         * Selection so a caller working from a stripped-down local view
         * model (BrowseScreen's FilteredRow) doesn't need to reconstruct
         * one just to call this. Null for a createFrom this app doesn't
         * know how to build a Catalogue filter for (no chevron should show
         * in that case). */
        fun forSelection(id: Int, name: String, createFrom: MediaType?): CatalogueEntryFilter? = when (createFrom) {
            MediaType.SELECTION -> SelectionSeeAll(id, name)
            MediaType.LICENSE -> LicenseSeeAll(id, name)
            MediaType.LAST_RELEASE -> LatestReleases
            MediaType.HISTORIC_USER -> AlreadyWatched
            MediaType.CATEGORY -> CategorySeeAll(id)
            else -> null
        }
    }
}

object NavCache {
    var pendingMedia: Media? = null
    var pendingPlayerTarget: PlayerTarget? = null
    var pendingCatalogueFilter: CatalogueEntryFilter? = null
}
