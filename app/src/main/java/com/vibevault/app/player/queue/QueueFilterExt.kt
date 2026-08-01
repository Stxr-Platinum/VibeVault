package com.vibevault.app.player.queue

import com.vibevault.app.domain.model.Track

/**
 * Filter extension to enforce musical style diversity in queues.
 * Strictly caps the number of occurrences of songs by the same artist in a queue window (default 1),
 * ensuring the queue consists of songs from diverse artists that share comparable musical style & genre.
 */
fun List<Track>.filterDiverseCharacteristics(
    maxPerArtist: Int = 1,
    currentlyPlayingTrackId: String? = null
): List<Track> {
    if (isEmpty()) return this

    val artistCounts = mutableMapOf<String, Int>()
    val seenTrackIds = mutableSetOf<String>()
    val result = mutableListOf<Track>()

    for (track in this) {
        if (!seenTrackIds.add(track.id)) continue

        val primaryArtist = getPrimaryArtist(track.artist)
        
        // Always include currently playing track
        if (track.id == currentlyPlayingTrackId) {
            result.add(track)
            artistCounts[primaryArtist] = (artistCounts[primaryArtist] ?: 0) + 1
            continue
        }

        val count = artistCounts[primaryArtist] ?: 0

        if (count < maxPerArtist) {
            result.add(track)
            artistCounts[primaryArtist] = count + 1
        }
    }

    return result
}

private fun getPrimaryArtist(artistString: String): String {
    return artistString
        .split(",", "&", "feat.", "ft.", "with", "/")
        .firstOrNull()
        ?.lowercase()
        ?.trim()
        ?: artistString.lowercase().trim()
}
