package com.kaloy.app.core.offline

data class OfflineTransferProgress(
    val songId: Long,
    val bytesTransferred: Long,
    val totalBytes: Long?
)

interface OfflineFileStore {
    suspend fun downloadAndEncrypt(
        playlistId: Long,
        songId: Long,
        downloadUrl: String,
        onProgress: (OfflineTransferProgress) -> Unit
    )

    suspend fun playableUrl(playlistId: Long, songId: Long): String?

    suspend fun deletePlaylist(playlistId: Long)

    // Pré-charge la clé de déchiffrement en mémoire avant que ExoPlayer ouvre le fichier.
    // Implémentation no-op par défaut ; AndroidOfflineFileStore fait le vrai travail.
    suspend fun prewarm(playlistId: Long, songId: Long) = Unit
}
