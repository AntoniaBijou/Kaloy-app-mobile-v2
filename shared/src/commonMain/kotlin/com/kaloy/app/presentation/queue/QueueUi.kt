package com.kaloy.app.presentation.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.model.Song
import com.kaloy.app.ui.theme.*

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = KaloyDarkCard,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(song, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(song.title, color = KaloyTextPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist?.stageName ?: "", color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture", tint = Color.White)
            }
            IconButton(onClick = onQueueClick) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Up Next", tint = KaloyPurple)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextSheet(
    currentSong: Song?,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onPlay: (Song) -> Unit,
    onRemove: (Song) -> Unit,
    onClear: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = KaloyDarkBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Up Next", color = KaloyTextPrimary, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("${songs.size + if (currentSong != null) 1 else 0} morceaux",
                        color = KaloyTextSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = KaloyTextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (currentSong != null) {
                Text("EN COURS", color = KaloyPurple, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                QueueSongRow(currentSong, true, onClick = { onPlay(currentSong) })
                Spacer(Modifier.height(12.dp))
            }
            Text("À SUIVRE", color = KaloyTextMuted, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            if (songs.isEmpty()) {
                Text("Aucune chanson à suivre", color = KaloyTextMuted, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(songs, key = { it.id }) { song ->
                        QueueSongRow(song, false, onClick = { onPlay(song) }, onRemove = { onRemove(song) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Ajouter") }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Vider la file")
                }
            }
        }
    }
}

@Composable
private fun QueueSongRow(song: Song, current: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SongArtwork(song, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = KaloyTextPrimary, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist?.stageName ?: "", color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (current) Icon(Icons.Default.PlayArrow, contentDescription = "En cours", tint = KaloyPurple)
        else {
            song.durationSeconds?.let { Text("${it / 60}:${(it % 60).toString().padStart(2, '0')}", color = KaloyTextMuted) }
            if (onRemove != null) IconButton(onClick = onRemove) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = KaloyTextSecondary)
            }
        }
    }
}

@Composable
fun SongActionsMenu(
    song: Song,
    onPlayNow: () -> Unit,
    onAddNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = KaloyTextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Lire maintenant") }, onClick = { expanded = false; onPlayNow() })
            DropdownMenuItem(text = { Text("Ajouter à la suite") }, onClick = { expanded = false; onAddNext() })
            DropdownMenuItem(text = { Text("Ajouter à la file d'attente") }, onClick = { expanded = false; onAddToQueue() })
        }
    }
}

@Composable
private fun SongArtwork(song: Song, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(KaloyPurple, KaloyPink))), contentAlignment = Alignment.Center) {
        val cover = song.album?.coverUrl ?: song.artist?.photoUrl
        if (!cover.isNullOrBlank()) AsyncImage(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("♪", color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

fun QueueManager.startSong(song: Song) = playNow(song)