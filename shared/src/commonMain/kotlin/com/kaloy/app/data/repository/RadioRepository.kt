package com.kaloy.app.data.repository

import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.model.Song
import kotlinx.serialization.Serializable

enum class TypeRadio { GENRE, ARTISTE }

@Serializable
data class RadioHistoriqueEntree(
    val type: TypeRadio,
    val sourceId: Long,
    val nom: String,
    val photoUrl: String? = null,
    val timestamp: Long = 0L
)

interface RadioRepository {
    suspend fun rechercherGenres(query: String): List<Genre>
    suspend fun rechercherArtistes(query: String): List<Artist>
    suspend fun getChansonsParGenre(genreId: Long): List<Song>
    suspend fun getChansonsParArtiste(artisteId: Long): List<Song>
    fun getHistorique(): List<RadioHistoriqueEntree>
    suspend fun sauvegarderHistorique(entree: RadioHistoriqueEntree)
}
