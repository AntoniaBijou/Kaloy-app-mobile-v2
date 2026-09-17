package com.kaloy.app.presentation.common

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    key(url) {
        AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.allowContentAccess = true
                settings.allowFileAccess = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 10; Mobile) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        Log.d("KaloyVideo", "WebView page started: $url")
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d("KaloyVideo", "WebView page finished: $url")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        Log.e("KaloyVideo", "WebView error ${error.errorCode}: ${error.description} for ${request.url}")
                    }
                }
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
                val separator = if (url.contains("?")) "&" else "?"
                val embedUrl = "$url${separator}playsinline=1&autoplay=1&rel=0" +
                    "&origin=https%3A%2F%2Fwww.youtube.com&enablejsapi=1" +
                    "&widget_referrer=https%3A%2F%2Fwww.youtube.com%2F"
                loadUrl(
                    embedUrl,
                    mapOf(
                        "Referer" to "https://www.youtube.com/",
                        "Origin" to "https://www.youtube.com"
                    )
                )
            }
        },
            modifier = modifier
        )
    }
}

@Composable
private fun ExoNativePlayer(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().also { p ->
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("KaloyVideo", "ExoPlayer error: ${error.errorCodeName}", error)
                }
            })
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
