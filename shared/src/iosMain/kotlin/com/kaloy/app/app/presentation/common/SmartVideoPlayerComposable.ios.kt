package com.kaloy.app.presentation.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * iOS : lecture de la video dans une WKWebView.
 *
 * Cette implementation manquait — la cible iOS ne compilait pas depuis l'ajout
 * du lecteur audio/video.
 *
 * Le choix de la WebView couvre les deux cas d'un seul tenant : les liens
 * YouTube (qui exigent de toute facon un rendu web) et les fichiers directs,
 * que WebKit sait lire nativement. Cote Android, ces deux cas sont separes
 * entre une WebView et ExoPlayer.
 *
 * allowsInlineMediaPlayback evite que la video ne bascule en plein ecran des
 * le lancement, comportement par defaut sur iPhone.
 *
 * Non verifiable a l'execution depuis Windows : seule la compilation l'a ete.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SmartVideoPlayerComposable(url: String, modifier: Modifier) {
    val configuration = remember {
        WKWebViewConfiguration().apply {
            allowsInlineMediaPlayback = true
        }
    }

    UIKitView(
        factory = {
            val vue = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
            NSURL.URLWithString(url)?.let { adresse ->
                vue.loadRequest(NSURLRequest.requestWithURL(adresse))
            }
            vue
        },
        modifier = modifier.fillMaxSize(),
        update = { vue ->
            NSURL.URLWithString(url)?.let { adresse ->
                vue.loadRequest(NSURLRequest.requestWithURL(adresse))
            }
        }
    )
}
