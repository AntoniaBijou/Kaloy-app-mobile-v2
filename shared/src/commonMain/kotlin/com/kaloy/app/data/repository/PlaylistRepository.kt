package com.kaloy.app.data.repository

import com.kaloy.app.data.dto.playlist.CreatePlaylistRequest
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Playlist

interface PlaylistRepository {
    suspend fun getMesPlaylists(): List<PlaylistUiItem>
    suspend fun getPlaylistsEditoriales(): List<EditorialPlaylist>
    suspend fun creerPlaylist(request: CreatePlaylistRequest): Playlist
    suspend fun modifierPlaylist(id: Long, request: UpdatePlaylistRequest)
    suspend fun supprimerPlaylist(id: Long)
    suspend fun fusionnerPlaylists(request: MergePlaylistsRequest): Playlist
    suspend fun telechargerPlaylist(id: Long)
}
