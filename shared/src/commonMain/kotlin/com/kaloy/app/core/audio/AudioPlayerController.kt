package com.kaloy.app.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerController {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val errorMessage: StateFlow<String?>
    val statusMessage: StateFlow<String>

    fun play(url: String)
    fun play(url: String, metadata: MediaMetadata) {
        play(url)
    }
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()
    fun setOnTrackCompleted(callback: () -> Unit) {}
    fun setOnSkipToPrevious(callback: () -> Unit) {}

    /** Met à jour l'état favori de la chanson courante (affiché sur la notification). */
    fun updateFavoriteState(isFavorite: Boolean) {}

    /** Enregistre le callback déclenché quand l'utilisateur tape le bouton cœur. */
    fun setOnToggleFavorite(callback: () -> Unit) {}
}
