package com.chocoplusmobile.i18n

import com.chocoplusmobile.state.AppLanguage

/**
 * A synchronously-readable mirror of Settings > language, for the handful of
 * places that need a translated string outside of Compose (Adapters.kt's
 * mapping functions run once per API response, not as composables) — same
 * reasoning as api.AuthTokenHolder.language mirroring the same setting for
 * the Accept-Language header. Kept in sync by AppViewModel.
 *
 * Composable UI code should prefer [t] (reads the language reactively via
 * LocalAppViewModel, so text actually recomposes when the setting changes)
 * over reading this directly.
 */
object AppLocale {
    var current: AppLanguage = AppLanguage.FR
}
