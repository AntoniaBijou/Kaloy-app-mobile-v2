package com.kaloy.app.presentation.lecteur

import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.MediaMetadata
import com.kaloy.app.core.offline.OfflineFileStore
import com.kaloy.app.core.offline.OfflinePlaylistCatalog
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.model.Album
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Song
import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.model.SongPlayerResponse
import com.kaloy.app.data.repository.LecteurRepository
import com.kaloy.app.presentation.favoris.FavorisViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LecteurUiState {
    object Loading : LecteurUiState()
    data class Success(val song: SongPlayerResponse) : LecteurUiState()
    data class Error(val message: String) : LecteurUiState()
}

enum class ModeEcoute { AUDIO, VIDEO, KARAOKE, PLAYBACK }

class LecteurViewModel(
    private val repository: LecteurRepository,
    private val audioPlayer: AudioPlayerController,
    private val queueManager: QueueManager,
    private val offlineCatalog: OfflinePlaylistCatalog,
    private val offlineFileStore: OfflineFileStore,
    private val sessionManager: AuthSessionManager,
    private val favorisViewModel: FavorisViewModel
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<LecteurUiState>(LecteurUiState.Loading)
    val uiState: StateFlow<LecteurUiState> = _uiState

    private val _modeEcoute = MutableStateFlow(ModeEcoute.AUDIO)
    val modeEcoute: StateFlow<ModeEcoute> = _modeEcoute

    val upNext: StateFlow<List<Song>> = queueManager.upNext
    val history: StateFlow<List<Song>> = queueManager.history

    // État optimiste : mis à jour immédiatement sur commande utilisateur,
    // puis confirmé par le moteur audio (évite le décalage IPC sur clics rapides).
    private val _isPlayingLocal = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlayingLocal

    val currentPositionMs: StateFlow<Long> = audioPlayer.currentPositionMs
    val durationMs: StateFlow<Long> = audioPlayer.durationMs
    val errorMessage: StateFlow<String?> = audioPlayer.errorMessage
    val statusMessage: StateFlow<String> = audioPlayer.statusMessage

    private var activeSongId: Long? = null
    private var activeModeId: Long? = null
    private var activeOfflinePlaylistId: Long? = null
    private var autoPlayTriggered = false
    private var loadJob: Job? = null

    init {
        scope.launch { audioPlayer.isPlaying.collect { _isPlayingLocal.value = it } }
        audioPlayer.setOnTrackCompleted { jouerSuivant() }
        audioPlayer.setOnSkipToPrevious { jouerPrecedent() }

        // Synchronise le cœur de la notification avec l'état favori de la chanson courante.
        scope.launch {
            combine(queueManager.currentSong, favorisViewModel.favorisSongIds) { song, favIds ->
                song?.id?.let { it in favIds } ?: false
            }.collect { isFav -> audioPlayer.updateFavoriteState(isFav) }
        }

        // Délègue le toggle depuis la notification à FavorisViewModel (gère optimiste + API + rollback).
        audioPlayer.setOnToggleFavorite {
            activeSongId?.let { favorisViewModel.toggleFavoriChansonId(it) }
        }
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

    fun charger(
        songId: Long,
        silencieux: Boolean = false,
        startMode: ModeEcoute? = null,
        offlinePlaylistId: Long? = null
    ) {
        activeOfflinePlaylistId = offlinePlaylistId
        val loadedSong = (_uiState.value as? LecteurUiState.Success)?.song
        if (loadedSong?.id == songId && queueManager.currentSong.value?.id == songId) {
            if (startMode != null && startMode != _modeEcoute.value) changerMode(startMode)
            return
        }
        if (!silencieux) autoPlayTriggered = false
        if (startMode != null) _modeEcoute.value = startMode
        saveHistoryForActiveSong()
        loadJob?.cancel()
        val requestedOfflinePlaylistId = activeOfflinePlaylistId
        loadJob = scope.launch {
            if (!silencieux) _uiState.value = LecteurUiState.Loading
            try {
                // Effectuer la lecture disque (SharedPreferences + JSON) et le réseau
                // hors du thread principal pour éviter tout blocage de l'UI.
                val song = withContext(Dispatchers.Default) {
                    val userId = sessionManager.getUserId()
                    val fallback = queueManager.currentSong.value?.takeIf { it.id == songId }
                    // 1er essai : chercher dans la playlist demandée (ou toutes si null)
                    val cached = offlineCatalog.findPlayableSong(
                        userId, songId, offlineFileStore, requestedOfflinePlaylistId, fallback
                    ) ?: if (requestedOfflinePlaylistId != null) {
                        // 2e essai : la chanson est peut-être téléchargée dans une autre playlist
                        offlineCatalog.findPlayableSong(userId, songId, offlineFileStore, null, fallback)
                    } else null

                    resolveSongPlayerDetails(cached) {
                        repository.getSongPlayerDetails(songId)
                    }
                }
                queueManager.syncCurrent(song.asQueueSong())
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                autoPlayTriggered = false
                _uiState.value = LecteurUiState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun togglePlayPause() {
        val playing = _isPlayingLocal.value
        _isPlayingLocal.value = !playing   // Feedback immédiat avant la réponse IPC
        if (playing) audioPlayer.pause() else audioPlayer.resume()
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
        autoPlayTriggered = false
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

    // Lance en arrière-plan le pré-chauffage de la clé de déchiffrement de la chanson
    // suivante pendant que la chanson courante est en lecture. Les opérations TEE du
    // Keystore (~300-600 ms) se terminent avant que l'utilisateur clique Suivant, donc
    // charger() trouve la clé en cache et lance setMedia() quasi immédiatement.
    private fun prewarmNextSong() {
        val nextSong = queueManager.upNext.value.firstOrNull() ?: return
        scope.launch {
            withContext(Dispatchers.Default) {
                runCatching {
                    val cached = offlineCatalog.findPlayableSong(
                        sessionManager.getUserId(),
                        nextSong.id,
                        offlineFileStore,
                        activeOfflinePlaylistId,
                        nextSong
                    )
                    if (cached != null) {
                        offlineFileStore.prewarm(cached.playlistId, cached.song.songId)
                    }
                }
            }
        }
    }

    fun jouerSuivant() {
        if (autoPlayTriggered) return
        val nextSong = queueManager.upNext.value.firstOrNull() ?: return
        autoPlayTriggered = true
        queueManager.playFromQueue(nextSong)
        charger(
            nextSong.id,
            silencieux = true,
            offlinePlaylistId = activeOfflinePlaylistId
        )
    }

    fun jouerDepuisFile(song: Song) {
        autoPlayTriggered = false
        queueManager.playFromQueue(song)
        charger(
            song.id,
            silencieux = true,
            offlinePlaylistId = activeOfflinePlaylistId
        )
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
        charger(
            prevSong.id,
            silencieux = true,
            offlinePlaylistId = activeOfflinePlaylistId
        )
    }

    private fun SongPlayerResponse.asQueueSong(): Song = Song(
        id = id,
        title = title,
        artist = Artist(stageName = artistStageName, photoUrl = artistPhotoUrl),
        album = albumId?.let { Album(id = it, title = albumTitle.orEmpty(), coverUrl = albumCoverUrl) },
        durationSeconds = durationSeconds
    )

    fun onPlayerScreenClosed() {
        saveHistoryForActiveSong()
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
        loadJob?.cancel()
        scope.cancel()
    }
}
