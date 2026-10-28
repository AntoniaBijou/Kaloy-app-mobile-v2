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
 * iOS : aiguillage entre le lecteur YouTube et la lecture d'un fichier direct.
 *
 * Ce routage manquait, et c'etait le defaut. Toute URL partait dans une
 * WKWebView, y compris les liens youtu.be — qui tombaient alors sur l'erreur
 * 152 de YouTube, faute d'origine declaree. Android faisait cette distinction
 * depuis toujours ; iOS, non.
 *
 * Le meme test que cote Android, volontairement : deux conditions divergentes
 * produiraient un comportement different d'une plateforme a l'autre pour la
 * meme chanson.
 */
@Composable
actual fun SmartVideoPlayerComposable(url: String, modifier: Modifier) {
    if (url.contains("youtube.com") || url.contains("youtu.be")) {
        InAppYouTubePlayer(videoUrl = url, modifier = modifier)
    } else {
        LecteurFichierDirect(url = url, modifier = modifier)
    }
}

/**
 * Fichiers heberges par le backend : WebKit les lit nativement.
 *
 * Cote Android ce cas passe par ExoPlayer. Ici la WebView suffit, et evite une
 * dependance supplementaire pour un besoin que le systeme couvre deja.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun LecteurFichierDirect(url: String, modifier: Modifier) {
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
