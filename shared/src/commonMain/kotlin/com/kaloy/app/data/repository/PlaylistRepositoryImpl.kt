package com.kaloy.app.data.repository

import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.RestResponse
import com.kaloy.app.data.dto.playlist.AddSongsRequest
import com.kaloy.app.data.dto.playlist.AddSongsResult
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistGenerationProposal
import com.kaloy.app.data.dto.playlist.PlaylistGenerationRequest
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.dto.playlist.ReorderSongItem
import com.kaloy.app.data.dto.playlist.SaveGeneratedPlaylistRequest
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.model.Playlist
import com.kaloy.app.data.model.PageResponse
import com.kaloy.app.data.model.Song
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

    override suspend fun getGenerationGenres(): List<Genre> =
        getAllPages { page, size ->
            client.get("$BASE_URL/genres?page=$page&size=$size") { withSessionToken() }
                .decodeData<PageResponse<Genre>>()
        }

    override suspend fun getGenerationArtists(): List<Artist> =
        getAllPages { page, size ->
            client.get("$BASE_URL/artists?page=$page&size=$size") { withSessionToken() }
                .decodeData<PageResponse<Artist>>()
        }

    override suspend fun genererPlaylists(request: PlaylistGenerationRequest): List<PlaylistGenerationProposal> =
        client.post("$BASE_URL/playlists/me/generate") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData<List<PlaylistGenerationProposal>>()

    override suspend fun conserverPlaylistGeneree(request: SaveGeneratedPlaylistRequest): Playlist =
        client.post("$BASE_URL/playlists/me/generated") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData<Playlist>()

    override suspend fun getCatalogueSongs(): List<Song> = try {
        getAllPages { page, size ->
            client.get("$BASE_URL/songs?page=$page&size=$size") { withSessionToken() }
                .decodeData<PageResponse<Song>>()
        }
    } catch (_: Exception) {
        emptyList()
    }

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

    override suspend fun getSongsInPlaylist(playlistId: Long): List<PlaylistSongUiItem> =
        client.get("$BASE_URL/playlists/me/$playlistId/songs") { withSessionToken() }
            .decodeData<List<PlaylistSongUiItem>>()

    override suspend fun addSongsToPlaylist(playlistId: Long, request: AddSongsRequest): AddSongsResult =
        client.post("$BASE_URL/playlists/me/$playlistId/songs") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData<AddSongsResult>()

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        client.delete("$BASE_URL/playlists/me/$playlistId/songs/$songId") { withSessionToken() }
    }

    override suspend fun reorderSongs(playlistId: Long, items: List<ReorderSongItem>) {
        client.put("$BASE_URL/playlists/me/$playlistId/songs/reorder") {
            withSessionToken()
            contentType(ContentType.Application.Json)
            setBody(items)
        }
    }

    private suspend fun <T> getAllPages(
        loadPage: suspend (page: Int, size: Int) -> PageResponse<T>
    ): List<T> {
        val pageSize = 100
        val results = mutableListOf<T>()
        var pageNumber = 0
        var lastPage = false
        while (!lastPage) {
            val page = loadPage(pageNumber, pageSize)
            results += page.content
            lastPage = page.last || page.content.isEmpty() ||
                (page.totalPages == 0 && page.content.size < pageSize)
            pageNumber++
        }
        return results
    }
}
