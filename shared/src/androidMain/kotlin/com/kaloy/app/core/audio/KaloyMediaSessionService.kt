package com.kaloy.app.core.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.kaloy.app.core.offline.OfflineAudioDataSourceFactory
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
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val audioAttributes = Media3AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Kaloy/1.0 Android")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val audioDataSourceFactory = OfflineAudioDataSourceFactory(this, httpDataSourceFactory)
        // Démarrer la lecture dès 200 ms de buffer (défaut ExoPlayer : 2 500 ms).
        // Réduit le délai entre « Suivant » et le début de lecture effective.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                200,   // startPlaybackAfterMs — démarre avec 200 ms au lieu de 2 500 ms
                2_000  // afterRebufferMs — seuil après un blocage réseau
            )
            .build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(audioDataSourceFactory))
            .setLoadControl(loadControl)
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
        val notificationPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()

            override fun seekToNext() {
                AndroidAudioPlayerController.skipToNext.tryEmit(Unit)
            }

            override fun seekToNextMediaItem() {
                AndroidAudioPlayerController.skipToNext.tryEmit(Unit)
            }

            override fun seekToPrevious() {
                AndroidAudioPlayerController.skipToPrevious.tryEmit(Unit)
            }

            override fun seekToPreviousMediaItem() {
                AndroidAudioPlayerController.skipToPrevious.tryEmit(Unit)
            }
        }
        // ExoPlayer gère le focus audio en autonomie (handleAudioFocus = true ci-dessus).
        // Ne pas créer de AudioFocusRequest manuel : les deux requêtes concurrentes
        // provoqueraient un AUDIOFOCUS_LOSS immédiat sur l'une d'elles, qui déclencherait
        // player.pause() juste après le démarrage d'une nouvelle piste.
        mediaSession = MediaSession.Builder(this, notificationPlayer).build()
        playerNotificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) startForeground(notificationId, notification)
                    else stopForeground(false)
                }
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopSelf()
                }
            })
            .build()
            .apply { setPlayer(notificationPlayer) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

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

    override fun onDestroy() {
        playerNotificationManager.setPlayer(null)
        artworkJob?.cancel()
        serviceScope.cancel()
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