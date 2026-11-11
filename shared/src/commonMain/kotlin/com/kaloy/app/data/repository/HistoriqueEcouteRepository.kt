package com.kaloy.app.data.repository

import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.FollowArtisteInfo
import com.kaloy.app.data.model.StatsFavoris
import com.kaloy.app.data.model.ListeningHistoryItem
import com.kaloy.app.data.model.PlaylistResume

class HistoriqueEcouteRepository(
    private val sessionManager: AuthSessionManager,
    private val api: KaloyApi = KaloyApi()
) {
    suspend fun getHistory(
        period: String,
        search: String,
        page: Int = 0,
        size: Int = 50
    ): HistoryPage {
        val token = sessionManager.getToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SESSION_EXPIRED")

        val response = api.getMyListeningHistory(token, period, search, page, size)
        if (response.status !in 200..299) {
            throw IllegalStateException(response.message.ifBlank { "Impossible de récupérer l'historique." })
        }
        val data = response.data
            ?: throw IllegalStateException("La réponse du serveur ne contient pas l'historique.")
        // nombreTotal et non totalElements : le backend range les totaux dans un
        // sous-objet « page », donc le champ plat reste a zero. Avec zero, la
        // condition « entries.size < totalElements » de RecentListeningHistory
        // est toujours fausse et l'historique ne charge jamais la suite.
        return HistoryPage(data.content, data.nombreTotal)
    }

    suspend fun deleteEntry(id: Long) {
        val token = sessionManager.getToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SESSION_EXPIRED")
        api.deleteMyListeningHistory(token, id)
    }

    suspend fun getStats(): Pair<Long, Long> {
        val month = getHistory(period = "MONTH", search = "", page = 0, size = 1)
        val all   = getHistory(period = "ALL",   search = "", page = 0, size = 1)
        return month.totalElements to all.totalElements
    }

    suspend fun getFollowsCount(): Int {
        val token = sessionManager.getToken()?.takeIf { it.isNotBlank() } ?: return 0
        val response = api.getMyFollowsCount(token)
        return response.data?.toInt() ?: 0
    }

    suspend fun getFavorisCount(): Int {
        val token = sessionManager.getToken()?.takeIf { it.isNotBlank() } ?: return 0
        val response = api.getMesFavorisCount(token)
        return response.data?.toInt() ?: 0
    }

    suspend fun getFavorisStats(): StatsFavoris {
        val token = sessionManager.getToken()?.takeIf { it.isNotBlank() } ?: return StatsFavoris()
        return api.getMesStatsFavoris(token).data ?: StatsFavoris()
    }

    suspend fun getArtistesEnSuivi(): List<FollowArtisteInfo> {
        val token = sessionManager.getToken()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val response = api.getMesArtistesEnSuivi(token)
        return response.data ?: emptyList()
    }

    suspend fun getPartagesStats(): Pair<Int, Int> {
        val token = sessionManager.getToken()?.takeIf { it.isNotBlank() } ?: return 0 to 0
        val playlists = api.getMesPlaylists(token).data ?: return 0 to 0
        val publiques = playlists.filter { it.visibility.equals("PUBLIC", ignoreCase = true) }
        return publiques.size to publiques.sumOf { it.nombreTitres }
    }
}

data class HistoryPage(
    val entries: List<ListeningHistoryItem>,
    val totalElements: Long
)
