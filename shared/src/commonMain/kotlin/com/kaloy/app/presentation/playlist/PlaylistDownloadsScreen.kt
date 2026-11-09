package com.kaloy.app.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.offline.OfflineDownloadCoordinator
import com.kaloy.app.core.offline.OfflineDownloadState
import com.kaloy.app.core.offline.OfflinePlaylistCatalog
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.OfflinePlaylistDownload
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.repository.PlaylistRepository
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.ui.theme.KaloyDarkBg
import com.kaloy.app.ui.theme.KaloyDarkCard
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyDarkSurface
import com.kaloy.app.ui.theme.KaloyGreen
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyPurpleLight
import com.kaloy.app.ui.theme.KaloyTextMuted
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

class PlaylistDownloadsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<PlaylistRepository>()
        val sessionManager = koinInject<AuthSessionManager>()
        val coordinator = koinInject<OfflineDownloadCoordinator>()
        val catalog = koinInject<OfflinePlaylistCatalog>()
        val queueManager = koinInject<QueueManager>()
        val scope = rememberCoroutineScope()

        var playlists by remember { mutableStateOf<List<OfflinePlaylistDownload>>(emptyList()) }
        var localPlaylistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var showingLocalCache by remember { mutableStateOf(false) }
        var processingId by remember { mutableStateOf<Long?>(null) }
        var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
        val downloadState by coordinator.state.collectAsState()

        fun loadDownloads() {
            scope.launch {
                loading = true
                error = null
                showingLocalCache = false
                try {
                    coordinator.syncPendingDeletes()
                    playlists = repository.getOfflineDownloads()
                    catalog.syncServerState(sessionManager.getUserId(), playlists)
                    localPlaylistIds = catalog.localPlaylists(sessionManager.getUserId())
                        .map { it.playlistId }.toSet()
                } catch (exception: Exception) {
                    if (exception.message == "SESSION_EXPIRED") {
                        sessionManager.clear()
                        navigator.replaceAll(WelcomeScreen())
                    } else {
                        playlists = catalog.displayPlaylists(sessionManager.getUserId())
                        localPlaylistIds = catalog.localPlaylists(sessionManager.getUserId())
                            .map { it.playlistId }.toSet()
                        if (playlists.isNotEmpty()) {
                            showingLocalCache = true
                        } else {
                            error = UserErrorMessages.fromThrowable(exception)
                        }
                    }
                } finally {
                    loading = false
                }
            }
        }

        fun refreshDownload(playlist: OfflinePlaylistDownload) {
            activeDownloadJob = scope.launch {
                processingId = playlist.playlistId
                error = null
                try {
                    coordinator.download(playlist.playlistId, refresh = true)
                    loadDownloads()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    if (exception.message == "SESSION_EXPIRED") {
                        sessionManager.clear()
                        navigator.replaceAll(WelcomeScreen())
                    } else {
                        error = UserErrorMessages.fromThrowable(exception)
                    }
                } finally {
                    processingId = null
                }
            }
        }

        fun deleteDownload(playlist: OfflinePlaylistDownload) {
            scope.launch {
                processingId = playlist.playlistId
                error = null
                try {
                    coordinator.delete(playlist)
                    loadDownloads()
                } catch (exception: Exception) {
                    if (catalog.get(sessionManager.getUserId())
                            .none { it.playlistId == playlist.playlistId }
                    ) {
                        playlists = playlists.filterNot { it.playlistId == playlist.playlistId }
                        localPlaylistIds = localPlaylistIds - playlist.playlistId
                    }
                    if (exception.message == "SESSION_EXPIRED") {
                        sessionManager.clear()
                        navigator.replaceAll(WelcomeScreen())
                    } else {
                        error = UserErrorMessages.fromThrowable(exception)
                    }
                } finally {
                    processingId = null
                }
            }
        }

        LaunchedEffect(Unit) { loadDownloads() }

        val count = playlists.count { it.active }
        val limit = 10

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Téléchargements",
                            color = KaloyTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = KaloyTextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadDownloads() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = KaloyTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaloyDarkSurface)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KaloyDarkBg)
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "$count/$limit playlists actives",
                            color = KaloyTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (count.toFloat() / limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = KaloyPurple,
                            trackColor = KaloyDarkElevated
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Les fichiers sont chiffrés dans l’espace privé de l’application. Validité : 30 jours.",
                            color = KaloyTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    Text(
                        "PLAYLISTS DISPONIBLES HORS-LIGNE",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showingLocalCache) {
                    item {
                        Text(
                            "Mode hors-ligne : liste issue du stockage local. La reconnexion est nécessaire pour actualiser ou renouveler.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            color = KaloyPurpleLight,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (loading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = KaloyPurple)
                        }
                    }
                } else if (error != null) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(error.orEmpty(), color = KaloyTextSecondary)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { loadDownloads() },
                                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }
                } else if (playlists.isEmpty()) {
                    item {
                        Text(
                            "Aucune playlist enregistrée pour le mode hors-ligne.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = KaloyTextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(playlists, key = { it.playlistId }) { playlist ->
                        DownloadedPlaylistCard(
                            playlist = playlist,
                            localAvailable = playlist.playlistId in localPlaylistIds,
                            processing = processingId == playlist.playlistId,
                            onRefresh = { refreshDownload(playlist) },
                            onDelete = { deleteDownload(playlist) },
                            onPlay = { selected ->
                                val songs = playlist.songs.sortedBy { it.position }.map { song ->
                                    Song(
                                        id = song.songId,
                                        title = song.title,
                                        artist = Artist(stageName = song.artistName),
                                        durationSeconds = song.durationSeconds
                                    )
                                }
                                songs.firstOrNull { it.id == selected.songId }?.let { start ->
                                    queueManager.setContext(songs, start)
                                    navigator.push(
                                        LecteurScreen(
                                            songId = start.id,
                                            offlinePlaylistId = playlist.playlistId
                                        )
                                    )
                                }
                            }
                        )
                    }
                }

            }
        }

        if (downloadState is OfflineDownloadState.Downloading) {
            val progress = downloadState as OfflineDownloadState.Downloading
            val fraction = progress.totalBytes?.takeIf { it > 0L }
                ?.let { (progress.bytesTransferred.toFloat() / it).coerceIn(0f, 1f) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("Téléchargement en cours") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(progress.playlistName)
                        Text("Titre ${progress.currentSong}/${progress.totalSongs} : ${progress.songTitle}")
                        if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("Disponible hors-ligne pendant 30 jours.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDownloadJob?.cancel() }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}

@Composable
private fun DownloadedPlaylistCard(
    playlist: OfflinePlaylistDownload,
    localAvailable: Boolean,
    processing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onPlay: (com.kaloy.app.data.dto.playlist.OfflineSongDownload) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(KaloyDarkElevated, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Pochette",
                        tint = KaloyPurpleLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.playlistName,
                        color = KaloyTextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${playlist.songs.size} titre${if (playlist.songs.size == 1) "" else "s"}",
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = if (playlist.active && localAvailable) KaloyGreen else KaloyPurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                playlist.active && localAvailable -> "Active • expire le ${playlist.expiresAt.substringBefore('T')}"
                                playlist.active -> "Téléchargement local incomplet"
                                else -> "Expirée • reconnexion requise"
                            },
                            color = if (playlist.active && localAvailable) KaloyGreen else KaloyPurpleLight,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (playlist.active && localAvailable) {
                playlist.songs.sortedBy { it.position }.forEach { song ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, color = KaloyTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artistName, color = KaloyTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onPlay(song) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Lire ${song.title}", tint = KaloyPurpleLight)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !processing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (playlist.active && localAvailable) "Renouveler" else "Rafraîchir")
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !processing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Supprimer")
                }
            }
        }
    }
}
