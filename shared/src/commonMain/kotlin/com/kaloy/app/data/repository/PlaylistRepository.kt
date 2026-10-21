package com.kaloy.app.data.repository

import com.kaloy.app.data.dto.playlist.AddSongsRequest
import com.kaloy.app.data.dto.playlist.AddSongsResult
import com.kaloy.app.data.dto.playlist.CreatePlaylistRequest
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.dto.playlist.ReorderSongItem
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Playlist
import com.kaloy.app.data.model.Song

interface PlaylistRepository {
    suspend fun getMesPlaylists(): List<PlaylistUiItem>
    suspend fun getPlaylistsEditoriales(): List<EditorialPlaylist>
    suspend fun creerPlaylist(request: CreatePlaylistRequest): Playlist
    suspend fun modifierPlaylist(id: Long, request: UpdatePlaylistRequest)
    suspend fun supprimerPlaylist(id: Long)
    suspend fun fusionnerPlaylists(request: MergePlaylistsRequest): Playlist
    suspend fun telechargerPlaylist(id: Long)
    suspend fun getSongsInPlaylist(playlistId: Long): List<PlaylistSongUiItem>
    suspend fun addSongsToPlaylist(playlistId: Long, request: AddSongsRequest): AddSongsResult
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    suspend fun reorderSongs(playlistId: Long, items: List<ReorderSongItem>)
    suspend fun getCatalogueSongs(): List<Song>
}
