package com.kaloy.app.data.repository

import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.core.util.maintenantIso
import com.kaloy.app.data.api.KaloyApi
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * Historique des recherches récentes — stratégie hybride.
 *
 * - Local (multiplatform-settings) : source de vérité pour l'affichage.
 *   Immédiat, hors-ligne, et fonctionne en mode visiteur.
 * - Backend (/searchhistorys) : synchronisation best-effort quand l'utilisateur
 *   est connecté, pour retrouver ses recherches sur un autre appareil.
 *
 * Toute erreur réseau est absorbée : l'historique local ne doit jamais
 * empêcher une recherche d'aboutir.
 */
class HistoriqueRechercheRepository(
    private val settings: Settings,
    private val gestionSession: AuthSessionManager,
    private val api: KaloyApi = KaloyApi()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Recherches récentes, de la plus récente à la plus ancienne. */
    fun lireLocal(): List<String> {
        val brut = settings.getStringOrNull(CLE_HISTORIQUE) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(brut)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Enregistre une recherche validée. Déduplique sans tenir compte de la casse
     * et remonte l'entrée en tête si elle existait déjà.
     */
    suspend fun enregistrer(texte: String) {
        val nettoye = texte.trim()
        if (nettoye.isBlank()) return

        val actuel = lireLocal()
        val fusionne = (listOf(nettoye) + actuel.filterNot { it.equals(nettoye, ignoreCase = true) })
            .take(MAX_ENTREES)
        ecrireLocal(fusionne)

        // Synchronisation backend : best-effort, jamais bloquante.
        if (gestionSession.isLoggedIn()) {
            val idUtilisateur = gestionSession.getUserId()
            if (idUtilisateur > 0) {
                try {
                    api.creerHistoriqueRecherche(idUtilisateur, nettoye, maintenantIso())
                } catch (_: Exception) {
                    // Hors-ligne ou backend indisponible : le local fait foi.
                }
            }
        }
    }

    /**
     * Récupère l'historique distant et le fusionne dans le local.
     * Appelé à l'ouverture de l'écran de recherche pour un utilisateur connecté.
     */
    suspend fun synchroniserDepuisBackend(): List<String> {
        if (!gestionSession.isLoggedIn()) return lireLocal()
        val idUtilisateur = gestionSession.getUserId()
        if (idUtilisateur <= 0) return lireLocal()

        return try {
            val reponse = api.getHistoriqueRecherche(idUtilisateur, size = MAX_ENTREES)
            val distant = reponse.data?.content?.map { it.queryText }?.filter { it.isNotBlank() } ?: emptyList()

            // Le local passe en premier (plus récent côté appareil), puis le distant.
            val fusionne = mutableListOf<String>()
            for (entree in lireLocal() + distant) {
                if (fusionne.none { it.equals(entree, ignoreCase = true) }) fusionne.add(entree)
                if (fusionne.size >= MAX_ENTREES) break
            }
            ecrireLocal(fusionne)
            fusionne
        } catch (_: Exception) {
            lireLocal()
        }
    }

    fun supprimer(texte: String): List<String> {
        val restant = lireLocal().filterNot { it.equals(texte, ignoreCase = true) }
        ecrireLocal(restant)
        return restant
    }

    fun vider() {
        settings.remove(CLE_HISTORIQUE)
    }

    private fun ecrireLocal(entrees: List<String>) {
        settings.putString(CLE_HISTORIQUE, json.encodeToString(entrees))
    }

    companion object {
        private const val CLE_HISTORIQUE = "historique_recherches"
        const val MAX_ENTREES = 10
    }
}
