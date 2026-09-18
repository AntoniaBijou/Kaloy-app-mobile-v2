package com.kaloy.app.core.queue

import com.kaloy.app.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QueueManager {
    private val _currentSong = MutableStateFlow<Song?>(null)
    private val _upNext = MutableStateFlow<List<Song>>(emptyList())

    val currentSong: StateFlow<Song?> = _currentSong
    val upNext: StateFlow<List<Song>> = _upNext

    fun playNow(song: Song) { _currentSong.value = song }

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

    fun remove(song: Song) { _upNext.value = _upNext.value.filterNot { it.id == song.id } }
    fun clear() { _upNext.value = emptyList() }
}