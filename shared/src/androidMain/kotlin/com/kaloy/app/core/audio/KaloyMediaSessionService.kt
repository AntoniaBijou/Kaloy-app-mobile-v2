package com.kaloy.app.core.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
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
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kaloy.app.core.offline.OfflineAudioDataSourceFactory
import com.kaloy.app.core.queue.ModeLecture
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KaloyMediaSessionService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null
    private var artworkRequestId = 0L
    private var currentArtworkBitmap: Bitmap? = null
    private var currentArtworkUri: Uri? = null
    private var currentArtworkMediaId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .build()
        )
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
                    val requestId = ++artworkRequestId
                    artworkJob?.cancel()

                    if (mediaItem == null) {
                        Log.d("KaloyAudio", "No current media item; retaining last artwork")
                        return
                    }

                    val metadata = mediaItem.mediaMetadata
                    val artworkData = metadata.artworkData
                    if (artworkData != null) {
                        val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                        if (bitmap != null) {
                            currentArtworkBitmap = bitmap
                            currentArtworkUri = metadata.artworkUri
                            currentArtworkMediaId = mediaItem.mediaId
                            return
                        }
                        Log.w("KaloyAudio", "Embedded artwork could not be decoded; retrying artwork URI")
                    }

                    val artworkUri = metadata.artworkUri
                    if (artworkUri == null) {
                        if (metadata.artworkData == null) {
                            currentArtworkBitmap = null
                            currentArtworkUri = null
                            currentArtworkMediaId = null
                        }
                        Log.w("KaloyAudio", "Current media item has no artwork URI")
                    } else {
                        val cachedBitmap = currentArtworkBitmap
                        if (
                            artworkUri == currentArtworkUri &&
                            mediaItem.mediaId == currentArtworkMediaId &&
                            cachedBitmap != null
                        ) {
                            publishArtwork(mediaItem.mediaId, artworkUri, requestId, cachedBitmap)
                        } else {
                            currentArtworkBitmap = null
                            currentArtworkUri = artworkUri
                            currentArtworkMediaId = mediaItem.mediaId
                            loadArtwork(mediaItem.mediaId, artworkUri, requestId)
                        }
                    }
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
        mediaSession = MediaSession.Builder(this, notificationPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                                .buildUpon()
                                .add(TOGGLE_FAVORITE_CMD)
                                .add(CYCLE_MODE_CMD)
                                .build()
                        )
                        .build()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand == TOGGLE_FAVORITE_CMD) {
                        AndroidAudioPlayerController.toggleFavorite.tryEmit(Unit)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (customCommand == CYCLE_MODE_CMD) {
                        AndroidAudioPlayerController.cycleMode.tryEmit(Unit)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        // Observe l'état favori → met à jour le custom layout (bouton cœur + mode)
        serviceScope.launch {
            AndroidAudioPlayerController.isFavorite.collect { isFav ->
                updateNotificationLayout(isFav, AndroidAudioPlayerController.currentModeLecture.value)
            }
        }
        serviceScope.launch {
            AndroidAudioPlayerController.currentModeLecture.collect { mode ->
                updateNotificationLayout(AndroidAudioPlayerController.isFavorite.value, mode)
            }
        }

    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (player.currentMediaItem == null) {
            Log.d("KaloyAudio", "Stopping idle media service after task removal")
            stopForeground(true)
            stopSelf()
        } else {
            Log.d(
                "KaloyAudio",
                "Keeping media service after task removal; playbackState=${player.playbackState}"
            )
        }
    }

    private fun updateNotificationLayout(isFav: Boolean, mode: ModeLecture) {
        val heartBtn = CommandButton.Builder()
            .setSessionCommand(TOGGLE_FAVORITE_CMD)
            .setIconResId(
                if (isFav) com.kaloy.app.shared.R.drawable.ic_kaloy_heart_filled
                else com.kaloy.app.shared.R.drawable.ic_kaloy_heart_border
            )
            .setDisplayName(if (isFav) "Retirer des favoris" else "Ajouter aux favoris")
            .build()
        val modeIconRes = when (mode) {
            ModeLecture.BOUCLE    -> com.kaloy.app.shared.R.drawable.ic_kaloy_repeat
            ModeLecture.ALEATOIRE -> com.kaloy.app.shared.R.drawable.ic_kaloy_shuffle
            ModeLecture.UNIQUE    -> com.kaloy.app.shared.R.drawable.ic_kaloy_repeat_one
        }
        val modeDisplayName = when (mode) {
            ModeLecture.BOUCLE    -> "Lecture en boucle"
            ModeLecture.ALEATOIRE -> "Lecture aléatoire"
            ModeLecture.UNIQUE    -> "Répéter une fois"
        }
        val modeBtn = CommandButton.Builder()
            .setSessionCommand(CYCLE_MODE_CMD)
            .setIconResId(modeIconRes)
            .setDisplayName(modeDisplayName)
            .build()
        mediaSession.setCustomLayout(listOf(heartBtn, modeBtn))
    }

    private fun loadArtwork(mediaId: String, uri: Uri, requestId: Long) {
        Log.d("KaloyAudio", "Loading artwork")
        artworkJob = serviceScope.launch(Dispatchers.IO) {
            val bitmap = try {
                downloadArtwork(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e("KaloyAudio", "loadArtwork failed", e)
                return@launch
            } catch (e: IllegalStateException) {
                Log.e("KaloyAudio", "loadArtwork failed", e)
                return@launch
            } catch (e: IllegalArgumentException) {
                Log.e("KaloyAudio", "loadArtwork failed", e)
                return@launch
            }
            launch(Dispatchers.Main) {
                publishArtwork(mediaId, uri, requestId, bitmap)
            }
        }
    }

    private fun publishArtwork(mediaId: String, uri: Uri, requestId: Long, bitmap: Bitmap) {
        val currentItem = player.currentMediaItem ?: return
        if (
            requestId != artworkRequestId ||
            currentItem.mediaId != mediaId ||
            currentItem.mediaMetadata.artworkUri != uri
        ) {
            return
        }

        currentArtworkBitmap = bitmap
        currentArtworkUri = uri
        currentArtworkMediaId = mediaId
        if (currentItem.mediaMetadata.artworkData != null) return

        val updatedMetadata = currentItem.mediaMetadata.buildUpon()
            .setArtworkData(bitmap.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val resumePlayback = player.playWhenReady
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentItem.buildUpon().setMediaMetadata(updatedMetadata).build()
        )
        if (resumePlayback) player.play()
    }

    private fun downloadArtwork(uri: Uri): Bitmap {
        require(uri.scheme == "http" || uri.scheme == "https") {
            "Unsupported artwork URI scheme: ${uri.scheme}"
        }
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = ARTWORK_CONNECT_TIMEOUT_MS
            readTimeout = ARTWORK_READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        val imageBytes = try {
            val status = connection.responseCode
            check(status in 200..299) { "Artwork request failed with HTTP $status" }
            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        check(totalBytes <= MAX_ARTWORK_BYTES) {
                            "Artwork exceeds the ${MAX_ARTWORK_BYTES / 1024 / 1024} MB limit"
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Artwork is not a valid image" }

        val sampleSize = generateSequence(1) { it * 2 }
            .takeWhile { maxOf(bounds.outWidth, bounds.outHeight) / it > MAX_ARTWORK_DIMENSION }
            .lastOrNull() ?: 1
        val decoded = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: error("Unable to decode artwork image")

        val largestDimension = maxOf(decoded.width, decoded.height)
        val bitmap = if (largestDimension > MAX_ARTWORK_DIMENSION) {
            val scale = MAX_ARTWORK_DIMENSION.toFloat() / largestDimension
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        return bitmap
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lecture musicale", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        serviceScope.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kaloy_media_playback"
        private const val ARTWORK_CONNECT_TIMEOUT_MS = 10_000
        private const val ARTWORK_READ_TIMEOUT_MS = 15_000
        private const val MAX_ARTWORK_BYTES = 10 * 1024 * 1024
        private const val MAX_ARTWORK_DIMENSION = 512
        val TOGGLE_FAVORITE_CMD = SessionCommand("kaloy.TOGGLE_FAVORITE", Bundle.EMPTY)
        val CYCLE_MODE_CMD = SessionCommand("kaloy.CYCLE_MODE", Bundle.EMPTY)
    }
}

private fun android.graphics.Bitmap.toByteArray(): ByteArray {
    val output = ByteArrayOutputStream()
    compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
    return output.toByteArray()
}
