package com.kaloy.app.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerController {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun play(url: String)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()
}
