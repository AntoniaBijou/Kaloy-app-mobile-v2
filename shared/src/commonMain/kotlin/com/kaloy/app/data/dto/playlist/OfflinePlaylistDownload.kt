package com.kaloy.app.data.dto.playlist

import kotlinx.serialization.Serializable

@Serializable
data class OfflinePlaylistDownload(
    val playlistId: Long = 0,
    val playlistName: String = "",
    val downloadedAt: String = "",
    val expiresAt: String = "",
    val active: Boolean = false,
    val songs: List<OfflineSongDownload> = emptyList()
)

@Serializable
data class OfflineSongDownload(
    val songId: Long = 0,
    val title: String = "",
    val artistName: String = "",
    val durationSeconds: Int? = null,
    val position: Int = 0,
    val downloadUrl: String? = null
)
