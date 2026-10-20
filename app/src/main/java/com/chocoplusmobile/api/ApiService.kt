package com.chocoplusmobile.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

// ── Request bodies — mirrors the *Payload interfaces in the RN app's
// src/api/*.ts one-for-one. ──

@Serializable
data class LoginPayload(val email: String, val password: String)

@Serializable
data class EmailPayload(val email: String)

/** Body for auth/send-verification-code — mirrors the backend's own
 * RegisterUser interface exactly: no password field. registerNewUser()
 * hardcodes every new account's password column to the literal string
 * 'null' regardless of what's sent (confirmed against user.service.ts) —
 * there is no password step anywhere in signup, an account only gets a
 * usable password later, through a separate reset-password flow. */
@Serializable
data class SendVerificationCodePayload(
    val pseudo: String,
    val firstName: String,
    val lastName: String,
    val dateBorn: String,
    val email: String,
)

/** Body for auth/register — the endpoint reads just these two fields off the
 * request body (`@Body('verificationCode') code`, `@Body('email') email`);
 * the rest of the identity was already cached server-side against `email`
 * by send-verification-code, keyed to the code it emailed out. */
@Serializable
data class RegisterPayload(
    val verificationCode: Int,
    val email: String,
)

/**
 * Mirrors the backend's UpdateUser interface exactly — field names included
 * (`currentPassWord`/`newPassWord`/`reNewPassWord`, not the more ordinary
 * `password`/`currentPassword` one might expect). `pseudo` and `date` are
 * both required server-side regardless of whether they actually changed
 * (`updateUserByUserId` rejects the whole request otherwise with
 * INCORRECT_PSEUDO/INCORRECT_DATE_BORN) — the password trio is the only
 * genuinely optional part, only applied when all three are present.
 */
@Serializable
data class UpdateUserByUserPayload(
    val pseudo: String,
    val date: String,
    val currentPassWord: String? = null,
    val newPassWord: String? = null,
    val reNewPassWord: String? = null,
)

@Serializable
data class UpdateUserRolePayload(val role: Role)

// Mirrors the NestJS backend's filters.interface.ts and its companion enums
// (filter-type.enum.ts, operation.enum.ts, logical-operator.ts,
// sort-catalog.enum.ts) — POST media/catalog takes this filter list as the
// bare request body (a JSON array, not wrapped in an object) alongside the
// sortFilter/orderDirection/count/offset query params.

@Serializable
enum class FilterType {
    CREDIT,
    @SerialName("KEY WORD") KEY_WORD,
    MEDIA,
    DECADE,
    YEAR,
    CATEGORY,
    SELECTION,
    LICENSE,
}

/**
 * The literal string the backend expects for this FilterType as a
 * `CatalogFilter.typeData` value (a plain String field — see CatalogFilter's
 * own doc below). `.name` alone is wrong here: KEY_WORD's wire value is
 * "KEY WORD" (with a space, per @SerialName above), not the Kotlin
 * identifier "KEY_WORD" — kotlinx.serialization only applies that
 * annotation when the enum itself is what gets serialized, which never
 * happens here since typeData is typed as String. Reading it back off the
 * generated serializer's descriptor keeps this correct automatically for
 * every entry, not just the one that happens to need it today.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
val FilterType.wireValue: String
    get() = FilterType.serializer().descriptor.getElementName(ordinal)

@Serializable
enum class Operation {
    CONTAIN,
    @SerialName("NOT CONTAIN") NOT_CONTAIN,
}

@Serializable
enum class LogicalOperator { AND, OR }

/** How medias/getCatalog results can be ordered — resolveSortColumn on the
 * backend maps each of these to a real SQL column (or RAND() for SHUFFLE).
 * ALREADY_WATCHED is a newer addition — confirmed against the backend's own
 * sort-catalog.enum.ts. */
@Serializable
enum class SortCatalog { TITLE, RELEASE_DATE, ADDED_DATE, DURATION, SHUFFLE, ALREADY_WATCHED }

@Serializable
data class CatalogFilterValue(val value: String)

/**
 * Mirrors FILTERS from filters.interface.ts. `typeData` there is typed as
 * `FilterType | Job` — the backend also accepts a credit Job (ACTOR,
 * DIRECTOR, …) here, not just a FilterType — so it's kept as a plain String
 * rather than a typed enum, since the app only ever sends FilterType values
 * (via `FilterType.X.name`) and mirroring the Job enum client-side isn't
 * worth it just for that. `id` isn't read anywhere in the backend's own
 * filter-building loop; kept only because the interface requires it.
 */
@Serializable
data class CatalogFilter(
    val id: Int,
    val typeData: String,
    val operation: Operation,
    val logic: LogicalOperator? = null,
    val valueLogic: LogicalOperator? = null,
    val value: List<CatalogFilterValue>,
)

