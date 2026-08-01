package com.vibevault.app.player.queue

import com.vibevault.app.domain.model.Track

interface Queue {
    val preloadItem: Track?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<Track>

    data class Status(
        val title: String?,
        val items: List<Track>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    )
}
