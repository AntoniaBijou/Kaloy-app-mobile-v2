package com.kaloy.app.core.envoi

import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.presentation.common.MediaSelectionne
import com.kaloy.app.presentation.common.copierEnLocal
import com.kaloy.app.presentation.common.espaceLibre
import com.kaloy.app.presentation.common.lireMorceau
import com.kaloy.app.presentation.common.supprimerCopie

/**
 * Envoi d'un fichier volumineux par morceaux, avec reprise.
 *
 * Pourquoi ce detour : l'ancien envoi chargeait le fichier entier en memoire
 * puis le postait d'un bloc. Une video de cinq minutes pese 300 a 500 Mo —
 * l'application tombait en OutOfMemoryError avant d'avoir commence, et une
 * coupure a 90 % imposait de tout reprendre.
 *
 * Ici la memoire ne depasse jamais la taille d'un morceau, 2 Mo, quelle que
 * soit la taille du fichier.
 */
class EnvoyeurParMorceaux(
    private val api: KaloyApi,
    private val memoire: MemoireEnvois
) {

    /** Etapes visibles par l'ecran, pour qu'il sache quoi afficher. */
    sealed interface Etape {
        /** Copie vers le stockage prive : rapide pour une photo, plusieurs secondes pour une video. */
        data object Preparation : Etape

        data class Envoi(val octetsEnvoyes: Long, val octetsTotal: Long) : Etape {
            val pourcentage: Int
                get() = if (octetsTotal <= 0L) 0 else ((octetsEnvoyes * 100) / octetsTotal).toInt()
        }

        /** Assemblage cote serveur. Bref, mais pas instantane sur 500 Mo. */
        data object Assemblage : Etape

        data class Termine(val url: String) : Etape

        /**
         * [reprenable] distingue une coupure reseau, ou la reprise a un sens, d'un
         * refus definitif comme un format invalide. Sans cette distinction, l'ecran
         * proposerait de reprendre un envoi que le serveur refusera toujours.
         */
        data class Echec(val message: String, val reprenable: Boolean) : Etape
    }

    /**
     * Envoie un media, en reprenant une session en cours si elle existe.
     *
     * [progression] est appele a chaque etape et a chaque morceau. L'appelant
     * doit s'attendre a de nombreux appels : 250 pour une video de 500 Mo.
     */
    suspend fun envoyer(
        media: MediaSelectionne,
        idEvenement: Long,
        progression: (Etape) -> Unit
    ) {
        // Reprise : une session en attente pour ce fichier et cet evenement ?
        val enAttente = memoire.lire()
        if (enAttente != null && enAttente.idEvenement == idEvenement
            && enAttente.taille == media.taille && enAttente.nomFichier == media.nomFichier) {
            reprendre(enAttente, progression)
            return
        }

        progression(Etape.Preparation)

        // Le double de la taille : la copie, plus une marge pour le systeme.
        // Mieux vaut refuser maintenant que remplir le telephone a mi-chemin et
        // laisser un fichier partiel.
        if (espaceLibre() < media.taille * 2) {
            progression(Etape.Echec(
                "Espace insuffisant sur l'appareil pour préparer ce fichier.", false))
            return
        }

        val chemin = copierEnLocal(media)
        if (chemin == null) {
            progression(Etape.Echec("Impossible de lire le fichier choisi.", false))
            return
        }

        val etat = try {
            api.ouvrirSessionEnvoi(media.nomFichier, media.typeMime, media.taille)
        } catch (e: Exception) {
            supprimerCopie(chemin)
            // Un refus a l'ouverture est definitif : format non supporte, ou
            // fichier trop volumineux. Reprendre ne changerait rien.
            progression(Etape.Echec(e.message ?: "Envoi refusé par le serveur.", false))
            return
        }

        val session = EnvoiEnAttente(
            idSession = etat.idSession,
            chemin = chemin,
            nomFichier = media.nomFichier,
            typeMime = media.typeMime,
            taille = media.taille,
            idEvenement = idEvenement
        )
        // Memorise AVANT le premier morceau : si l'application est tuee au
        // deuxieme morceau, la reprise doit retrouver la session.
        memoire.ecrire(session)

        poursuivre(session, etat, progression)
    }

    /**
     * Reprend une session memorisee.
     *
     * Interroge d'abord le serveur : lui seul sait quels morceaux sont arrives.
     * Si la session a ete purgee — plus de 24 heures — on repart de zero plutot
     * que d'echouer.
     */
    suspend fun reprendre(session: EnvoiEnAttente, progression: (Etape) -> Unit) {
        val etat = try {
            api.etatSessionEnvoi(session.idSession)
        } catch (e: Exception) {
            progression(Etape.Echec(e.message ?: "Serveur injoignable.", true))
            return
        }

        if (etat == null) {
            memoire.effacer()
            supprimerCopie(session.chemin)
            progression(Etape.Echec(
                "L'envoi précédent a expiré. Choisissez à nouveau le fichier.", false))
            return
        }

        poursuivre(session, etat, progression)
    }

    /** Boucle d'envoi, partagee par le premier essai et la reprise. */
    private suspend fun poursuivre(
        session: EnvoiEnAttente,
        etat: com.kaloy.app.data.model.EtatSessionEnvoi,
        progression: (Etape) -> Unit
    ) {
        val dejaRecus = etat.morceauxRecus.toMutableSet()
        val tailleMorceau = etat.tailleMorceau
        var envoyes = dejaRecus.size.toLong() * tailleMorceau

        progression(Etape.Envoi(minOf(envoyes, session.taille), session.taille))

        for (indice in 0 until etat.morceauxAttendus) {
            if (indice in dejaRecus) continue

            val morceau = lireMorceau(session.chemin, indice.toLong() * tailleMorceau, tailleMorceau)
            if (morceau == null) {
                // La copie a disparu — nettoyage du cache par le systeme. Inutile
                // de proposer une reprise : il n'y a plus rien a envoyer.
                memoire.effacer()
                progression(Etape.Echec(
                    "Le fichier préparé n'est plus disponible. Recommencez l'envoi.", false))
                return
            }

            try {
                api.envoyerMorceau(session.idSession, indice, morceau)
            } catch (e: Exception) {
                // Le morceau est perdu, pas les precedents : le serveur les a
                // deja. La reprise sautera tout ce qui est arrive.
                progression(Etape.Echec(
                    e.message ?: "Connexion interrompue pendant l'envoi.", true))
                return
            }

            dejaRecus.add(indice)
            envoyes += morceau.size
            progression(Etape.Envoi(minOf(envoyes, session.taille), session.taille))
        }

        progression(Etape.Assemblage)

        val url = try {
            api.finirSessionEnvoi(session.idSession)
        } catch (e: Exception) {
            // L'assemblage peut echouer sur une taille incoherente : un morceau
            // tronque. Reprenable, le reenvoi du morceau fautif corrigera.
            progression(Etape.Echec(e.message ?: "Échec de l'assemblage.", true))
            return
        }

        // La copie et la memoire ne servent plus : les garder remplirait le
        // telephone et proposerait de reprendre un envoi deja termine.
        supprimerCopie(session.chemin)
        memoire.effacer()

        progression(Etape.Termine(url))
    }

    /**
     * Envoi retenu entre deux lancements de l'application, s'il y en a un.
     *
     * L'ecran s'en sert pour proposer « Reprendre » a l'ouverture. On ne reprend
     * jamais tout seul : un envoi de 400 Mo sur reseau mobile se decide.
     */
    fun envoiRetenu(): EnvoiEnAttente? = memoire.lire()

    /** Abandon explicite : on nettoie, sinon la copie resterait sur le disque. */
    fun abandonner() {
        memoire.lire()?.let { supprimerCopie(it.chemin) }
        memoire.effacer()
    }
}
