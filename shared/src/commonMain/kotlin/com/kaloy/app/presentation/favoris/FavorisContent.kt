package com.kaloy.app.presentation.favoris

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaloy.app.data.model.FavorisAlbum
import com.kaloy.app.data.model.FavorisArtiste
import com.kaloy.app.data.model.FavorisSong
import com.kaloy.app.data.model.Song
import com.kaloy.app.ui.theme.*

private enum class FavoriTab(val label: String) {
    CHANSONS("Chansons"),
    ALBUMS("Albums"),
    ARTISTES("Artistes")
}

@Composable
fun FavorisContent(
    viewModel: FavorisViewModel,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var selectedTab by remember { mutableStateOf(FavoriTab.CHANSONS) }

    LaunchedEffect(Unit) { viewModel.charger() }

    DisposableEffect(Unit) { onDispose { viewModel.dispose() } }

    errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            // L'erreur est affichée via le Snackbar du composant parent
            viewModel.clearError()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(KaloyDarkBg)) {
        when (val state = uiState) {
            is FavorisUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KaloyPurple)
                }
            }

            is FavorisUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(state.message, color = KaloyRed, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { viewModel.charger() },
                            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                        ) {
                            Text("Réessayer")
                        }
                    }
                }
            }

            is FavorisUiState.Success -> {
                // Boutons Tout lire / Aléatoire (uniquement si des chansons existent)
                if (state.chansons.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Tout lire", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onShuffle,
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaloyPurpleLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                tint = KaloyPurpleLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Aléatoire",
                                color = KaloyPurpleLight,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // TabRow
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = KaloyDarkSurface,
                    contentColor = KaloyPurpleLight,
                    edgePadding = 16.dp,
                    divider = { HorizontalDivider(color = KaloyDarkElevated) }
                ) {
                    FavoriTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selectedContentColor = KaloyPurpleLight,
                            unselectedContentColor = KaloyTextMuted
                        )
                    }
                }

                when (selectedTab) {
                    FavoriTab.CHANSONS -> ChansonsTab(
                        chansons = state.chansons,
                        onPlay = onPlaySong,
                        onToggle = { song -> viewModel.toggleFavoriChanson(song) }
                    )
                    FavoriTab.ALBUMS -> AlbumsTab(
                        albums = state.albums,
                        onToggle = { album -> viewModel.toggleFavoriAlbum(album) }
                    )
                    FavoriTab.ARTISTES -> ArtistesTab(
                        artistes = state.artistes,
                        onToggle = { artiste -> viewModel.toggleFavoriArtiste(artiste) }
                    )
                }
            }
        }
    }
}

// ── Onglet Chansons ───────────────────────────────────────────────────────────

@Composable
private fun ChansonsTab(
    chansons: List<FavorisSong>,
    onPlay: (Song) -> Unit,
    onToggle: (Song) -> Unit
) {
    if (chansons.isEmpty()) {
        EmptyFavorisState("Vous n'avez pas encore de chansons favorites.\nExplorez la musique et ajoutez ce que vous aimez !")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(chansons, key = { it.likeId }) { favori ->
            SongRow(
                favori = favori,
                onPlay = { onPlay(favori.song) },
                onToggle = { onToggle(favori.song) }
            )
        }
    }
}

@Composable
private fun SongRow(
    favori: FavorisSong,
    onPlay: () -> Unit,
    onToggle: () -> Unit
) {
    val song = favori.song
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(KaloyDarkCard),
            contentAlignment = Alignment.Center
        ) {
            if (song.album?.coverUrl != null) {
                AsyncImage(
                    model = song.album.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = KaloyTextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist?.stageName ?: "Artiste inconnu",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        song.durationSeconds?.let { dur ->
            Text(
                text = "%d:%02d".format(dur / 60, dur % 60),
                color = KaloyTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        BoutonFavori(isFavori = true, onToggle = onToggle)
    }
}

// ── Onglet Albums ─────────────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(
    albums: List<FavorisAlbum>,
    onToggle: (com.kaloy.app.data.model.Album) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyFavorisState("Vous n'avez pas encore d'albums favoris.\nExplorez la musique et ajoutez ce que vous aimez !")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(albums, key = { it.likeId }) { favori ->
            val album = favori.album
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KaloyDarkCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (album.coverUrl != null) {
                        AsyncImage(
                            model = album.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = KaloyTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        color = KaloyTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(album.artist?.stageName ?: "Artiste inconnu")
                            album.releaseDate?.take(4)?.let { append(" · $it") }
                        },
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BoutonFavori(isFavori = true, onToggle = { onToggle(album) })
            }
        }
    }
}

// ── Onglet Artistes ───────────────────────────────────────────────────────────

@Composable
private fun ArtistesTab(
    artistes: List<FavorisArtiste>,
    onToggle: (com.kaloy.app.data.model.Artist) -> Unit
) {
    if (artistes.isEmpty()) {
        EmptyFavorisState("Vous n'avez pas encore d'artistes favoris.\nExplorez la musique et ajoutez ce que vous aimez !")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(artistes, key = { it.likeId }) { favori ->
            val artiste = favori.artiste
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(KaloyDarkCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (artiste.photoUrl != null) {
                        AsyncImage(
                            model = artiste.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = artiste.stageName.take(1).uppercase(),
                            color = KaloyPurpleLight,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = artiste.stageName,
                    color = KaloyTextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BoutonFavori(isFavori = true, onToggle = { onToggle(artiste) })
            }
        }
    }
}

// ── Etat vide ─────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFavorisState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = KaloyTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
