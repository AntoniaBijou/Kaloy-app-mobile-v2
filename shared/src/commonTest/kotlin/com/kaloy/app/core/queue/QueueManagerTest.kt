package com.kaloy.app.core.queue

import com.kaloy.app.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueManagerTest {

    @Test
    fun playbackContextSupportsNextAndPreviousTransitions() {
        val queue = QueueManager()
        val songs = listOf(
            Song(id = 1, title = "Premier"),
            Song(id = 2, title = "Deuxième"),
            Song(id = 3, title = "Troisième")
        )

        queue.setContext(songs, songs[1])

        assertEquals(2L, queue.currentSong.value?.id)
        assertEquals(listOf(3L), queue.upNext.value.map { it.id })
        assertEquals(listOf(1L), queue.history.value.map { it.id })

        queue.playFromQueue(songs[2])

        assertEquals(3L, queue.currentSong.value?.id)
        assertEquals(listOf(2L, 1L), queue.history.value.map { it.id })
        assertEquals(emptyList(), queue.upNext.value)

        assertEquals(2L, queue.playPrevious()?.id)
        assertEquals(2L, queue.currentSong.value?.id)
        assertEquals(listOf(3L), queue.upNext.value.map { it.id })
    }

    @Test
    fun syncingAnUnrelatedSongClearsStaleUpcomingSongs() {
        val queue = QueueManager()
        val first = Song(id = 1, title = "Premier")
        queue.setContext(listOf(first, Song(id = 2, title = "Suivant")), first)

        queue.syncCurrent(Song(id = 3, title = "Indépendant"))

        assertEquals(3L, queue.currentSong.value?.id)
        assertEquals(emptyList(), queue.upNext.value)
        assertEquals(1L, queue.history.value.first().id)
    }
}
