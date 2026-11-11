package com.kaloy.app.presentation.common

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class YouTubeEmbedUtilsTest {

    @Test
    fun iframeUsesSuppliedOriginAndAndroidBridge() {
        val html = YouTubeEmbedUtils.getIframeHtml(
            videoId = "abcdefghijk",
            origin = "https://com.kaloy.app"
        )

        assertContains(html, "origin: 'https://com.kaloy.app'")
        assertContains(html, "window.KaloyVideo.postMessage(texte)")
        assertContains(html, "enablejsapi: 1")
    }

    @Test
    fun extractsIdsFromSupportedYoutubeLinks() {
        assertEquals(
            "abcdefghijk",
            YouTubeEmbedUtils.extractVideoId("https://www.youtube.com/watch?v=abcdefghijk")
        )
        assertEquals(
            "abcdefghijk",
            YouTubeEmbedUtils.extractVideoId("https://youtu.be/abcdefghijk")
        )
        assertEquals(
            "abcdefghijk",
            YouTubeEmbedUtils.extractVideoId("https://www.youtube.com/shorts/abcdefghijk")
        )
    }
}
