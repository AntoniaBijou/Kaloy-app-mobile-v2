package com.kaloy.app.presentation.chanson

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.model.SongPlayerResponse
import com.kaloy.app.data.repository.LecteurRepository
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.lecteur.ModeEcoute
import com.kaloy.app.ui.components.ErrorState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class FicheChansonViewModel(
    private val repo: LecteurRepository,
    private val songId: Long
) : ViewModel() {
    var chanson by mutableStateOf<SongPlayerResponse?>(null)
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init { charger() }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try { chanson = repo.getSongPlayerDetails(songId) }
            catch (e: Exception) { erreur = e.message ?: "Erreur de chargement" }
            finally { enChargement = false }
        }
    }
}

data class EcranDetailChansonVoyager(val idChanson: Long) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val repo = koinInject<LecteurRepository>()
        val vm = remember(idChanson) { FicheChansonViewModel(repo, idChanson) }

        when {
            vm.enChargement -> Box(
                modifier = Modifier.fillMaxSize().background(KaloyDarkBg),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }

            vm.erreur != null -> Box(
                modifier = Modifier.fillMaxSize().background(KaloyDarkBg),
                contentAlignment = Alignment.Center
            ) { ErrorState(message = vm.erreur!!, onRetry = { vm.charger() }) }

            vm.chanson != null -> FicheChansonContent(
                chanson = vm.chanson!!,
                idChanson = idChanson,
                onBack = { navigateur.pop() }
            )
        }
    }
}

