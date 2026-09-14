package com.kaloy.app.presentation.lecteur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.data.model.SongPlayerResponse
import com.kaloy.app.data.repository.LecteurRepository
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

data class LecteurScreen(val songId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val repository = koinInject<LecteurRepository>()
        val audioPlayer = koinInject<AudioPlayerController>()

        val viewModel = remember { LecteurViewModel(repository, audioPlayer) }

        val uiState by viewModel.uiState.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val currentPositionMs by viewModel.currentPositionMs.collectAsState()
        val durationMs by viewModel.durationMs.collectAsState()
        val modeEcoute by viewModel.modeEcoute.collectAsState()

        DisposableEffect(songId) {
            viewModel.charger(songId)
            onDispose { viewModel.dispose() }
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
                            onClick = { viewModel.charger(songId) },
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
                        modeEcoute = modeEcoute,
                        onRetour = { navigateur.pop() },
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onChangerMode = { viewModel.changerMode(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LecteurContenu(
    song: SongPlayerResponse,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    modeEcoute: ModeEcoute,
    onRetour: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onChangerMode: (ModeEcoute) -> Unit
) {
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
                .padding(top = 8.dp, bottom = 24.dp),
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
            Spacer(modifier = Modifier.size(48.dp))
        }

        // ---- Pochette de l'album ----
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(KaloyPurple, KaloyPink))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumCoverUrl != null) {
                AsyncImage(
                    model = song.albumCoverUrl,
                    contentDescription = "Pochette ${song.albumTitle}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(text = "♪", color = Color.White, fontSize = 80.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        // ---- Titre + Artiste ----
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
                if (song.albumTitle != null) {
                    Text(
                        text = song.albumTitle,
                        color = KaloyTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Barre de progression ----
        val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
        Slider(
            value = progress,
            onValueChange = { onSeek((it * durationMs).toLong()) },
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
            Text(
                text = formatDuree(currentPositionMs),
                color = KaloyTextMuted,
                fontSize = 12.sp
            )
            Text(
                text = formatDuree(durationMs),
                color = KaloyTextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Contrôles de lecture ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onSeek(0L) },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Début",
                    tint = KaloyTextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Bouton Play/Pause principal
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
                onClick = { /* TODO: chanson suivante */ },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Suivant",
                    tint = KaloyTextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Sélecteur de mode ----
        val modesDisponibles = buildList {
            add(ModeEcoute.AUDIO to "Audio")
            if (song.karaokeStreamUrl != null) add(ModeEcoute.KARAOKE to "Karaoké")
            if (song.playbackStreamUrl != null) add(ModeEcoute.PLAYBACK to "Playback")
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
                            fontSize = 13.sp,
                            fontWeight = if (selectionne) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---- Paroles ----
        if (!song.lyrics.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KaloyDarkSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Paroles",
                    color = KaloyTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = song.lyrics,
                    color = KaloyTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Infos supplémentaires ----
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
        }

        Spacer(Modifier.height(32.dp))
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

private fun formatDuree(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSecondes = ms / 1000
    val minutes = totalSecondes / 60
    val secondes = totalSecondes % 60
    return "$minutes:${secondes.toString().padStart(2, '0')}"
}
