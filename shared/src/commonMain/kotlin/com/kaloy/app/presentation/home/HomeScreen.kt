package com.kaloy.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.ListeningHistorySearchDto
import com.kaloy.app.data.api.UserIdDto
import com.kaloy.app.data.model.*
import com.kaloy.app.presentation.artist.EcranDetailArtisteVoyager
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.presentation.search.EcranRechercheVoyager
import com.kaloy.app.ui.components.*
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ============================================================
// ViewModel de l'écran d'accueil (variables en français)
// ============================================================

class AccueilViewModel : ViewModel() {
    private val api = KaloyApi()

    // --- Données affichées ---
    var artistes by mutableStateOf<List<Artist>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlistsEditoriales by mutableStateOf<List<EditorialPlaylist>>(emptyList())
        private set
    var ecoutesRecentes by mutableStateOf<List<ListeningHistory>>(emptyList())
        private set

    // --- États ---
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        chargerDonnees()
    }

    fun chargerDonnees() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val resultatArtistes = try { api.getArtists(size = 20) } catch (e: Exception) { null }
                val resultatAlbums = try { api.getAlbums(size = 10) } catch (e: Exception) { null }
                val resultatChansons = try { api.getSongs(size = 15) } catch (e: Exception) { null }
                val resultatPlaylists = try { api.getEditorialPlaylists(size = 10) } catch (e: Exception) { null }

                // Simuler utilisateur ID = 1
                val resultatHistorique = try {
                    api.getListeningHistory(
                        query = ListeningHistorySearchDto(UserIdDto(1L)),
                        size = 10
                    )
                } catch (e: Exception) { null }

                artistes = resultatArtistes?.data?.content ?: emptyList()
                albums = resultatAlbums?.data?.content ?: emptyList()
                chansons = resultatChansons?.data?.content ?: emptyList()
                playlistsEditoriales = resultatPlaylists?.data?.content ?: emptyList()

                // Garder les chansons uniques dans l'historique
                ecoutesRecentes = (resultatHistorique?.data?.content ?: emptyList())
                    .distinctBy { it.song?.id }

                if (artistes.isEmpty() && albums.isEmpty() && chansons.isEmpty()) {
                    erreur = "Aucune donnée disponible. Vérifiez que le backend est démarré."
                }
            } catch (e: Exception) {
                erreur = "Erreur de connexion: ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }
}

// ============================================================
// Écran d'accueil Voyager (Sprint 0 — intégré depuis Ancien)
// ============================================================

data class HomeScreen(
    val username: String = "Utilisateur",
    val isVisitor: Boolean = false
) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val gestionSession = koinInject<AuthSessionManager>()
        val nomAffiche = if (isVisitor) username else gestionSession.getDisplayName().ifBlank { username }
        val modeleVue: AccueilViewModel = viewModel { AccueilViewModel() }

        // Onglet sélectionné dans la barre de navigation
        var ongletSelectionne by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            if (!isVisitor && !gestionSession.isLoggedIn()) {
                navigateur.replace(WelcomeScreen())
            }
        }

        Scaffold(
            bottomBar = {
                if (!isVisitor) {
                    NavigationBar(containerColor = KaloyDarkSurface) {
                        NavigationBarItem(
                            selected = ongletSelectionne == 0,
                            onClick = { ongletSelectionne = 0 },
                            icon = {
                                Icon(Icons.Default.Home, contentDescription = "Accueil")
                            },
                            label = { Text("Accueil") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                        NavigationBarItem(
                            selected = ongletSelectionne == 1,
                            onClick = {
                                navigateur.push(EcranRechercheVoyager())
                            },
                            icon = {
                                Icon(Icons.Default.Search, contentDescription = "Recherche")
                            },
                            label = { Text("Recherche") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                        NavigationBarItem(
                            selected = ongletSelectionne == 2,
                            onClick = { navigateur.push(MoiScreen()) },
                            icon = {
                                Icon(Icons.Default.Person, contentDescription = "Mon profil")
                            },
                            label = { Text("Moi") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                    }
                }
            },
            containerColor = KaloyDarkBg
        ) { espaceInterieur ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espaceInterieur)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // ---- Header avec gradient ----
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        KaloyPurple.copy(alpha = 0.8f),
                                        KaloyPink.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                            .padding(16.dp)
                            .safeContentPadding(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text(
                                text = "Bonsoir $nomAffiche 🎶",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Découvrez la musique malgache",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // ---- Barre de recherche ----
                item {
                    OutlinedButton(
                        onClick = { navigateur.push(EcranRechercheVoyager()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KaloyTextSecondary
                        )
                    ) {
                        Text(
                            text = "🔍 Rechercher artistes, albums, chansons...",
                            modifier = Modifier.fillMaxWidth(),
                            color = KaloyTextMuted
                        )
                    }
                }

                // ---- Contenu ----
                when {
                    modeleVue.enChargement -> {
                        item { LoadingIndicator() }
                    }
                    modeleVue.erreur != null -> {
                        item {
                            ErrorState(
                                message = modeleVue.erreur!!,
                                onRetry = { modeleVue.chargerDonnees() }
                            )
                        }
                    }
                    else -> {
                        // Récemment écouté
                        if (modeleVue.ecoutesRecentes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "🕒 Récemment écouté")
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(modeleVue.ecoutesRecentes) { historiqueObj ->
                                        historiqueObj.song?.let { chanson ->
                                            Column(
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .clickable { navigateur.push(LecteurScreen(songId = chanson.id)) },
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(100.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(KaloyPurple, KaloyPink)
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "♪",
                                                        color = Color.White,
                                                        fontSize = 32.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = chanson.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = KaloyTextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = chanson.artist?.stageName ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = KaloyTextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }

                        // Playlists à la une
                        if (modeleVue.playlistsEditoriales.isNotEmpty()) {
                            item {
                                SectionHeader(title = "🔥 À la une")
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.playlistsEditoriales) { playlist ->
                                        EditorialPlaylistCard(
                                            playlist = playlist,
                                            onClick = { /* TODO: navigation playlist */ }
                                        )
                                    }
                                }
                            }
                        }

                        // Artistes populaires
                        if (modeleVue.artistes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "🎤 Artistes")
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.artistes) { artiste ->
                                        ArtistCard(
                                            artist = artiste,
                                            onClick = {
                                                navigateur.push(EcranDetailArtisteVoyager(idArtiste = artiste.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Albums récents
                        if (modeleVue.albums.isNotEmpty()) {
                            item {
                                SectionHeader(title = "💿 Albums")
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

                        // Chansons
                        if (modeleVue.chansons.isNotEmpty()) {
                            item {
                                SectionHeader(title = "🎵 Chansons")
                            }
                            items(
                                count = modeleVue.chansons.size,
                                key = { modeleVue.chansons[it].id }
                            ) { index ->
                                val chanson = modeleVue.chansons[index]
                                SongRow(
                                    song = chanson,
                                    index = index,
                                    onClick = { navigateur.push(LecteurScreen(songId = chanson.id)) }
                                )
                                if (index < modeleVue.chansons.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = KaloyDarkElevated
                                    )
                                }
                            }
                        }

                        // Bouton visiteur
                        if (isVisitor) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { navigateur.replace(WelcomeScreen()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text("Créer un compte")
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
}
