package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun InAppPdfViewer(url: String, modifier: Modifier) {
    UIKitView(
        factory = {
            WKWebView(frame = CGRectZero.readValue()).apply {
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest(URL = it)) }
            }
        },
        modifier = modifier
    )
}
