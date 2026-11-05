package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable

/**
 * Selection d'une photo ou d'une video, par la camera ou depuis la galerie.
 *
 * Quatre entrees a plat plutot que deux suivies d'un second choix : un appui de
 * moins, et le menu d'ajout en comptait deja deux.
 */
enum class SourceMedia {
    PHOTO_CAMERA,
    PHOTO_GALERIE,
    VIDEO_CAMERA,
    VIDEO_GALERIE,

    /**
     * Un fichier audio pris dans les documents de l'appareil, pour une
     * chanson declaree par un artiste.
     *
     * Pas une source visuelle, et c'est tout le probleme : les selecteurs
     * utilises pour les photos et les videos — PickVisualMedia sur Android,
     * UIImagePickerController sur iOS — sont visuels par conception et ne
     * proposeront jamais un MP3. Chaque plateforme ouvre donc ici un
     * selecteur de documents a la place.
     */
    AUDIO_FICHIER,

    /**
     * Un fichier audio OU video pris dans les documents, pour un karaoke.
     *
     * Une source a part et non AUDIO_FICHIER elargie : un karaoke affiche
     * les paroles, donc c'est une video dans la plupart des cas -- celui du
     * jeu de donnees du projet en est une, 13 Mo en H.264. Mais l'audio de
     * la chanson, lui, doit rester de l'audio : le formulaire a deja un
     * champ separe pour l'adresse de la video, et accepter une video dans
     * audio_url brouillerait les deux colonnes.
     *
     * Le defaut corrige ici : le selecteur du karaoke ne listait que des
     * types audio, donc un .mp4 apparaissait GRISE sur le telephone, sans
     * aucun message. Un refus muet est le pire des refus.
     */
    AUDIO_OU_VIDEO_FICHIER
}

/**
 * Ce que le selecteur renvoie : une reference, pas un contenu.
 *
 * Volontairement sans les octets. L'ancienne version portait un ByteArray, donc
 * le fichier entier en memoire : une video de cinq minutes pese 300 a 500 Mo et
 * l'application tombait en OutOfMemoryError avant meme de commencer l'envoi.
 *
 * [taille] est connue des la selection, ce qui permet de refuser un fichier
 * trop volumineux ou un espace disque insuffisant avant de rien copier.
 */
data class MediaSelectionne(
    val uri: String,
    val nomFichier: String,
    val typeMime: String,
    val taille: Long
)

/**
 * Renvoie une fonction a appeler avec la source voulue. Le rappel recoit null
 * si l'utilisateur annule, ou si la lecture des metadonnees echoue.
 */
@Composable
expect fun rememberSelecteurMedia(
    onMediaSelectionne: (MediaSelectionne?) -> Unit
): (SourceMedia) -> Unit

/**
 * Copie le media vers le stockage prive de l'application, et renvoie son chemin.
 *
 * Indispensable pour la reprise apres fermeture. Sur Android, le selecteur
 * accorde l'acces a l'URI pour la duree du processus seulement : si
 * l'application est tuee, le droit disparait et l'URI memorisee devient
 * illisible. Un chemin vers une copie privee, lui, reste valable.
 *
 * Suspend parce qu'une copie de 500 Mo prend plusieurs secondes : l'appeler
 * depuis le rappel du selecteur figerait l'interface.
 *
 * Renvoie null en cas d'echec. La copie est supprimee par [supprimerCopie]
 * quand l'envoi aboutit ou que l'utilisateur abandonne.
 */
expect suspend fun copierEnLocal(media: MediaSelectionne): String?

/**
 * Lit [longueur] octets a partir de [decalage].
 *
 * C'est ce qui remplace le chargement integral : l'envoyeur appelle cette
 * fonction une fois par morceau de 2 Mo, donc la memoire ne depasse jamais
 * 2 Mo quelle que soit la taille du fichier.
 *
 * Renvoie null si le fichier a disparu — cas reel apres un nettoyage du cache
 * par le systeme, qu'il faut distinguer d'une erreur reseau.
 */
expect suspend fun lireMorceau(chemin: String, decalage: Long, longueur: Int): ByteArray?

/** Supprime la copie privee. Sans effet si elle n'existe plus. */
expect fun supprimerCopie(chemin: String)

/**
 * Octets libres sur le stockage prive.
 *
 * Sert a refuser une copie qui remplirait le telephone, plutot que d'echouer a
 * mi-parcours en laissant un fichier partiel.
 */
expect fun espaceLibre(): Long
