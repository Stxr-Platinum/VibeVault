package com.vibevault.app.player.queue

import com.vibevault.app.domain.model.Track

interface Queue {
    val preloadItem: Track?
    val radioChips: kotlinx.coroutines.flow.StateFlow<List<com.music.innertube.pages.RadioChip>>? get() = null

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
