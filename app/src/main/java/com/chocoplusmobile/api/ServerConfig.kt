package com.chocoplusmobile.api

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.text

/**
 * Backend URL + secret API header pair — entered in the app's server form
 * (ui/components/ServerConfigDialog.kt) instead of the old build-time .env.
 */
data class ServerConfig(val url: String, val headerName: String, val headerValue: String) {
    /** Why this config can't be saved, or null when it's usable. */
    fun validationError(): String? {
        val parsed = url.toHttpUrlOrNull() ?: return Tr.SERVER_INVALID_URL.text()
        if (parsed.query != null || parsed.fragment != null) return Tr.SERVER_URL_HAS_PARAMS.text()
        if (headerName.isBlank()) return Tr.SERVER_HEADER_NAME_REQUIRED.text()
        if (headerValue.isEmpty()) return Tr.SERVER_HEADER_VALUE_REQUIRED.text()
        // OkHttp throws on any header it wouldn't send — catch it here, not
        // on the first request.
        return runCatching { Headers.Builder().add(headerName, headerValue) }
            .exceptionOrNull()?.let { Tr.SERVER_HEADER_INVALID.text() }
    }

    companion object {
        /** Trims what users typically paste around a URL: spaces, a trailing "/". */
        fun of(url: String, headerName: String, headerValue: String) =
            ServerConfig(url.trim().trimEnd('/'), headerName.trim(), headerValue.trim())
    }
}

/**
 * The active config, read synchronously by everything that builds a request
 * (NetworkModule's interceptor + base URL, Stream, DownloadManager). Loaded
 * once in ChocoApp.onCreate, before anything touches the network — saving
 * a new one restarts the app (see [save]) rather than swapping it live, so
 * Retrofit, the caches and the session all start clean against the new
 * server.
 */
object ServerConfigHolder {
    private const val PREFS = "chocoplus_server"
    private const val KEY_URL = "url"
    private const val KEY_HEADER_NAME = "header_name"
    private const val KEY_HEADER_VALUE = "header_value"

    @Volatile
    var current: ServerConfig? = null
        private set

    val isConfigured: Boolean get() = current != null

    /** Base URL without trailing slash — "" until configured (the UI then
     * only shows the server form, so nothing builds a request with it). */
    val apiUrl: String get() = current?.url.orEmpty()

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = ServerConfig(
            url = p.getString(KEY_URL, null).orEmpty(),
            headerName = p.getString(KEY_HEADER_NAME, null).orEmpty(),
            headerValue = p.getString(KEY_HEADER_VALUE, null).orEmpty(),
        ).takeIf { it.validationError() == null }
    }

    /** Persists [config] (synchronously — the process is about to be
     * killed) and relaunches the app on it. */
    fun save(context: Context, config: ServerConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, config.url)
            .putString(KEY_HEADER_NAME, config.headerName)
            .putString(KEY_HEADER_VALUE, config.headerValue)
            .commit()
        restartApp(context)
    }

    private fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        exitProcess(0)
    }
}

/** Outcome of the form's "Tester" button. */
sealed class ServerTestResult(val message: String, val success: Boolean) {
    class Ok(code: Int) : ServerTestResult(Tr.SERVER_TEST_OK.text(code), true)
    class Reachable(code: Int) : ServerTestResult(Tr.SERVER_TEST_REACHABLE.text(code), true)
    class Rejected(code: Int) : ServerTestResult(Tr.SERVER_TEST_REJECTED.text(code), false)
    class ServerError(code: Int) : ServerTestResult(Tr.SERVER_TEST_SERVER_ERROR.text(code), false)
    class Unreachable(detail: String) : ServerTestResult(Tr.SERVER_TEST_UNREACHABLE.text(detail), false)
    object Cleartext : ServerTestResult(Tr.SERVER_TEST_CLEARTEXT.text(), false)
}

object ServerConnectionTester {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** GET on the bare base URL (no endpoint) with only the secret header —
     * any answer proves the URL; 401/403 is what a wrong header gets. */
    suspend fun test(config: ServerConfig): ServerTestResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(config.url + "/")
            .header(config.headerName, config.headerValue)
            .build()
        try {
            client.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> ServerTestResult.Ok(r.code)
                    r.code == 401 || r.code == 403 -> ServerTestResult.Rejected(r.code)
                    r.code >= 500 -> ServerTestResult.ServerError(r.code)
                    else -> ServerTestResult.Reachable(r.code)
                }
            }
        } catch (e: java.net.UnknownServiceException) {
            // What OkHttp throws when usesCleartextTraffic="false" blocks http://.
            ServerTestResult.Cleartext
        } catch (e: java.io.IOException) {
            ServerTestResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }
}
