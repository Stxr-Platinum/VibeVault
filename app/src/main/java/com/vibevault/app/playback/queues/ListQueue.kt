package com.vibevault.app.playback.queues

import androidx.media3.common.MediaItem
import com.vibevault.app.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage() = false

    override suspend fun nextPage() = emptyList<MediaItem>()
}
