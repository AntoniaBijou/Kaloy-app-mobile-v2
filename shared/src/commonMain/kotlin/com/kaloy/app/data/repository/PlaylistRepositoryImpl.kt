package com.kaloy.app.data.repository

import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.RestResponse
import com.kaloy.app.data.dto.playlist.CreatePlaylistRequest
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Playlist
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class PlaylistRepositoryImpl(
    private val client: HttpClient,
    private val sessionManager: AuthSessionManager
) : PlaylistRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun HttpRequestBuilder.withSessionToken() {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) throw IllegalStateException("SESSION_EXPIRED")
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend inline fun <reified T> HttpResponse.decodeData(): T {
        val rest = body<RestResponse>()
        if (status.value >= 400) throw Exception(rest.message.ifBlank { "Une erreur est survenue." })
        val data = rest.data ?: throw Exception("Réponse vide du serveur.")
        return json.decodeFromJsonElement(serializer<T>(), data)
    }

    override suspend fun getMesPlaylists(): List<PlaylistUiItem> = try {
        client.get("$BASE_URL/playlists/me") { withSessionToken() }
            .decodeData<List<PlaylistUiItem>>()
    } catch (_: Exception) { emptyList() }

    override suspend fun getPlaylistsEditoriales(): List<EditorialPlaylist> = try {
        client.get("$BASE_URL/editorialplaylists/featured") { withSessionToken() }
            .decodeData<List<EditorialPlaylist>>()
    } catch (_: Exception) { emptyList() }

    override suspend fun creerPlaylist(request: CreatePlaylistRequest): Playlist =
        client.post("$BASE_URL/playlists/me") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData<Playlist>()

    override suspend fun modifierPlaylist(id: Long, request: UpdatePlaylistRequest) {
        client.put("$BASE_URL/playlists/me/$id") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun supprimerPlaylist(id: Long) {
        client.delete("$BASE_URL/playlists/me/$id") { withSessionToken() }
    }

    override suspend fun fusionnerPlaylists(request: MergePlaylistsRequest): Playlist =
        client.post("$BASE_URL/playlists/merge") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData<Playlist>()

    override suspend fun telechargerPlaylist(id: Long) {
        client.post("$BASE_URL/playlists/$id/download") { withSessionToken() }
    }
}
