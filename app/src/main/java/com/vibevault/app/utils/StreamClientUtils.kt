package com.vibevault.app.utils

import com.music.innertube.models.YouTubeClient

/**
 * Shared utility for resolving the correct User-Agent and Origin/Referer headers
 * based on the `c` (client) query parameter embedded in YouTube stream URLs.
 */
object StreamClientUtils {

    /**
     * Resolve the correct User-Agent for a YouTube media request based on
     * the `c` query parameter from the stream URL.
     */
    fun resolveUserAgent(clientParam: String): String {
        val c = clientParam.trim()
        return when {
            c.equals("WEB_REMIX", ignoreCase = true) ||
                c.equals("WEB", ignoreCase = true) ||
                c.equals("WEB_CREATOR", ignoreCase = true) -> YouTubeClient.USER_AGENT_WEB

            c.equals("TVHTML5", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY_EMBEDDED_PLAYER", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY", ignoreCase = true) -> YouTubeClient.TVHTML5.userAgent

            c.equals("IOS_MUSIC", ignoreCase = true) -> YouTubeClient.IOS.userAgent

            c.startsWith("IOS", ignoreCase = true) -> YouTubeClient.IOS.userAgent

            c.startsWith("ANDROID_VR", ignoreCase = true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent

            c.equals("ANDROID_MUSIC", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent

            c.startsWith("ANDROID_CREATOR", ignoreCase = true) -> YouTubeClient.ANDROID_CREATOR.userAgent

            c.startsWith("ANDROID", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent

            c.startsWith("VISIONOS", ignoreCase = true) -> YouTubeClient.VISIONOS.userAgent

            else -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent
        }
    }

    /**
     * Data class holding Origin and Referer header values.
     */
    data class OriginReferer(val origin: String?, val referer: String?)

    /**
     * Determine the correct Origin and Referer for a YouTube media request.
     */
    fun resolveOriginReferer(clientParam: String): OriginReferer {
        val c = clientParam.trim()
        return when {
            c.equals("WEB_REMIX", ignoreCase = true) ||
                c.equals("WEB", ignoreCase = true) ||
                c.equals("WEB_CREATOR", ignoreCase = true) ->
                OriginReferer(YouTubeClient.ORIGIN_YOUTUBE_MUSIC, YouTubeClient.REFERER_YOUTUBE_MUSIC)

            c.equals("TVHTML5", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY_EMBEDDED_PLAYER", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY", ignoreCase = true) ->
                OriginReferer("https://www.youtube.com", "https://www.youtube.com/tv")

            else -> OriginReferer(null, null)
        }
    }

    /**
     * Check whether the given client parameter represents a web-type client.
     */
    fun isWebClient(clientParam: String): Boolean {
        val c = clientParam.trim()
        return c.equals("WEB", ignoreCase = true) ||
            c.equals("WEB_REMIX", ignoreCase = true) ||
            c.equals("WEB_CREATOR", ignoreCase = true) ||
            c.equals("MWEB", ignoreCase = true) ||
            c.equals("WEB_EMBEDDED_PLAYER", ignoreCase = true) ||
            c.equals("TVHTML5", ignoreCase = true) ||
            c.equals("TVHTML5_SIMPLY_EMBEDDED_PLAYER", ignoreCase = true) ||
            c.equals("TVHTML5_SIMPLY", ignoreCase = true)
    }

    /**
     * Patch the `cver` parameter in a stream URL to match the client version used.
     */
    fun patchClientVersion(url: String, clientVersion: String): String {
        if (!url.contains("cver=")) return url
        return url.replace(Regex("cver=[^&]+"), "cver=$clientVersion")
    }

    /**
     * Append a poToken to a stream URL as the `pot` query parameter.
     */
    fun appendPoToken(url: String, poToken: String): String {
        if (url.contains("pot=")) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$poToken"
    }
}
