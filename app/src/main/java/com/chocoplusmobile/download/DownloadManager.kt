package com.chocoplusmobile.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chocoplusmobile.BuildConfig
import com.chocoplusmobile.api.Episode
import com.chocoplusmobile.api.Media
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.PlayerTarget
import com.chocoplusmobile.api.Role
import com.chocoplusmobile.data.Adapters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Only these roles may download — mirrors the backend's FamilyUserGuard. */
fun Role?.canDownload(): Boolean = this == Role.ADMIN || this == Role.FAMILY

@Serializable
enum class DownloadKind { MOVIE, EPISODE }

@Serializable
enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED,
    /** Interrupted (app restart, logout) — not running, resumable. */
    PAUSED,
}

/**
 * One downloaded (or downloading) movie or episode. `media` is the movie —
 * or, for an episode, its series — and `episode` the episode itself; both
 * are copies of the API records with every image URL rewritten to a local
 * `file:` URI, so the Downloads screen renders them with no network at all.
 * They stay null until the metadata step of the download has run.
 */
@Serializable
data class DownloadEntry(
    val key: String,
    val kind: DownloadKind,
    /** The movie's id, or the series' id for an episode. */
    val mediaId: Int,
    val episodeId: Int? = null,
    val seasonId: Int? = null,
    val media: Media? = null,
    val episode: Episode? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0,
    /** -1 while unknown (no Content-Length yet). */
    val totalBytes: Long = -1,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING

    /** 0–100, or null while the total size isn't known yet. */
    val progressPercent: Int?
        get() = if (status == DownloadStatus.COMPLETED) 100
        else if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
        else null
}

