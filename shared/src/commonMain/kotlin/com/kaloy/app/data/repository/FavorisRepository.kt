package com.kaloy.app.data.repository

import com.kaloy.app.data.model.FavorisAlbum
import com.kaloy.app.data.model.FavorisArtiste
import com.kaloy.app.data.model.FavorisSong

interface FavorisRepository {

    // ── Chansons ──────────────────────────────────────────────────────────────
    suspend fun getMesFavorisChansons(): List<FavorisSong>
    suspend fun aimerChanson(songId: Long): FavorisSong
    suspend fun retirerChansonDesFavoris(songId: Long)
    suspend fun estFavoriChanson(songId: Long): Boolean

    // ── Albums ────────────────────────────────────────────────────────────────
    suspend fun getMesFavorisAlbums(): List<FavorisAlbum>
    suspend fun aimerAlbum(albumId: Long): FavorisAlbum
    suspend fun retirerAlbumDesFavoris(albumId: Long)

    // ── Artistes ──────────────────────────────────────────────────────────────
    suspend fun getMesFavorisArtistes(): List<FavorisArtiste>
    suspend fun aimerArtiste(artistId: Long): FavorisArtiste
    suspend fun retirerArtisteDesFavoris(artistId: Long)

    // ── Sync hors ligne ───────────────────────────────────────────────────────
    suspend fun synchroniser()
}
