package com.kaloy.app.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.repository.PlaylistRepository
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.lecteur.LecteurViewModel
import com.kaloy.app.presentation.queue.MiniPlayer
import com.kaloy.app.ui.components.EmptyState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.theme.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

data class PlaylistDetailScreen(
    val playlistId: Long,
    val playlistName: String,
    val isPublicPlaylist: Boolean = false,
    val isOwner: Boolean = true,
    val ownerName: String? = null,
    val ownerPhotoUrl: String? = null
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val repository = koinInject<PlaylistRepository>()
        val sessionManager = koinInject<AuthSessionManager>()

        val modeleVue = remember { PlaylistDetailViewModel(repository, sessionManager) }
        DisposableEffect(Unit) { onDispose { modeleVue.dispose() } }

        LaunchedEffect(playlistId, playlistName, isPublicPlaylist, isOwner) {
            modeleVue.charger(playlistId, playlistName, isPublicPlaylist, isOwner)
        }

        val fileAttente = koinInject<QueueManager>()
        val lecteur = koinInject<LecteurViewModel>()

        val uiState by modeleVue.uiState.collectAsState()
        val operationState by modeleVue.operationState.collectAsState()
        val activeDialog by modeleVue.activeDialog.collectAsState()
        val catalogueSongs by modeleVue.catalogueSongs.collectAsState()
        val chansonCourante by fileAttente.currentSong.collectAsState()
        val chansonsSuivantes by fileAttente.upNext.collectAsState()
        val historique by fileAttente.history.collectAsState()
        val lectureActive by lecteur.isPlaying.collectAsState()
        val positionCourante by lecteur.currentPositionMs.collectAsState()

        LaunchedEffect(operationState) {
            when (operationState) {
                is PlaylistDetailOperationState.NeedsRelogin -> navigateur.replaceAll(WelcomeScreen())
                is PlaylistDetailOperationState.Success, is PlaylistDetailOperationState.Error -> {
                    delay(2000)
                    modeleVue.resetOperationState()
                }
                else -> {}
            }
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = playlistName,
                            fontWeight = FontWeight.Bold,
                            color = KaloyTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigateur.pop() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Retour",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    actions = {
                        val songsForPlay = (uiState as? PlaylistDetailUiState.Success)?.songs ?: emptyList()
                        if (songsForPlay.isNotEmpty()) {
                            IconButton(onClick = {
                                val songModels = songsForPlay.map { item ->
                                    Song(
                                        id = item.songId,
                                        title = item.title,
                                        artist = Artist(stageName = item.artistName),
                                        durationSeconds = item.durationSeconds
                                    )
                                }
                                fileAttente.setContext(songModels, songModels.first())
                                navigateur.push(LecteurScreen(songModels.first().id))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Lire la playlist",
                                    tint = KaloyPurple
                                )
                            }
                        }
                        if (isOwner && modeleVue.isOrdreModifie) {
                            TextButton(onClick = { modeleVue.sauvegarderOrdre() }) {
                                Text("Sauvegarder", color = KaloyPurple, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaloyDarkSurface)
                )
            },
            bottomBar = {
                chansonCourante?.let { chanson ->
                    MiniPlayer(
                        song = chanson,
                        isPlaying = lectureActive,
                        onClick = { navigateur.push(LecteurScreen(chanson.id)) },
                        onTogglePlay = { lecteur.togglePlayPause() },
                        onPrevious = { lecteur.jouerPrecedent() },
                        onNext = { lecteur.jouerSuivant() },
                        canPrevious = historique.isNotEmpty() || positionCourante > 3_000L,
                        canNext = chansonsSuivantes.isNotEmpty()
                    )
                }
            },
            floatingActionButton = {
                if (isOwner) {
                    FloatingActionButton(
                        onClick = {
                            modeleVue.chargerCatalogue()
                            modeleVue.afficherDialog(ActiveDialogDetail.AddSongs)
                        },
                        containerColor = KaloyPurple,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter des chansons", tint = KaloyTextPrimary)
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KaloyDarkBg)
                    .padding(paddingValues)
            ) {
                when (val etat = uiState) {
                    PlaylistDetailUiState.Loading -> {
                        item { LoadingIndicator(modifier = Modifier.padding(top = 32.dp)) }
                    }
                    is PlaylistDetailUiState.Error -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(etat.message, color = KaloyRed, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { modeleVue.charger(playlistId, playlistName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                                ) { Text("Réessayer") }
                            }
                        }
                    }
                    is PlaylistDetailUiState.Success -> {
                        if (isPublicPlaylist) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!ownerPhotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ownerPhotoUrl,
                                            contentDescription = "Profil de ${ownerName ?: "l'auteur"}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Propriétaire",
                                            tint = KaloyPurpleLight,
                                            modifier = Modifier.size(42.dp).padding(8.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Playlist publique", color = KaloyPurpleLight,
                                            style = MaterialTheme.typography.labelMedium)
                                        Text(ownerName ?: "Créateur", color = KaloyTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = KaloyPurpleLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${etat.songs.size} chanson${if (etat.songs.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KaloyTextSecondary
                                )
                            }
                        }

                        if (etat.songs.isEmpty()) {
                            item {
                                EmptyState(
                                    message = if (isOwner)
                                        "Aucune chanson dans cette playlist.\nAppuyez sur + pour en ajouter."
                                    else "Cette playlist publique ne contient pas encore de chanson."
                                )
                            }
                        } else {
                            items(etat.songs, key = { it.playlistSongId }) { song ->
                                PlaylistDetailSongRow(
                                    item = song,
                                    isFirst = etat.songs.first().playlistSongId == song.playlistSongId,
                                    isLast = etat.songs.last().playlistSongId == song.playlistSongId,
                                    editable = isOwner,
                                    onMoveUp = { modeleVue.deplacerVersHaut(song) },
                                    onMoveDown = { modeleVue.deplacerVersBas(song) },
                                    onRemove = { modeleVue.afficherDialog(ActiveDialogDetail.ConfirmRemove(song)) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = KaloyDarkElevated
                                )
                            }
                        }
                    }
                }

                if (operationState is PlaylistDetailOperationState.Loading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = KaloyPurple,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                when (val op = operationState) {
                    is PlaylistDetailOperationState.Success -> item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = KaloyGreen.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = op.message,
                                color = KaloyGreen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    is PlaylistDetailOperationState.Error -> item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = KaloyRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = op.message,
                                color = KaloyRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    else -> {}
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        when (val d = activeDialog) {
            ActiveDialogDetail.AddSongs -> if (isOwner) AddSongsDialog(
                songs = catalogueSongs,
                onAjouter = { modeleVue.ajouterChansons(it) },
                onDismiss = { modeleVue.fermerDialog() }
            )
            is ActiveDialogDetail.ConfirmRemove -> if (isOwner) ConfirmRemoveSongDialog(
                titre = d.item.title,
                onConfirm = { modeleVue.retirerChanson(d.item) },
                onDismiss = { modeleVue.fermerDialog() }
            )
            ActiveDialogDetail.None -> {}
        }

    }
}

// ============================================================
// Ligne chanson dans le détail de la playlist
// ============================================================

@Composable
private fun PlaylistDetailSongRow(
    item: PlaylistSongUiItem,
    isFirst: Boolean,
    isLast: Boolean,
    editable: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        item.durationSeconds?.let { seconds ->
            val min = seconds / 60
            val sec = (seconds % 60).toString().padStart(2, '0')
            Text(
                text = "$min:$sec",
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextMuted,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (editable) {
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Monter",
                    tint = if (!isFirst) KaloyTextSecondary else KaloyDarkElevated,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Descendre",
                    tint = if (!isLast) KaloyTextSecondary else KaloyDarkElevated,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Retirer",
                    tint = KaloyRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================
// Dialog — Ajouter des chansons
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsDialog(
    songs: List<Song>,
    onAjouter: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var recherche by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(setOf<Long>()) }

    val filtrees = if (recherche.isBlank()) songs
    else songs.filter {
        it.title.contains(recherche, ignoreCase = true) ||
                (it.artist?.stageName?.contains(recherche, ignoreCase = true) == true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Ajouter des chansons", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Rechercher...", color = KaloyTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = KaloyTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaloyPurple,
                        unfocusedBorderColor = KaloyDarkElevated,
                        focusedTextColor = KaloyTextPrimary,
                        unfocusedTextColor = KaloyTextPrimary,
                        cursorColor = KaloyPurple,
                        focusedContainerColor = KaloyDarkElevated,
                        unfocusedContainerColor = KaloyDarkElevated
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KaloyPurple, modifier = Modifier.size(24.dp))
                    }
                } else if (filtrees.isEmpty()) {
                    Text(
                        "Aucun résultat pour \"$recherche\"",
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(filtrees, key = { it.id }) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selection = if (song.id in selection) selection - song.id else selection + song.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = song.id in selection,
                                    onCheckedChange = { checked ->
                                        selection = if (checked) selection + song.id else selection - song.id
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = KaloyPurple,
                                        uncheckedColor = KaloyTextMuted
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = KaloyTextPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist?.stageName ?: "",
                                        color = KaloyTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                if (selection.isNotEmpty()) {
                    Text(
                        "${selection.size} chanson(s) sélectionnée(s)",
                        color = KaloyPurpleLight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAjouter(selection.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = selection.isNotEmpty()
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ============================================================
// Dialog — Confirmation retrait d'une chanson
// ============================================================

@Composable
private fun ConfirmRemoveSongDialog(
    titre: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Retirer la chanson", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "Voulez-vous retirer « $titre » de cette playlist ?",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retirer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}
