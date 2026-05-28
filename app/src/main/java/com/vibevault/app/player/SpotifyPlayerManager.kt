package com.vibevault.app.player

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.vibevault.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class SpotifyPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var appRemote: SpotifyAppRemote? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val TAG = "SpotifyPlayerManager"
    }

    /**
     * Error events emitted to the UI layer. Observers can collect this to show
     * error messages, dismiss loading spinners, etc.
     */
    private val _errorEvents = MutableSharedFlow<SpotifyError>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<SpotifyError> = _errorEvents.asSharedFlow()

    /**
     * Represents a categorized Spotify error for UI consumption.
     */
    sealed class SpotifyError(val message: String) {
        /** Spotify app is not installed on the device */
        class AppNotInstalled : SpotifyError("Spotify app is missing. Please install the official Spotify app to play music.")
        /** Connection to Spotify failed for another reason */
        class ConnectionFailed(reason: String?) : SpotifyError(reason ?: "Failed to connect to Spotify")
        /** Playback failed after connecting */
        class PlaybackFailed(reason: String?) : SpotifyError(reason ?: "Unable to play track")
    }

    /**
     * Attempts to connect to Spotify App Remote.
     * @return true if connected successfully
     * @throws CouldNotFindSpotifyApp if the Spotify app is not installed
     * @throws Exception for other connection failures
     */
    suspend fun connect(): Boolean = suspendCoroutine { continuation ->
        if (appRemote?.isConnected == true) {
            continuation.resume(true)
            return@suspendCoroutine
        }

        val connectionParams = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                appRemote = spotifyAppRemote
                Log.i(TAG, "Connected to Spotify App Remote!")
                continuation.resume(true)
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "Failed to connect to Spotify App Remote", throwable)
                continuation.resumeWithException(throwable)
            }
        })
    }

    /**
     * Plays a track via Spotify App Remote. Handles connection failures gracefully
     * and emits errors via [errorEvents] so the UI can react.
     *
     * @return false if playback could not be initiated (connection failure, app missing, etc.)
     */
    suspend fun playTrack(trackId: String): Boolean {
        try {
            if (appRemote?.isConnected != true) {
                val connected = try {
                    connect()
                } catch (e: CouldNotFindSpotifyApp) {
                    Log.e(TAG, "Spotify app not installed on device during connect", e)
                    _errorEvents.tryEmit(SpotifyError.AppNotInstalled())
                    Toast.makeText(
                        context,
                        "Spotify app is missing. Please install the official Spotify app to play music.",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                } catch (e: com.spotify.android.appremote.api.error.UserNotAuthorizedException) {
                    Log.e(TAG, "User not authorized (Missing Scopes, missing SHA-1 fingerprint on Dashboard, or not Premium)", e)
                    _errorEvents.tryEmit(SpotifyError.ConnectionFailed("Not Authorized"))
                    Toast.makeText(
                        context,
                        "Spotify connection rejected: Please check your Developer Dashboard SHA-1 fingerprint or Premium status.",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                } catch (t: Throwable) {
                    Log.e(TAG, "Exception during connect", t)
                    _errorEvents.tryEmit(SpotifyError.ConnectionFailed(t.message))
                    Toast.makeText(
                        context,
                        "Failed to connect to Spotify: ${t.message ?: "Unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                if (!connected) {
                    _errorEvents.tryEmit(SpotifyError.ConnectionFailed(null))
                    return false
                }
            }
            
            // Format strictly: spotify:track:ID
            val spotifyUri = if (!trackId.startsWith("spotify:track:")) {
                "spotify:track:$trackId"
            } else {
                trackId
            }

            Log.d(TAG, "Attempting to play: $spotifyUri")
            
            return suspendCancellableCoroutine { continuation ->
                val call = appRemote?.playerApi?.play(spotifyUri)
                if (call == null) {
                    if (continuation.isActive) continuation.resume(false)
                    return@suspendCancellableCoroutine
                }
                call.setResultCallback {
                    Log.d(TAG, "Playback started successfully via App Remote")
                    if (continuation.isActive) continuation.resume(true)
                }.setErrorCallback { error ->
                    Log.e(TAG, "Playback failed: ${error.message}", error)
                    _errorEvents.tryEmit(SpotifyError.PlaybackFailed(error.message))
                    Toast.makeText(
                        context,
                        "Unable to play: ${error.message ?: "Spotify Premium required or App Remote disconnected"}",
                        Toast.LENGTH_LONG
                    ).show()
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } catch (e: CouldNotFindSpotifyApp) {
            Log.e(TAG, "Spotify app not installed on device", e)
            _errorEvents.tryEmit(SpotifyError.AppNotInstalled())
            Toast.makeText(
                context,
                "Spotify app is missing. Please install the official Spotify app to play music.",
                Toast.LENGTH_LONG
            ).show()
            return false
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during playback execution", t)
            _errorEvents.tryEmit(SpotifyError.ConnectionFailed(t.message))
            Toast.makeText(
                context,
                "Failed to connect to Spotify: ${t.message ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    fun pause() {
        appRemote?.playerApi?.pause()
    }

    fun resume() {
        appRemote?.playerApi?.resume()
    }

    fun disconnect() {
        appRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        appRemote = null
    }
}
