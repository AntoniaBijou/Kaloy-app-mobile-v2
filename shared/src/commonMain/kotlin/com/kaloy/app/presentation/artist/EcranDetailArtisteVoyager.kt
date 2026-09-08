package com.kaloy.app.presentation.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.*
import com.kaloy.app.ui.components.*
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================
// ViewModel détail artiste (variables en français)
// ============================================================

class DetailArtisteViewModel(private val idArtiste: Long) : ViewModel() {
    private val api = KaloyApi()

    var artiste by mutableStateOf<Artist?>(null)
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var membres by mutableStateOf<List<ArtistGroupMember>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        chargerArtiste()
    }

    fun chargerArtiste() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val resultatArtiste = api.getArtistById(idArtiste)
                artiste = resultatArtiste.data

                val resultatAlbums = try { api.getArtistAlbums(idArtiste, size = 20) } catch (_: Exception) { null }
                albums = resultatAlbums?.data?.content ?: emptyList()

                val resultatChansons = try { api.getArtistSongs(idArtiste, size = 30) } catch (_: Exception) { null }
                chansons = resultatChansons?.data?.content ?: emptyList()
            } catch (e: Exception) {
                erreur = "Impossible de charger l'artiste: ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }
}

// ============================================================
// Écran détail artiste Voyager (Sprint 2 — intégré depuis Ancien)
// ============================================================

data class EcranDetailArtisteVoyager(val idArtiste: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: DetailArtisteViewModel = viewModel(key = "artiste_$idArtiste") {
            DetailArtisteViewModel(idArtiste)
        }

        when {
            modeleVue.enChargement -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            modeleVue.erreur != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = modeleVue.erreur!!,
                        onRetry = { modeleVue.chargerArtiste() }
                    )
                }
            }
            modeleVue.artiste != null -> {
                val artisteDetail = modeleVue.artiste!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // ---- Header artiste avec gradient ----
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            KaloyPurple,
                                            KaloyPink.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .safeContentPadding(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Bouton retour
                                IconButton(
                                    onClick = { navigateur.pop() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("←", fontSize = 24.sp, color = Color.White)
                                }

                                // Info artiste
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(KaloyPurpleDark, KaloyPink)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = artisteDetail.stageName.take(2).uppercase(),
                                            fontSize = 36.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = artisteDetail.stageName,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (artisteDetail.isCertified) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("✓", color = KaloyCyan, fontSize = 18.sp)
                                            }
                                        }
                                        Text(
                                            text = artisteDetail.artistType?.name ?: "Artiste",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        artisteDetail.activeSinceYear?.let {
                                            Text(
                                                text = "Actif depuis $it",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---- Biographie ----
                    artisteDetail.bio?.let { biographie ->
                        if (biographie.isNotBlank()) {
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Biographie",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = KaloyTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = biographie,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = KaloyTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // ---- Albums ----
                    if (modeleVue.albums.isNotEmpty()) {
                        item {
                            SectionHeader(title = "💿 Albums (${modeleVue.albums.size})")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(modeleVue.albums) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = { /* TODO: navigation album */ }
                                    )
                                }
                            }
                        }
                    }

                    // ---- Chansons ----
                    if (modeleVue.chansons.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🎵 Chansons (${modeleVue.chansons.size})")
                        }
                        items(
                            count = modeleVue.chansons.size,
                            key = { modeleVue.chansons[it].id }
                        ) { index ->
                            val chanson = modeleVue.chansons[index]
                            SongRow(
                                song = chanson,
                                index = index,
                                onClick = { /* TODO: navigation chanson */ }
                            )
                            if (index < modeleVue.chansons.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = KaloyDarkElevated
                                )
                            }
                        }
                    }

                    // Espacement en bas
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
