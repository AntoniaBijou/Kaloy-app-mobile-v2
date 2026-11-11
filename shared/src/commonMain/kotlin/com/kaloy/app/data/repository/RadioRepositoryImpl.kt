package com.kaloy.app.data.repository

import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.api.ArtistSearch
import com.kaloy.app.data.api.GenreSearch
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.model.Song
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RadioRepositoryImpl(
    private val settings: Settings,
    private val sessionManager: AuthSessionManager
) : RadioRepository {

    private val api = KaloyApi()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val CLE_HISTORIQUE = "radio_historique"

    override suspend fun rechercherGenres(query: String): List<Genre> {
        if (query.isBlank()) return emptyList()
        return try {
            val res = api.rechercherGenres(GenreSearch(name = query), size = 15)
            res.data?.content ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun rechercherArtistes(query: String): List<Artist> {
        if (query.isBlank()) return emptyList()
        return try {
            val res = api.searchArtists(ArtistSearch(stageName = query), size = 15)
            res.data?.content ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getChansonsParGenre(genreId: Long): List<Song> {
        return try {
            val res = api.chansonsParGenre(genreId, size = 100)
            res.data?.content?.mapNotNull { it.song } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getChansonsParArtiste(artisteId: Long): List<Song> {
        return try {
            val res = api.getArtistSongs(artisteId, size = 100)
            res.data?.content ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getHistorique(): List<RadioHistoriqueEntree> {
        val brut = settings.getStringOrNull(CLE_HISTORIQUE) ?: return emptyList()
        return try {
            json.decodeFromString<List<RadioHistoriqueEntree>>(brut)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun sauvegarderHistorique(entree: RadioHistoriqueEntree) {
        val liste = getHistorique().toMutableList()
        liste.removeAll { it.sourceId == entree.sourceId && it.type == entree.type }
        liste.add(0, entree)
        settings.putString(CLE_HISTORIQUE, json.encodeToString(liste.take(10)))
    }
}
