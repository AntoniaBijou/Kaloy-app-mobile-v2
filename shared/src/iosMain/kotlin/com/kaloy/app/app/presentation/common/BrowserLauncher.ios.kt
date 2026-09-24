package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS : ouverture d'une adresse dans Safari.
 *
 * Cette implementation manquait — la cible iOS ne compilait pas depuis l'ajout
 * du lecteur audio/video.
 *
 * Non verifiable a l'execution depuis Windows : seule la compilation l'a ete.
 */
@Composable
actual fun rememberBrowserLauncher(): (String) -> Unit = remember {
    { adresse ->
        val url = NSURL.URLWithString(adresse)
        if (url != null) {
            UIApplication.sharedApplication.openURL(url)
        }
    }
}
