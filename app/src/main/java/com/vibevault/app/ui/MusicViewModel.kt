package com.vibevault.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController

class MusicViewModel : ViewModel() {

    fun playTrack(trackId: String, title: String, artist: String, coverUrl: String, controller: MediaController?) {
        if (controller == null) return

        // Construct metadata for the lock-screen/notification
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(coverUrl))
            .build()

        // Create a shell MediaItem containing ONLY the trackId and metadata.
        // The URI is left null. Our PlaybackService's CustomMediaSessionCallback 
        // will intercept this and fetch the stream URL asynchronously.
        val mediaItem = MediaItem.Builder()
            .setMediaId(trackId)
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }
}
