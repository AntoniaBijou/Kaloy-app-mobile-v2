package com.kaloy.app.core.audio

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
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
    private var mediaRequestStartedAtMs: Long? = null
    private var mediaRequestApplied = false
    private var completionHandled = false
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
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (mediaRequestStartedAtMs != null) mediaRequestApplied = true
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY && mediaRequestApplied) {
                                logMediaRequestDuration("Media3 ready")
                            }
                            when (playbackState) {
                                Player.STATE_ENDED -> {
                                    if (!completionHandled) {
                                        completionHandled = true
                                        _state.value = MediaPlayerStatus.Completed
                                    }
                                }
                                Player.STATE_IDLE -> {
                                    completionHandled = false
                                    updateState()
                                }
                                else -> updateState()
                            }
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying && mediaRequestApplied) {
                                logMediaRequestDuration("Playback started")
                            }
                            updateState()
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("KaloyAudio", "Playback failed", error)
                            _state.value = MediaPlayerStatus.Error(error.message ?: "Erreur de lecture")
                        }
                    })
                    updateState()
                    pendingMedia?.let { mediaItem ->
                        logMediaRequestDuration("MediaController connected")
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
        mediaRequestStartedAtMs = SystemClock.elapsedRealtime()
        mediaRequestApplied = false
        _state.value = MediaPlayerStatus.Loading
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
            Log.i("KaloyAudio", "Media request queued; offline=${url.startsWith("kaloy-offline://")}")
            pendingMedia = mediaItem
            _state.value = MediaPlayerStatus.Loading
            return
        }
        // Forcer l'état Loading immédiatement pour éviter que le polling
        // revoie STATE_ENDED pendant la latence IPC et déclenche un double-saut.
        _state.value = MediaPlayerStatus.Loading
        connectedController.setMediaItem(mediaItem)
        Log.i("KaloyAudio", "Media request sent; offline=${url.startsWith("kaloy-offline://")}")
        connectedController.prepare()
        connectedController.play()
    }

    private fun logMediaRequestDuration(event: String) {
        val startedAtMs = mediaRequestStartedAtMs ?: return
        Log.i("KaloyAudio", "$event ${SystemClock.elapsedRealtime() - startedAtMs}ms after media request")
        if (event == "Playback started") {
            mediaRequestStartedAtMs = null
            mediaRequestApplied = false
        }
    }

    override fun release() {
        released = true
        controller = null
        scope.cancel()
        MediaController.releaseFuture(controllerFuture)
    }

    private fun updateState() {
        if (_state.value is MediaPlayerStatus.Error) return
        val connectedController = controller ?: return
        // STATE_ENDED géré exclusivement par le Player.Listener (completionHandled).
        // Ignorer ici évite l'émission Paused transitoire qui ferait flasher le bouton Play.
        if (connectedController.playbackState == Player.STATE_ENDED) return
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