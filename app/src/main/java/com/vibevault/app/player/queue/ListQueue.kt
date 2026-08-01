package com.vibevault.app.player.queue

import com.vibevault.app.domain.model.Track

class ListQueue(
    val items: List<Track>,
    val startIndex: Int = 0,
    val title: String? = null,
    override val preloadItem: Track? = null
) : Queue {
    override suspend fun getInitialStatus(): Queue.Status {
        return Queue.Status(
            title = title,
            items = items,
            mediaItemIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        )
    }

    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage(): List<Track> = emptyList()
}
