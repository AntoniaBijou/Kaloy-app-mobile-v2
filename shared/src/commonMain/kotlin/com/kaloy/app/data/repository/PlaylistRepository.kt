package com.kaloy.app.data.repository

import com.kaloy.app.data.dto.playlist.AddSongsRequest
import com.kaloy.app.data.dto.playlist.AddSongsResult
import com.kaloy.app.data.dto.playlist.MergePlaylistsRequest
import com.kaloy.app.data.dto.playlist.OfflinePlaylistDownload
import com.kaloy.app.data.dto.playlist.PlaylistGenerationProposal
import com.kaloy.app.data.dto.playlist.PlaylistGenerationRequest
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.model.PageResponse
import com.kaloy.app.data.dto.playlist.ReorderSongItem
import com.kaloy.app.data.dto.playlist.SaveGeneratedPlaylistRequest
import com.kaloy.app.data.dto.playlist.UpdatePlaylistRequest
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.EditorialPlaylist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.model.Playlist
import com.kaloy.app.data.model.Song

interface PlaylistRepository {
    suspend fun getMesPlaylists(): List<PlaylistUiItem>
    suspend fun setPlaylistVisibility(id: Long, visibility: String): PlaylistUiItem
    suspend fun getPublicPlaylists(search: String, page: Int, size: Int): PageResponse<PlaylistUiItem>
    suspend fun getPublicPlaylistSongs(playlistId: Long): List<PlaylistSongUiItem>
    suspend fun getPlaylistsEditoriales(): List<EditorialPlaylist>
    suspend fun getGenerationGenres(): List<Genre>
    suspend fun getGenerationArtists(): List<Artist>
    suspend fun genererPlaylists(request: PlaylistGenerationRequest): List<PlaylistGenerationProposal>
    suspend fun conserverPlaylistGeneree(request: SaveGeneratedPlaylistRequest): Playlist
    suspend fun modifierPlaylist(id: Long, request: UpdatePlaylistRequest)
    suspend fun supprimerPlaylist(id: Long)
    suspend fun fusionnerPlaylists(request: MergePlaylistsRequest): Playlist
    suspend fun telechargerPlaylist(id: Long)
    suspend fun requestOfflineDownload(id: Long): OfflinePlaylistDownload
    suspend fun getOfflineDownloads(): List<OfflinePlaylistDownload>
    suspend fun refreshOfflineDownload(id: Long): OfflinePlaylistDownload
    suspend fun deleteOfflineDownload(id: Long)
    suspend fun getSongsInPlaylist(playlistId: Long): List<PlaylistSongUiItem>
    suspend fun addSongsToPlaylist(playlistId: Long, request: AddSongsRequest): AddSongsResult
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    suspend fun reorderSongs(playlistId: Long, items: List<ReorderSongItem>)
    suspend fun getCatalogueSongs(): List<Song>
}
