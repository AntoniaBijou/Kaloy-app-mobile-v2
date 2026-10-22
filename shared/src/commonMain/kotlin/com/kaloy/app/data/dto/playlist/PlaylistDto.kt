package com.kaloy.app.data.dto.playlist

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistUiItem(
    val id: Long = 0,
    val name: String = "",
    val coverUrl: String? = null,
    val nombreTitres: Int = 0,
    val dureeSecondes: Int = 0,
    val estTelecharge: Boolean = false,
    val shareToken: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val genreIds: List<Long> = emptyList(),
    val artistIds: List<Long> = emptyList(),
    val durationMinutes: Int = 30
)

@Serializable
data class PlaylistGenerationRequest(
    val durationMinutes: Int = 30,
    val includedGenreIds: List<Long> = emptyList(),
    val excludedGenreIds: List<Long> = emptyList(),
    val includedArtistIds: List<Long> = emptyList(),
    val excludedArtistIds: List<Long> = emptyList()
)

@Serializable
data class PlaylistGenerationSong(
    val id: Long = 0,
    val title: String = "",
    val artistName: String = "",
    val durationSeconds: Int = 0,
    val coverUrl: String? = null
)

@Serializable
data class PlaylistGenerationProposal(
    val proposalNumber: Int = 0,
    val totalDurationSeconds: Int = 0,
    val songs: List<PlaylistGenerationSong> = emptyList()
)

@Serializable
data class SaveGeneratedPlaylistRequest(
    val name: String,
    val criteria: PlaylistGenerationRequest,
    val songIds: List<Long>
)

@Serializable
data class MergePlaylistsRequest(
    val sourceIds: List<Long>,
    val resultName: String
)

@Serializable
data class UpdatePlaylistRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class PlaylistSongUiItem(
    val playlistSongId: Long = 0,
    val songId: Long = 0,
    val title: String = "",
    val artistName: String = "",
    val durationSeconds: Int? = null,
    val coverUrl: String? = null,
    val position: Int = 0,
    val addedAt: String? = null
)

@Serializable
data class AddSongsRequest(val songIds: List<Long>)

@Serializable
data class ReorderSongItem(val songId: Long, val position: Int)

@Serializable
data class AddSongsResult(
    val added: List<PlaylistSongUiItem> = emptyList(),
    val alreadyPresent: List<Long> = emptyList(),
    val notFound: List<Long> = emptyList()
)
