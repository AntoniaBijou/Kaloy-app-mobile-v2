package com.kaloy.app.core.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AndroidAudioPlayerController(private val context: Context) : AudioPlayerController {

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPositionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)

    override val isPlaying: StateFlow<Boolean> = _isPlaying
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs
    override val durationMs: StateFlow<Long> = _durationMs

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = p.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    init {
        scope.launch {
            while (true) {
                if (player.isPlaying) {
                    _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    override fun play(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun release() {
        scope.cancel()
        player.release()
    }
}
