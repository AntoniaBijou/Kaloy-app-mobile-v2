package com.kaloy.app.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerController {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val errorMessage: StateFlow<String?>
    val statusMessage: StateFlow<String>

    fun play(url: String)
    fun play(url: String, metadata: MediaMetadata) {
        play(url)
    }
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()
}
