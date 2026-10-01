package com.kaloy.app.core.queue

import com.kaloy.app.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QueueManager {
    private val _currentSong = MutableStateFlow<Song?>(null)
    private val _upNext = MutableStateFlow<List<Song>>(emptyList())
    private val _history = MutableStateFlow<List<Song>>(emptyList())

    val currentSong: StateFlow<Song?> = _currentSong
    val upNext: StateFlow<List<Song>> = _upNext
    val history: StateFlow<List<Song>> = _history

    fun playNow(song: Song) {
        _currentSong.value?.let { prev ->
            _history.value = listOf(prev) + _history.value.take(49)
        }
        _currentSong.value = song
    }

    // Définit une liste entière comme contexte de lecture.
    // Les chansons avant startSong deviennent l'historique, celles après deviennent upNext.
    fun setContext(songs: List<Song>, startSong: Song) {
        val startIndex = songs.indexOfFirst { it.id == startSong.id }
        if (startIndex < 0) {
            playNow(startSong)
            return
        }
        _currentSong.value = startSong
        _upNext.value = songs.drop(startIndex + 1)
        _history.value = songs.take(startIndex).reversed().take(49)
    }

    fun addNext(song: Song) {
        _upNext.value = listOf(song) + _upNext.value.filterNot { it.id == song.id }
    }

    fun addToQueue(song: Song) {
        if (_currentSong.value?.id == song.id || _upNext.value.any { it.id == song.id }) return
        _upNext.value = _upNext.value + song
    }

    fun playFromQueue(song: Song) {
        _upNext.value = _upNext.value.filterNot { it.id == song.id }
        playNow(song)
    }

    // Retourne la chanson précédente et met la chanson actuelle en tête de file
    fun playPrevious(): Song? {
        val prevSong = _history.value.firstOrNull() ?: return null
        _history.value = _history.value.drop(1)
        _currentSong.value?.let { curr ->
            _upNext.value = listOf(curr) + _upNext.value.filterNot { it.id == curr.id }
        }
        _currentSong.value = prevSong
        return prevSong
    }

    fun remove(song: Song) { _upNext.value = _upNext.value.filterNot { it.id == song.id } }
    fun clear() { _upNext.value = emptyList() }
}