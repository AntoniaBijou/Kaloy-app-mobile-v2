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
data class MergePlaylistsRequest(
    val sourceIds: List<Long>,
    val resultName: String
)
