package com.chocoplusmobile.api

import com.chocoplusmobile.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mirrors the RN app's api/client.ts: an in-memory token/language cache
 * (set by AppViewModel once, read synchronously by the interceptor below —
 * same reasoning as the RN side's inMemoryToken/inMemoryLanguage) plus a
 * hook AppViewModel installs so any 401 anywhere logs the user out instead
 * of leaving stale/broken screens behind.
 */
object AuthTokenHolder {
    @Volatile
    var token: String? = null

    @Volatile
    var language: String = "fr"

    @Volatile
    var unauthorizedHandler: (() -> Unit)? = null
}

class ApiException(message: String, val status: Int? = null) : Exception(message)

/** The backend couldn't be reached or couldn't answer: no connectivity, a
 * DNS failure, a timeout (no HTTP status at all) or a 5xx. Distinct from a
 * 4xx, where the server did answer — e.g. 401/403 on a wrong password. */
fun Throwable.isServerUnavailable(): Boolean {
    val status = (this as? ApiException ?: return false).status
    return status == null || status >= 500
}

private class AuthHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .addHeader(BuildConfig.HEADER_NAME_FIELD_SECRET_API, BuildConfig.HEADER_SECRET_API)
            .addHeader("Accept-Language", AuthTokenHolder.language)
        AuthTokenHolder.token?.let { builder.addHeader("Authorization", "Bearer $it") }
        val response = chain.proceed(builder.build())
        if (response.code == 401) {
            AuthTokenHolder.unauthorizedHandler?.invoke()
        }
        return response
    }
}

object NetworkModule {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // ignoreUnknownKeys only covers unknown JSON *fields* — an unknown
        // *enum value* on a field that does exist (e.g. Selection.createFrom
        // getting a MediaType the backend added before this app knew about
        // it — HISTORIC_USER/LAST_RELEASE did exactly this) still throws
        // and fails the whole response otherwise. coerceInputValues falls
        // back to the property's own default in that case instead (every
        // enum field in this app is nullable with a `= null` default, so
        // that's what an unrecognized value becomes) — the next backend
        // addition degrades to "this one field reads as unset" rather than
        // taking down the whole screen again.
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthHeadersInterceptor())
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: ApiService = retrofit.create(ApiService::class.java)

    // No interceptors: the probe must never trip the global 401 → logout
    // hook (it carries no token, and a protected root may well answer 401).
    private val probeClient: OkHttpClient = okHttpClient.newBuilder()
        .apply { interceptors().clear() }
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Cheap "is the backend up?" check: any HTTP answer below 500 — even
     * a 401/404 — means the server is there; a network error, a timeout or
     * a 5xx (incl. a reverse proxy's 502/503 for a dead backend) means not. */
    suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            probeClient.newCall(Request.Builder().url(BuildConfig.API_URL + "/").build()).execute().use { it.code < 500 }
        } catch (e: java.io.IOException) {
            false
        }
    }

    /** Wraps a suspend Retrofit call, turning HttpException/IOException into
     * the same ApiException shape the RN app's axios interceptor produced —
     * screens show `err.message` either way. */
    suspend fun <T> safeCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: retrofit2.HttpException) {
            val raw = e.response()?.errorBody()?.string()
            val message = raw?.let { runCatching { json.parseToJsonElement(it).toString() }.getOrNull() }
            throw ApiException(message ?: e.message() ?: "Erreur réseau", e.code())
        } catch (e: java.io.IOException) {
            throw ApiException(e.message ?: "Erreur réseau")
        }
    }
}

/** credit/job-filters is effectively static reference data (the list of Job
 * codes the catalog's job filter accepts) — fetched once per process and
 * kept here instead of refetching every time the Catalogue filter sheet
 * opens. `get()` is safe to call from multiple composables/screens; only
 * the first caller actually hits the network. */
object JobFilterCache {
    @Volatile
    private var cached: List<String>? = null

    suspend fun get(): List<String> {
        cached?.let { return it }
        val fetched = NetworkModule.safeCall { NetworkModule.api.getJobFilters() }
        cached = fetched
        return fetched
    }
}
