package com.kaloy.app.data.repository

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.RestResponse
import com.kaloy.app.data.model.SongPlayerResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class LecteurRepositoryImpl(
    private val client: HttpClient,
    private val sessionManager: AuthSessionManager
) : LecteurRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun HttpRequestBuilder.withSessionToken() {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) throw IllegalStateException("SESSION_EXPIRED")
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend inline fun <reified T> HttpResponse.decodeData(): T {
        val rest = body<RestResponse>()
        if (status.value >= 400) throw Exception(UserErrorMessages.fromRawMessage(rest.message))
        val data = rest.data ?: throw Exception("Données introuvables.")
        return json.decodeFromJsonElement(serializer<T>(), data)
    }

    override suspend fun getSongPlayerDetails(songId: Long): SongPlayerResponse {
        val response = client.get("$BASE_URL/media/songs/$songId/player") {
            withSessionToken()
        }
        if (response.status == HttpStatusCode.Unauthorized) throw Exception("SESSION_EXPIRED")
        return response.decodeData()
    }
}
