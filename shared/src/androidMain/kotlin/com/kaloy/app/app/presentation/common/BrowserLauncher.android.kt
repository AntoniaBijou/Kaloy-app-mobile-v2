package com.kaloy.app.presentation.common

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberBrowserLauncher(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
