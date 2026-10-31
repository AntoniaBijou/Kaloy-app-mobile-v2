package com.kaloy.app.data.repository

import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.offline.FavorisCatalog
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.RestResponse
import com.kaloy.app.data.model.FavorisAlbum
import com.kaloy.app.data.model.FavorisArtiste
import com.kaloy.app.data.model.FavorisSong
import com.kaloy.app.data.model.PendingFavoriSync
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class FavorisRepositoryImpl(
    private val client: HttpClient,
    private val sessionManager: AuthSessionManager,
    private val catalog: FavorisCatalog
) : FavorisRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
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

    // ── Chansons ──────────────────────────────────────────────────────────────

    override suspend fun getMesFavorisChansons(): List<FavorisSong> {
        val list = client.get("$BASE_URL/favoris/me/chansons") { withSessionToken() }
            .decodeData<List<FavorisSong>>()
        catalog.sauvegarderChansons(list)
        return list
    }

    override suspend fun aimerChanson(songId: Long): FavorisSong {
        val result = client.post("$BASE_URL/favoris/me/chansons/$songId") { withSessionToken() }
            .decodeData<FavorisSong>()
        catalog.ajouterChanson(result)
        return result
    }

    override suspend fun retirerChansonDesFavoris(songId: Long) {
        val response = client.delete("$BASE_URL/favoris/me/chansons/$songId") { withSessionToken() }
        if (!response.status.isSuccess()) throw Exception("Échec du retrait des favoris (${response.status.value})")
        catalog.retirerChanson(songId)
    }

    override suspend fun estFavoriChanson(songId: Long): Boolean {
        val response = client.get("$BASE_URL/favoris/me/chansons/$songId/etat") { withSessionToken() }
            .decodeData<Map<String, Boolean>>()
        return response["liked"] == true
    }

    // ── Albums ────────────────────────────────────────────────────────────────

    override suspend fun getMesFavorisAlbums(): List<FavorisAlbum> {
        val list = client.get("$BASE_URL/favoris/me/albums") { withSessionToken() }
            .decodeData<List<FavorisAlbum>>()
        catalog.sauvegarderAlbums(list)
        return list
    }

    override suspend fun aimerAlbum(albumId: Long): FavorisAlbum {
        val result = client.post("$BASE_URL/favoris/me/albums/$albumId") { withSessionToken() }
            .decodeData<FavorisAlbum>()
        catalog.ajouterAlbum(result)
        return result
    }

    override suspend fun retirerAlbumDesFavoris(albumId: Long) {
        val response = client.delete("$BASE_URL/favoris/me/albums/$albumId") { withSessionToken() }
        if (!response.status.isSuccess()) throw Exception("Échec du retrait des favoris (${response.status.value})")
        catalog.retirerAlbum(albumId)
    }

    // ── Artistes ──────────────────────────────────────────────────────────────

    override suspend fun getMesFavorisArtistes(): List<FavorisArtiste> {
        val list = client.get("$BASE_URL/favoris/me/artistes") { withSessionToken() }
            .decodeData<List<FavorisArtiste>>()
        catalog.sauvegarderArtistes(list)
        return list
    }

    override suspend fun aimerArtiste(artistId: Long): FavorisArtiste {
        val result = client.post("$BASE_URL/favoris/me/artistes/$artistId") { withSessionToken() }
            .decodeData<FavorisArtiste>()
        catalog.ajouterArtiste(result)
        return result
    }

    override suspend fun retirerArtisteDesFavoris(artistId: Long) {
        val response = client.delete("$BASE_URL/favoris/me/artistes/$artistId") { withSessionToken() }
        if (!response.status.isSuccess()) throw Exception("Échec du retrait des favoris (${response.status.value})")
        catalog.retirerArtiste(artistId)
    }

    // ── Sync hors ligne ───────────────────────────────────────────────────────

    override suspend fun synchroniser() {
        val pending = catalog.pendingSync().toList()
        for (sync in pending) {
            runCatching {
                when (sync.type) {
                    "SONG" -> when (sync.action) {
                        "ADD"    -> aimerChanson(sync.entityId)
                        "REMOVE" -> retirerChansonDesFavoris(sync.entityId)
                    }
                    "ALBUM" -> when (sync.action) {
                        "ADD"    -> aimerAlbum(sync.entityId)
                        "REMOVE" -> retirerAlbumDesFavoris(sync.entityId)
                    }
                    "ARTIST" -> when (sync.action) {
                        "ADD"    -> aimerArtiste(sync.entityId)
                        "REMOVE" -> retirerArtisteDesFavoris(sync.entityId)
                    }
                }
                catalog.removePendingSync(sync)
            }
        }
    }
}
