package com.kaloy.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FavorisSong(
    val likeId: Long = 0,
    val song: Song = Song(),
    val likedAt: String? = null
)

@Serializable
data class FavorisAlbum(
    val likeId: Long = 0,
    val album: Album = Album(),
    val likedAt: String? = null
)

@Serializable
data class FavorisArtiste(
    val likeId: Long = 0,
    val artiste: Artist = Artist(),
    val likedAt: String? = null
)

@Serializable
data class PendingFavoriSync(
    val type: String,      // "SONG", "ALBUM", "ARTIST"
    val action: String,    // "ADD" ou "REMOVE"
    val entityId: Long,
    val entityJson: String? = null
)
