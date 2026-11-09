package com.kaloy.app.presentation.lecteur

import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.MediaMetadata
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.model.Album
import com.kaloy.app.data.model.Artist
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

                // La barre du bas affiche QueueManager.currentSong. Sans
                // cette ligne, cette valeur ne changeait QUE depuis
                // l'accueil : c'etait le seul ecran a renseigner la file.
                // Lancer depuis la recherche, un genre, un album, une fiche
                // d'artiste ou le bouton Lire du profil jouait donc le bon
                // son en affichant la chanson precedente.
                //
                // Ici plutot qu'a chaque appel : meme la rangee « ecoutes
                // recemment » de l'accueil avait oublie de le faire, et le
                // prochain ecran l'oublierait a son tour.
                //
                // Le garde-fou sur l'identifiant est indispensable. Suivant
                // et Precedent renseignent la file AVANT d'appeler charger(),
                // par playFromQueue() et playPrevious() ; sans lui, playNow()
                // empilerait une deuxieme fois la chanson dans l'historique.
                //
                // playNow() et non setContext() : la file d'attente n'est pas
                // touchee. Lancer une chanson depuis la recherche laisse donc
                // la file de l'accueil en place, et Suivant y mene toujours.
                if (queueManager.currentSong.value?.id != song.id) {
                    queueManager.playNow(song.versChanson())
                }

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
            // On ne relance QUE si la piste change. play() repart de zero :
            // sans ce test, la position etait perdue des qu'on touchait la
            // barre du bas pour revenir au morceau en cours.
            //
            // La comparaison porte sur l'ADRESSE et non sur l'identifiant de
            // la chanson : l'adresse encode aussi le mode. Ouvrir le playback
            // d'un morceau deja en ecoute relance donc bien, puisque son
            // adresse differe de celle de l'audio.
            // « Terminee » compte comme « plus chargee ». iOS n'a aucune
            // detection de fin de piste : rien n'y effacerait l'adresse,
            // et une chanson arrivee au bout ne repartirait jamais.
            val duree = audioPlayer.durationMs.value
            val terminee = duree > 0 &&
                audioPlayer.currentPositionMs.value >= duree - 1_000L

            if (audioPlayer.currentUrl.value != url || terminee) {
                audioPlayer.play(
                    url,
                    MediaMetadata(
                        title = song.title,
                        artist = song.artistStageName,
                        artworkUrl = song.albumCoverUrl ?: song.artistPhotoUrl,
                        durationMs = (song.durationSeconds ?: 0) * 1000L
                    )
                )
            } else if (!audioPlayer.isPlaying.value) {
                // Meme piste, deja chargee, mais a l'arret : on reprend la ou
                // elle en est. Ne rien faire la laisserait en pause, alors que
                // l'utilisateur vient d'appuyer sur la chanson POUR l'ecouter.
                //
                // resume() et non play() : play() rechargerait la piste et
                // reviendrait au debut, ce que tout ce qui precede evite.
                audioPlayer.resume()
            }
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

    /**
     * La chanson telle que la file d'attente et la barre du bas l'attendent.
     *
     * Le lecteur travaille sur SongPlayerResponse, la file sur Song : deux
     * formes du meme objet, l'une a plat et l'autre imbriquee.
     *
     * La barre affiche le titre, le nom d'artiste et la pochette. Les trois
     * sont repris ici, sans quoi on lirait un titre sans artiste au-dessus
     * d'un carre vide -- la pochette venant de l'album, puis a defaut de la
     * photo de l'artiste.
     */
    private fun SongPlayerResponse.versChanson() = Song(
        id = id,
        title = title,
        durationSeconds = durationSeconds,
        artist = Artist(
            id = artistId,
            stageName = artistStageName,
            photoUrl = artistPhotoUrl
        ),
        // Nul si la chanson n'appartient a aucun album : la barre se rabat
        // alors sur la photo de l'artiste.
        album = albumId?.let {
            Album(id = it, title = albumTitle ?: "", coverUrl = albumCoverUrl)
        }
    )

    fun dispose() {
        saveHistoryForActiveSong()
        scope.cancel()
    }
}
