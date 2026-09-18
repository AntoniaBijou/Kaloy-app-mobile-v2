package com.kaloy.app.core.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KaloyMediaSessionService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null
    private var resumeAfterTransientLoss = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioAttributes = Media3AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Kaloy/1.0 Android")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build().apply {
            setAudioAttributes(audioAttributes, true)
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Log.d("KaloyAudio", "Media item: ${mediaItem?.localConfiguration?.uri}")
                    loadArtwork(mediaItem?.mediaMetadata?.artworkUri)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d("KaloyAudio", "Playback state: $playbackState, duration=${player.duration}")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("KaloyAudio", "Is playing: $isPlaying")
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("KaloyAudio", "ExoPlayer error: ${error.errorCodeName}", error)
                    Log.e("KaloyAudio", "Cause: ${error.cause?.message}")
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(::onAudioFocusChanged)
            .build()
        requestAudioFocus()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    private fun requestAudioFocus() {
        audioManager.requestAudioFocus(audioFocusRequest)
    }

    private fun onAudioFocusChanged(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeAfterTransientLoss = player.isPlaying
                player.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterTransientLoss) {
                    resumeAfterTransientLoss = false
                    player.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterTransientLoss = false
                player.pause()
            }
        }
    }

    private fun loadArtwork(uri: Uri?) {
        artworkJob?.cancel()
        if (uri == null) return
        artworkJob = serviceScope.launch(Dispatchers.IO) {
            runCatching { URL(uri.toString()).openStream().use(BitmapFactory::decodeStream) }
                .getOrNull()
                ?.let { bitmap ->
                    launch(Dispatchers.Main) {
                        val currentItem = player.currentMediaItem ?: return@launch
                        val metadata = currentItem.mediaMetadata.buildUpon()
                            .setArtworkData(
                                bitmap.toByteArray(),
                                MediaMetadata.PICTURE_TYPE_FRONT_COVER
                            )
                            .build()
                        player.replaceMediaItem(
                            player.currentMediaItemIndex,
                            currentItem.buildUpon().setMediaMetadata(metadata).build()
                        )
                    }
                }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lecture musicale", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun createNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kaloy")
            .setContentText("Lecture musicale")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        artworkJob?.cancel()
        serviceScope.cancel()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kaloy_media_playback"
        private const val NOTIFICATION_ID = 1001
    }
}

private fun android.graphics.Bitmap.toByteArray(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
    return output.toByteArray()
}