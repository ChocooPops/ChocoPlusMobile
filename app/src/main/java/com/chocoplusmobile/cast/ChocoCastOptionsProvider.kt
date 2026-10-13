package com.chocoplusmobile.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Registers the app with the Cast SDK — referenced by AndroidManifest.xml's
 * OPTIONS_PROVIDER_CLASS meta-data, required before CastContext's shared
 * instance can be obtained anywhere in the app.
 *
 * Uses Google's own DEFAULT_MEDIA_RECEIVER_APPLICATION_ID — a public
 * "Default Media Receiver" that needs no registration and plays any plain
 * HTTP(S) video URL out of the box, which is all "Diffuser" needs (no
 * custom on-TV branding). Swap in a real Application ID from the Google
 * Cast SDK Developer Console here if that ever changes — nothing else in
 * the app depends on which one is used.
 */
class ChocoCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