@Serializable
data class CatalogResponse(val medias: List<Media> = emptyList(), val total: Int = 0)

@Serializable
data class SubmitSupportPayload(
    val subject: String,
    val areaConcerned: List<String>,
    val description: String? = null,
)

/** `watchProgress` is a percentage (0–100) of the video's total length. */
@Serializable
data class WatchProgressPayload(val watchProgress: Double)

/** One Retrofit interface for every non-admin route the app calls — grouped
 * by resource, same order as the RN app's separate api-layer TS files (kept
 * as comments here since Kotlin has no equivalent module-per-file
 * convention as lightweight as those were). */
interface ApiService {

    // ── auth.ts ──
    @POST("auth/login")
    suspend fun login(@Body payload: LoginPayload): TokenModel

    @POST("auth/send-verification-code")
    suspend fun sendVerificationCode(@Body payload: SendVerificationCodePayload): ReturnMessage

    @POST("auth/resend-verification-code")
    suspend fun resendVerificationCode(@Body payload: EmailPayload): ReturnMessage

    @POST("auth/register")
    suspend fun register(@Body payload: RegisterPayload): ReturnMessage

    /** Checks the current token against the user's role server-side, and
     * returns a fresh token when it no longer matches. */
    @GET("auth/synch-role-token")
    suspend fun syncRoleToken(): SyncRoleTokenResponse

    // ── user.ts ──
    @GET("user/current-user")
    suspend fun getCurrentUser(): User

    @GET("user/my-list")
    suspend fun getMyList(): List<Media>

    @PUT("user/toggle-into-my-list/{mediaId}")
    suspend fun toggleIntoMyList(@Path("mediaId") mediaId: Int): ReturnMessage

    @PUT("user/profil-picture/{idProfilPicture}")
    suspend fun updateProfilePicture(@Path("idProfilPicture") idProfilPicture: Int): ProfilPhoto

    @PUT("user/update-user-by-user")
    suspend fun updateUserByUser(@Body payload: UpdateUserByUserPayload): ReturnMessage

    @DELETE("user")
    suspend fun deleteCurrentUser(): ReturnMessage

    // ── admin-only, guarded server-side by AdminUserGuard — only ever
    // called from AdminUsersScreen, itself only reachable when the current
    // user's own role is ADMIN. ──
    @GET("user/all-user")
    suspend fun getAllUsers(): List<User>

    @PUT("user/update-role-by-admin/{id}")
    suspend fun updateUserRoleByAdmin(@Path("id") id: Int, @Body payload: UpdateUserRolePayload): ReturnMessage

    @DELETE("user/delete-user-by-admin/{id}")
    suspend fun deleteUserByAdmin(@Path("id") id: Int): ReturnMessage

    // ── movie.ts ──
    @GET("movie/random-movie")
    suspend fun getRandomMovie(): Media

    @GET("movie/{id}")
    suspend fun getMovieById(@Path("id") id: Int): Media

    // ── series.ts ──
    @GET("series/episodes/{seriesId}/{seasonId}")
    suspend fun getEpisodes(@Path("seriesId") seriesId: Int, @Path("seasonId") seasonId: Int): List<Episode>

    @GET("series/random-series")
    suspend fun getRandomSeries(): Media

    @GET("series/first-episode/{seriesId}")
    suspend fun getFirstEpisode(@Path("seriesId") seriesId: Int): Episode

    @GET("series/last-episode-watched/{seriesId}")
    suspend fun getLastEpisodeWatched(@Path("seriesId") seriesId: Int): Episode

    @GET("series/{seriesId}")
    suspend fun getSeriesById(@Path("seriesId") seriesId: Int): Media

    @GET("series/episode/{episodeId}")
    suspend fun getEpisodeById(@Path("episodeId") episodeId: Int): Episode

    // ── category.ts ──
    @GET("category/all-categories")
    suspend fun getAllCategories(): List<CategorySimple>

    // ── credit.ts ──
    /** The raw Job codes (e.g. "DIRECTOR") the catalog's job filter accepts
     * as `typeData` — see CatalogFilter's doc above. Effectively static
     * reference data; cached client-side by JobFilterCache so this only
     * ever fires once per app run. */
    @GET("credit/job-filters")
    suspend fun getJobFilters(): List<String>

    // ── selection.ts ──
    @GET("selection/selection-home")
    suspend fun getSelectionHome(): List<Selection>

    @GET("selection/random-media-selection-by-type/{mediaType}")
    suspend fun getRandomMediaSelectionByType(@Path("mediaType") mediaType: String): List<Selection>

    // ── license.ts ──
    @GET("license/home-page")
    suspend fun getHomePageLicenses(): List<License>

    @GET("license/research-page")
    suspend fun getResearchPageLicenses(): List<License>

