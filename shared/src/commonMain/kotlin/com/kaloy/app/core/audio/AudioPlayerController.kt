package com.kaloy.app.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerController {
    val isPlaying: StateFlow<Boolean>

    /**
     * L'adresse de la piste chargee, ou null si le lecteur est vide.
     *
     * Renseignee par play(), effacee par release(), inchangee par
     * pause() : une piste en pause reste chargee.
     *
     * Elle existe pour que l'appelant puisse savoir s'il est deja sur
     * cette piste. Sans elle, rouvrir le lecteur rappelait play(), qui
     * repart de zero : la position etait perdue des qu'on touchait la
     * barre du bas pour revenir au morceau en cours.
     *
     * L'adresse et non l'identifiant de la chanson : elle encode aussi
     * le mode. Un meme morceau en Audio et en Playback a deux adresses.
     */
    val currentUrl: StateFlow<String?>
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
