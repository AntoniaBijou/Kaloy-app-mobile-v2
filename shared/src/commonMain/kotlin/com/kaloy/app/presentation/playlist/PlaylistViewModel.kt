package com.kaloy.app.presentation.playlist

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistGenerationProposal
import com.kaloy.app.data.dto.playlist.PlaylistGenerationRequest
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.dto.playlist.SaveGeneratedPlaylistRequest
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.model.PageResponse
import com.kaloy.app.data.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlaylistSortCritere { DATE_DESC, DATE_ASC, NOM_AZ, NOMBRE_TITRES }

sealed class PlaylistUiState {
    data object Loading : PlaylistUiState()
    data class Success(
        val mesPlaylists: List<PlaylistUiItem>,
        val editoriales: List<EditorialPlaylist>,
        val nombreTelecharges: Int
    ) : PlaylistUiState()
    data class Error(val message: String) : PlaylistUiState()
}

sealed class PlaylistOperationState {
    data object Idle : PlaylistOperationState()
    data object Loading : PlaylistOperationState()
    data class Success(val message: String) : PlaylistOperationState()
    data class Error(val message: String) : PlaylistOperationState()
    data object NeedsRelogin : PlaylistOperationState()
}

sealed class PublicPlaylistUiState {
    data object Idle : PublicPlaylistUiState()
    data object Loading : PublicPlaylistUiState()
    data class Success(val playlists: List<PlaylistUiItem>) : PublicPlaylistUiState()
    data class Error(val message: String) : PublicPlaylistUiState()
}

sealed class MergePreviewState {
    data object Idle : MergePreviewState()
    data object Loading : MergePreviewState()
    data class Success(
        val sourceIds: List<Long>,
        val songs: List<PlaylistSongUiItem>,
        val duplicateCount: Int
    ) : MergePreviewState()
    data class Error(val message: String) : MergePreviewState()
}

sealed class PlaylistGenerationUiState {
    data object Idle : PlaylistGenerationUiState()
    data object LoadingOptions : PlaylistGenerationUiState()
    data class Ready(val genres: List<Genre>, val artists: List<Artist>) : PlaylistGenerationUiState()
    data class Generating(val genres: List<Genre>, val artists: List<Artist>) : PlaylistGenerationUiState()
    data class Proposals(
        val genres: List<Genre>,
        val artists: List<Artist>,
        val criteria: PlaylistGenerationRequest,
        val proposals: List<PlaylistGenerationProposal>,
        val savedProposalNumbers: Set<Int> = emptySet()
    ) : PlaylistGenerationUiState()
    data class Error(
        val message: String,
        val genres: List<Genre> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val optionsLoaded: Boolean = false
    ) : PlaylistGenerationUiState()
}

sealed class ActiveDialogPlaylist {
    data object None : ActiveDialogPlaylist()
    data object CreatePlaylist : ActiveDialogPlaylist()
    data object FilterSort : ActiveDialogPlaylist()
    data class Merge(val preSelected: List<Long> = emptyList()) : ActiveDialogPlaylist()
    data class Options(val playlist: PlaylistUiItem) : ActiveDialogPlaylist()
    data class ConfirmVisibility(val playlist: PlaylistUiItem) : ActiveDialogPlaylist()
    data class ConfirmDelete(val id: Long) : ActiveDialogPlaylist()
    data class ModifierPlaylist(val playlist: PlaylistUiItem) : ActiveDialogPlaylist()
}

