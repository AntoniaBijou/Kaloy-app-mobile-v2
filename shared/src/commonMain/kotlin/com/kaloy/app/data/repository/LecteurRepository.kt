package com.kaloy.app.data.repository

import com.kaloy.app.data.model.SongPlayerResponse

interface LecteurRepository {
    suspend fun getSongPlayerDetails(songId: Long): SongPlayerResponse
}