@Composable
private fun FicheChansonContent(
    chanson: SongPlayerResponse,
    idChanson: Long,
    onBack: () -> Unit
) {
    val navigator = LocalNavigator.currentOrThrow
    val queueManager = koinInject<QueueManager>()
    val uriHandler = LocalUriHandler.current

    var isLiked by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Audio") }

    val tabs = remember(chanson) {
        buildList {
            add("Audio")
            if (!chanson.videoStreamUrl.isNullOrBlank()) add("Vidéo")
            if (!chanson.karaokeStreamUrl.isNullOrBlank()) add("Karaoké")
            if (!chanson.playbackStreamUrl.isNullOrBlank()) add("Playback")
            if (!chanson.solfaUrl.isNullOrBlank()) add("Solfa")
            add("Paroles")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaloyDarkBg)
            .verticalScroll(rememberScrollState())
    ) {
        // Zone 1 : Header
        FicheHeader(chanson = chanson, onBack = onBack)

        // Zone 2 : Onglets médias + contenu
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            tabs.forEach { tab ->
                TextButton(
                    onClick = { selectedTab = tab },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (selectedTab == tab) KaloyPurple.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (selectedTab == tab) KaloyPurple else KaloyTextMuted
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = tab,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        TabContent(
            tab = selectedTab,
            chanson = chanson,
            onPlay = { mode -> navigator.push(LecteurScreen(idChanson, mode)) },
            onOpenUri = { uri -> uriHandler.openUri(uri) }
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = KaloyTextMuted.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        InfoSection(chanson = chanson)

        // Zone 3 : Barre d'actions
        ActionBar(
            chanson = chanson,
            isLiked = isLiked,
            onPlay = { navigator.push(LecteurScreen(idChanson, ModeEcoute.AUDIO)) },
            onQueue = {
                queueManager.addToQueue(
                    Song(id = chanson.id, title = chanson.title, durationSeconds = chanson.durationSeconds)
                )
            },
            onLike = { isLiked = !isLiked }
        )

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun FicheHeader(chanson: SongPlayerResponse, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(KaloyPurpleDark.copy(alpha = 0.95f), KaloyDarkBg)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = KaloyTextPrimary
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(KaloyDarkCard),
                contentAlignment = Alignment.Center
            ) {
                if (!chanson.albumCoverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = chanson.albumCoverUrl,
                        contentDescription = "Pochette de ${chanson.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = KaloyTextMuted,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = chanson.title,
                style = MaterialTheme.typography.headlineSmall,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chanson.artistStageName,
                    style = MaterialTheme.typography.titleMedium,
                    color = KaloyTextSecondary
                )
                if (chanson.artistIsCertified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Certifié",
                        tint = KaloyGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val year = chanson.albumReleaseDate?.take(4) ?: chanson.releaseDate?.take(4)
            val albumLine = buildString {
                append(chanson.albumTitle ?: "Single")
                if (!year.isNullOrBlank()) append("  •  $year")
            }
            Text(
                text = albumLine,
                style = MaterialTheme.typography.labelMedium,
                color = KaloyTextMuted
            )
        }
    }
}

@Composable
private fun ActionBar(
    chanson: SongPlayerResponse,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onLike: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(label = "Écouter", onClick = onPlay) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(KaloyPurple),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Écouter",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        ActionButton(label = "File", onClick = onQueue) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = "File d'attente",
                tint = KaloyCyan,
                modifier = Modifier.size(28.dp)
            )
        }
        ActionButton(label = "Télécharger", onClick = {}) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = "Télécharger",
                tint = if (chanson.isDownloadable) KaloyTextPrimary else KaloyTextMuted,
                modifier = Modifier.size(28.dp)
            )
        }
        ActionButton(label = if (isLiked) "Liké" else "Like", onClick = onLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isLiked) KaloyRed else KaloyTextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        ActionButton(label = "Partager", onClick = {}) {
            Icon(Icons.Default.Share, contentDescription = "Partager", tint = KaloyTextSecondary, modifier = Modifier.size(28.dp))
        }
        ActionButton(label = "Suivre", onClick = {}) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Suivre", tint = KaloyTextSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { icon() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = KaloyTextMuted,
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun TabContent(
    tab: String,
    chanson: SongPlayerResponse,
    onPlay: (ModeEcoute) -> Unit,
    onOpenUri: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = KaloyDarkCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        when (tab) {
            "Audio" -> Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = KaloyPurple, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                val dur = chanson.durationSeconds
                if (dur != null) {
                    Text(
                        text = "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = { onPlay(ModeEcoute.AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lire en audio")
                }
            }

            "Vidéo" -> Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = KaloyCyan, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { chanson.videoStreamUrl?.let { onOpenUri(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ouvrir le clip", color = Color.Black)
                }
            }

            "Karaoké" -> Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = KaloyPink, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onPlay(ModeEcoute.KARAOKE) },
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPink),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lancer le Karaoké")
                }
            }

            "Playback" -> Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = KaloyGreen, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onPlay(ModeEcoute.PLAYBACK) },
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mode Playback", color = Color.Black)
                }
            }

            "Solfa" -> Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = KaloyPurpleLight, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { chanson.solfaUrl?.let { onOpenUri(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurpleLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Voir les partitions", color = KaloyDarkBg)
                }
            }

            "Paroles" -> Column(modifier = Modifier.padding(20.dp)) {
                if (!chanson.lyrics.isNullOrBlank()) {
                    Text(
                        text = chanson.lyrics,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextSecondary,
                        lineHeight = 26.sp
                    )
                } else {
                    Text(
                        text = "Les paroles ne sont pas disponibles pour cette chanson.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextMuted,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(chanson: SongPlayerResponse) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = KaloyDarkCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Informations",
                style = MaterialTheme.typography.titleMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            InfoRow("Album", chanson.albumTitle ?: "Single")

            val dur = chanson.durationSeconds
            if (dur != null) {
                InfoRow("Durée", "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}")
            }

            val date = chanson.albumReleaseDate ?: chanson.releaseDate
            if (!date.isNullOrBlank()) {
                InfoRow("Date de sortie", date.take(10))
            }

            if (chanson.language.isNotBlank()) {
                InfoRow("Langue", chanson.language.uppercase())
            }

            if (!chanson.authorComposer.isNullOrBlank()) {
                InfoRow("Auteur / Compositeur", chanson.authorComposer)
            }

            if (!chanson.musicalArranger.isNullOrBlank()) {
                InfoRow("Arrangeur musical", chanson.musicalArranger)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = KaloyTextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = KaloyTextPrimary, fontWeight = FontWeight.Medium)
    }
}
