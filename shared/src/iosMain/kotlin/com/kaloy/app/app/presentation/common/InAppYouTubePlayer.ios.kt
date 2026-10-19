package com.kaloy.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaloy.app.ui.theme.KaloyDarkCard
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary

/**
 * Pendant iOS du lecteur YouTube integre : renvoie vers Safari.
 *
 * Cette implementation manquait depuis le commit 876a66f du 1er octobre, qui a
 * cree l'expect sans son actual. La cible iOS ne compilait donc plus pendant
 * deux semaines, alors que le todo affirmait le contraire.
 *
 * Pourquoi un renvoi vers Safari et non un vrai lecteur :
 *
 * Cote Android, le lecteur s'appuie sur la bibliotheque android-youtube-player,
 * qui n'a pas d'equivalent iOS. Il faudrait donc un WKWebView chargeant l'URL
 * d'integration, avec les memes questions d'origine et de referent qui
 * produisent l'erreur 152 cote Android — et aucun moyen de verifier quoi que ce
 * soit sans un Mac.
 *
 * Ecrire ce lecteur aurait donne du code qui a l'air de marcher sans l'avoir
 * jamais ete. C'est exactement ce qui s'est produit avec YoutubeWebPlayer :
 * modifie le 7 octobre pour corriger l'erreur 152, alors qu'il etait mort
 * depuis le 1er — la correction n'a jamais tourne.
 *
 * Ce renvoi, lui, est honnete : il fait ce qu'il annonce, et le bouton
 * « Regarder sur YouTube » est de toute facon le repli que le lecteur Android
 * propose quand l'integration echoue.
 */
@Composable
actual fun InAppYouTubePlayer(videoUrl: String, modifier: Modifier) {
    val ouvrirDansLeNavigateur = rememberBrowserLauncher()

    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Clip vidéo",
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "La lecture intégrée n'est pas disponible sur iOS. "
                        + "Le clip s'ouvre dans votre navigateur.",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = { ouvrirDansLeNavigateur(videoUrl) },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                Text("Regarder sur YouTube")
            }
        }
    }
}
