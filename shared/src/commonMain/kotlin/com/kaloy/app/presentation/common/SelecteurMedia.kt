package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable

/**
 * Selection d'une photo, par la camera ou depuis la galerie (Sprint 5).
 *
 * Contrairement a [rememberSingleImagePicker], qui ne renvoie qu'une URI locale
 * inexploitable par le serveur, celui-ci renvoie les OCTETS du fichier : c'est
 * ce qu'il faut pour l'envoyer au backend.
 */
enum class SourceMedia {
    CAMERA,
    GALERIE
}

/**
 * Photo choisie, prete a etre televersee.
 *
 * @param octets contenu brut du fichier
 * @param nomFichier nom suggere, a titre indicatif
 * @param typeMime type reel du contenu ; le backend n'accepte que
 *        image/jpeg, image/png et image/webp
 */
data class MediaChoisi(
    val octets: ByteArray,
    val nomFichier: String,
    val typeMime: String
) {
    // equals/hashCode redefinis : ByteArray se compare par reference par defaut,
    // ce qui rendrait deux selections identiques toujours differentes.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaChoisi) return false
        return nomFichier == other.nomFichier &&
            typeMime == other.typeMime &&
            octets.contentEquals(other.octets)
    }

    override fun hashCode(): Int {
        var resultat = octets.contentHashCode()
        resultat = 31 * resultat + nomFichier.hashCode()
        resultat = 31 * resultat + typeMime.hashCode()
        return resultat
    }
}

/**
 * Renvoie une fonction a appeler avec la source voulue. Le rappel recoit null
 * si l'utilisateur annule, ou si la lecture du fichier echoue.
 */
@Composable
expect fun rememberSelecteurMedia(
    onMediaChoisi: (MediaChoisi?) -> Unit
): (SourceMedia) -> Unit
