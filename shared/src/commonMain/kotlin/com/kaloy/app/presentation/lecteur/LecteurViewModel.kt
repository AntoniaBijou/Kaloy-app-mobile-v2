package com.kaloy.app.presentation.lecteur

import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.MediaMetadata
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.model.SongPlayerResponse
import com.kaloy.app.data.repository.LecteurRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LecteurUiState {
    object Loading : LecteurUiState()
    data class Success(val song: SongPlayerResponse) : LecteurUiState()
    data class Error(val message: String) : LecteurUiState()
}

enum class ModeEcoute { AUDIO, VIDEO, KARAOKE, PLAYBACK }

class LecteurViewModel(
    private val repository: LecteurRepository,
    private val audioPlayer: AudioPlayerController,
    private val queueManager: QueueManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<LecteurUiState>(LecteurUiState.Loading)
    val uiState: StateFlow<LecteurUiState> = _uiState

    private val _modeEcoute = MutableStateFlow(ModeEcoute.AUDIO)
    val modeEcoute: StateFlow<ModeEcoute> = _modeEcoute

    val upNext: StateFlow<List<Song>> = queueManager.upNext
    val history: StateFlow<List<Song>> = queueManager.history

    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPositionMs: StateFlow<Long> = audioPlayer.currentPositionMs
    val durationMs: StateFlow<Long> = audioPlayer.durationMs
    val errorMessage: StateFlow<String?> = audioPlayer.errorMessage
    val statusMessage: StateFlow<String> = audioPlayer.statusMessage

    private var activeSongId: Long? = null
    private var activeModeId: Long? = null
    private var autoPlayTriggered = false

    init {
        audioPlayer.setOnTrackCompleted { jouerSuivant() }
        audioPlayer.setOnSkipToPrevious { jouerPrecedent() }
    }

    private fun ModeEcoute.toPlayModeId(): Long = when (this) {
        ModeEcoute.AUDIO    -> 1L
        ModeEcoute.VIDEO    -> 2L
        ModeEcoute.PLAYBACK -> 3L
        ModeEcoute.KARAOKE  -> 4L
    }

    private fun saveHistoryForActiveSong() {
        val songId = activeSongId ?: return
        val modeId = activeModeId ?: return
        val positionMs = audioPlayer.currentPositionMs.value
        val totalMs = audioPlayer.durationMs.value
        val durationSec = (positionMs / 1000).toInt()
        if (durationSec <= 0) return
        val completed = totalMs > 0L && positionMs.toDouble() / totalMs >= 0.9
        activeSongId = null
        activeModeId = null
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { repository.saveListeningHistory(songId, modeId, durationSec, completed) }
        }
    }

    fun charger(songId: Long, silencieux: Boolean = false, startMode: ModeEcoute? = null) {
        autoPlayTriggered = false
        if (startMode != null) _modeEcoute.value = startMode
        saveHistoryForActiveSong()
        scope.launch {
            if (!silencieux) _uiState.value = LecteurUiState.Loading
            try {
                val song = repository.getSongPlayerDetails(songId)
                _uiState.value = LecteurUiState.Success(song)
                lancerLecture(song, _modeEcoute.value)
            } catch (e: Exception) {
                _uiState.value = LecteurUiState.Error(e.message ?: "Erreur de chargement")
            }
        }
    }

    fun togglePlayPause() {
        if (audioPlayer.isPlaying.value) audioPlayer.pause() else audioPlayer.resume()
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun changerMode(mode: ModeEcoute) {
        if (mode == _modeEcoute.value) return
        val currentSong = (_uiState.value as? LecteurUiState.Success)?.song ?: return
        _modeEcoute.value = mode
        activeModeId = mode.toPlayModeId()
        lancerLecture(currentSong, mode)
    }

    private fun lancerLecture(song: SongPlayerResponse, mode: ModeEcoute) {
        activeSongId = song.id
        activeModeId = mode.toPlayModeId()
        val url = when (mode) {
            ModeEcoute.AUDIO    -> song.audioStreamUrl
            ModeEcoute.VIDEO    -> null  // YouTube géré par WebView
            ModeEcoute.KARAOKE  -> null  // Vidéo MP4 géré par ExoVideoPlayerComposable
            ModeEcoute.PLAYBACK -> song.playbackStreamUrl ?: song.audioStreamUrl
        }
        if (url != null) {
            audioPlayer.play(
                url,
                MediaMetadata(
                    title = song.title,
                    artist = song.artistStageName,
                    artworkUrl = song.albumCoverUrl ?: song.artistPhotoUrl,
                    durationMs = (song.durationSeconds ?: 0) * 1000L
                )
            )
        } else {
            audioPlayer.pause()
        }
    }

    fun jouerSuivant() {
        if (autoPlayTriggered) return
        val nextSong = queueManager.upNext.value.firstOrNull() ?: return
        autoPlayTriggered = true
        queueManager.playFromQueue(nextSong)
        charger(nextSong.id, silencieux = true)
    }

    fun jouerPrecedent() {
        if (currentPositionMs.value > 3_000L) {
            seekTo(0L)
            return
        }
        val prevSong = queueManager.playPrevious() ?: run {
            seekTo(0L)
            return
        }
        charger(prevSong.id, silencieux = true)
    }

    fun dispose() {
        saveHistoryForActiveSong()
        scope.cancel()
    }
}
