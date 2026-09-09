package com.kaloy.app.presentation.lecteur

import com.kaloy.app.core.audio.AudioPlayerController
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

enum class ModeEcoute { AUDIO, KARAOKE, PLAYBACK }

class LecteurViewModel(
    private val repository: LecteurRepository,
    private val audioPlayer: AudioPlayerController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<LecteurUiState>(LecteurUiState.Loading)
    val uiState: StateFlow<LecteurUiState> = _uiState

    private val _modeEcoute = MutableStateFlow(ModeEcoute.AUDIO)
    val modeEcoute: StateFlow<ModeEcoute> = _modeEcoute

    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPositionMs: StateFlow<Long> = audioPlayer.currentPositionMs
    val durationMs: StateFlow<Long> = audioPlayer.durationMs

    fun charger(songId: Long) {
        scope.launch {
            _uiState.value = LecteurUiState.Loading
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
        val currentSong = (_uiState.value as? LecteurUiState.Success)?.song ?: return
        _modeEcoute.value = mode
        lancerLecture(currentSong, mode)
    }

    private fun lancerLecture(song: SongPlayerResponse, mode: ModeEcoute) {
        val url = when (mode) {
            ModeEcoute.AUDIO -> song.audioStreamUrl
            ModeEcoute.KARAOKE -> song.karaokeStreamUrl ?: song.audioStreamUrl
            ModeEcoute.PLAYBACK -> song.playbackStreamUrl ?: song.audioStreamUrl
        }
        url?.let { audioPlayer.play(it) }
    }

    fun dispose() {
        audioPlayer.release()
        scope.cancel()
    }
}
