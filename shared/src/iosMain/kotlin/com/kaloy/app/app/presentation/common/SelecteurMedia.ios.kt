package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Pendant iOS du selecteur de media.
 *
 * NON IMPLEMENTE, et volontairement. Le selecteur iOS passe par PHPickerViewController
 * et UIImagePickerController, qui demandent un controleur de vue natif, des
 * autorisations declarees dans Info.plist, et un Mac avec Xcode pour verifier
 * quoi que ce soit.
 *
 * Renvoyer null partout est le comportement sain : l'ecran affiche son message
 * d'echec habituel, et le reste de l'application fonctionne. Une implementation
 * ecrite sans pouvoir etre executee aurait l'air de marcher tout en etant
 * fausse — c'est exactement ce qui s'est produit avec YoutubeWebPlayer, corrige
 * le 7 octobre alors qu'il etait mort depuis le 1er.
 *
 * Ces declarations existent pour que la cible iOS compile, rien de plus.
 */
@Composable
actual fun rememberSelecteurMedia(
    onMediaSelectionne: (MediaSelectionne?) -> Unit
): (SourceMedia) -> Unit = remember { { onMediaSelectionne(null) } }

actual suspend fun copierEnLocal(media: MediaSelectionne): String? = null

actual suspend fun lireMorceau(chemin: String, decalage: Long, longueur: Int): ByteArray? = null

actual fun supprimerCopie(chemin: String) {
    // Rien a supprimer : aucune copie n'est jamais creee sur iOS.
}

actual fun espaceLibre(): Long = 0L
