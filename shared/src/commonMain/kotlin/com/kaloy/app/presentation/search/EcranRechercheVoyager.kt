package com.kaloy.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kaloy.app.data.api.ArtistSearch
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Song
import com.kaloy.app.presentation.artist.EcranDetailArtisteVoyager
import com.kaloy.app.ui.components.*
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================
// ViewModel de recherche (variables en français)
// ============================================================

class RechercheViewModel : ViewModel() {
    private val api = KaloyApi()
    private var tacheRecherche: Job? = null

    var rechercheTexte by mutableStateOf("")
        private set
    var artistes by mutableStateOf<List<Artist>>(emptyList())
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var enCoursDeRecherche by mutableStateOf(false)
        private set
    var aCherche by mutableStateOf(false)
        private set

    fun surChangementTexte(nouveauTexte: String) {
        rechercheTexte = nouveauTexte
        // Debounce : attend 400ms après la dernière frappe
        tacheRecherche?.cancel()
        if (nouveauTexte.length >= 2) {
            tacheRecherche = viewModelScope.launch {
                delay(400)
                effectuerRecherche(nouveauTexte)
            }
        } else {
            artistes = emptyList()
            chansons = emptyList()
            aCherche = false
        }
    }

    private suspend fun effectuerRecherche(texteRecherche: String) {
        enCoursDeRecherche = true
        try {
            // Recherche artistes
            val resultatArtistes = try {
                api.searchArtists(ArtistSearch(stageName = texteRecherche), size = 20)
            } catch (_: Exception) { null }
            artistes = resultatArtistes?.data?.content ?: emptyList()

            // TODO: Quand le backend supportera la recherche de chansons, ajouter ici
            chansons = emptyList()

            aCherche = true
        } catch (_: Exception) {
            // Gestion silencieuse
        } finally {
            enCoursDeRecherche = false
        }
    }
}

// ============================================================
// Écran de recherche Voyager (Sprint 1 — intégré depuis Ancien)
// ============================================================

class EcranRechercheVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: RechercheViewModel = viewModel { RechercheViewModel() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
        ) {
            // ---- Barre de recherche ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateur.pop() }) {
                    Text("←", fontSize = 24.sp, color = KaloyTextPrimary)
                }

                OutlinedTextField(
                    value = modeleVue.rechercheTexte,
                    onValueChange = { modeleVue.surChangementTexte(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Rechercher...",
                            color = KaloyTextMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaloyPurple,
                        unfocusedBorderColor = KaloyDarkElevated,
                        focusedTextColor = KaloyTextPrimary,
                        unfocusedTextColor = KaloyTextPrimary,
                        cursorColor = KaloyPurple,
                        focusedContainerColor = KaloyDarkCard,
                        unfocusedContainerColor = KaloyDarkCard
                    )
                )
            }

            // ---- Résultats ----
            when {
                modeleVue.enCoursDeRecherche -> {
                    LoadingIndicator()
                }
                !modeleVue.aCherche -> {
                    // État initial
                    EmptyState(message = "Tapez au moins 2 caractères pour rechercher")
                }
                modeleVue.artistes.isEmpty() && modeleVue.chansons.isEmpty() -> {
                    EmptyState(message = "Aucun résultat pour \"${modeleVue.rechercheTexte}\"")
                }
                else -> {
                    LazyColumn {
                        // Artistes trouvés
                        if (modeleVue.artistes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "🎤 Artistes (${modeleVue.artistes.size})")
                            }
                            items(
                                items = modeleVue.artistes,
                                key = { it.id }
                            ) { artiste ->
                                LigneRechercheArtiste(
                                    artiste = artiste,
                                    onClick = {
                                        navigateur.push(EcranDetailArtisteVoyager(idArtiste = artiste.id))
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = KaloyDarkElevated
                                )
                            }
                        }

                        // Chansons trouvées
                        if (modeleVue.chansons.isNotEmpty()) {
                            item {
                                SectionHeader(title = "🎵 Chansons (${modeleVue.chansons.size})")
                            }
                            items(
                                count = modeleVue.chansons.size,
                                key = { modeleVue.chansons[it].id }
                            ) { index ->
                                SongRow(
                                    song = modeleVue.chansons[index],
                                    index = index,
                                    onClick = { /* TODO: navigation chanson */ }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Ligne de résultat artiste (composant local)
// ============================================================

@Composable
private fun LigneRechercheArtiste(
    artiste: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar mini
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(KaloyPurple, KaloyPink)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = artiste.stageName.take(2).uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artiste.stageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (artiste.isCertified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("✓", color = KaloyCyan, fontSize = 14.sp)
                }
            }
            Text(
                text = artiste.artistType?.name ?: "Artiste",
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextSecondary
            )
        }

        TextButton(onClick = onClick) {
            Text("Voir", color = KaloyPurpleLight)
        }
    }
}
