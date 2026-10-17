package com.kaloy.app.presentation.playlist

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.CreatePlaylistRequest
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

sealed class ActiveDialogPlaylist {
    data object None : ActiveDialogPlaylist()
    data object CreatePlaylist : ActiveDialogPlaylist()
    data object FilterSort : ActiveDialogPlaylist()
    data class Merge(val preSelected: List<Long> = emptyList()) : ActiveDialogPlaylist()
    data class Options(val playlist: PlaylistUiItem) : ActiveDialogPlaylist()
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

    private var toutesPlaylists: List<PlaylistUiItem> = emptyList()
    private var editoriales: List<EditorialPlaylist> = emptyList()

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

    fun trierPar(critere: PlaylistSortCritere) {
        critereTri.value = critere
        if (_uiState.value !is PlaylistUiState.Loading) actualiserListeAffichee()
    }

    fun afficherDialog(dialog: ActiveDialogPlaylist) { _activeDialog.value = dialog }
    fun fermerDialog() { _activeDialog.value = ActiveDialogPlaylist.None }

    fun creerPlaylist(nom: String, genreIds: List<Long>, artistIds: List<Long>, dureeMin: Int) {
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.creerPlaylist(CreatePlaylistRequest(nom, genreIds, artistIds, dureeMin))
                _operationState.value = PlaylistOperationState.Success("Playlist créée avec succès.")
                fermerDialog()
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
        scope.launch {
            _operationState.value = PlaylistOperationState.Loading
            try {
                repository.fusionnerPlaylists(MergePlaylistsRequest(ids, nomResultat))
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
    fun dispose() = scope.cancel()
}
