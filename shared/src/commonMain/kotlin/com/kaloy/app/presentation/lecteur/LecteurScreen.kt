package com.kaloy.app.presentation.lecteur

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.kaloy.app.core.offline.OfflineDownloadCoordinator
import com.kaloy.app.presentation.common.InAppPdfViewer
import com.kaloy.app.core.queue.ModeLecture
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.dto.playlist.AddSongsRequest
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.model.SongPlayerResponse
import com.kaloy.app.data.repository.PlaylistRepository
import com.kaloy.app.presentation.common.SmartVideoPlayerComposable
import com.kaloy.app.presentation.common.rememberBrowserLauncher
import com.kaloy.app.presentation.favoris.BoutonFavori
import com.kaloy.app.presentation.favoris.FavorisViewModel
import com.kaloy.app.presentation.queue.UpNextSheet
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class LecteurScreen(
    val songId: Long,
    val startMode: ModeEcoute = ModeEcoute.AUDIO,
    val offlinePlaylistId: Long? = null
) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val queueManager = koinInject<QueueManager>()
        val viewModel = koinInject<LecteurViewModel>()
        val favorisViewModel = koinInject<FavorisViewModel>()
        val favorisSongIds by favorisViewModel.favorisSongIds.collectAsState()

        val uiState by viewModel.uiState.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val currentPositionMs by viewModel.currentPositionMs.collectAsState()
        val durationMs by viewModel.durationMs.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()
        val modeEcoute by viewModel.modeEcoute.collectAsState()
        val upNext by viewModel.upNext.collectAsState()
        val history by viewModel.history.collectAsState()
        val currentSong by queueManager.currentSong.collectAsState()
        val modeLecture by queueManager.modeLecture.collectAsState()
        var afficherFile by remember { mutableStateOf(false) }

        LaunchedEffect(songId, startMode, offlinePlaylistId) {
            viewModel.charger(
                songId,
                startMode = startMode,
                offlinePlaylistId = offlinePlaylistId
            )
        }
        DisposableEffect(viewModel) {
            onDispose { viewModel.onPlayerScreenClosed() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            KaloyPurpleDark,
                            KaloyDarkBg,
                            KaloyDarkBg
                        )
                    )
                )
        ) {
            when (val state = uiState) {
                is LecteurUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = KaloyPurple
                    )
                }
                is LecteurUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = KaloyRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = KaloyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.charger(songId, offlinePlaylistId = offlinePlaylistId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                        ) {
                            Text("Réessayer")
                        }
                    }
                }
                is LecteurUiState.Success -> {
                    LecteurContenu(
                        song = state.song,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        errorMessage = errorMessage,
                        statusMessage = statusMessage,
                        modeEcoute = modeEcoute,
                        modeLecture = modeLecture,
                        aSuivant = upNext.isNotEmpty(),
                        aPrecedent = history.isNotEmpty() || currentPositionMs > 3_000L,
                        isFavori = favorisSongIds.contains(state.song.id),
                        onRetour = { navigateur.pop() },
                        onAfficherFile = { afficherFile = true },
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onChangerMode = { viewModel.changerMode(it) },
                        onChangerModeLecture = { queueManager.setModeLecture(it) },
                        onSuivant = { viewModel.jouerSuivant() },
                        onPrecedent = { viewModel.jouerPrecedent() },
                        onToggleFavori = { favorisViewModel.toggleFavoriChansonId(state.song.id) }
                    )
                }
            }
        }

        if (afficherFile) {
            UpNextSheet(
                currentSong = currentSong,
                songs = upNext,
                onDismiss = { afficherFile = false },
                onPlay = { song ->
                    afficherFile = false
                    viewModel.jouerDepuisFile(song)
                },
                onRemove = queueManager::remove,
                onClear = queueManager::clear
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LecteurContenu(
    song: SongPlayerResponse,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    errorMessage: String?,
    statusMessage: String,
    modeEcoute: ModeEcoute,
    modeLecture: ModeLecture,
    aSuivant: Boolean,
    aPrecedent: Boolean,
    isFavori: Boolean,
    onRetour: () -> Unit,
    onAfficherFile: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onChangerMode: (ModeEcoute) -> Unit,
    onChangerModeLecture: (ModeLecture) -> Unit,
    onSuivant: () -> Unit,
    onPrecedent: () -> Unit,
    onToggleFavori: () -> Unit
) {
    val queueManager = koinInject<QueueManager>()
    val playlistRepository = koinInject<PlaylistRepository>()
    val downloadCoordinator = koinInject<OfflineDownloadCoordinator>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var afficherMenuOptions by remember { mutableStateOf(false) }
    var afficherChoisirPlaylist by remember { mutableStateOf(false) }
    var afficherTelecharger by remember { mutableStateOf(false) }

    val iconeMode = when (modeLecture) {
        ModeLecture.BOUCLE    -> Icons.Default.Repeat
        ModeLecture.ALEATOIRE -> Icons.Default.Shuffle
        ModeLecture.UNIQUE    -> Icons.Default.RepeatOne
    }
    val prochainMode = when (modeLecture) {
        ModeLecture.BOUCLE    -> ModeLecture.ALEATOIRE
        ModeLecture.ALEATOIRE -> ModeLecture.UNIQUE
        ModeLecture.UNIQUE    -> ModeLecture.BOUCLE
    }

    val videoUrl = when (modeEcoute) {
        ModeEcoute.VIDEO   -> song.videoStreamUrl?.takeIf { it.isNotBlank() }
        ModeEcoute.KARAOKE -> song.karaokeStreamUrl?.takeIf { it.isNotBlank() }
        else               -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Barre du haut ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRetour) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Lecteur",
                    modifier = Modifier.weight(1f),
                    color = KaloyTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onAfficherFile) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = "Ouvrir la file Up Next",
                        tint = KaloyTextSecondary
                    )
                }
            }

            // ---- Zone principale : SmartVideoPlayer ou Pochette ----
            if (videoUrl != null) {
                SmartVideoPlayerComposable(
                    url = videoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(KaloyPurple, KaloyPink))
                        )
                ) {
                    if (song.albumCoverUrl != null) {
                        AsyncImage(
                            model = song.albumCoverUrl,
                            contentDescription = "Pochette ${song.albumTitle}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = "♪",
                            color = Color.White,
                            fontSize = 80.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            // ---- Titre / Artiste / Album + Année ----
            TitreArtiste(
                song = song,
                isFavori = isFavori,
                onToggleFavori = onToggleFavori
            )

            Spacer(Modifier.height(20.dp))

            // ---- Barre de progression + Contrôles (mode audio/playback uniquement) ----
            if (videoUrl == null) {
                val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
                Slider(
                    value = progress,
                    onValueChange = { onSeek((it * durationMs).toLong()) },
                    onValueChangeFinished = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = KaloyPurple,
                        activeTrackColor = KaloyPurple,
                        inactiveTrackColor = KaloyDarkElevated
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuree(currentPositionMs), color = KaloyTextMuted, fontSize = 12.sp)
                    Text(text = formatDuree(durationMs), color = KaloyTextMuted, fontSize = 12.sp)
                }

                if (errorMessage != null) {
                    Text(
                        text = "Lecture impossible: $errorMessage",
                        color = KaloyRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                Text(
                    text = statusMessage,
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Spacer(Modifier.height(20.dp))

                // ---- Contrôles de lecture ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onChangerModeLecture(prochainMode) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = iconeMode,
                            contentDescription = null,
                            tint = KaloyOrangeVif,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onPrecedent,
                        enabled = aPrecedent,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Précédent",
                            tint = KaloyTextSecondary.copy(alpha = if (aPrecedent) 1f else 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(KaloyPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onTogglePlay, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Lecture",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSuivant,
                        enabled = aSuivant,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Suivant",
                            tint = KaloyTextSecondary.copy(alpha = if (aSuivant) 1f else 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // ---- Menu ⋮ ----
                    IconButton(
                        onClick = { afficherMenuOptions = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Plus d'options",
                            tint = KaloyTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Sélecteur de mode ----
            val modesDisponibles = buildList {
                add(ModeEcoute.AUDIO to "Audio")
                if (!song.videoStreamUrl.isNullOrBlank()) add(ModeEcoute.VIDEO to "Vidéo")
                if (!song.karaokeStreamUrl.isNullOrBlank()) add(ModeEcoute.KARAOKE to "Karaoké")
                if (!song.playbackStreamUrl.isNullOrBlank()) add(ModeEcoute.PLAYBACK to "Playback")
            }

            if (modesDisponibles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KaloyDarkSurface),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    modesDisponibles.forEach { (mode, label) ->
                        val selectionne = modeEcoute == mode
                        TextButton(
                            onClick = { onChangerMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selectionne) Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(KaloyPurple.copy(alpha = 0.2f))
                                    else Modifier.padding(4.dp)
                                )
                        ) {
                            Text(
                                text = label,
                                color = if (selectionne) KaloyPurple else KaloyTextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (selectionne) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Paroles / Solfa ----
            SectionTexteLecteur(song)
            Spacer(Modifier.height(16.dp))

            // ---- Crédits ----
            if (!song.authorComposer.isNullOrBlank() || !song.musicalArranger.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(KaloyDarkSurface)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Crédits",
                        color = KaloyTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (!song.authorComposer.isNullOrBlank()) {
                        InfoLigne("Auteur/Compositeur", song.authorComposer)
                    }
                    if (!song.musicalArranger.isNullOrBlank()) {
                        InfoLigne("Arrangeur", song.musicalArranger)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Commentaires (UI statique) ----
            SectionCommentaires()
            Spacer(Modifier.height(8.dp))

            // ---- Barre d'interactions (UI-only) ----
            BarreInteractions()

            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // ---- Bottom sheets ----
    if (afficherMenuOptions) {
        MoreOptionsSheet(
            song = song,
            onDismiss = { afficherMenuOptions = false },
            onAddToQueue = {
                afficherMenuOptions = false
                queueManager.addToQueue(
                    Song(
                        id = song.id,
                        title = song.title,
                        durationSeconds = song.durationSeconds
                    )
                )
                scope.launch { snackbarHostState.showSnackbar("Ajouté à la file d'attente") }
            },
            onAjouterAPlaylist = {
                afficherMenuOptions = false
                afficherChoisirPlaylist = true
            },
            onTelecharger = {
                afficherMenuOptions = false
                afficherTelecharger = true
            }
        )
    }

    if (afficherChoisirPlaylist) {
        ChoisirPlaylistSheet(
            songId = song.id,
            pourTelecharger = false,
            playlistRepository = playlistRepository,
            downloadCoordinator = downloadCoordinator,
            onDismiss = { afficherChoisirPlaylist = false },
            onSuccess = { message ->
                afficherChoisirPlaylist = false
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }

    if (afficherTelecharger) {
        ChoisirPlaylistSheet(
            songId = song.id,
            pourTelecharger = true,
            playlistRepository = playlistRepository,
            downloadCoordinator = downloadCoordinator,
            onDismiss = { afficherTelecharger = false },
            onSuccess = { message ->
                afficherTelecharger = false
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreOptionsSheet(
    song: SongPlayerResponse,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onAjouterAPlaylist: () -> Unit,
    onTelecharger: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KaloyDarkCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = KaloyTextMuted) }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            OptionLigne(
                icone = Icons.Default.Add,
                label = "Ajouter à la file d'attente",
                tint = KaloyCyan,
                onClick = onAddToQueue
            )
            OptionLigne(
                icone = Icons.Default.PlaylistAdd,
                label = "Ajouter à une playlist",
                tint = KaloyPurple,
                onClick = onAjouterAPlaylist
            )
            if (song.isDownloadable) {
                OptionLigne(
                    icone = Icons.Default.CloudDownload,
                    label = "Télécharger",
                    tint = KaloyGreen,
                    onClick = onTelecharger
                )
            }
        }
    }
}

@Composable
private fun OptionLigne(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = KaloyTextPrimary,
            fontSize = 15.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoisirPlaylistSheet(
    songId: Long,
    pourTelecharger: Boolean,
    playlistRepository: PlaylistRepository,
    downloadCoordinator: OfflineDownloadCoordinator,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<PlaylistUiItem>>(emptyList()) }
    var chargement by remember { mutableStateOf(true) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            playlists = playlistRepository.getMesPlaylists()
        } catch (e: Exception) {
            erreur = e.message ?: "Impossible de charger les playlists"
        } finally {
            chargement = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KaloyDarkCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = KaloyTextMuted) }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = if (pourTelecharger) "Télécharger vers..." else "Ajouter à une playlist",
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = KaloyDarkElevated)

            when {
                chargement -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = KaloyPurple)
                    }
                }
                erreur != null -> {
                    Text(
                        text = erreur!!,
                        color = KaloyRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                playlists.isEmpty() -> {
                    Text(
                        text = "Aucune playlist disponible.\nCréez-en une depuis l'onglet Playlist.",
                        color = KaloyTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !enCours) {
                                        scope.launch {
                                            enCours = true
                                            try {
                                                playlistRepository.addSongsToPlaylist(
                                                    playlist.id,
                                                    AddSongsRequest(listOf(songId))
                                                )
                                                if (pourTelecharger) {
                                                    downloadCoordinator.download(playlist.id)
                                                    onSuccess("Téléchargement vers « ${playlist.name} » démarré")
                                                } else {
                                                    onSuccess("Ajouté à « ${playlist.name} »")
                                                }
                                            } catch (e: Exception) {
                                                erreur = e.message ?: "Une erreur est survenue"
                                            } finally {
                                                enCours = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = KaloyPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = KaloyTextPrimary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.nombreTitres} titre${if (playlist.nombreTitres > 1) "s" else ""}",
                                        color = KaloyTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                if (enCours) {
                                    CircularProgressIndicator(
                                        color = KaloyPurple,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = KaloyDarkElevated.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCommentaires() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KaloyDarkSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Comment,
                    contentDescription = null,
                    tint = KaloyTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Commentaires (12)",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            TextButton(
                onClick = {},
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Voir tout", color = KaloyPurple, fontSize = 12.sp)
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = KaloyPurple,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        HorizontalDivider(
            color = KaloyDarkElevated,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Commentaire exemple
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KaloyDarkElevated)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = KaloyTextMuted,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Jean K.",
                        color = KaloyTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "il y a 2h",
                        color = KaloyTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Magnifique chanson ! 🙏",
                color = KaloyTextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = KaloyRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "5", color = KaloyTextMuted, fontSize = 12.sp)
                Spacer(Modifier.width(16.dp))
                TextButton(
                    onClick = {},
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Répondre", color = KaloyTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BarreInteractions() {
    HorizontalDivider(color = KaloyDarkElevated)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = {}) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = KaloyTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Like", color = KaloyTextSecondary, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(KaloyDarkElevated)
        )
        TextButton(onClick = {}) {
            Icon(
                Icons.Default.Comment,
                contentDescription = null,
                tint = KaloyTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Commenter", color = KaloyTextSecondary, fontSize = 13.sp)
        }
    }
    HorizontalDivider(color = KaloyDarkElevated)
}

@Composable
private fun TitreArtiste(
    song: SongPlayerResponse,
    isFavori: Boolean,
    onToggleFavori: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.artistStageName,
                    color = KaloyTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.artistIsCertified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Certifié",
                        tint = KaloyGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            // Ligne : Album • Année
            val year = (song.albumReleaseDate ?: song.releaseDate)?.take(4)
            val infoLine = buildString {
                if (!song.albumTitle.isNullOrBlank()) append(song.albumTitle)
                if (!year.isNullOrBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(year)
                }
            }
            if (infoLine.isNotEmpty()) {
                Text(
                    text = infoLine,
                    color = KaloyTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        BoutonFavori(isFavori = isFavori, onToggle = onToggleFavori)
    }
}

@Composable
private fun InfoLigne(label: String, valeur: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = KaloyTextMuted, fontSize = 13.sp)
        Text(
            text = valeur,
            color = KaloyTextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun SectionTexteLecteur(song: SongPlayerResponse) {
    var choix by remember(song.id) { mutableStateOf("Paroles") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KaloyDarkSurface)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            listOf("Paroles", "Solfa").forEach { nom ->
                val actif = choix == nom
                TextButton(
                    onClick = { choix = nom },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (actif) KaloyOrange.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (actif) KaloyPink else KaloyTextMuted
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = nom,
                        fontSize = 12.sp,
                        fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        when {
            choix == "Solfa" && !song.solfaUrl.isNullOrBlank() -> {
                InAppPdfViewer(
                    url = song.solfaUrl!!,
                    modifier = Modifier.fillMaxWidth().height(500.dp)
                )
            }
            else -> {
                val texte = when {
                    choix == "Paroles" && !song.lyrics.isNullOrBlank() -> cleanLyrics(song.lyrics)
                    choix == "Solfa" && !song.solfa.isNullOrBlank() -> cleanLyrics(song.solfa)
                    else -> null
                }
                val absent = if (choix == "Paroles")
                    "Les paroles ne sont pas disponibles pour cette chanson."
                else
                    "Le solfa n'est pas disponible pour cette chanson."

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = texte ?: absent,
                        color = if (texte != null) KaloyTextPrimary else KaloyTextMuted,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontStyle = if (texte != null) FontStyle.Normal else FontStyle.Italic,
                        textAlign = if (texte != null) TextAlign.Start else TextAlign.Center,
                        modifier = if (texte != null) Modifier else Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun cleanLyrics(raw: String): String =
    raw.replace(Regex("\\[\\d{2}:\\d{2}\\.\\d{2}\\]\\s*"), "")
        .replace("\\n", "\n")
        .trim()

private fun formatDuree(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSecondes = ms / 1000
    val minutes = totalSecondes / 60
    val secondes = totalSecondes % 60
    return "$minutes:${secondes.toString().padStart(2, '0')}"
}
