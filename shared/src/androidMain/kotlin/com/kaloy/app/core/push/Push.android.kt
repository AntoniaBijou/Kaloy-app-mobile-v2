package com.kaloy.app.core.push

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Recupere le jeton FCM de cette installation.
 *
 * Le Task de Firebase est enveloppe a la main plutot qu'avec la bibliotheque
 * kotlinx-coroutines-play-services : cela economise une dependance pour un seul
 * appel.
 *
 * Un echec renvoie null au lieu de propager : il arrive sur un appareil sans
 * Google Play Services, et l'application doit continuer de fonctionner sans
 * push.
 */
actual suspend fun obtenirJetonPush(): String? = suspendCancellableCoroutine { suite ->
    FirebaseMessaging.getInstance().token
        .addOnCompleteListener { tache ->
            if (tache.isSuccessful) {
                suite.resume(tache.result)
            } else {
                Log.w("KaloyPush", "Jeton FCM indisponible", tache.exception)
                suite.resume(null)
            }
        }
}

actual fun plateformePush(): String = "ANDROID"

/**
 * Demande POST_NOTIFICATIONS, obligatoire depuis Android 13 (API 33).
 *
 * Avant cette version, la permission n'existe pas : le contrat
 * RequestPermission la refuserait, d'ou le court-circuit sur Build.VERSION.
 * Sans ce test, l'application se croirait privee de notifications sur tous les
 * telephones plus anciens — et le projet descend jusqu'a l'API 24.
 */
@Composable
actual fun rememberDemandePermissionNotification(
    onResultat: (Boolean) -> Unit
): () -> Unit {
    val lanceur = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { accorde -> onResultat(accorde) }
    )

    return remember(lanceur) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lanceur.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResultat(true)
            }
        }
    }
}
