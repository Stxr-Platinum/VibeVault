package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistPanelVideoRenderer
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class RadioChip(
    val title: String,
    val params: String,
    val isSelected: Boolean
)

data class NextResult(
    val title: String? = null,
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String?,
    val endpoint: WatchEndpoint, // current or continuation next endpoint
    val radioChips: List<RadioChip> = emptyList(),
)

object NextPage {
    fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val longByLineRuns = renderer.longBylineText?.runs?.splitBySeparator() ?: return null

        // Extract library tokens using the new method that properly handles multiple toggle items
        val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

        val rawRuns = renderer.longBylineText.runs
        val hasViewsInByline = rawRuns.any { it.text.contains("views", ignoreCase = true) }
        val mVideoType = renderer.navigationEndpoint.musicVideoType
        val isVideoSong = (mVideoType != null && mVideoType != MUSIC_VIDEO_TYPE_ATV) || hasViewsInByline

        val albumRun = if (isVideoSong) null else {
            rawRuns.firstOrNull { run ->
                val browse = run.navigationEndpoint?.browseEndpoint
                browse?.isAlbumEndpoint == true ||
                    browse?.browseId?.startsWith("MPREb_") == true ||
                    browse?.browseId?.startsWith("OLAK5uy_") == true
            } ?: longByLineRuns.getOrNull(1)?.firstOrNull()?.takeIf {
                val browse = it.navigationEndpoint?.browseEndpoint
                browse != null && (browse.isAlbumEndpoint || browse.browseId.startsWith("MPREb_") || browse.browseId.startsWith("OLAK5uy_"))
            }
        }

        val resolvedAlbum = albumRun?.let { run ->
            val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
            if (!browseId.isNullOrBlank()) {
                Album(name = run.text, id = browseId)
            } else null
        }

        return SongItem(
            id = renderer.videoId ?: return null,
            title =
                renderer.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null,
            artists =
                longByLineRuns.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: return null,
            album = resolvedAlbum,
            duration =
                renderer.lengthText
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.parseTime() ?: return null,
            musicVideoType = if (isVideoSong && mVideoType == null) "MUSIC_VIDEO_TYPE_OMV" else mVideoType,
            thumbnail =
                renderer.thumbnail.thumbnails
                    .lastOrNull()
                    ?.url ?: return null,
            explicit =
                renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
            libraryAddToken = libraryTokens.addToken,
            libraryRemoveToken = libraryTokens.removeToken
        )
    }
}
