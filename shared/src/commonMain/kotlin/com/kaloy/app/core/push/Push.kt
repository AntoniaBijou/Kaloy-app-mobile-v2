package com.kaloy.app.core.push

import androidx.compose.runtime.Composable

/**
 * Acces au jeton de notification push, propre a chaque plateforme.
 *
 * Trois declarations plutot qu'une classe : les trois operations n'ont aucun
 * etat commun, et le projet utilise deja ce patron pour le selecteur d'image
 * et le lanceur de navigateur.
 */

/**
 * Jeton d'enregistrement de cet appareil, ou null.
 *
 * Suspend parce que Firebase le recupere de facon asynchrone — il peut devoir
 * contacter ses serveurs au premier appel.
 *
 * Renvoie null quand le push n'est pas disponible : pas de Google Play Services
 * sur l'appareil, ou plateforme non encore branchee. L'appelant doit traiter ce
 * cas sans eclat : une application qui refuse de fonctionner parce que le push
 * est indisponible serait pire que pas de push du tout.
 */
expect suspend fun obtenirJetonPush(): String?

/** « ANDROID » ou « IOS », tel que l'attend POST /me/devices. */
expect fun plateformePush(): String

/**
 * Demande a l'utilisateur l'autorisation d'afficher des notifications.
 *
 * Renvoie une fonction a appeler au bon moment — le meme patron que
 * rememberSingleImagePicker. Le moment choisi est la premiere ouverture du
 * centre de notifications : l'utilisateur vient d'appuyer sur « Notifications »,
 * la demande arrive donc quand elle a du sens. La poser au premier lancement de
 * l'application la ferait refuser par reflexe, et un refus est quasi definitif :
 * il faut ensuite aller dans les reglages Android pour revenir en arriere.
 *
 * [onResultat] recoit true si les notifications peuvent s'afficher. Sur les
 * versions d'Android anterieures a 13, aucune permission n'existe et le
 * resultat est true immediatement.
 */
@Composable
expect fun rememberDemandePermissionNotification(
    onResultat: (Boolean) -> Unit
): () -> Unit
