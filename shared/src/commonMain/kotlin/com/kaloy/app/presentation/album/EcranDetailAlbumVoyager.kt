package com.kaloy.app.presentation.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.kaloy.app.data.model.Album
import com.kaloy.app.data.model.Song
import com.kaloy.app.presentation.chanson.EcranDetailChansonVoyager
import com.kaloy.app.ui.components.ErrorState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.components.SectionHeader
import com.kaloy.app.ui.components.SongRow
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

class DetailAlbumViewModel(private val idAlbum: Long) : ViewModel() {
    private val api = KaloyApi()

    var album by mutableStateOf<Album?>(null)
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        chargerAlbum()
    }

    fun chargerAlbum() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                // Charger les détails de l'album
                val resultatAlbum = api.getAlbumById(idAlbum)
                album = resultatAlbum.data

                // Charger les chansons via la route correcte
                val resultatChansons = try { api.getAlbumSongs(idAlbum, size = 100) } catch (e: Exception) { null }
                chansons = resultatChansons?.data?.content ?: emptyList()
                
            } catch (e: Exception) {
                erreur = "Impossible de charger l'album: ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }
}

data class EcranDetailAlbumVoyager(val idAlbum: Long) : Screen {
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: DetailAlbumViewModel = viewModel(key = "album_$idAlbum") {
            DetailAlbumViewModel(idAlbum)
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
                        onRetry = { modeleVue.chargerAlbum() }
                    )
                }
            }
            modeleVue.album != null -> {
                val albumDetail = modeleVue.album!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // ---- Header album avec gradient ----
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            KaloyPurpleDark,
                                            KaloyPink.copy(alpha = 0.5f),
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
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // En-tête avec bouton retour
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    IconButton(
                                        onClick = { navigateur.pop() },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("←", fontSize = 24.sp, color = Color.White)
                                    }
                                }

                                // Pochette de l'album
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(KaloyPurple, KaloyPink)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("♪", fontSize = 60.sp, color = Color.White)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Titre de l'album
                                Text(
                                    text = albumDetail.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // Artiste
                                Text(
                                    text = albumDetail.artist?.stageName ?: "Artiste Inconnu",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                
                                // Année
                                albumDetail.releaseDate?.let {
                                    Text(
                                        text = "Sortie le $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // ---- Chansons de l'album ----
                    if (modeleVue.chansons.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🎵 Titres (${modeleVue.chansons.size})")
                        }
                        itemsIndexed(modeleVue.chansons) { index, chanson ->
                            SongRow(
                                song = chanson,
                                index = index,
                                onClick = { 
                                    navigateur.push(EcranDetailChansonVoyager(chanson.id)) 
                                }
                            )
                            if (index < modeleVue.chansons.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = KaloyDarkElevated
                                )
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucune chanson dans cet album.",
                                    color = KaloyTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Espacement final
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