    @GET("license/{id}")
    suspend fun getLicenseById(@Path("id") id: Int): License

    // ── media.ts ──
    /** Full record for either a movie or a series — used by the download
     * feature to store a title's metadata locally next to its video. */
    @GET("media/{id}")
    suspend fun getMediaById(@Path("id") id: Int): Media

    @GET("media/research/{keyword}")
    suspend fun researchMedia(@Path("keyword") keyword: String): List<Media>

    @GET("media/media-info/{mediaId}")
    suspend fun getMediaInfo(@Path("mediaId") mediaId: Int): MediaInfo

    @POST("media/catalog")
    suspend fun getCatalog(
        @Body filters: List<CatalogFilter>,
        @Query("sortFilter") sortFilter: String?,
        @Query("orderDirection") orderDirection: String?,
        @Query("count") count: Int?,
        @Query("offset") offset: Int?,
    ): CatalogResponse

    // ── news.ts ──
    @GET("news")
    suspend fun getNews(): List<News>

    // ── newsVideoRunning.ts ──
    @GET("news-video-running/movies")
    suspend fun getNewsVideoRunningMovies(): NewsVideoRunning

    @GET("news-video-running/series")
    suspend fun getNewsVideoRunningSeries(): NewsVideoRunning

    // ── profilPhoto.ts ──
    @GET("profil-photo")
    suspend fun getProfilPhotos(): List<ProfilPhoto>

    // ── similarTitle.ts ──
    @GET("similar-title/{id}")
    suspend fun getSimilarTitles(@Path("id") id: Int): List<Media>

    // ── statUser.ts (userId 0 always targets "me", same as the RN app) ──
    // All three `limit` params below are required despite the `= 10`
    // default on the backend's own method signature: NestJS's bare
    // ParseIntPipe validates the raw query value *before* that default
    // parameter ever gets a chance to apply, so an entirely absent `limit`
    // throws "Validation failed (numeric string is expected)" — a 400 —
    // instead of quietly falling back to 10. Sending it explicitly avoids
    // that.
    @GET("stat-user/{userId}/categories/preferences")
    suspend fun getCategoryPreferences(@Path("userId") userId: Int = 0, @Query("limit") limit: Int = 10): UserCategoryPreferences

    @GET("stat-user/{userId}/categories/preferences-weighted")
    suspend fun getCategoryPreferencesWeighted(@Path("userId") userId: Int = 0, @Query("limit") limit: Int = 10): UserCategoryPreferences

    @GET("stat-user/{userId}/categories/preferences-by-time")
    suspend fun getCategoryPreferencesByTime(@Path("userId") userId: Int = 0, @Query("limit") limit: Int = 10): List<CategoryTimeStat>

    @GET("stat-user/users/{userId}/watch-time")
    suspend fun getWatchTime(@Path("userId") userId: Int = 0): WatchTimeStats

    /** `startDate` is required server-side (`getUserWatchingHistory`'s own
     * signature has no default for it, unlike periodType/contentType) — the
     * SQL literally compares `su.createdAt >= ?` against it, so omitting it
     * silently returns nothing rather than erroring. Callers pass a real
     * "yyyy-MM-dd" string. */
    @GET("stat-user/users/{userId}/watching-history")
    suspend fun getWatchingHistory(
        @Path("userId") userId: Int = 0,
        @Query("startDate") startDate: String,
        @Query("periodType") periodType: String = "week",
        @Query("contentType") contentType: String = "all",
    ): WatchingStatsResponse

    @GET("stat-user/users/{userId}/top-media")
    suspend fun getTopMedia(@Path("userId") userId: Int = 0, @Query("mediaType") mediaType: String = "all"): TopMediaResponse

    // Fire-and-forget from PlayerScreen on exit — the returned
    // ProgressingStreamMedia isn't used client-side, so it's discarded.
    @POST("stat-user/save-watching-progress-movie/{movieId}")
    suspend fun saveMovieWatchProgress(@Path("movieId") movieId: Int, @Body payload: WatchProgressPayload): Unit

    @POST("stat-user/save-watching-progress-episode/{episodeId}")
    suspend fun saveEpisodeWatchProgress(@Path("episodeId") episodeId: Int, @Body payload: WatchProgressPayload): Unit

    // ── support.ts ──
    @GET("support")
    suspend fun getSupportTopics(): List<Support>

    @POST("support")
    suspend fun submitSupport(@Body payload: SubmitSupportPayload): ReturnMessage

    // ── documentation.ts — the backend serves one raw PDF file (inline
    // application/pdf, not a structured FAQ), rendered page-by-page on the
    // client via Android's own PdfRenderer. @Streaming avoids Retrofit
    // buffering the whole file into memory before handing back the body. ──
    @Streaming
    @GET("documentation")
    suspend fun getDocumentationPdf(): okhttp3.ResponseBody
}
