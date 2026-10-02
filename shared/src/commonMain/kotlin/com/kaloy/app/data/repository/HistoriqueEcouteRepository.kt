package com.kaloy.app.data.repository

import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.ListeningHistoryItem

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
        return HistoryPage(data.content, data.totalElements)
    }
}

data class HistoryPage(
    val entries: List<ListeningHistoryItem>,
    val totalElements: Long
)
