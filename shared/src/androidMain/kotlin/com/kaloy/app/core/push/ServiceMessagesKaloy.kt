package com.kaloy.app.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Reception des notifications push, cote Android.
 *
 * Android affiche tout seul les messages recus quand l'application est en
 * arriere-plan ou fermee. En revanche, quand elle est au premier plan, il ne
 * fait rien et appelle onMessageReceived : c'est pourquoi cette classe doit
 * construire la notification elle-meme, sinon un push arrivant pendant qu'on
 * utilise l'application passerait inapercu.
 */
class ServiceMessagesKaloy : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val titre = message.notification?.title ?: message.data["type"] ?: "Kaloy"
        val corps = message.notification?.body ?: return

        creerCanal()

        // L'appui ouvre simplement l'application. Rediriger vers l'ecran precis
        // de la notification demanderait un routage par lien profond que
        // Voyager ne porte pas encore ici ; les donnees type et idCible sont
        // deja transmises par le serveur, donc le jour ou ce routage existera
        // il n'y aura rien a changer cote backend.
        val ouvrir = Intent(this, Class.forName("com.kaloy.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val action = PendingIntent.getActivity(
            this, 0, ouvrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titre)
            .setContentText(corps)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corps))
            .setAutoCancel(true)
            .setContentIntent(action)
            .build()

        val gestionnaire = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // L'horloge comme identifiant : deux notifications successives ne
        // doivent pas s'ecraser l'une l'autre, ce qui arriverait avec un
        // identifiant fixe.
        gestionnaire.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Le jeton a change : l'enregistrement cote serveur est perime.
     *
     * On se contente de tracer. L'application reenregistre son jeton a chaque
     * ouverture du centre de notifications, ce qui rattrape le changement a la
     * prochaine utilisation. Tenter l'appel ici demanderait un jeton
     * d'authentification valide, alors que ce service tourne aussi quand
     * personne n'est connecte — et un echec silencieux serait plus trompeur
     * que ce decalage temporaire.
     */
    override fun onNewToken(token: String) {
        Log.i("KaloyPush", "Nouveau jeton FCM, reenregistrement a la prochaine ouverture")
    }

    /**
     * Cree le canal de notification.
     *
     * Obligatoire depuis Android 8 : sans canal, notify() ne produit rien et
     * n'emet aucune erreur. Creer un canal deja existant est sans effet, donc
     * l'appel a chaque message ne coute rien.
     */
    private fun creerCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val canal = NotificationChannel(
            CANAL,
            "Notifications Kaloy",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Invitations, reponses et actualites de vos artistes"
        }
        val gestionnaire = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        gestionnaire.createNotificationChannel(canal)
    }

    companion object {
        /** Doit correspondre a la valeur declaree dans AndroidManifest.xml. */
        const val CANAL = "kaloy_notifications"
    }
}
