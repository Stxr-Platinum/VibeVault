package com.vibevault.app.data.remote.api

import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.domain.model.*
import com.vibevault.app.core.session.SessionManager
import com.music.spotify.Spotify
import com.music.spotify.models.SpotifyPlaylist
import com.music.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SpotifyApiService @Inject constructor(
    private val sessionManager: SessionManager
) {

    private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T {
        return runCatching { block() }.getOrElse { error ->
            if ((error as? Spotify.SpotifyException)?.statusCode != 401) {
                throw error
            }
            sessionManager.refreshSpotifyToken() ?: throw error
            block()
        }
    }

    suspend fun getUserProfile(): Result<Any> = Result.failure(Exception("Stub"))

    suspend fun searchTracks(query: String): Result<SpotifySearchResponse> = Result.failure(Exception("Stub"))
    suspend fun getRecommendations(seedId: String?): Result<List<SpotifyTrackDto>> = Result.failure(Exception("Stub"))
    suspend fun getTrack(trackId: String): Result<SpotifyTrackDto> = Result.failure(Exception("Stub"))
    suspend fun getRecentlyPlayed(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getFeaturedPlaylists(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getNewReleases(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getTopArtists(): Result<Any> = Result.failure(Exception("Stub"))

    suspend fun getUserPlaylists(): Result<List<SpotifyPlaylistDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistsList = mutableListOf<SpotifyPlaylist>()
            var offset = 0
            val limit = 50
            while (true) {
                val page = spotifyCallWithTokenRetry {
                    Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                }
                playlistsList.addAll(page.items)
                offset += limit
                if (offset >= page.total || page.items.isEmpty()) break
            }

            playlistsList.map { playlist ->
                SpotifyPlaylistDto(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    images = playlist.images.map { img -> SpotifyImageDto(url = img.url, height = img.height, width = img.width) },
                    owner = playlist.owner?.let { owner -> SpotifyUserDto(id = owner.id, displayName = owner.displayName) }
                )
            }
        }
    }

    suspend fun getBrowseCategories(): Result<Any> = Result.failure(Exception("Stub"))

    suspend fun getPlaylistTracks(playlistId: String): Result<List<SpotifyPlaylistItemDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = mutableListOf<SpotifyTrack>()
            var offset = 0
            val limit = 100
            while (true) {
                val page = spotifyCallWithTokenRetry {
                    Spotify.playlistTracks(playlistId, limit = limit, offset = offset).getOrThrow()
                }
                tracks.addAll(page.items.mapNotNull { it.track })
                offset += limit
                if (offset >= page.total || page.items.isEmpty()) break
            }

            tracks.map { track ->
                val trackDto = SpotifyTrackDto(
                    id = track.id,
                    name = track.name,
                    artists = track.artists.map { SpotifyArtistDto(id = it.id ?: "", name = it.name) },
                    album = track.album?.let { album ->
                        SpotifyAlbumDto(
                            id = album.id,
                            name = album.name,
                            images = album.images.map { img -> SpotifyImageDto(url = img.url, height = img.height, width = img.width) }
                        )
                    },
                    durationMs = track.durationMs.toLong(),
                    previewUrl = null
                )
                SpotifyPlaylistItemDto(track = trackDto, item = trackDto)
            }
        }
    }
}
