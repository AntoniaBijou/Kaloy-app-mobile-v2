package com.kaloy.app.core.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineAudioUrlTest {
    @Test
    fun createsStableMedia3UrlForPositiveIds() {
        assertEquals("kaloy-offline://audio/12/34", offlineAudioUrl(12, 34))
    }

    @Test
    fun rejectsNonPositiveIds() {
        assertFailsWith<IllegalArgumentException> { offlineAudioUrl(0, 34) }
        assertFailsWith<IllegalArgumentException> { offlineAudioUrl(12, -1) }
    }
}
