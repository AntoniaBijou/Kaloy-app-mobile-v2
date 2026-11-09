package com.kaloy.app.core.offline

import com.kaloy.app.core.util.maintenantIso
import com.kaloy.app.data.dto.playlist.OfflinePlaylistDownload
import com.kaloy.app.data.dto.playlist.OfflineSongDownload
import com.kaloy.app.data.model.Song
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflinePlaylistCatalog(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun get(userId: Long): List<OfflinePlaylistDownload> {
        if (userId <= 0L) return emptyList()
        val stored = settings.getStringOrNull(key(userId)) ?: return emptyList()
        return json.decodeFromString<List<OfflinePlaylistDownload>>(stored)
    }

    fun save(userId: Long, playlist: OfflinePlaylistDownload) {
        require(userId > 0L) { "A valid user is required for offline downloads" }
        val sanitized = playlist.copy(
            songs = playlist.songs.map { it.copy(downloadUrl = null) }
        )
        write(userId, listOf(sanitized) + get(userId).filterNot { it.playlistId == playlist.playlistId })
    }

    fun syncServerState(userId: Long, serverPlaylists: List<OfflinePlaylistDownload>) {
        if (userId <= 0L) return
        val now = maintenantIso()
        val serverById = serverPlaylists.associateBy { it.playlistId }
        val synchronized = get(userId).map { local ->
            val server = serverById[local.playlistId]
            local.copy(
                active = server?.active == true && server.expiresAt > now,
                expiresAt = server?.expiresAt ?: local.expiresAt
            )
        }
        write(userId, synchronized)
    }

    fun delete(userId: Long, playlistId: Long) {
        if (userId <= 0L) return
        val remaining = get(userId).filterNot { it.playlistId == playlistId }
        write(userId, remaining)
    }

    fun pendingDeletes(userId: Long): List<Long> {
        if (userId <= 0L) return emptyList()
        val stored = settings.getStringOrNull(pendingDeleteKey(userId)) ?: return emptyList()
        return json.decodeFromString<List<Long>>(stored)
    }

    fun addPendingDelete(userId: Long, playlistId: Long) {
        if (userId <= 0L) return
        val pending = pendingDeletes(userId).toSet() + playlistId
        settings.putString(pendingDeleteKey(userId), json.encodeToString(pending.toList()))
    }

    fun resolvePendingDelete(userId: Long, playlistId: Long) {
        if (userId <= 0L) return
        val pending = pendingDeletes(userId).filterNot { it == playlistId }
        settings.putString(pendingDeleteKey(userId), json.encodeToString(pending))
    }

    fun localPlaylists(userId: Long): List<OfflinePlaylistDownload> =
        get(userId).filter { it.active && it.expiresAt > maintenantIso() }

    fun isPlaylistAvailableOffline(userId: Long, playlistId: Long): Boolean =
        localPlaylists(userId).any { it.playlistId == playlistId }

    fun displayPlaylists(userId: Long): List<OfflinePlaylistDownload> {
        val now = maintenantIso()
        return get(userId).map { playlist ->
            playlist.copy(active = playlist.active && playlist.expiresAt > now)
        }
    }

    suspend fun findPlayableSong(
        userId: Long,
        songId: Long,
        fileStore: OfflineFileStore,
        playlistId: Long? = null,
        fallbackSong: Song? = null
    ): CachedOfflineSong? {
        // Utiliser get() (toutes les playlists) et non localPlaylists() :
        // l'existence du fichier sur disque est le seul critère de lecture hors-ligne.
        // localPlaylists() exclut les playlists dont active=false ou expiresAt dépassé,
        // ce qui bloque la lecture alors que les fichiers sont toujours présents localement.
        for (playlist in get(userId).filter {
            playlistId == null || it.playlistId == playlistId
        }) {
            val song = playlist.songs.firstOrNull { it.songId == songId }
                ?: fallbackSong
                    ?.takeIf { it.id == songId }
                    ?.let {
                        OfflineSongDownload(
                            songId = it.id,
                            title = it.title,
                            artistName = it.artist?.stageName.orEmpty(),
                            durationSeconds = it.durationSeconds
                        )
                    }
                ?: continue
            val url = fileStore.playableUrl(playlist.playlistId, songId) ?: continue
            return CachedOfflineSong(playlist.playlistId, playlist.playlistName, song, url)
        }
        return null
    }

    private fun key(userId: Long) = "offline_playlist_catalog_$userId"
    private fun pendingDeleteKey(userId: Long) = "offline_playlist_pending_delete_$userId"

    private fun write(userId: Long, playlists: List<OfflinePlaylistDownload>) {
        settings.putString(key(userId), json.encodeToString(playlists))
    }
}

data class CachedOfflineSong(
    val playlistId: Long,
    val playlistName: String,
    val song: OfflineSongDownload,
    val playableUrl: String
)
