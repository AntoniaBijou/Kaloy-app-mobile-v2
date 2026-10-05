package com.kaloy.app.presentation.moi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.model.ListeningHistoryItem
import com.kaloy.app.data.repository.HistoriqueEcouteRepository
import com.kaloy.app.ui.theme.KaloyCyan
import com.kaloy.app.ui.theme.KaloyDarkBg
import com.kaloy.app.ui.theme.KaloyDarkCard
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyPurpleLight
import com.kaloy.app.ui.theme.KaloyRed
import com.kaloy.app.ui.theme.KaloyTextMuted
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class HistoryPeriod(val label: String) {
    TODAY("Aujourd'hui"),
    YESTERDAY("Hier"),
    WEEK("Semaine"),
    MONTH("Mois"),
    ALL("Tout")
}

private data class ListeningEntry(
    val id: Long,
    val songId: Long,
    val title: String,
    val artist: String,
    val time: String,
    val duration: String,
    val date: String,
    val listenedSeconds: Int
)

private data class ListeningDay(
    val title: String,
    val count: Int,
    val entries: List<ListeningEntry>
)

private fun toListeningEntry(item: ListeningHistoryItem): ListeningEntry {
    val songDuration = item.songDurationSeconds ?: 0
    return ListeningEntry(
        id = item.id,
        songId = item.songId,
        title = item.songTitle.ifBlank { "Titre inconnu" },
        artist = item.artistName.ifBlank { "Artiste inconnu" },
        time = item.listenedAt.substringAfter("T", "").take(5).ifBlank { "—" },
        duration = formatTrackDuration(songDuration),
        date = item.listenedAt.substringBefore("T").ifBlank { "Date inconnue" },
        listenedSeconds = item.durationListenedSeconds ?: 0
    )
}

private fun formatTrackDuration(seconds: Int): String =
    "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"

private fun formatDayTitle(date: String, period: HistoryPeriod): String {
    if (period == HistoryPeriod.TODAY) return "Aujourd'hui"
    if (period == HistoryPeriod.YESTERDAY) return "Hier"
    val parts = date.split("-")
    if (parts.size != 3) return date
    val monthNames = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre"
    )
    val month = parts[1].toIntOrNull()?.minus(1)?.let(monthNames::getOrNull) ?: return date
    val day = parts[2].toIntOrNull()?.toString() ?: return date
    return "$day $month ${parts[0]}"
}

