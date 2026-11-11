package com.kaloy.app.presentation.lecteur

import com.kaloy.app.core.offline.CachedOfflineSong
import com.kaloy.app.data.dto.playlist.OfflineSongDownload
import com.kaloy.app.data.model.SongPlayerResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LecteurSongResolverTest {

    @Test
    fun keepsFullOnlineDetailsAndUsesCachedAudioWhenAvailable() = runBlocking {
        val cached = cachedSong()
        val online = SongPlayerResponse(
            id = 42,
            title = "Song",
            artistStageName = "Artist",
            audioStreamUrl = "https://server/audio",
            videoStreamUrl = "https://server/video",
            karaokeStreamUrl = "https://server/karaoke",
            playbackStreamUrl = "https://server/playback",
            albumCoverUrl = "https://server/cover",
            lyrics = "Lyrics",
            solfaUrl = "https://server/solfa",
            authorComposer = "Composer",
            musicalArranger = "Arranger"
        )

        val result = resolveSongPlayerDetails(cached) { online }

        assertEquals("kaloy-offline://audio", result.audioStreamUrl)
        assertEquals(online.videoStreamUrl, result.videoStreamUrl)
        assertEquals(online.karaokeStreamUrl, result.karaokeStreamUrl)
        assertEquals(online.playbackStreamUrl, result.playbackStreamUrl)
        assertEquals(online.albumCoverUrl, result.albumCoverUrl)
        assertEquals(online.lyrics, result.lyrics)
        assertEquals(online.solfaUrl, result.solfaUrl)
        assertEquals(online.authorComposer, result.authorComposer)
        assertEquals(online.musicalArranger, result.musicalArranger)
        assertTrue(result.isOffline)
    }

    @Test
    fun usesCachedAudioWhenTheDetailsRequestFailsWithIo() = runBlocking {
        val result = resolveSongPlayerDetails(cachedSong()) {
            throw IOException("No network")
        }

        assertEquals(42, result.id)
        assertEquals("Song", result.title)
        assertEquals("Artist", result.artistStageName)
        assertEquals("kaloy-offline://audio", result.audioStreamUrl)
        assertNull(result.videoStreamUrl)
        assertTrue(result.isOffline)
    }

    @Test
    fun doesNotUseCachedAudioForServerOrSessionErrors() = runBlocking {
        val failure = IllegalStateException("SESSION_EXPIRED")
        var thrown: Throwable? = null

        try {
            resolveSongPlayerDetails(cachedSong()) { throw failure }
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(failure, thrown)
    }

    @Test
    fun returnsOnlineDetailsWhenThereIsNoCachedAudio() = runBlocking {
        val online = SongPlayerResponse(id = 42, audioStreamUrl = "https://server/audio")

        val result = resolveSongPlayerDetails(null) { online }

        assertEquals(online, result)
        assertFalse(result.isOffline)
    }

    private fun cachedSong() = CachedOfflineSong(
        playlistId = 7,
        playlistName = "Offline",
        song = OfflineSongDownload(
            songId = 42,
            title = "Song",
            artistName = "Artist",
            durationSeconds = 120
        ),
        playableUrl = "kaloy-offline://audio"
    )
}
