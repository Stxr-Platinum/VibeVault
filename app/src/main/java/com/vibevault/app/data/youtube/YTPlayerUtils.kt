/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.vibevault.app.data.youtube

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY
import com.music.innertube.models.YouTubeClient.Companion.MWEB
import com.music.innertube.models.response.PlayerResponse
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.IpVersion
import com.music.innertube.strategy.ContentAwareFallbackStrategy
import com.music.innertube.strategy.ContentHints
import com.music.jiosaavn.SaavnService
import com.music.jiosaavn.SaavnSong
import com.vibevault.app.constants.AudioQuality
import com.vibevault.app.constants.EnableSaavnStreamingKey
import com.vibevault.app.constants.SaavnAudioQualityKey
import com.vibevault.app.constants.SaavnAudioQuality
import com.vibevault.app.utils.BotDetectionMitigator
import com.vibevault.app.utils.dataStore
import com.vibevault.app.utils.get
import com.vibevault.app.utils.getAsync
import com.vibevault.app.utils.cipher.CipherDeobfuscator
import com.vibevault.app.utils.potoken.PoTokenGenerator
import com.vibevault.app.utils.potoken.PoTokenResult
import com.vibevault.app.utils.sabr.EjsNTransformSolver
import com.vibevault.app.utils.PlaybackLogLevel
import com.vibevault.app.utils.PlaybackLogManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32
    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX
    private val fallbackStrategy = ContentAwareFallbackStrategy()

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        ANDROID_VR_1_43_32,
        MWEB,
        WEB_REMIX,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        ANDROID_CREATOR,
        WEB_CREATOR
    )

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
        val streamHeaders: Map<String, String> = emptyMap(),
        /** True when the stream is sourced from JioSaavn (not YouTube). */
        val isSaavnStream: Boolean = false,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        contentHints: ContentHints = ContentHints(),
    ): Result<PlaybackData> {
        if (context != null) {
            val saavnEnabled = context.dataStore.getAsync(EnableSaavnStreamingKey, false)
            if (saavnEnabled) {
                Timber.tag(logTag).d("JioSaavn streaming enabled — trying Saavn for videoId=$videoId")
                val saavnResult = runCatching {
                    val (currentSong, meta) = coroutineScope {
                        val nextDeferred = async {
                            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
                            nextResult?.items?.getOrNull(nextResult.currentIndex ?: 0)
                                ?: nextResult?.items?.firstOrNull()
                        }
                        val metaDeferred = async {
                            playerResponseForMetadata(videoId, playlistId).getOrNull()
                        }
                        nextDeferred.await() to metaDeferred.await()
                    }

                    val title = currentSong?.title ?: meta?.videoDetails?.title.orEmpty()
                    val artistNames: List<String> = if (currentSong?.artists?.isNotEmpty() == true) {
                        currentSong.artists.map { it.name }
                    } else {
                        listOf(meta?.videoDetails?.author.orEmpty().trim()).filter { it.isNotBlank() }
                    }
                    val artist = artistNames.joinToString(", ")

                    if (title.isNotBlank() && !com.vibevault.app.utils.TrackMatcher.hasVersionModifier(title)) {
                        val expectedDuration = meta?.videoDetails?.lengthSeconds?.toIntOrNull()
                        val albumName = currentSong?.album?.name.orEmpty()
                        val wantedTitleLower = title.lowercase(java.util.Locale.US)
                        val wantedArtistsLower = artistNames.map { it.lowercase(java.util.Locale.US) }

                        val primaryQuery = if (albumName.isNotBlank()) "$albumName $title $artist" else "$title $artist"
                        val fallbackQuery = "$title $artist"

                        suspend fun findMatch(searchQuery: String): SaavnSong? {
                            if (searchQuery.isBlank()) return null
                            val rawSongs = SaavnService.searchSongs(searchQuery).getOrNull() ?: return null
                            return rawSongs.firstOrNull { candidate ->
                                val candidateTitleLower = candidate.name.lowercase(java.util.Locale.US)
                                val candidateArtists = candidate.artists.primary.map { it.name.lowercase(java.util.Locale.US) }
                                val titleMatches = candidateTitleLower == wantedTitleLower
                                val artistMatches = candidateArtists.sorted() == wantedArtistsLower.sorted()
                                val durationMatches = if (expectedDuration != null && candidate.duration != null) {
                                    java.lang.Math.abs(expectedDuration - candidate.duration!!) <= 12
                                } else {
                                    true
                                }
                                titleMatches && artistMatches && durationMatches
                            }
                        }

                        var bestSong = findMatch(primaryQuery)
                        if (bestSong == null && primaryQuery != fallbackQuery) {
                            bestSong = findMatch(fallbackQuery)
                        }

                        if (bestSong != null) {
                            val qualityKey = context.dataStore.getAsync(SaavnAudioQualityKey, SaavnAudioQuality.QUALITY_320.name)
                            val quality = runCatching { SaavnAudioQuality.valueOf(qualityKey) }
                                .getOrDefault(SaavnAudioQuality.QUALITY_320)

                            var resolvedUrl = SaavnService.selectBestUrl(bestSong.downloadUrl, quality.toApiValue())
                            if (resolvedUrl.isNullOrBlank()) {
                                resolvedUrl = SaavnService.getBestStreamUrl(bestSong.id, quality.toApiValue())
                            }

                            if (!resolvedUrl.isNullOrBlank()) {
                                val contentLength = SaavnService.getContentLength(resolvedUrl)
                                return@runCatching PlaybackData(
                                    audioConfig = meta?.playerConfig?.audioConfig,
                                    videoDetails = meta?.videoDetails,
                                    playbackTracking = meta?.playbackTracking,
                                    format = PlayerResponse.StreamingData.Format(
                                        itag = when (quality) {
                                            SaavnAudioQuality.QUALITY_320 -> 141
                                            SaavnAudioQuality.QUALITY_160 -> 140
                                            SaavnAudioQuality.QUALITY_96 -> 139
                                        },
                                        url = resolvedUrl,
                                        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                                        bitrate = when (quality) {
                                            SaavnAudioQuality.QUALITY_320 -> 320_000
                                            SaavnAudioQuality.QUALITY_160 -> 160_000
                                            SaavnAudioQuality.QUALITY_96 -> 96_000
                                        },
                                        width = null,
                                        height = null,
                                        contentLength = contentLength,
                                        quality = quality.toApiValue(),
                                        fps = null,
                                        qualityLabel = null,
                                        averageBitrate = null,
                                        audioQuality = quality.toApiValue(),
                                        approxDurationMs = null,
                                        audioSampleRate = null,
                                        audioChannels = null,
                                        loudnessDb = null,
                                        lastModified = null,
                                        signatureCipher = null,
                                        cipher = null,
                                        audioTrack = null
                                    ),
                                    streamUrl = resolvedUrl,
                                    streamExpiresInSeconds = 3600,
                                    streamClient = "JioSaavn",
                                    isSaavnStream = true
                                )
                            }
                        }
                    }
                    null
                }.getOrNull()

                if (saavnResult != null) {
                    return Result.success(saavnResult)
                }
            }
        }

        val firstAttempt = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, contentHints)
        
        if (firstAttempt.isFailure && YouTube.cookie == null) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, contentHints)
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return retryResult
        }
        
        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return firstAttempt
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        contentHints: ContentHints = ContentHints(),
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")
        
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null

        var signatureTimestamp: Int? = null
        var signatureTimestampFetched = false
        suspend fun getSigTimestampLazy(): Int? {
            if (!signatureTimestampFetched) {
                val result = getSignatureTimestampOrNull(videoId)
                signatureTimestamp = result.timestamp
                signatureTimestampFetched = true
            }
            return signatureTimestamp
        }

        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        var metadataResponse: PlayerResponse? = null
        var mainPlayerResponse: PlayerResponse
        coroutineScope {
            val mainDeferred = async {
                val sigTimestamp = if (MAIN_CLIENT.useSignatureTimestamp) getSigTimestampLazy() else null
                YouTube.player(videoId, playlistId, MAIN_CLIENT, sigTimestamp, poToken?.playerRequestPoToken).getOrThrow()
            }
            val metaDeferred = if (isLoggedIn) async {
                try {
                    var metaPoToken: PoTokenResult? = null
                    val metaSessionId = YouTube.visitorData
                    if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                        try {
                            metaPoToken = poTokenGenerator.getWebClientPoToken(videoId, metaSessionId)
                        } catch (_: Exception) { }
                    }
                    val sigTimestamp = if (METADATA_CLIENT.useSignatureTimestamp) getSigTimestampLazy() else null
                    YouTube.player(videoId, playlistId, METADATA_CLIENT, sigTimestamp, metaPoToken?.playerRequestPoToken).getOrNull()
                } catch (_: Exception) { null }
            } else null

            mainPlayerResponse = mainDeferred.await()
            metadataResponse = metaDeferred?.await()
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )
        val wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        val currentStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestricted = currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )

        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
        val musicVideoType = mainPlayerResponse.videoDetails?.musicVideoType.orEmpty()
        val effectiveHints = contentHints.copy(
            isExplicit = contentHints.isExplicit == true || isAgeRestricted,
            isKidsContent = contentHints.isKidsContent
                ?: musicVideoType.contains("KIDS", ignoreCase = true).takeIf { it },
            isLive = contentHints.isLive
                ?: musicVideoType.contains("LIVE", ignoreCase = true).takeIf { it },
            isUploaded = isPrivateTrack,
        )
        val resolvedFallback = fallbackStrategy.resolveClients(effectiveHints)
        val streamClients = if (MAIN_CLIENT in resolvedFallback) {
            listOf(MAIN_CLIENT) + (resolvedFallback - MAIN_CLIENT)
        } else {
            listOf(MAIN_CLIENT) + resolvedFallback
        }
        var successClient: YouTubeClient? = null

        for ((clientIndex, client) in streamClients.withIndex()) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                continue
            }
            if (client.requirePoToken && poToken == null) {
                continue
            }

            if (client == MAIN_CLIENT) {
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
            } else {
                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    } catch (_: Exception) { }
                }

                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                val clientSigTimestamp = if (wasOriginallyAgeRestricted || !client.useSignatureTimestamp) null else getSigTimestampLazy()
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val responseToUse = streamPlayerResponse

                format = findFormat(responseToUse, audioQuality, connectivityManager)
                if (format == null) continue

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) continue

                val currentClient = client

                if (streamUrl != null && streamUrl!!.contains("n=")) {
                    try {
                        var transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                        if (transformed == streamUrl) {
                            transformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                        }
                        if (transformed == streamUrl) {
                            transformed = com.music.innertube.pages.YouTubeExtractor.deobfuscateUrlNParam(streamUrl!!)
                        }
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).w(e, "N-transform failed")
                    }
                }

                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) continue

                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (client == MAIN_CLIENT || isPrivatelyOwned || clientIndex == streamClients.size - 1) {
                    successClient = currentClient
                    break
                }

                if (validateStatus(streamUrl!!, currentClient.streamHeaders())) {
                    successClient = currentClient
                    break
                } else {
                    if (currentClient.useWebPoTokens) {
                        var nTransformWorked = false
                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                            if (nTransformed != streamUrl) {
                                if (validateStatus(nTransformed, currentClient.streamHeaders())) {
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    successClient = currentClient
                                }
                            }
                        } catch (_: Exception) { }

                        if (nTransformWorked) break
                    }
                }
            }
        }

        if (streamPlayerResponse == null || streamPlayerResponse.playabilityStatus.status != "OK" || format == null || streamUrl == null || streamExpiresInSeconds == null) {
            throw Exception("Bad stream player response or non-playable format")
        }

        PlaybackData(
            audioConfig = audioConfig,
            videoDetails = videoDetails,
            playbackTracking = playbackTracking,
            format = format!!,
            streamUrl = streamUrl!!,
            streamExpiresInSeconds = streamExpiresInSeconds!!,
            streamClient = successClient?.clientName ?: "unknown",
            streamHeaders = successClient?.streamHeaders().orEmpty(),
        )
    }

    private fun YouTubeClient.streamHeaders(): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "en-US,en;q=0.9")

            when (clientName) {
                "WEB_REMIX" -> {
                    put("Referer", "https://music.youtube.com/")
                    put("Origin", "https://music.youtube.com")
                }
                "WEB_CREATOR" -> {
                    put("Referer", "https://studio.youtube.com/")
                    put("Origin", "https://studio.youtube.com")
                }
                else -> {
                    put("Referer", "https://www.youtube.com/")
                    put("Origin", "https://www.youtube.com")
                }
            }
        }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        return playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }
    }

    private fun validateStatus(
        url: String,
        requestHeaders: Map<String, String>,
    ): Boolean {
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)

            requestHeaders.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            reportException(e)
        }
        return false
    }

    private fun reportException(e: Exception) {
        Timber.tag(logTag).w(e, "Exception reported in YTPlayerUtils")
    }

    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        if (!format.url.isNullOrEmpty()) {
            return format.url
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (!customDeobfuscatedUrl.isNullOrEmpty()) {
                return customDeobfuscatedUrl
            }
        }

        if (skipNewPipe) return null

        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) return deobfuscatedUrl

        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) return streamUrl

            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) return audioStream
        }

        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
