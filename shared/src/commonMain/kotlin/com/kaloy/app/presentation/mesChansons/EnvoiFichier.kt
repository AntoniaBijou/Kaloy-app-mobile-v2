package com.kaloy.app.presentation.mesChansons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaloy.app.core.envoi.EnvoyeurParMorceaux
import com.kaloy.app.presentation.common.MediaSelectionne
import com.kaloy.app.presentation.common.SourceMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * L'etat d'un fichier a envoyer depuis le formulaire de declaration.
 *
 * Une instance par fichier demande — l'audio de la chanson, le karaoke, le
 * playback. La version precedente tenait l'audio dans quatre variables et
 * quatre methodes du ViewModel ; les tripler aurait fait douze de chaque, pour
 * un comportement identique a chaque fois.
 *
 * Chaque instance a sa propre [cible], donc sa propre case dans la memoire
 * d'envoi : choisir le karaoke ne chasse pas l'envoi de l'audio en cours, et
 * les trois se reprennent separement apres une coupure.
 *
 * L'etat vit ici et non dans un `remember` de l'ecran, parce que Voyager
 * detruit les composables au `push` : un fichier choisi puis un aller-retour
 * vers un autre ecran aurait tout perdu, envoi compris.
 */
class EnvoiFichier(
    private val envoyeur: EnvoyeurParMorceaux,
    private val portee: CoroutineScope,
    val cible: String,
    /** Ce que l'ecran affiche sur le bouton : « Choisir un fichier audio ». */
    val libelle: String,
    /** La ligne d'explication sous le bouton. */
    val aide: String,
    /**
     * Quel selecteur ouvrir.
     *
     * Le karaoke ouvre AUDIO_OU_VIDEO_FICHIER, les deux autres
     * AUDIO_FICHIER. L'ecran lit cette valeur au lieu de la coder en dur :
     * c'est le defaut trouve a la recette du 04/11 sur un vrai telephone, ou
     * le bloc du karaoke ouvrait le selecteur audio et grisait donc les .mp4.
     */
    val source: SourceMedia = SourceMedia.AUDIO_FICHIER,
    /**
     * Pourquoi ce fichier ne convient pas, ou null s'il convient.
     *
     * Une fonction et non un drapeau : l'emplacement porte sa regle, et cette
     * classe n'a rien a savoir des types ni des tailles acceptees.
     */
    private val refus: (MediaSelectionne) -> String? = ::refusAudio
) {

    /**
     * Le chemin rendu par l'envoi, de la forme /uploads/xxx.m4a.
     *
     * Jamais saisi a la main : l'artiste choisit un fichier, il est envoye, et
     * c'est le serveur qui donne ce chemin. Un chemin et non une adresse
     * absolue, parce que le nom d'hote dependrait du reseau du moment — c'est
     * le defaut § 1.10, qui avait rendu injoignables tous les medias a chaque
     * changement d'adresse Wi-Fi.
     */
    var url by mutableStateOf("")
        private set

    /** Le fichier choisi, pour afficher son nom et sa taille avant l'envoi. */
    var fichier by mutableStateOf<MediaSelectionne?>(null)
        private set

    var etape by mutableStateOf<EnvoyeurParMorceaux.Etape?>(null)
        private set

    var erreur by mutableStateOf<String?>(null)
        private set

    /** Vrai pendant l'envoi : on n'enregistre pas une chanson a mi-envoi. */
    val enCours: Boolean
        get() = when (etape) {
            is EnvoyeurParMorceaux.Etape.Preparation,
            is EnvoyeurParMorceaux.Etape.Envoi,
            is EnvoyeurParMorceaux.Etape.Assemblage -> true
            else -> false
        }

    /**
     * Prend le fichier choisi et lance l'envoi.
     *
     * Le refus de format ou de taille tombe ICI, avant la premiere requete :
     * le serveur refuserait de son cote, mais a l'ouverture de session, et
     * l'artiste attendrait pour rien. Un fichier de 200 Mo refuse apres
     * plusieurs minutes d'envoi serait la pire des reponses.
     *
     * L'envoi part sans attendre l'enregistrement de la chanson : l'artiste
     * continue de remplir le formulaire pendant que le fichier monte. Le
     * chemin obtenu n'est rattache a rien jusqu'au POST — si l'artiste
     * abandonne, le fichier reste sur le disque du serveur sans etre
     * reference. C'est une limite connue, la meme que pour les medias
     * d'evenement.
     */
    fun choisir(media: MediaSelectionne?) {
        if (media == null) {
            // Annulation, ou lecture des metadonnees impossible.
            return
        }
        val motif = refus(media)
        if (motif != null) {
            erreur = motif
            return
        }
        erreur = null
        fichier = media
        url = ""
        lancer(media)
    }

    /** Abandonne le fichier : on nettoie, sinon la copie reste sur le telephone. */
    fun retirer() {
        envoyeur.abandonner(cible)
        fichier = null
        url = ""
        etape = null
        erreur = null
    }

    /** Rejoue l'envoi apres une coupure. Le serveur dit ou il s'etait arrete. */
    fun reprendre() {
        lancer(fichier ?: return)
    }

    private fun lancer(media: MediaSelectionne) {
        portee.launch {
            envoyeur.envoyer(media, cible) { nouvelle ->
                etape = nouvelle
                if (nouvelle is EnvoyeurParMorceaux.Etape.Termine) {
                    url = nouvelle.url
                }
            }
        }
    }
}
