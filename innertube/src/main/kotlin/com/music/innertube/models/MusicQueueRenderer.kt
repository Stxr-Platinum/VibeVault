package com.music.innertube.models

import com.music.innertube.models.NavigationEndpoint
import kotlinx.serialization.Serializable

@Serializable
data class MusicQueueRenderer(
    val content: Content?,
    val header: Header?,
    val subHeaderChipCloud: Header.MusicQueueHeaderRenderer.ChipCloud? = null,
) {
    @Serializable
    data class Content(
        val playlistPanelRenderer: PlaylistPanelRenderer,
    )

    @Serializable
    data class Header(
        val musicQueueHeaderRenderer: MusicQueueHeaderRenderer?,
    ) {
        @Serializable
        data class MusicQueueHeaderRenderer(
            val title: Runs?,
            val subtitle: Runs?,
            val chips: ChipCloud? = null,
        ) {
            @Serializable
            data class ChipCloud(
                val chipCloudRenderer: ChipCloudRenderer? = null,
            ) {
                @Serializable
                data class ChipCloudRenderer(
                    val chips: List<Chip>? = null,
                ) {
                    @Serializable
                    data class Chip(
                        val chipCloudChipRenderer: ChipCloudChipRenderer? = null,
                    ) {
                        @Serializable
                        data class ChipCloudChipRenderer(
                            val text: Runs? = null,
                            val navigationEndpoint: NavigationEndpoint? = null,
                            val isSelected: Boolean = false,
                        )
                    }
                }
            }
        }
    }
}
