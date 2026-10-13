package com.chocoplusmobile.api

import com.chocoplusmobile.data.Adapters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Mirrors the RN app's navigation/types.ts PlayerParams union. */
sealed class PlayerTarget {
    abstract val title: String
    abstract val posterUrl: String?
    abstract val progressPercent: Double?

    data class MovieTarget(
        val movieId: Int,
        override val title: String,
        override val posterUrl: String?,
        override val progressPercent: Double?,
    ) : PlayerTarget()

    data class EpisodeTarget(
        val seasonId: Int,
        val episodeId: Int,
        val seriesId: Int,
        override val title: String,
        override val posterUrl: String?,
        override val progressPercent: Double?,
    ) : PlayerTarget()
}

/**
 * Last progress (percent) the player itself recorded per movie/episode,
 * for the rest of the process. The PlayerTarget handed to PlayerScreen is
 * built from whatever Media/Episode the calling screen already had — often
 * a ContentCache snapshot fetched before this session's viewing — so
 * without this, quitting a video and relaunching it would resume from the
 * stale server value instead of where the viewer actually stopped.
 */
object WatchProgressStore {
    /** Snapshot of everything recorded so far — a StateFlow (not a plain
     * map) so the detail screens' progress bars recompose on their own
     * when the player records a new value. */
    data class Snapshot(
        val movies: Map<Int, Double> = emptyMap(),
        val episodes: Map<Int, Double> = emptyMap(),
        /** seriesId → id of the episode last left in the player. */
        val lastEpisodeBySeries: Map<Int, Int> = emptyMap(),
    ) {
        fun movie(movieId: Int): Double? = movies[movieId]
        fun episode(episodeId: Int): Double? = episodes[episodeId]
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun record(target: PlayerTarget, percent: Double) {
        _state.update { s ->
            when (target) {
                is PlayerTarget.MovieTarget -> s.copy(movies = s.movies + (target.movieId to percent))
                is PlayerTarget.EpisodeTarget -> s.copy(
                    episodes = s.episodes + (target.episodeId to percent),
                    lastEpisodeBySeries = s.lastEpisodeBySeries + (target.seriesId to target.episodeId),
                )
            }
        }
    }

    /** `target` with its progressPercent replaced by the locally recorded
     * one, when there is one — otherwise unchanged. */
    fun applyTo(target: PlayerTarget): PlayerTarget {
        val s = _state.value
        return when (target) {
            is PlayerTarget.MovieTarget -> s.movie(target.movieId)?.let { target.copy(progressPercent = it) } ?: target
            is PlayerTarget.EpisodeTarget -> s.episode(target.episodeId)?.let { target.copy(progressPercent = it) } ?: target
        }
    }
}

/**
 * Mirrors the RN app's api/playback.ts. Movies stream directly by id;
 * series need an episode first. No endpoint returns "the" episode to play
 * for a series without picking one, so this asks for the last one watched
 * and falls back to the very first episode for a series the user has never
 * started.
 */
object Playback {
    suspend fun resolvePlayTarget(media: Media): PlayerTarget {
        if (media.mediaType == MediaType.SERIES) {
            val episode = try {
                NetworkModule.safeCall { NetworkModule.api.getLastEpisodeWatched(media.id) }
            } catch (e: Exception) {
                NetworkModule.safeCall { NetworkModule.api.getFirstEpisode(media.id) }
            }
            return PlayerTarget.EpisodeTarget(
                seasonId = episode.seasonId,
                episodeId = episode.id,
                seriesId = media.id,
                title = "${media.title} · ${episode.name}",
                posterUrl = Adapters.episodePosterUri(episode),
                progressPercent = episode.watchProgress,
            )
        }
        return PlayerTarget.MovieTarget(
            movieId = media.id,
            title = media.title,
            posterUrl = Adapters.posterUri(media),
            progressPercent = media.watchProgress,
        )
    }
}
