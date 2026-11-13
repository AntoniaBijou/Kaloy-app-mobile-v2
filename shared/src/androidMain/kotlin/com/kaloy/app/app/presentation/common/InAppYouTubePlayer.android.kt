package com.kaloy.app.presentation.common

import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
actual fun InAppYouTubePlayer(videoUrl: String, modifier: Modifier) {
    val videoId = YouTubeEmbedUtils.extractVideoId(videoUrl)
    if (videoId == null) {
        Box(
            modifier = modifier.background(ComposeColor.Black).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lien vidéo YouTube invalide.",
                color = ComposeColor.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember(videoId) { mutableStateOf<WebView?>(null) }
    var isLoaded by remember(videoId) { mutableStateOf(false) }
    var loadError by remember(videoId) { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, videoId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy()
            }
            webView = null
        }
    }

    Box(modifier = modifier.background(ComposeColor.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                WebView(context).also { view ->
                    webView = view
                    view.setBackgroundColor(Color.BLACK)
                    view.settings.javaScriptEnabled = true
                    view.settings.domStorageEnabled = true
                    view.settings.mediaPlaybackRequiresUserGesture = false
                    view.webChromeClient = WebChromeClient()
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (!loadError) isLoaded = true
                        }
                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) loadError = true
                        }
                    }
                    view.loadUrl(
                        "https://www.youtube.com/embed/$videoId?playsinline=1&rel=0",
                        mapOf("Referer" to YouTubeEmbedUtils.ORIGINE)
                    )
                }
            },
            modifier = Modifier.matchParentSize()
        )

        when {
            loadError -> Text(
                text = "Vidéo non disponible.",
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
            !isLoaded -> CircularProgressIndicator(color = ComposeColor.White)
        }
    }
}
