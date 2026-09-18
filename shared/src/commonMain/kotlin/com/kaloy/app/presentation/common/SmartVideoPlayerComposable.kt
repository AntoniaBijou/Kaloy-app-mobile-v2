package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun SmartVideoPlayerComposable(url: String, modifier: Modifier = Modifier)
