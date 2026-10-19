package com.kaloy.app.core.envoi

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Envoi interrompu, retenu pour pouvoir le reprendre.
 *
 * [chemin] pointe vers la copie en stockage prive, et non vers l'URI d'origine :
 * sur Android, le selecteur n'accorde l'acces a l'URI que pour la duree du
 * processus. Apres une fermeture de l'application, l'URI serait illisible alors
 * qu'un chemin vers notre propre copie reste valable.
 *
 * [taille] et [nomFichier] servent a reconnaitre le fichier : si l'utilisateur
 * en choisit un autre, on ouvre une nouvelle session plutot que de melanger
 * deux fichiers dans la meme — le serveur detecterait l'incoherence a
 * l'assemblage, mais trop tard.
 */
@Serializable
data class EnvoiEnAttente(
    val idSession: String,
    val chemin: String,
    val nomFichier: String,
    val typeMime: String,
    val taille: Long,
    val idEvenement: Long
)

/**
 * Retient l'envoi en cours entre deux lancements de l'application.
 *
 * Un seul envoi a la fois, volontairement : deux envois simultanes de 500 Mo
 * sur un reseau mobile se nuiraient l'un a l'autre, et l'interface n'a de place
 * que pour une barre de progression.
 *
 * S'appuie sur multiplatform-settings, deja utilise par AuthSessionManager —
 * pas de nouvelle dependance.
 */
class MemoireEnvois(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    fun lire(): EnvoiEnAttente? {
        val brut = settings.getStringOrNull(CLE) ?: return null
        return try {
            json.decodeFromString<EnvoiEnAttente>(brut)
        } catch (_: Exception) {
            // Format change entre deux versions de l'application : on oublie
            // plutot que de planter au demarrage de l'ecran.
            settings.remove(CLE)
            null
        }
    }

    fun ecrire(envoi: EnvoiEnAttente) {
        settings.putString(CLE, json.encodeToString(envoi))
    }

    fun effacer() {
        settings.remove(CLE)
    }

    private companion object {
        const val CLE = "envoi_en_attente"
    }
}
