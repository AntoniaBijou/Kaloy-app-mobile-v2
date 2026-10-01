package com.kaloy.app.presentation.common

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
actual fun InAppYouTubePlayer(videoUrl: String, modifier: Modifier) {
    val videoId = YouTubeEmbedUtils.extractVideoId(videoUrl)
    if (videoId == null) {
        Log.e("KaloyVideo", "Impossible d'extraire l'ID YouTube de : $videoUrl")
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var youTubePlayerView by remember { mutableStateOf<YouTubePlayerView?>(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            youTubePlayerView?.let { lifecycleOwner.lifecycle.removeObserver(it) }
        }
    }

    AndroidView(
        factory = { ctx ->
            YouTubePlayerView(ctx).also { view ->
                youTubePlayerView = view
                lifecycleOwner.lifecycle.addObserver(view)
                view.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoId, 0f)
                    }
                })
            }
        },
        modifier = modifier
    )
}
