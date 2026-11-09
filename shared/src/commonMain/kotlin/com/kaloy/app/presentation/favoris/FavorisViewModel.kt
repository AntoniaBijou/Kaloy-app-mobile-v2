package com.kaloy.app.presentation.favoris

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.offline.FavorisCatalog
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.model.Album
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.FavorisAlbum
import com.kaloy.app.data.model.FavorisArtiste
import com.kaloy.app.data.model.FavorisSong
import com.kaloy.app.data.model.PendingFavoriSync
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.repository.FavorisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FavorisUiState {
    data object Loading : FavorisUiState()
    data class Success(
        val chansons: List<FavorisSong>,
        val albums: List<FavorisAlbum>,
        val artistes: List<FavorisArtiste>
    ) : FavorisUiState()
    data class Error(val message: String) : FavorisUiState()
}

class FavorisViewModel(
    private val repository: FavorisRepository,
    private val catalog: FavorisCatalog,
    private val queueManager: QueueManager,
    private val sessionManager: AuthSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<FavorisUiState>(FavorisUiState.Loading)
    val uiState: StateFlow<FavorisUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init { charger() }

    fun charger() {
        scope.launch {
            _uiState.value = FavorisUiState.Loading
            // Afficher le cache immédiatement pendant le chargement réseau
            val cachedChansons = catalog.lireChansons()
            val cachedAlbums = catalog.lireAlbums()
            val cachedArtistes = catalog.lireArtistes()
            if (cachedChansons.isNotEmpty() || cachedAlbums.isNotEmpty() || cachedArtistes.isNotEmpty()) {
                _uiState.value = FavorisUiState.Success(cachedChansons, cachedAlbums, cachedArtistes)
            }
            runCatching {
                repository.synchroniser()
                val chansons = repository.getMesFavorisChansons()
                val albums = repository.getMesFavorisAlbums()
                val artistes = repository.getMesFavorisArtistes()
                _uiState.value = FavorisUiState.Success(chansons, albums, artistes)
            }.onFailure { e ->
                if (e.message == "SESSION_EXPIRED") {
                    sessionManager.clear()
                    _uiState.value = FavorisUiState.Error("Session expirée. Veuillez vous reconnecter.")
                } else if (_uiState.value is FavorisUiState.Loading) {
                    // Pas de cache : afficher l'erreur
                    _uiState.value = FavorisUiState.Error(UserErrorMessages.fromThrowable(e))
                }
                // Si cache affiché, on garde le cache silencieusement
            }
        }
    }

    // ── Toggle chanson ────────────────────────────────────────────────────────

    fun toggleFavoriChanson(song: Song) {
        val current = (_uiState.value as? FavorisUiState.Success) ?: return
        val isLiked = current.chansons.any { it.song.id == song.id }

        // Mise à jour optimiste immédiate
        val newChansons = if (isLiked)
            current.chansons.filterNot { it.song.id == song.id }
        else
            listOf(FavorisSong(likeId = 0, song = song)) + current.chansons
        _uiState.value = current.copy(chansons = newChansons)

        scope.launch {
            runCatching {
                if (isLiked) {
                    repository.retirerChansonDesFavoris(song.id)
                } else {
                    repository.aimerChanson(song.id)
                }
                charger()
            }.onFailure { e ->
                // Rollback
                _uiState.value = current
                _errorMessage.value = UserErrorMessages.fromThrowable(e)
                // Marquer pour sync hors ligne si erreur réseau
                if (e.message?.contains("SESSION_EXPIRED") == false) {
                    catalog.addPendingSync(
                        PendingFavoriSync(
                            type = "SONG",
                            action = if (isLiked) "REMOVE" else "ADD",
                            entityId = song.id
                        )
                    )
                }
            }
        }
    }

    // ── Toggle album ──────────────────────────────────────────────────────────

    fun toggleFavoriAlbum(album: Album) {
        val current = (_uiState.value as? FavorisUiState.Success) ?: return
        val isLiked = current.albums.any { it.album.id == album.id }

        val newAlbums = if (isLiked)
            current.albums.filterNot { it.album.id == album.id }
        else
            listOf(FavorisAlbum(likeId = 0, album = album)) + current.albums
        _uiState.value = current.copy(albums = newAlbums)

        scope.launch {
            runCatching {
                if (isLiked) {
                    repository.retirerAlbumDesFavoris(album.id)
                } else {
                    repository.aimerAlbum(album.id)
                }
                charger()
            }.onFailure { e ->
                _uiState.value = current
                _errorMessage.value = UserErrorMessages.fromThrowable(e)
                if (e.message?.contains("SESSION_EXPIRED") == false) {
                    catalog.addPendingSync(
                        PendingFavoriSync(
                            type = "ALBUM",
                            action = if (isLiked) "REMOVE" else "ADD",
                            entityId = album.id
                        )
                    )
                }
            }
        }
    }

    // ── Toggle artiste ────────────────────────────────────────────────────────

    fun toggleFavoriArtiste(artiste: Artist) {
        val current = (_uiState.value as? FavorisUiState.Success) ?: return
        val isLiked = current.artistes.any { it.artiste.id == artiste.id }

        val newArtistes = if (isLiked)
            current.artistes.filterNot { it.artiste.id == artiste.id }
        else
            listOf(FavorisArtiste(likeId = 0, artiste = artiste)) + current.artistes
        _uiState.value = current.copy(artistes = newArtistes)

        scope.launch {
            runCatching {
                if (isLiked) {
                    repository.retirerArtisteDesFavoris(artiste.id)
                } else {
                    repository.aimerArtiste(artiste.id)
                }
                charger()
            }.onFailure { e ->
                _uiState.value = current
                _errorMessage.value = UserErrorMessages.fromThrowable(e)
                if (e.message?.contains("SESSION_EXPIRED") == false) {
                    catalog.addPendingSync(
                        PendingFavoriSync(
                            type = "ARTIST",
                            action = if (isLiked) "REMOVE" else "ADD",
                            entityId = artiste.id
                        )
                    )
                }
            }
        }
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    fun jouerTousLesFavoris(): Song? {
        val songs = (_uiState.value as? FavorisUiState.Success)?.chansons?.map { it.song }
            ?: return null
        if (songs.isEmpty()) return null
        val first = songs.first()
        queueManager.setContext(songs, first)
        return first
    }

    fun jouerAleatoire(): Song? {
        val songs = (_uiState.value as? FavorisUiState.Success)?.chansons?.map { it.song }
            ?: return null
        if (songs.isEmpty()) return null
        val shuffled = songs.shuffled()
        val first = shuffled.first()
        queueManager.setContext(shuffled, first)
        return first
    }

    fun clearError() { _errorMessage.value = null }

    fun dispose() { scope.cancel() }
}
