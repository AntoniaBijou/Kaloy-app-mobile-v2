package com.kaloy.app.core.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidAudioPlayerController(private val context: Context) : AudioPlayerController {

    companion object {
        val skipToNext = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val skipToPrevious = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** État favori de la chanson courante — lu par KaloyMediaSessionService pour le custom layout. */
        private val _isFavorite = MutableStateFlow(false)
        val isFavorite = _isFavorite.asStateFlow()

        /** Émis par KaloyMediaSessionService quand l'utilisateur tape le bouton cœur. */
        val toggleFavorite = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        fun setFavoriteState(isFav: Boolean) { _isFavorite.value = isFav }
    }

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPositionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow("Service audio en connexion")
    private val _currentUrl = MutableStateFlow<String?>(null)

    override val isPlaying: StateFlow<Boolean> = _isPlaying
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs
    override val durationMs: StateFlow<Long> = _durationMs
    override val errorMessage: StateFlow<String?> = _errorMessage
    override val statusMessage: StateFlow<String> = _statusMessage
    override val currentUrl: StateFlow<String?> = _currentUrl

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val controller = AndroidMediaPlayerController(context)
    private var onTrackCompletedCallback: () -> Unit = {}
    private var onSkipToPreviousCallback: () -> Unit = {}
    private var onToggleFavoriteCallback: () -> Unit = {}

    override fun setOnTrackCompleted(callback: () -> Unit) {
        onTrackCompletedCallback = callback
    }

    override fun setOnSkipToPrevious(callback: () -> Unit) {
        onSkipToPreviousCallback = callback
    }

    override fun updateFavoriteState(isFavorite: Boolean) {
        setFavoriteState(isFavorite)
    }

    override fun setOnToggleFavorite(callback: () -> Unit) {
        onToggleFavoriteCallback = callback
    }

    init {
        scope.launch {
            skipToNext.collect { onTrackCompletedCallback() }
        }
        scope.launch {
            skipToPrevious.collect { onSkipToPreviousCallback() }
        }
        scope.launch {
            toggleFavorite.collect { onToggleFavoriteCallback() }
        }
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
                    is MediaPlayerStatus.Completed -> {
                        _statusMessage.value = "Lecture terminée"
                        // La piste est au bout : elle n'est plus « en
                        // cours ». Sans cela, reappuyer sur la meme
                        // chanson ne la relancerait pas, le lecteur la
                        // croyant deja chargee.
                        _currentUrl.value = null
                        _isPlaying.value = false
                        onTrackCompletedCallback()
                    }
                    is MediaPlayerStatus.Error -> {
                        _statusMessage.value = "Erreur Media3"
                        Log.e("KaloyAudio", "Erreur de lecture: ${state.message}")
                        _errorMessage.value = state.message
                        // Une piste en echec doit pouvoir etre relancee.
                        _currentUrl.value = null
                        _isPlaying.value = false
                    }
                    is MediaPlayerStatus.Loading -> {
                        // Ne pas changer statusMessage : le message précédent ("Lecture active" ou
                        // "Lecture en pause") reste affiché — l'utilisateur voit la transition
                        // directement, sans "Flux en chargement" intermédiaire.
                        // Ne pas toucher _isPlaying non plus : conserve le bouton Pause affiché.
                        _currentPositionMs.value = 0L
                        _durationMs.value = 0L
                    }
                    else -> {
                        _statusMessage.value = "Service audio prêt"
                        _isPlaying.value = false
                        _currentPositionMs.value = 0L
                        _durationMs.value = 0L
                    }
                }
            }
        }
    }

    override fun play(url: String) {
        _currentUrl.value = url
        controller.setMedia(url, MediaMetadata("Kaloy", ""))
    }

    override fun play(url: String, metadata: MediaMetadata) {
        _currentUrl.value = url
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
        // Le lecteur est vide : plus aucune piste chargee.
        _currentUrl.value = null
        scope.cancel()
        controller.release()
    }
}