class PlaylistViewModel(
    private val repository: PlaylistRepository,
    private val sessionManager: AuthSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _operationState = MutableStateFlow<PlaylistOperationState>(PlaylistOperationState.Idle)
    val operationState: StateFlow<PlaylistOperationState> = _operationState.asStateFlow()

    private val _activeDialog = MutableStateFlow<ActiveDialogPlaylist>(ActiveDialogPlaylist.None)
    val activeDialog: StateFlow<ActiveDialogPlaylist> = _activeDialog.asStateFlow()

    private val _mergePreview = MutableStateFlow<MergePreviewState>(MergePreviewState.Idle)
    val mergePreview: StateFlow<MergePreviewState> = _mergePreview.asStateFlow()

    private val _generationState =
        MutableStateFlow<PlaylistGenerationUiState>(PlaylistGenerationUiState.Idle)
    val generationState: StateFlow<PlaylistGenerationUiState> = _generationState.asStateFlow()

    private val _publicPlaylistState = MutableStateFlow<PublicPlaylistUiState>(PublicPlaylistUiState.Idle)
    val publicPlaylistState: StateFlow<PublicPlaylistUiState> = _publicPlaylistState.asStateFlow()
    val discoveryMode = MutableStateFlow(false)
    val publicSearchQuery = MutableStateFlow("")

    private var toutesPlaylists: List<PlaylistUiItem> = emptyList()
    private var editoriales: List<EditorialPlaylist> = emptyList()
    private var mergePreviewJob: Job? = null
    private var publicSearchJob: Job? = null

    val requeteRecherche = MutableStateFlow("")
    val critereTri = MutableStateFlow(PlaylistSortCritere.DATE_DESC)

    init { charger() }

    fun charger() {
        scope.launch {
            _uiState.value = PlaylistUiState.Loading
            try {
                toutesPlaylists = repository.getMesPlaylists()
                editoriales = repository.getPlaylistsEditoriales()
                actualiserListeAffichee()
            } catch (e: Exception) {
                if (e.message == "SESSION_EXPIRED") {
                    sessionManager.clear()
                    _operationState.value = PlaylistOperationState.NeedsRelogin
                } else {
                    _uiState.value = PlaylistUiState.Error(UserErrorMessages.fromThrowable(e))
                }
            }
        }
    }

    private fun actualiserListeAffichee() {
        val query = requeteRecherche.value.lowercase()
        val filtered = if (query.isBlank()) toutesPlaylists
                       else toutesPlaylists.filter { it.name.lowercase().contains(query) }
        val sorted = when (critereTri.value) {
            PlaylistSortCritere.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            PlaylistSortCritere.DATE_ASC -> filtered.sortedBy { it.updatedAt }
            PlaylistSortCritere.NOM_AZ -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSortCritere.NOMBRE_TITRES -> filtered.sortedByDescending { it.nombreTitres }
        }
        _uiState.value = PlaylistUiState.Success(
            mesPlaylists = sorted,
            editoriales = editoriales,
            nombreTelecharges = toutesPlaylists.count { it.estTelecharge }
        )
    }

    fun rechercherQuery(query: String) {
        requeteRecherche.value = query
        if (_uiState.value !is PlaylistUiState.Loading) actualiserListeAffichee()
    }

    fun afficherDecouverte() {
        discoveryMode.value = true
        if (_publicPlaylistState.value is PublicPlaylistUiState.Idle) chargerPlaylistsPubliques()
    }

    fun afficherMesPlaylists() {
        discoveryMode.value = false
    }

    fun rechercherPlaylistsPubliques(query: String) {
        publicSearchQuery.value = query
        publicSearchJob?.cancel()
        _publicPlaylistState.value = PublicPlaylistUiState.Loading
        publicSearchJob = scope.launch {
            delay(350)
            chargerPlaylistsPubliques(query)
        }
    }

    fun chargerPlaylistsPubliques(query: String = publicSearchQuery.value) {
        publicSearchJob?.cancel()
        publicSearchJob = scope.launch {
            _publicPlaylistState.value = PublicPlaylistUiState.Loading
            try {
                val playlists = mutableListOf<PlaylistUiItem>()
                var page = 0
                var isLastPage = false
                while (!isLastPage) {
                    val response: PageResponse<PlaylistUiItem> =
                        repository.getPublicPlaylists(query, page, 30)
                    playlists += response.content
                    isLastPage = response.last || response.content.isEmpty()
                    page++
                }
                _publicPlaylistState.value = PublicPlaylistUiState.Success(playlists)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message == "SESSION_EXPIRED") {
                    sessionManager.clear()
                    _operationState.value = PlaylistOperationState.NeedsRelogin
                } else {
                    _publicPlaylistState.value =
                        PublicPlaylistUiState.Error(UserErrorMessages.fromThrowable(e))
                }
            }
        }
    }

    fun trierPar(critere: PlaylistSortCritere) {
        critereTri.value = critere
        if (_uiState.value !is PlaylistUiState.Loading) actualiserListeAffichee()
    }

    fun afficherDialog(dialog: ActiveDialogPlaylist) {
        _activeDialog.value = dialog
        if (dialog !is ActiveDialogPlaylist.Merge) viderApercuFusion()
        if (dialog is ActiveDialogPlaylist.CreatePlaylist) chargerOptionsGeneration()
        else _generationState.value = PlaylistGenerationUiState.Idle
    }

    fun fermerDialog() {
        _activeDialog.value = ActiveDialogPlaylist.None
        viderApercuFusion()
        _generationState.value = PlaylistGenerationUiState.Idle
    }

    fun chargerApercuFusion(ids: List<Long>) {
        val idsOrdonnes = ordonnerIdsSelectionnes(ids)
        mergePreviewJob?.cancel()
        if (idsOrdonnes.size < 2) {
            _mergePreview.value = MergePreviewState.Idle
            return
        }

        _mergePreview.value = MergePreviewState.Loading
        mergePreviewJob = scope.launch {
            try {
                val songsById = LinkedHashMap<Long, PlaylistSongUiItem>()
                var totalSongCount = 0
                idsOrdonnes.forEach { playlistId ->
                    val songs = repository.getSongsInPlaylist(playlistId)
                    totalSongCount += songs.size
                    songs.forEach { song -> songsById.putIfAbsent(song.songId, song) }
                }
                _mergePreview.value = MergePreviewState.Success(
                    sourceIds = idsOrdonnes,
                    songs = songsById.values.toList(),
                    duplicateCount = totalSongCount - songsById.size
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message == "SESSION_EXPIRED") {
                    sessionManager.clear()
                    _operationState.value = PlaylistOperationState.NeedsRelogin
                } else {
                    _mergePreview.value = MergePreviewState.Error(UserErrorMessages.fromThrowable(e))
                }
            }
        }
    }

    private fun viderApercuFusion() {
        mergePreviewJob?.cancel()
        mergePreviewJob = null
        _mergePreview.value = MergePreviewState.Idle
    }

    private fun ordonnerIdsSelectionnes(ids: List<Long>): List<Long> {
        val selection = ids.toSet()
        val playlistsAffichees =
            (_uiState.value as? PlaylistUiState.Success)?.mesPlaylists ?: toutesPlaylists
        return playlistsAffichees.map { it.id }.filter { it in selection }
    }

    private fun chargerOptionsGeneration() {
        scope.launch {
            _generationState.value = PlaylistGenerationUiState.LoadingOptions
            try {
                val genres = repository.getGenerationGenres()
                val artists = repository.getGenerationArtists()
                _generationState.value = PlaylistGenerationUiState.Ready(genres, artists)
            } catch (e: Exception) {
                _generationState.value = PlaylistGenerationUiState.Error(
                    UserErrorMessages.fromThrowable(e)
                )
            }
        }
    }

    fun genererPlaylists(criteria: PlaylistGenerationRequest) {
        val current = _generationState.value
        val genres = when (current) {
            is PlaylistGenerationUiState.Ready -> current.genres
            is PlaylistGenerationUiState.Generating -> current.genres
            is PlaylistGenerationUiState.Proposals -> current.genres
            is PlaylistGenerationUiState.Error -> current.genres
            else -> emptyList()
        }
        val artists = when (current) {
            is PlaylistGenerationUiState.Ready -> current.artists
            is PlaylistGenerationUiState.Generating -> current.artists
            is PlaylistGenerationUiState.Proposals -> current.artists
            is PlaylistGenerationUiState.Error -> current.artists
            else -> emptyList()
        }
        scope.launch {
            _generationState.value = PlaylistGenerationUiState.Generating(genres, artists)
            try {
                val proposals = repository.genererPlaylists(criteria)
                _generationState.value = if (proposals.isEmpty()) {
                    PlaylistGenerationUiState.Error(
                        "Aucune proposition ne correspond à ces critères.",
                        genres,
                        artists
                    )
                } else {
                    PlaylistGenerationUiState.Proposals(genres, artists, criteria, proposals)
                }
            } catch (e: Exception) {
                _generationState.value = PlaylistGenerationUiState.Error(
                    UserErrorMessages.fromThrowable(e),
                    genres,
                    artists,
                    optionsLoaded = true
                )
            }
        }
    }

    fun conserverPlaylistGeneree(
        criteria: PlaylistGenerationRequest,
        proposal: PlaylistGenerationProposal,
        nom: String
    ) {
        if (nom.isBlank() || nom.trim().length > 150) {
            _operationState.value = PlaylistOperationState.Error(
                "Le nom doit contenir entre 1 et 150 caractères."
            )
            return
        }
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.conserverPlaylistGeneree(
                    SaveGeneratedPlaylistRequest(
                        name = nom.trim(),
                        criteria = criteria,
                        songIds = proposal.songs.map { it.id }
                    )
                )
                val current = _generationState.value as? PlaylistGenerationUiState.Proposals
                if (current != null) {
                    _generationState.value = current.copy(
                        savedProposalNumbers = current.savedProposalNumbers + proposal.proposalNumber
                    )
                }
                _operationState.value = PlaylistOperationState.Success("Proposition conservée dans vos playlists.")
                charger()
            } catch (e: Exception) {
                _operationState.value = PlaylistOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun modifierPlaylist(id: Long, nom: String, description: String?) {
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.modifierPlaylist(id, UpdatePlaylistRequest(nom, description))
                _operationState.value = PlaylistOperationState.Success("Playlist modifiée.")
                fermerDialog()
                charger()
            } catch (e: Exception) {
                _operationState.value = PlaylistOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun changerVisibilite(playlist: PlaylistUiItem) {
        val nouvelleVisibilite = if (playlist.visibility == "PUBLIC") "PRIVATE" else "PUBLIC"
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.setPlaylistVisibility(playlist.id, nouvelleVisibilite)
                fermerDialog()
                _operationState.value = PlaylistOperationState.Success(
                    if (nouvelleVisibilite == "PUBLIC")
                        "Playlist partagée avec les utilisateurs connectés."
                    else
                        "Playlist rendue privée."
                )
                charger()
                if (discoveryMode.value) chargerPlaylistsPubliques()
            } catch (e: Exception) {
                _operationState.value = PlaylistOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun supprimerPlaylist(id: Long) {
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.supprimerPlaylist(id)
                _operationState.value = PlaylistOperationState.Success("Playlist supprimée.")
                fermerDialog()
                charger()
            } catch (e: Exception) {
                _operationState.value = PlaylistOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun fusionnerPlaylists(ids: List<Long>, nomResultat: String) {
        val idsOrdonnes = ordonnerIdsSelectionnes(ids)
        if (idsOrdonnes.size < 2 || nomResultat.isBlank() || nomResultat.trim().length > 150) {
            _operationState.value = PlaylistOperationState.Error(
                "Sélectionnez au moins deux playlists et saisissez un nom de 1 à 150 caractères."
            )
            return
        }
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.fusionnerPlaylists(MergePlaylistsRequest(idsOrdonnes, nomResultat.trim()))
                _operationState.value = PlaylistOperationState.Success("Playlists fusionnées.")
                fermerDialog()
                charger()
            } catch (e: Exception) {
                _operationState.value = PlaylistOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun telechargerPlaylist(id: Long) {
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.telechargerPlaylist(id)
                _operationState.value = PlaylistOperationState.Success("Playlist téléchargée.")
                charger()
            } catch (e: Exception) {
                _operationState.value = PlaylistOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun resetOperationState() { _operationState.value = PlaylistOperationState.Idle }
    fun dispose() {
        viderApercuFusion()
        publicSearchJob?.cancel()
        scope.cancel()
    }
}
