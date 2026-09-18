package com.kaloy.app.core.audio

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.concurrent.Executor

class AndroidMediaPlayerController(context: Context) : MediaPlayerController {
    private val _state = MutableStateFlow<MediaPlayerStatus>(MediaPlayerStatus.Idle)
    override val state: StateFlow<MediaPlayerStatus> = _state

    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, KaloyMediaSessionService::class.java))
        ).buildAsync()
    private var controller: MediaController? = null
    private var released = false
    private var pendingMedia: MediaItem? = null
    private val mainExecutor = Executor { command -> command.run() }
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)

    init {
        Log.d("KaloyAudio", "Création du MediaController")
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get().also { connectedController ->
                    Log.d("KaloyAudio", "MediaController connecté au service")
                    if (released) return@also
                    connectedController.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()
                    override fun onPlaybackStateChanged(playbackState: Int) = updateState()
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        _state.value = MediaPlayerStatus.Error(error.message ?: "Erreur de lecture")
                    }
                })
                    updateState()
                    pendingMedia?.let { mediaItem ->
                        connectedController.setMediaItem(mediaItem)
                        connectedController.prepare()
                        connectedController.play()
                        pendingMedia = null
                    }
                }
            }.onFailure { error ->
                Log.e("KaloyAudio", "Connexion MediaController impossible", error)
                _state.value = MediaPlayerStatus.Error(
                    error.message ?: "Connexion au service audio impossible"
                )
            }
        }, mainExecutor)
        scope.launch {
            while (isActive) {
                if (controller != null) updateState()
                delay(250)
            }
        }
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun setMedia(url: String, metadata: MediaMetadata) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                Media3Metadata.Builder()
                    .setTitle(metadata.title)
                    .setArtist(metadata.artist)
                    .setArtworkUri(metadata.artworkUrl?.let(android.net.Uri::parse))
                    .build()
            )
            .build()
        val connectedController = controller
        if (connectedController == null || released) {
            Log.d("KaloyAudio", "Flux mis en attente avant connexion: ${url.take(80)}")
            pendingMedia = mediaItem
            _state.value = MediaPlayerStatus.Loading
            return
        }
        connectedController.setMediaItem(mediaItem)
        Log.d("KaloyAudio", "Flux envoyé à Media3: ${url.take(80)}")
        connectedController.prepare()
        connectedController.play()
    }

    override fun release() {
        released = true
        controller = null
        scope.cancel()
        MediaController.releaseFuture(controllerFuture)
    }

    private fun updateState() {
        val connectedController = controller ?: return
        val positionMs = connectedController.currentPosition.coerceAtLeast(0L)
        val durationMs = connectedController.duration.coerceAtLeast(0L)
        _state.value = when {
            connectedController.isPlaying -> MediaPlayerStatus.Playing(positionMs, durationMs)
            connectedController.playbackState == Player.STATE_BUFFERING -> MediaPlayerStatus.Loading
            connectedController.currentMediaItem != null -> MediaPlayerStatus.Paused(positionMs, durationMs)
            else -> MediaPlayerStatus.Idle
        }
    }
}