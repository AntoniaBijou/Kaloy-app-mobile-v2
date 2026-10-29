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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.kaloy.app.ui.theme.KaloyDarkCard
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * Lecteur YouTube integre, cote iOS.
 *
 * Il n'existe pas d'equivalent iOS a la bibliotheque androidyoutubeplayer : on
 * fabrique donc la page d'integration nous-memes et on la sert dans une
 * WKWebView.
 *
 * Le point qui decide de tout est le couple loadHTMLString / baseURL. Charger
 * directement l'URL youtu.be — ce que faisait SmartVideoPlayerComposable
 * jusqu'au 28/10/2026 — revient a presenter a YouTube une page embarquee sans
 * origine declaree, et son lecteur refuse : « This video is unavailable,
 * Error code 152 ».
 *
 * En servant la page sous une origine explicite, et en annoncant la meme dans
 * playerVars.origin, les deux correspondent et le lecteur accepte. C'est la
 * meme correction que celle apportee cote Android par le passage de la
 * bibliotheque en 13.0.0, qui utilise desormais le nom de paquet de
 * l'application comme origine.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun InAppYouTubePlayer(videoUrl: String, modifier: Modifier) {
    val videoId = YouTubeEmbedUtils.extractVideoId(videoUrl)

    if (videoId == null) {
        RepliOuvrirDansYouTube(videoUrl = videoUrl, modifier = modifier)
        return
    }

    val journal = remember { JournalLecteurYouTube() }
    val configuration = remember(journal) {
        WKWebViewConfiguration().apply {
            // Sans ceci, iOS bascule la video en plein ecran des le lancement.
            allowsInlineMediaPlayback = true
            // Sans ceci, autoplay est ignore : iOS exige un geste de
            // l'utilisateur avant de lancer un media.
            mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
            userContentController.addScriptMessageHandler(
                journal,
                YouTubeEmbedUtils.CANAL_JOURNAL
            )
        }
    }

    // key plutot qu'un bloc update : recharger la page a chaque recomposition
    // relancerait la video depuis le debut. Ici la vue n'est reconstruite que
    // si l'on change de clip.
    key(videoId) {
        UIKitView(
            factory = {
                WKWebView(
                    frame = CGRectZero.readValue(),
                    configuration = configuration
                ).apply {
                    loadHTMLString(
                        string = YouTubeEmbedUtils.getIframeHtml(videoId),
                        baseURL = NSURL.URLWithString(YouTubeEmbedUtils.ORIGINE)
                    )
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Remonte dans les journaux ce que la page dit d'elle-meme.
 *
 * Sans ce canal, un refus de YouTube resterait des pixels dans une WebView,
 * sans la moindre trace cote application. C'est precisement ce qui a rendu
 * l'erreur 152 si longue a cerner cote Android : on voyait l'ecran, jamais le
 * code.
 *
 * Visible dans la console Xcode, ou par `xcrun simctl spawn booted log stream`.
 */
private class JournalLecteurYouTube : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        println("KaloyVideo : ${didReceiveScriptMessage.body}")
    }
}

/** Repli quand l'URL ne contient aucun identifiant exploitable. */
@Composable
private fun RepliOuvrirDansYouTube(videoUrl: String, modifier: Modifier) {
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
                text = "Ce lien n'a pas pu être reconnu comme une vidéo YouTube.",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = { ouvrirDansLeNavigateur(videoUrl) },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                Text("Ouvrir dans YouTube")
            }
        }
    }
}
