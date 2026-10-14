package com.kaloy.app.data.repository

import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.CreatePlaylistRequest
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Playlist
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class PlaylistRepositoryImpl(
    private val client: HttpClient,
    private val sessionManager: AuthSessionManager
) : PlaylistRepository {

    private fun io.ktor.client.request.HttpRequestBuilder.withSessionToken() {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) throw IllegalStateException("SESSION_EXPIRED")
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    override suspend fun getMesPlaylists(): List<PlaylistUiItem> = try {
        client.get("$BASE_URL/playlists/me") { withSessionToken() }.body()
    } catch (_: Exception) { emptyList() }

    override suspend fun getPlaylistsEditoriales(): List<EditorialPlaylist> = try {
        client.get("$BASE_URL/editorialplaylists/featured") { withSessionToken() }.body()
    } catch (_: Exception) { emptyList() }

    override suspend fun creerPlaylist(request: CreatePlaylistRequest): Playlist =
        client.post("$BASE_URL/playlists") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun supprimerPlaylist(id: Long) {
        client.delete("$BASE_URL/playlists/$id") { withSessionToken() }
    }

    override suspend fun fusionnerPlaylists(request: MergePlaylistsRequest): Playlist =
        client.post("$BASE_URL/playlists/merge") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun telechargerPlaylist(id: Long) {
        client.post("$BASE_URL/playlists/$id/download") { withSessionToken() }
    }
}
