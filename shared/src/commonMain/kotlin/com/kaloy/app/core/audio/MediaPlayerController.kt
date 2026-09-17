package com.kaloy.app.core.audio

import kotlinx.coroutines.flow.StateFlow

data class MediaMetadata(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L
)

sealed interface MediaPlayerStatus {
    data object Idle : MediaPlayerStatus
    data object Loading : MediaPlayerStatus
    data class Playing(val positionMs: Long, val durationMs: Long) : MediaPlayerStatus
    data class Paused(val positionMs: Long, val durationMs: Long) : MediaPlayerStatus
    data class Error(val message: String) : MediaPlayerStatus
}

interface MediaPlayerController {
    val state: StateFlow<MediaPlayerStatus>

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setMedia(url: String, metadata: MediaMetadata)
    fun release()
}