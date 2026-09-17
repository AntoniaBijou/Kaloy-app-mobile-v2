package com.kaloy.app.presentation.common

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
actual fun SmartVideoPlayerComposable(url: String, modifier: Modifier) {
    if (url.contains("youtube.com") || url.contains("youtu.be")) {
        YoutubeWebPlayer(url = url, modifier = modifier)
    } else {
        ExoNativePlayer(url = url, modifier = modifier)
    }
}

@Composable
private fun YoutubeWebPlayer(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 10; Mobile) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    private var customView: View? = null
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        customView = view
                        (context as? Activity)?.window?.decorView?.let { decor ->
                            (decor as? ViewGroup)?.addView(
                                view,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                    override fun onHideCustomView() {
                        customView?.let { v ->
                            (context as? Activity)?.window?.decorView?.let { decor ->
                                (decor as? ViewGroup)?.removeView(v)
                            }
                        }
                        customView = null
                    }
                }
                val html = """
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <style>
                        * { margin:0; padding:0; box-sizing:border-box; }
                        body { background:#000; width:100vw; height:100vh; overflow:hidden; }
                        iframe { width:100%; height:100%; border:0; display:block; }
                    </style>
                    </head>
                    <body>
                    <iframe src="$url&playsinline=1&autoplay=1&rel=0"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                        allowfullscreen>
                    </iframe>
                    </body>
                    </html>
                """.trimIndent()
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ExoNativePlayer(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}
