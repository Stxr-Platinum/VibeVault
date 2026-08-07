package com.vibevault.app.ui.utils

/**
 * Resizes a Google CDN or YouTube thumbnail URL to high resolution (e.g., 1200x1200).
 */
fun String.resize(
    width: Int? = 1200,
    height: Int? = 1200,
): String {
    val isGoogleCdn = this.contains("googleusercontent.com") || 
                      this.contains("ggpht.com") || 
                      this.contains(Regex("=[wshd]\\d+"))

    val isYtimg = this.contains("ytimg") || this.contains("youtube.com") || this.contains("/vi/")
    val isSpotifyCdn = this.contains("scdn.co") || this.contains("spotify.com")

    return when {
        isGoogleCdn -> resizeGoogleCdn(width, height)
        isYtimg -> resizeYtimg(width, height)
        isSpotifyCdn -> resizeSpotifyCdn()
        else -> this
    }
}

private fun String.resizeSpotifyCdn(): String {
    // Replace Spotify low/medium resolution hashes with maximum high-res 640x640 hash (ab67616d0000b273)
    return this.replace("ab67616d00004851", "ab67616d0000b273")
               .replace("ab67616d00001e02", "ab67616d0000b273")
}

private fun String.resizeGoogleCdn(width: Int?, height: Int?): String {
    val w = (width ?: height ?: 1200).coerceAtLeast(544)
    val h = (height ?: width ?: 1200).coerceAtLeast(544)

    if (this.contains(Regex("w\\d+-h\\d+"))) {
        return this.replace(Regex("w\\d+-h\\d+"), "w$w-h$h")
    }

    val baseUrl = this.split(Regex("=[wshd]"), limit = 2)[0]
    return "$baseUrl=w$w-h$h-p-l90-rj"
}

private fun String.resizeYtimg(width: Int?, height: Int?): String {
    val w = width ?: height ?: 1200
    val videoId = Regex("/vi(?:_webp)?/([^/]+)/").find(this)?.groupValues?.get(1) ?: return this

    return when {
        w >= 800 -> "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        w >= 320 -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        else -> "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    }
}
