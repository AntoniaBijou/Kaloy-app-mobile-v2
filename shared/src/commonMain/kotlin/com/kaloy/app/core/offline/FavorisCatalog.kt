package com.kaloy.app.core.offline

import com.kaloy.app.data.model.FavorisAlbum
import com.kaloy.app.data.model.FavorisArtiste
import com.kaloy.app.data.model.FavorisSong
import com.kaloy.app.data.model.PendingFavoriSync
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FavorisCatalog(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Chansons ──────────────────────────────────────────────────────────────

    fun lireChansons(): List<FavorisSong> =
        settings.getStringOrNull(KEY_SONGS)?.let {
            runCatching { json.decodeFromString<List<FavorisSong>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun sauvegarderChansons(list: List<FavorisSong>) =
        settings.putString(KEY_SONGS, json.encodeToString(list))

    fun ajouterChanson(f: FavorisSong) {
        val current = lireChansons().filterNot { it.song.id == f.song.id }
        sauvegarderChansons(listOf(f) + current)
    }

    fun retirerChanson(songId: Long) =
        sauvegarderChansons(lireChansons().filterNot { it.song.id == songId })

    // ── Albums ────────────────────────────────────────────────────────────────

    fun lireAlbums(): List<FavorisAlbum> =
        settings.getStringOrNull(KEY_ALBUMS)?.let {
            runCatching { json.decodeFromString<List<FavorisAlbum>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun sauvegarderAlbums(list: List<FavorisAlbum>) =
        settings.putString(KEY_ALBUMS, json.encodeToString(list))

    fun ajouterAlbum(f: FavorisAlbum) {
        val current = lireAlbums().filterNot { it.album.id == f.album.id }
        sauvegarderAlbums(listOf(f) + current)
    }

    fun retirerAlbum(albumId: Long) =
        sauvegarderAlbums(lireAlbums().filterNot { it.album.id == albumId })

    // ── Artistes ──────────────────────────────────────────────────────────────

    fun lireArtistes(): List<FavorisArtiste> =
        settings.getStringOrNull(KEY_ARTISTES)?.let {
            runCatching { json.decodeFromString<List<FavorisArtiste>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun sauvegarderArtistes(list: List<FavorisArtiste>) =
        settings.putString(KEY_ARTISTES, json.encodeToString(list))

    fun ajouterArtiste(f: FavorisArtiste) {
        val current = lireArtistes().filterNot { it.artiste.id == f.artiste.id }
        sauvegarderArtistes(listOf(f) + current)
    }

    fun retirerArtiste(artistId: Long) =
        sauvegarderArtistes(lireArtistes().filterNot { it.artiste.id == artistId })

    // ── Syncs en attente (mode hors ligne) ────────────────────────────────────

    fun pendingSync(): List<PendingFavoriSync> =
        settings.getStringOrNull(KEY_PENDING)?.let {
            runCatching { json.decodeFromString<List<PendingFavoriSync>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun addPendingSync(sync: PendingFavoriSync) {
        val existing = pendingSync()
            .filterNot { it.type == sync.type && it.entityId == sync.entityId }
        settings.putString(KEY_PENDING, json.encodeToString(existing + sync))
    }

    fun removePendingSync(sync: PendingFavoriSync) {
        val updated = pendingSync()
            .filterNot { it.type == sync.type && it.action == sync.action && it.entityId == sync.entityId }
        settings.putString(KEY_PENDING, json.encodeToString(updated))
    }

    fun clearPendingSync() = settings.putString(KEY_PENDING, json.encodeToString(emptyList<PendingFavoriSync>()))

    private companion object {
        const val KEY_SONGS    = "favoris_chansons"
        const val KEY_ALBUMS   = "favoris_albums"
        const val KEY_ARTISTES = "favoris_artistes"
        const val KEY_PENDING  = "favoris_pending_sync"
    }
}
