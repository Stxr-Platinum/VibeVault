package com.music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/en/login"
private const val SPOTIFY_TOKEN_URL = "https://open.spotify.com/get_access_token"

@Serializable
data class SpotifyTokenResponse(
    val accessToken: String,
    val accessTokenExpirationTimestampMs: Long,
    val isAnonymous: Boolean,
)

class SpotifyAuth {

    companion object {
        const val LOGIN_URL = SPOTIFY_LOGIN_URL
        @Volatile
        private var client: HttpClient? = null

        private fun getClient(): HttpClient = client ?: HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }.also { client = it }

        suspend fun fetchAccessToken(spDc: String, spKey: String): Result<SpotifyTokenResponse> = runCatching {
            val response = getClient().post(SPOTIFY_TOKEN_URL) {
                headers.append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                headers.append("Cookie", "sp_dc=$spDc; sp_key=$spKey")
                headers.append("Accept", "application/json")
                headers.append("Content-Type", "application/json")
                setBody(TextContent("{}", ContentType.Application.Json))
            }

            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                throw Exception("Failed to fetch token: ${response.status.value} - $body")
            }

            val json = Json { isLenient = true; ignoreUnknownKeys = true }
            json.decodeFromString<SpotifyTokenResponse>(body)
        }
    }
}