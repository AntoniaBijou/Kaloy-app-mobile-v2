package com.kaloy.app.presentation.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.kaloy.app.data.model.Song
import com.kaloy.app.presentation.chanson.EcranDetailChansonVoyager
import com.kaloy.app.ui.components.EmptyState
import com.kaloy.app.ui.components.ErrorState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.components.SongRow
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================
// ViewModel — chansons rattachées à un genre
// ============================================================

class GenreViewModel(private val idGenre: Long) : ViewModel() {
    private val api = KaloyApi()

    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        chargerChansons()
    }

    fun chargerChansons() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                // La liaison song_genres porte la chanson complète en relation.
                val resultat = api.chansonsParGenre(idGenre, size = 50)
                chansons = resultat.data?.content?.mapNotNull { it.song } ?: emptyList()
            } catch (e: Exception) {
                erreur = e.message ?: "Impossible de charger les chansons de ce genre."
            } finally {
                enChargement = false
            }
        }
    }
}

// ============================================================
// Écran genre — Sprint 2 (recherche globale)
// ============================================================

data class EcranGenreVoyager(
    val idGenre: Long,
    val nomGenre: String
) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: GenreViewModel = viewModel { GenreViewModel(idGenre) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
        ) {
            // ---- En-tête ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(colors = listOf(KaloyPurple, KaloyPink))
                    )
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateur.pop() }) {
                    Text("←", fontSize = 24.sp, color = androidx.compose.ui.graphics.Color.White)
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = nomGenre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Genre musical",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // ---- Contenu ----
            when {
                modeleVue.enChargement -> LoadingIndicator()

                modeleVue.erreur != null -> ErrorState(
                    message = modeleVue.erreur!!,
                    onRetry = { modeleVue.chargerChansons() }
                )

                modeleVue.chansons.isEmpty() -> EmptyState(
                    message = "Aucune chanson dans le genre « $nomGenre »"
                )

                else -> LazyColumn {
                    itemsIndexed(
                        items = modeleVue.chansons,
                        key = { _, chanson -> chanson.id }
                    ) { index, chanson ->
                        SongRow(
                            song = chanson,
                            index = index,
                            onClick = { navigateur.push(EcranDetailChansonVoyager(chanson.id)) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}
