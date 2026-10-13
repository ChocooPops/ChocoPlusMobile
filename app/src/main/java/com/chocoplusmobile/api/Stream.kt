package com.chocoplusmobile.api

import com.chocoplusmobile.BuildConfig
import java.net.URLEncoder

/**
 * Mirrors the RN app's api/stream.ts: <video>-style consumers can't send an
 * Authorization header, so these routes take the JWT as a `?token=` query
 * param instead. Confirmed live that the secret header every other API call
 * gets automatically via AuthHeadersInterceptor is *not* required for these
 * routes specifically (a plain VLC desktop open of the same URL, with no
 * custom header at all, plays fine) — so there's nothing extra for
 * VlcPlayer.kt to attach to the stream itself.
 */
object Stream {
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    fun movieUrl(movieId: Int, token: String): String =
        "${BuildConfig.API_URL}/stream/stream-movie/$movieId?token=${encode(token)}"

    fun episodeUrl(seasonId: Int, episodeId: Int, token: String): String =
        "${BuildConfig.API_URL}/stream/stream-episode/$seasonId/$episodeId?token=${encode(token)}"

    fun newsUrl(newsId: Int, token: String): String =
        "${BuildConfig.API_URL}/stream/stream-news/$newsId?token=${encode(token)}"
}
