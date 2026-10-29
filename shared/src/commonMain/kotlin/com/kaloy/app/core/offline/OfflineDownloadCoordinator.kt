package com.kaloy.app.core.offline

import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.OfflinePlaylistDownload
import com.kaloy.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed class OfflineDownloadState {
    data object Idle : OfflineDownloadState()
    data class Downloading(
        val playlistId: Long,
        val playlistName: String,
        val songTitle: String,
        val currentSong: Int,
        val totalSongs: Int,
        val bytesTransferred: Long,
        val totalBytes: Long?
    ) : OfflineDownloadState()
}

class OfflineDownloadCoordinator(
    private val repository: PlaylistRepository,
    private val fileStore: OfflineFileStore,
    private val sessionManager: AuthSessionManager,
    private val catalog: OfflinePlaylistCatalog
) {
    private val _state = MutableStateFlow<OfflineDownloadState>(OfflineDownloadState.Idle)
    val state: StateFlow<OfflineDownloadState> = _state.asStateFlow()

    suspend fun download(playlistId: Long, refresh: Boolean = false) {
        sessionManager.getToken()?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("SESSION_EXPIRED")
        val playlist = if (refresh) {
            repository.refreshOfflineDownload(playlistId)
        } else {
            repository.requestOfflineDownload(playlistId)
        }
        require(playlist.active) { "Le serveur n’a pas activé ce téléchargement." }
        val songs = playlist.songs.sortedBy { it.position }
        require(songs.isNotEmpty()) { "Cette playlist ne contient aucune chanson à télécharger." }
        try {
            songs.forEachIndexed { index, song ->
                val url = song.downloadUrl
                    ?: throw IllegalStateException("Aucune URL de téléchargement pour « ${song.title} ».")
                fileStore.downloadAndEncrypt(
                    playlistId = playlist.playlistId,
                    songId = song.songId,
                    downloadUrl = absoluteDownloadUrl(url),
                ) { progress ->
                    _state.value = OfflineDownloadState.Downloading(
                        playlistId = playlist.playlistId,
                        playlistName = playlist.playlistName,
                        songTitle = song.title,
                        currentSong = index + 1,
                        totalSongs = songs.size,
                        bytesTransferred = progress.bytesTransferred,
                        totalBytes = progress.totalBytes
                    )
                }
            }
            catalog.save(sessionManager.getUserId(), playlist)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try {
                    fileStore.deletePlaylist(playlist.playlistId)
                    catalog.delete(sessionManager.getUserId(), playlist.playlistId)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                try {
                    repository.deleteOfflineDownload(playlist.playlistId)
                } catch (deactivationFailure: Throwable) {
                    failure.addSuppressed(deactivationFailure)
                }
            }
            throw failure
        } finally {
            _state.value = OfflineDownloadState.Idle
        }
    }

    suspend fun delete(playlist: OfflinePlaylistDownload) {
        fileStore.deletePlaylist(playlist.playlistId)
        catalog.delete(sessionManager.getUserId(), playlist.playlistId)
        try {
            repository.deleteOfflineDownload(playlist.playlistId)
        } catch (failure: Throwable) {
            catalog.addPendingDelete(sessionManager.getUserId(), playlist.playlistId)
            throw failure
        }
    }

    suspend fun syncPendingDeletes() {
        val userId = sessionManager.getUserId()
        for (playlistId in catalog.pendingDeletes(userId)) {
            repository.deleteOfflineDownload(playlistId)
            catalog.resolvePendingDelete(userId, playlistId)
        }
    }

    private fun absoluteDownloadUrl(value: String): String {
        if (value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        ) return value
        val contextPath = "/mozika"
        val origin = BASE_URL.removeSuffix(contextPath)
        return if (value.startsWith("$contextPath/")) "$origin$value"
        else BASE_URL.trimEnd('/') + if (value.startsWith('/')) value else "/$value"
    }
}
