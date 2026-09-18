package com.kaloy.app.core.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AndroidAudioPlayerController(private val context: Context) : AudioPlayerController {

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPositionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow("Service audio en connexion")

    override val isPlaying: StateFlow<Boolean> = _isPlaying
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs
    override val durationMs: StateFlow<Long> = _durationMs
    override val errorMessage: StateFlow<String?> = _errorMessage
    override val statusMessage: StateFlow<String> = _statusMessage

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val controller = AndroidMediaPlayerController(context)

    init {
        scope.launch {
            controller.state.collect { state ->
                when (state) {
                    is MediaPlayerStatus.Playing -> {
                        _statusMessage.value = "Lecture active"
                        _errorMessage.value = null
                        _isPlaying.value = true
                        _currentPositionMs.value = state.positionMs
                        _durationMs.value = state.durationMs
                    }
                    is MediaPlayerStatus.Paused -> {
                        _statusMessage.value = "Lecture en pause"
                        _isPlaying.value = false
                        _currentPositionMs.value = state.positionMs
                        _durationMs.value = state.durationMs
                    }
                    is MediaPlayerStatus.Error -> {
                        _statusMessage.value = "Erreur Media3"
                        Log.e("KaloyAudio", "Erreur de lecture: ${state.message}")
                        _errorMessage.value = state.message
                        _isPlaying.value = false
                    }
                    is MediaPlayerStatus.Loading -> {
                        _statusMessage.value = "Flux en chargement"
                        _isPlaying.value = false
                    }
                    else -> {
                        _statusMessage.value = "Service audio prêt"
                        _isPlaying.value = false
                    }
                }
            }
        }
    }

    override fun play(url: String) {
        controller.setMedia(url, MediaMetadata("Kaloy", ""))
    }

    override fun play(url: String, metadata: MediaMetadata) {
        controller.setMedia(url, metadata)
    }

    override fun pause() {
        controller.pause()
    }

    override fun resume() {
        controller.play()
    }

    override fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    override fun release() {
        scope.cancel()
        controller.release()
    }
}
