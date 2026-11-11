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
 *
 * [cible] dit a quoi l'envoi est destine, et sert aussi de cle de rangement :
 * « evenement:12 » pour un media d'evenement, « chanson:audio »,
 * « chanson:karaoke » ou « chanson:playback » pour les trois fichiers d'une
 * chanson en cours de declaration. C'etait auparavant un identifiant
 * d'evenement, ce qui n'avait plus de sens des qu'un autre ecran s'est mis a
 * envoyer des fichiers : une session de chanson aurait ete prise pour une
 * session d'evenement a la reprise, et le fichier serait reparti au mauvais
 * endroit.
 */
@Serializable
data class EnvoiEnAttente(
    val idSession: String,
    val chemin: String,
    val nomFichier: String,
    val typeMime: String,
    val taille: Long,
    val cible: String
)

/**
 * Retient les envois en cours entre deux lancements de l'application.
 *
 * Un envoi par cible, et non un seul en tout. La version precedente n'avait
 * qu'une case : l'ecran de declaration d'une chanson demandant trois fichiers
 * — l'audio, le karaoke et le playback — choisir le deuxieme effacait le
 * premier, et un envoi de chanson effacait un envoi d'evenement en attente.
 * La cible fait desormais office de cle.
 *
 * Tout tient dans une seule entree de reglages, un dictionnaire serialise,
 * plutot qu'une entree par cible : l'interface Settings ne garantit pas de
 * pouvoir enumerer ses cles selon les plateformes, et il n'y aura jamais plus
 * d'une poignee d'envois en attente.
 *
 * S'appuie sur multiplatform-settings, deja utilise par AuthSessionManager —
 * pas de nouvelle dependance.
 */
class MemoireEnvois(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    /** L'envoi retenu pour cette cible, s'il y en a un. */
    fun lire(cible: String): EnvoiEnAttente? = carnet()[cible]

    /** Tous les envois retenus, pour un menage ou un diagnostic. */
    fun toutes(): List<EnvoiEnAttente> = carnet().values.toList()

    fun ecrire(envoi: EnvoiEnAttente) {
        ecrireCarnet(carnet() + (envoi.cible to envoi))
    }

    fun effacer(cible: String) {
        val restant = carnet() - cible
        if (restant.isEmpty()) settings.remove(CLE) else ecrireCarnet(restant)
    }

    private fun carnet(): Map<String, EnvoiEnAttente> {
        val brut = settings.getStringOrNull(CLE)
        if (brut != null) {
            return try {
                json.decodeFromString<Map<String, EnvoiEnAttente>>(brut)
            } catch (_: Exception) {
                // Format change entre deux versions de l'application : on
                // oublie plutot que de planter au demarrage de l'ecran.
                settings.remove(CLE)
                emptyMap()
            }
        }
        return reprendreAncienneCase()
    }

    /**
     * Recupere l'envoi laisse par la version a case unique.
     *
     * Sans cela, mettre l'application a jour pendant un envoi de 400 Mo ferait
     * perdre la reprise sans rien dire, et laisserait la copie sur le disque.
     * L'ancienne cle est effacee des que son contenu est repris.
     */
    private fun reprendreAncienneCase(): Map<String, EnvoiEnAttente> {
        val ancien = settings.getStringOrNull(CLE_ANCIENNE) ?: return emptyMap()
        settings.remove(CLE_ANCIENNE)
        val envoi = try {
            json.decodeFromString<EnvoiEnAttente>(ancien)
        } catch (_: Exception) {
            return emptyMap()
        }
        val carnet = mapOf(envoi.cible to envoi)
        ecrireCarnet(carnet)
        return carnet
    }

    private fun ecrireCarnet(carnet: Map<String, EnvoiEnAttente>) {
        settings.putString(CLE, json.encodeToString(carnet))
    }

    private companion object {
        const val CLE = "envois_en_attente"

        /** La cle de la version a case unique, lue une derniere fois. */
        const val CLE_ANCIENNE = "envoi_en_attente"
    }
}
