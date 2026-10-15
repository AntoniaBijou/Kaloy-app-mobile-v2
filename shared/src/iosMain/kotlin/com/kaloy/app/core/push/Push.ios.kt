package com.kaloy.app.core.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Pendant iOS du push.
 *
 * NON IMPLEMENTE, et volontairement. Le push iOS passe par APNs : il demande
 * une cle APNs creee sur le portail developpeur Apple et deposee dans Firebase,
 * une application iOS declaree dans le meme projet Firebase, son
 * GoogleService-Info.plist, et un Mac avec Xcode pour verifier quoi que ce soit.
 *
 * Renvoyer null ici est le comportement sain : le reste de l'application
 * fonctionne, simplement sans notification systeme. Une implementation ecrite
 * sans pouvoir etre executee aurait l'air de marcher tout en etant fausse, ce
 * qui est pire — c'est exactement ce qui s'est produit avec les lecteurs audio
 * iOS, ecrits puis jamais lances.
 */
actual suspend fun obtenirJetonPush(): String? = null

actual fun plateformePush(): String = "IOS"

/**
 * Aucune demande n'est faite : sans jeton APNs, l'autorisation ne servirait a
 * rien. On renvoie false, ce qui laisse l'application se replier sur les
 * notifications visibles dans l'ecran.
 */
@Composable
actual fun rememberDemandePermissionNotification(
    onResultat: (Boolean) -> Unit
): () -> Unit = remember { { onResultat(false) } }