@Composable
fun RecentListeningHistory(
    sessionManager: AuthSessionManager,
    modifier: Modifier = Modifier,
    onAddToQueue: ((songId: Long, title: String, artistName: String) -> Unit)? = null,
    onNavigateToSong: ((songId: Long) -> Unit)? = null,
    onPlay: ((songId: Long) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf(HistoryPeriod.TODAY) }
    var openMenuFor by remember { mutableStateOf<Long?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var reloadCounter by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(0) }
    var entries by remember { mutableStateOf<List<ListeningEntry>>(emptyList()) }
    var totalElements by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val repository = remember(sessionManager) { HistoriqueEcouteRepository(sessionManager) }

    LaunchedEffect(selectedPeriod, query, page, reloadCounter) {
        delay(250)
        isLoading = true
        loadError = null
        try {
            val result = repository.getHistory(
                period = selectedPeriod.name,
                search = query.trim(),
                page = page,
                size = 100
            )
            val loadedEntries = result.entries.map(::toListeningEntry)
            entries = if (page == 0) loadedEntries else entries + loadedEntries
            totalElements = result.totalElements
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            loadError = exception.message ?: "Impossible de charger l'historique d'écoute."
        } finally {
            isLoading = false
        }
    }

    val visibleDays = entries
        .groupBy { it.date }
        .map { (date, dayEntries) ->
            ListeningDay(
                title = formatDayTitle(date, selectedPeriod),
                count = dayEntries.size,
                entries = dayEntries
            )
        }

    val listenedSeconds = entries.sumOf { it.listenedSeconds }
    val listenedDuration = "${listenedSeconds / 3600} h ${(listenedSeconds % 3600) / 60} min"
    val distinctArtists = entries.map { it.artist }.distinct().size

    LazyColumn(
        modifier = modifier.background(KaloyDarkBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HistorySearchField(
                query = query,
                onQueryChange = {
                    query = it
                    page = 0
                }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryPeriod.entries.forEach { period ->
                    HistoryPeriodChip(
                        period = period,
                        selected = selectedPeriod == period,
                        onClick = {
                            selectedPeriod = period
                            page = 0
                        }
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Résumé",
                    color = KaloyTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HistoryStatCard(
                        modifier = Modifier.weight(1f),
                        value = entries.size.toString(),
                        label = "Chansons\nécoutées",
                        icon = Icons.Default.MusicNote
                    )
                    HistoryStatCard(
                        modifier = Modifier.weight(1f),
                        value = listenedDuration,
                        label = "Temps\nd'écoute",
                        icon = Icons.Default.AccessTime
                    )
                    HistoryStatCard(
                        modifier = Modifier.weight(1f),
                        value = distinctArtists.toString(),
                        label = "Artistes\ndifférents",
                        icon = Icons.Default.QueueMusic
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KaloyPurpleLight)
                }
            }
        } else if (loadError != null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(loadError.orEmpty(), color = KaloyRed, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { reloadCounter++ },
                        colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                    ) {
                        Text("Réessayer")
                    }
                }
            }
        } else if (visibleDays.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = KaloyDarkCard
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = KaloyPurpleLight)
                        Text(
                            "Aucun résultat",
                            color = KaloyTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Aucun titre ne correspond à cette période ou à cette recherche.",
                            color = KaloyTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            visibleDays.forEach { day ->
                item(key = "header-${day.title}") {
                    HistoryDayHeader(title = day.title, count = day.count)
                }
                items(day.entries, key = { it.id }) { entry ->
                    ListeningHistoryRow(
                        entry = entry,
                        menuExpanded = openMenuFor == entry.id,
                        onMenuOpen = { openMenuFor = entry.id },
                        onMenuDismiss = { openMenuFor = null },
                        onPlay = { onPlay?.invoke(entry.songId) },
                        onAddToQueue = {
                            onAddToQueue?.invoke(entry.songId, entry.title, entry.artist)
                            openMenuFor = null
                        },
                        onNavigateToSong = {
                            onNavigateToSong?.invoke(entry.songId)
                            openMenuFor = null
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { repository.deleteEntry(entry.id) }
                                entries = entries.filterNot { it.id == entry.id }
                                openMenuFor = null
                            }
                        }
                    )
                }
            }
        }
        if (!isLoading && loadError == null && entries.size.toLong() < totalElements) {
            item {
                TextButton(
                    onClick = { page++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Charger la suite", color = KaloyPurpleLight)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showClearConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = KaloyRed)
                Spacer(Modifier.width(8.dp))
                Text("Effacer tout l'historique", color = KaloyRed)
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Toutes vos entrées d'historique seront supprimées définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val toDelete = entries.toList()
                        toDelete.forEach { entry -> runCatching { repository.deleteEntry(entry.id) } }
                        entries = emptyList()
                        showClearConfirmation = false
                    }
                }) {
                    Text("Supprimer tout", color = KaloyRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Annuler") }
            },
            containerColor = KaloyDarkCard,
            titleContentColor = KaloyTextPrimary,
            textContentColor = KaloyTextSecondary
        )
    }
}

@Composable
private fun HistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = KaloyDarkCard
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = KaloyTextMuted)
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Rechercher dans l'historique...",
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = KaloyTextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HistoryPeriodChip(
    period: HistoryPeriod,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) KaloyPurple else KaloyDarkCard
    ) {
        Text(
            text = period.label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            color = if (selected) Color.White else KaloyTextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun HistoryStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = KaloyPurpleLight, modifier = Modifier.size(18.dp))
            Text(
                value,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (value.length > 4) 16.sp else 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun HistoryDayHeader(title: String, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "•",
                color = KaloyPurpleLight,
                fontSize = 22.sp,
                lineHeight = 22.sp
            )
            Text(
                "$title — $count chansons",
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KaloyDarkElevated))
    }
}

@Composable
private fun ListeningHistoryRow(
    entry: ListeningEntry,
    menuExpanded: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onPlay: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onNavigateToSong: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(54.dp).background(KaloyDarkElevated, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = KaloyPurpleLight, modifier = Modifier.size(25.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToSong() },
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                entry.title,
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.artist,
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${entry.time} • ${entry.duration}",
                color = KaloyTextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Lire ${entry.title}", tint = KaloyPurpleLight)
        }
        Box {
            IconButton(onClick = onMenuOpen) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options pour ${entry.title}", tint = KaloyTextSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text("Ajouter à la file d'attente") },
                    leadingIcon = { Icon(Icons.Default.Add, null, tint = KaloyPurpleLight) },
                    onClick = onAddToQueue
                )
                DropdownMenuItem(
                    text = { Text("Supprimer de l'historique") },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = KaloyRed) },
                    onClick = onDelete
                )
                DropdownMenuItem(
                    text = { Text("Voir la fiche chanson") },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = KaloyCyan) },
                    onClick = onNavigateToSong
                )
                DropdownMenuItem(
                    text = { Text("Ajouter aux favoris") },
                    leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, tint = KaloyPurpleLight) },
                    onClick = onMenuDismiss
                )
                DropdownMenuItem(
                    text = { Text("Ajouter à une playlist") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = KaloyPurpleLight) },
                    onClick = onMenuDismiss
                )
            }
        }
    }
}