/**
 * Downloads movies/episodes for offline viewing: video file from
 * `download/download-movie|episode/:id`, plus metadata (`media/:id`,
 * `series/episode/:id`) and the images the Downloads screen and detail
 * screens show, all stored under filesDir/downloads.
 *
 * Downloads run in this object's own process-wide scope (not a screen's),
 * kept alive in the background by [DownloadService] while any is active.
 * An interrupted download keeps its `.part` file and resumes with an HTTP
 * Range request when restarted (falling back to a fresh download if the
 * server ignores the Range header).
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val MAX_PARALLEL = 2
    private const val PROGRESS_THROTTLE_MS = 400L

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val slots = Semaphore(MAX_PARALLEL)
    private val indexMutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _entries = MutableStateFlow<Map<String, DownloadEntry>>(emptyMap())
    val entries: StateFlow<Map<String, DownloadEntry>> = _entries.asStateFlow()

    // Downloads can be several GB — the shared client's 15s timeouts are
    // per-read so they'd mostly be fine, but there's no reason to fail a
    // download over one slow chunk either.
    private val httpClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val rootDir get() = File(appContext.filesDir, "downloads")
    private val videosDir get() = File(rootDir, "videos").apply { mkdirs() }
    private val imagesDir get() = File(rootDir, "images").apply { mkdirs() }
    private val indexFile get() = File(rootDir, "index.json")

    fun movieKey(movieId: Int) = "movie_$movieId"
    fun episodeKey(episodeId: Int) = "episode_$episodeId"

    fun init(context: Context) {
        appContext = context.applicationContext
        val loaded = try {
            if (indexFile.exists()) json.decodeFromString(ListSerializer(DownloadEntry.serializer()), indexFile.readText()) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable download index, starting empty", e)
            emptyList()
        }
        // Nothing is actually running at process start — whatever was
        // in-flight is PAUSED until resumePending() picks it up (once a
        // token is available to authenticate the request).
        _entries.value = loaded.associate { e ->
            e.key to if (e.isActive) e.copy(status = DownloadStatus.PAUSED) else e
        }
    }

    // ── Public actions ──────────────────────────────────────────────────

    fun downloadMovie(movieId: Int) {
        enqueue(DownloadEntry(key = movieKey(movieId), kind = DownloadKind.MOVIE, mediaId = movieId))
    }

    fun downloadEpisode(episode: Episode) {
        enqueue(
            DownloadEntry(
                key = episodeKey(episode.id),
                kind = DownloadKind.EPISODE,
                mediaId = episode.seriesId,
                episodeId = episode.id,
                seasonId = episode.seasonId,
            ),
        )
    }

    /** A whole season in one go — skips episodes already downloaded or
     * in progress, so it's also how a partially-downloaded season is
     * completed. */
    fun downloadEpisodes(episodes: List<Episode>) {
        episodes.forEach { ep ->
            val existing = _entries.value[episodeKey(ep.id)]
            if (existing == null || existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.PAUSED) downloadEpisode(ep)
        }
    }

    /** Restarts a failed or paused download where it left off. */
    fun retry(key: String) {
        val entry = _entries.value[key] ?: return
        enqueue(entry.copy(status = DownloadStatus.QUEUED, error = null))
    }

    /** Cancels an in-progress download or deletes a finished one — either
     * way the entry and every file it owns are gone afterwards. */
    fun remove(key: String) {
        val entry = _entries.value[key] ?: return
        _entries.update { it - key }
        val job = jobs.remove(key)
        scope.launch {
            job?.cancelAndJoin()
            videoFile(key).delete()
            partFile(key).delete()
            entry.episodeId?.let { imageFile("episode_$it").delete() }
            // Series/movie artwork is shared by every episode of the series —
            // only cleaned up once nothing references that title any more.
            if (_entries.value.values.none { it.mediaId == entry.mediaId && it.kind == entry.kind }) {
                imagesDir.listFiles { f -> f.name.startsWith(mediaImagePrefix(entry.kind, entry.mediaId)) }?.forEach { it.delete() }
            }
            persist()
        }
    }

    /** Deletes everything — finished downloads, in-progress ones (which are
     * cancelled first) and every stored file, metadata and image included. */
    fun removeAll() {
        val running = jobs.values.toList()
        jobs.clear()
        _entries.value = emptyMap()
        scope.launch {
            running.forEach { it.cancelAndJoin() }
            videosDir.listFiles()?.forEach { it.delete() }
            imagesDir.listFiles()?.forEach { it.delete() }
            persist()
        }
    }

    /** Restarts every download left PAUSED (by a previous app run, or by
     * pauseAll()) — called once a valid token is available. */
    fun resumePending() {
        _entries.value.values.filter { it.status == DownloadStatus.PAUSED && !jobs.containsKey(it.key) }.forEach { enqueue(it) }
    }

    /** Stops every running download without deleting anything (logout) —
     * they're PAUSED and resume on the next resumePending(). */
    fun pauseAll() {
        jobs.keys.toList().forEach { key -> jobs.remove(key)?.cancel() }
        _entries.update { map -> map.mapValues { (_, e) -> if (e.isActive) e.copy(status = DownloadStatus.PAUSED) else e } }
        scope.launch { persist() }
    }

    /** Local `file:` URI of a completed download for this player target,
     * or null if it isn't (fully) downloaded. */
    fun localVideoUri(target: PlayerTarget): String? {
        val key = when (target) {
            is PlayerTarget.MovieTarget -> movieKey(target.movieId)
            is PlayerTarget.EpisodeTarget -> episodeKey(target.episodeId)
        }
        if (_entries.value[key]?.status != DownloadStatus.COMPLETED) return null
        val file = videoFile(key)
        return if (file.exists()) Uri.fromFile(file).toString() else null
    }

    // ── Internals ───────────────────────────────────────────────────────

    private fun videoFile(key: String) = File(videosDir, "$key.video")
    private fun partFile(key: String) = File(videosDir, "$key.part")
    private fun imageFile(name: String) = File(imagesDir, "$name.img")
    private fun mediaImagePrefix(kind: DownloadKind, mediaId: Int) =
        if (kind == DownloadKind.MOVIE) "movie_${mediaId}_" else "series_${mediaId}_"

    private fun enqueue(entry: DownloadEntry) {
        if (jobs.containsKey(entry.key)) return
        val current = _entries.value[entry.key]
        if (current?.status == DownloadStatus.COMPLETED) return
        // Keep whatever metadata/progress an earlier attempt already got.
        val merged = current?.copy(status = DownloadStatus.QUEUED, error = null) ?: entry
        _entries.update { it + (entry.key to merged) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                slots.withPermit { run(entry.key) }
            } finally {
                // Only drop our own handle — a quick remove()+re-download
                // may already have registered a newer job under this key.
                coroutineContext[Job]?.let { jobs.remove(entry.key, it) }
            }
        }
        jobs[entry.key] = job
        job.start()
        scope.launch { persist() }
        DownloadService.start(appContext)
    }

    private fun updateEntry(key: String, block: (DownloadEntry) -> DownloadEntry) {
        _entries.update { map -> map[key]?.let { map + (key to block(it)) } ?: map }
    }

    private suspend fun run(key: String) {
        updateEntry(key) { it.copy(status = DownloadStatus.DOWNLOADING) }
        try {
            if (_entries.value[key]?.media == null) fetchMetadata(key)
            downloadVideo(key)
            updateEntry(key) { it.copy(status = DownloadStatus.COMPLETED, bytesDownloaded = it.totalBytes.coerceAtLeast(it.bytesDownloaded)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Download $key failed", e)
            updateEntry(key) { it.copy(status = DownloadStatus.FAILED, error = e.message ?: "Erreur réseau") }
        } finally {
            withContext(NonCancellable) { persist() }
        }
    }

    private suspend fun fetchMetadata(key: String) {
        val entry = _entries.value[key] ?: return
        // A series' metadata/artwork is shared by all its episodes — reuse
        // the local copy another episode already stored when there is one.
        val sharedMedia = _entries.value.values.firstOrNull { it.kind == entry.kind && it.mediaId == entry.mediaId && it.media != null && it.key != key }?.media
        val media = sharedMedia ?: localizeMedia(entry.kind, NetworkModule.safeCall { NetworkModule.api.getMediaById(entry.mediaId) })
        val episode = entry.episodeId?.let { id ->
            val ep = NetworkModule.safeCall { NetworkModule.api.getEpisodeById(id) }
            // This route returns a raw DB row, so its ids may be missing (0)
            // — keep the ones already known from the series screen instead.
            ep.copy(
                srcPoster = saveImage(Adapters.episodePosterUri(ep), "episode_$id"),
                seasonId = ep.seasonId.takeIf { it != 0 } ?: entry.seasonId ?: 0,
                seriesId = ep.seriesId.takeIf { it != 0 } ?: entry.mediaId,
            )
        }
        updateEntry(key) { it.copy(media = media, episode = episode, seasonId = episode?.seasonId ?: it.seasonId) }
        persist()
    }

    /** Downloads the images the app actually displays for a title (at the
     * current quality setting) and points the record's URLs at them. */
    private suspend fun localizeMedia(kind: DownloadKind, media: Media): Media {
        val prefix = mediaImagePrefix(kind, media.id)
        val poster = saveImage(Adapters.posterUri(media), "${prefix}poster")
        val backdrop = saveImage(Adapters.backdropUri(media), "${prefix}backdrop")
        val logo = saveImage(Adapters.logoUri(media), "${prefix}logo")
        return media.copy(
            srcPoster = media.srcPoster.copy(
                normal = listOfNotNull(poster),
                special = emptyList(),
                license = emptyList(),
                horizontal = listOfNotNull(backdrop),
            ),
            srcBackgroundImage = backdrop,
            srcLogo = logo,
            // Cast/crew photos aren't stored — drop their URLs so nothing
            // tries to fetch them offline.
            credits = media.credits.map { it.copy(srcPoster = null) },
            seasons = media.seasons?.map { s -> s.copy(srcPoster = null, episodes = emptyList()) },
        )
    }

    /** Best-effort: a missing image just means a gradient placeholder. */
    private suspend fun saveImage(url: String?, name: String): String? {
        if (url.isNullOrEmpty()) return null
        return try {
            coroutineContext.ensureActive()
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val file = imageFile(name)
                response.body?.byteStream()?.use { input -> FileOutputStream(file).use { input.copyTo(it) } } ?: return null
                Uri.fromFile(file).toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not store image $url", e)
            null
        }
    }

    private suspend fun downloadVideo(key: String) {
        val entry = _entries.value[key] ?: return
        val url = when (entry.kind) {
            DownloadKind.MOVIE -> "${BuildConfig.API_URL}/download/download-movie/${entry.mediaId}"
            DownloadKind.EPISODE -> "${BuildConfig.API_URL}/download/download-episode/${entry.episodeId}"
        }
        val part = partFile(key)
        val offset = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
        val call = httpClient.newCall(request)
        // A blocking read doesn't notice coroutine cancellation by itself —
        // cancelling the call makes it throw right away instead.
        coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }

        call.execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Erreur ${response.code}")
            val body = response.body ?: throw java.io.IOException("Réponse vide")
            // 206 = the server honoured the Range header, append; 200 = it
            // sent the whole file again, start over.
            val resumed = response.code == 206 && offset > 0
            val start = if (resumed) offset else 0L
            val length = body.contentLength()
            val total = if (length >= 0) start + length else -1L
            updateEntry(key) { it.copy(bytesDownloaded = start, totalBytes = total) }

            var written = start
            var lastEmit = 0L
            body.byteStream().use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= PROGRESS_THROTTLE_MS) {
                            lastEmit = now
                            val w = written
                            updateEntry(key) { it.copy(bytesDownloaded = w) }
                        }
                    }
                }
            }
            if (total > 0 && written < total) throw java.io.IOException("Téléchargement interrompu")
            updateEntry(key) { it.copy(bytesDownloaded = written, totalBytes = if (total > 0) total else written) }
        }
        val target = videoFile(key)
        target.delete()
        if (!part.renameTo(target)) throw java.io.IOException("Impossible d'enregistrer la vidéo")
    }

    private suspend fun persist() {
        indexMutex.withLock {
            try {
                rootDir.mkdirs()
                val tmp = File(rootDir, "index.json.tmp")
                tmp.writeText(json.encodeToString(ListSerializer(DownloadEntry.serializer()), _entries.value.values.toList()))
                if (!tmp.renameTo(indexFile)) {
                    indexFile.delete()
                    tmp.renameTo(indexFile)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist download index", e)
            }
        }
    }
}
