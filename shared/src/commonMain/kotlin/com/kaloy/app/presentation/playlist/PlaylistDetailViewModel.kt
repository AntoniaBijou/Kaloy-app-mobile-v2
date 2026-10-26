package com.kaloy.app.presentation.playlist

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.AddSongsRequest
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.dto.playlist.ReorderSongItem
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState()
    data class Success(
        val playlistId: Long,
        val playlistName: String,
        val songs: List<PlaylistSongUiItem>
    ) : PlaylistDetailUiState()
    data class Error(val message: String) : PlaylistDetailUiState()
}

sealed class PlaylistDetailOperationState {
    data object Idle : PlaylistDetailOperationState()
    data object Loading : PlaylistDetailOperationState()
    data class Success(val message: String) : PlaylistDetailOperationState()
    data class Error(val message: String) : PlaylistDetailOperationState()
    data object NeedsRelogin : PlaylistDetailOperationState()
}

sealed class ActiveDialogDetail {
    data object None : ActiveDialogDetail()
    data object AddSongs : ActiveDialogDetail()
    data class ConfirmRemove(val item: PlaylistSongUiItem) : ActiveDialogDetail()
}

class PlaylistDetailViewModel(
    private val repository: PlaylistRepository,
    private val sessionManager: AuthSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private val _operationState = MutableStateFlow<PlaylistDetailOperationState>(PlaylistDetailOperationState.Idle)
    val operationState: StateFlow<PlaylistDetailOperationState> = _operationState.asStateFlow()

    private val _activeDialog = MutableStateFlow<ActiveDialogDetail>(ActiveDialogDetail.None)
    val activeDialog: StateFlow<ActiveDialogDetail> = _activeDialog.asStateFlow()

    private val _catalogueSongs = MutableStateFlow<List<Song>>(emptyList())
    val catalogueSongs: StateFlow<List<Song>> = _catalogueSongs.asStateFlow()

    private var playlistId: Long = 0L
    private var playlistName: String = ""
    private var playlistIsPublic: Boolean = false
    private var canManagePlaylist: Boolean = true

    // Tracks whether local reorder differs from persisted order
    private var ordreModifie: Boolean = false
    val isOrdreModifie: Boolean get() = ordreModifie

    fun charger(id: Long, name: String, isPublic: Boolean = false, canManage: Boolean = true) {
        playlistId = id
        playlistName = name
        playlistIsPublic = isPublic
        canManagePlaylist = canManage
        ordreModifie = false
        scope.launch {
            _uiState.value = PlaylistDetailUiState.Loading
            try {
                val songs = if (isPublic) repository.getPublicPlaylistSongs(id)
                    else repository.getSongsInPlaylist(id)
                _uiState.value = PlaylistDetailUiState.Success(id, name, songs)
            } catch (e: Exception) {
                if (e.message == "SESSION_EXPIRED") {
                    sessionManager.clear()
                    _operationState.value = PlaylistDetailOperationState.NeedsRelogin
                } else {
                    _uiState.value = PlaylistDetailUiState.Error(UserErrorMessages.fromThrowable(e))
                }
            }
        }
    }

    fun afficherDialog(dialog: ActiveDialogDetail) { _activeDialog.value = dialog }
    fun fermerDialog() { _activeDialog.value = ActiveDialogDetail.None }

    fun chargerCatalogue() {
        scope.launch {
            try {
                _catalogueSongs.value = repository.getCatalogueSongs()
            } catch (_: Exception) {}
        }
    }

    fun ajouterChansons(songIds: List<Long>) {
        if (!canManagePlaylist) return
        scope.launch {
            _operationState.value = PlaylistDetailOperationState.Loading
            fermerDialog()
            try {
                val result = repository.addSongsToPlaylist(playlistId, AddSongsRequest(songIds))
                val message = buildString {
                    if (result.added.isNotEmpty()) append("${result.added.size} chanson(s) ajoutée(s). ")
                    if (result.alreadyPresent.isNotEmpty()) append("${result.alreadyPresent.size} déjà présente(s). ")
                    if (result.notFound.isNotEmpty()) append("${result.notFound.size} introuvable(s).")
                }
                _operationState.value = PlaylistDetailOperationState.Success(message.trim())
                charger(playlistId, playlistName, playlistIsPublic, canManagePlaylist)
            } catch (e: Exception) {
                _operationState.value = PlaylistDetailOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun retirerChanson(item: PlaylistSongUiItem) {
        if (!canManagePlaylist) return
        scope.launch {
            _operationState.value = PlaylistDetailOperationState.Loading
            fermerDialog()
            try {
                repository.removeSongFromPlaylist(playlistId, item.songId)
                _operationState.value = PlaylistDetailOperationState.Success("Chanson retirée.")
                charger(playlistId, playlistName, playlistIsPublic, canManagePlaylist)
            } catch (e: Exception) {
                _operationState.value = PlaylistDetailOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun deplacerVersHaut(item: PlaylistSongUiItem) {
        if (!canManagePlaylist) return
        val current = (_uiState.value as? PlaylistDetailUiState.Success)?.songs ?: return
        val idx = current.indexOfFirst { it.playlistSongId == item.playlistSongId }
        if (idx <= 0) return
        val mutable = current.toMutableList()
        val tmp = mutable[idx - 1]
        mutable[idx - 1] = mutable[idx]
        mutable[idx] = tmp
        ordreModifie = true
        _uiState.value = PlaylistDetailUiState.Success(playlistId, playlistName, mutable)
    }

    fun deplacerVersBas(item: PlaylistSongUiItem) {
        if (!canManagePlaylist) return
        val current = (_uiState.value as? PlaylistDetailUiState.Success)?.songs ?: return
        val idx = current.indexOfFirst { it.playlistSongId == item.playlistSongId }
        if (idx < 0 || idx >= current.size - 1) return
        val mutable = current.toMutableList()
        val tmp = mutable[idx + 1]
        mutable[idx + 1] = mutable[idx]
        mutable[idx] = tmp
        ordreModifie = true
        _uiState.value = PlaylistDetailUiState.Success(playlistId, playlistName, mutable)
    }

    fun sauvegarderOrdre() {
        if (!canManagePlaylist) return
        val songs = (_uiState.value as? PlaylistDetailUiState.Success)?.songs ?: return
        val items = songs.mapIndexed { index, song ->
            ReorderSongItem(song.songId, index)
        }
        scope.launch {
            _operationState.value = PlaylistDetailOperationState.Loading
            try {
                repository.reorderSongs(playlistId, items)
                ordreModifie = false
                _operationState.value = PlaylistDetailOperationState.Success("Ordre sauvegardé.")
                charger(playlistId, playlistName, playlistIsPublic, canManagePlaylist)
            } catch (e: Exception) {
                _operationState.value = PlaylistDetailOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun resetOperationState() { _operationState.value = PlaylistDetailOperationState.Idle }
    fun dispose() = scope.cancel()
}
