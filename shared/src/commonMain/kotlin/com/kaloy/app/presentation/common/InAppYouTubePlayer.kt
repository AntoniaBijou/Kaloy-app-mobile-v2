package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun InAppYouTubePlayer(videoUrl: String, modifier: Modifier = Modifier)
