package com.kaloy.app.presentation.lecteur

import com.kaloy.app.core.offline.CachedOfflineSong
import com.kaloy.app.data.model.SongPlayerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

internal suspend fun resolveSongPlayerDetails(
    cachedAudio: CachedOfflineSong?,
    fetchOnline: suspend () -> SongPlayerResponse
): SongPlayerResponse {
    val onlineDetails = try {
        fetchOnline()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        if (cachedAudio == null) throw e
        return cachedAudio.toOfflinePlayerResponse()
    }

    return if (cachedAudio == null) {
        onlineDetails
    } else {
        onlineDetails.copy(
            audioStreamUrl = cachedAudio.playableUrl,
            isOffline = true
        )
    }
}

private fun CachedOfflineSong.toOfflinePlayerResponse() = SongPlayerResponse(
    id = song.songId,
    title = song.title,
    durationSeconds = song.durationSeconds,
    artistStageName = song.artistName,
    audioStreamUrl = playableUrl,
    isOffline = true
)
