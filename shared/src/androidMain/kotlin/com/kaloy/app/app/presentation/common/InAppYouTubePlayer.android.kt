package com.kaloy.app.presentation.common

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
actual fun InAppYouTubePlayer(videoUrl: String, modifier: Modifier) {
    val videoId = YouTubeEmbedUtils.extractVideoId(videoUrl)
    if (videoId == null) {
        Log.e("KaloyVideo", "Impossible d'extraire l'ID YouTube de : $videoUrl")
        Box(
            modifier = modifier.background(ComposeColor.Black).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lien de vidéo YouTube invalide.",
                color = ComposeColor.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember(videoId) { mutableStateOf<WebView?>(null) }
    var isReady by remember(videoId) { mutableStateOf(false) }
    var playbackError by remember(videoId) { mutableStateOf<String?>(null) }

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
            webView?.let { playerView ->
                playerView.stopLoading()
                playerView.loadUrl("about:blank")
                playerView.removeJavascriptInterface(JS_BRIDGE_NAME)
                playerView.destroy()
            }
            webView = null
        }
    }

    Box(modifier = modifier.background(ComposeColor.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                val origin = "https://${context.packageName.lowercase()}"
                WebView(context).also { view ->
                    webView = view
                    view.setBackgroundColor(Color.BLACK)
                    view.settings.javaScriptEnabled = true
                    view.settings.domStorageEnabled = true
                    view.settings.mediaPlaybackRequiresUserGesture = false
                    view.addJavascriptInterface(
                        YouTubeJavascriptBridge { message ->
                            Log.d("KaloyVideo", message)
                            when {
                                message == "pret" -> isReady = true
                                message.startsWith("erreur ") -> {
                                    val code = message.removePrefix("erreur ")
                                    playbackError = "Lecture YouTube impossible (code $code)."
                                    Log.e("KaloyVideo", "Lecture refusée: $message (vidéo $videoId)")
                                }
                                else -> {
                                    playbackError = "Impossible de charger le lecteur YouTube."
                                    Log.e("KaloyVideo", message)
                                }
                            }
                        },
                        JS_BRIDGE_NAME
                    )
                    view.webChromeClient = WebChromeClient()
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean =
                            request.isForMainFrame && request.url.host != context.packageName

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                playbackError = "Impossible de charger le lecteur YouTube."
                                Log.e("KaloyVideo", "Erreur WebView: ${error.description}")
                            }
                        }
                    }
                    view.loadDataWithBaseURL(
                        origin,
                        YouTubeEmbedUtils.getIframeHtml(videoId, origin),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.matchParentSize()
        )

        if (playbackError != null) {
            Text(
                text = playbackError.orEmpty(),
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        } else if (!isReady) {
            CircularProgressIndicator(color = ComposeColor.White)
        }
    }
}

private class YouTubeJavascriptBridge(
    private val onMessage: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(message: String) {
        mainHandler.post { onMessage(message) }
    }
}

private const val JS_BRIDGE_NAME = "KaloyVideo"
