package com.kaloy.app.presentation.chanson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Song
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.ui.components.EmptyState
import com.kaloy.app.ui.components.ErrorState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.components.SongRow
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ============================================================
// ViewModel — catalogue complet des chansons (paginé)
// ============================================================

class ToutesChansonsViewModel : ViewModel() {
    private val api = KaloyApi()
    private val taillePage = 30

    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var chargementSuivant by mutableStateOf(false)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set
    var aEncore by mutableStateOf(false)
        private set
    private var pageCourante = 0

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            pageCourante = 0
            try {
                val resultat = api.getSongs(page = 0, size = taillePage)
                val page = resultat.data
                chansons = page?.content ?: emptyList()
                aEncore = page?.last == false
            } catch (e: Exception) {
                erreur = e.message ?: "Impossible de charger les chansons."
            } finally {
                enChargement = false
            }
        }
    }

    fun chargerPlus() {
        if (chargementSuivant || !aEncore) return
        viewModelScope.launch {
            chargementSuivant = true
            try {
                pageCourante++
                val resultat = api.getSongs(page = pageCourante, size = taillePage)
                val page = resultat.data
                chansons = chansons + (page?.content ?: emptyList())
                aEncore = page?.last == false
            } catch (e: Exception) {
                pageCourante--
            } finally {
                chargementSuivant = false
            }
        }
    }
}

// ============================================================
// Écran — toutes les chansons du catalogue
// ============================================================

class EcranToutesChansonsVoyager : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val fileAttente = koinInject<QueueManager>()
        val modeleVue: ToutesChansonsViewModel = viewModel { ToutesChansonsViewModel() }

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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "Toutes les chansons",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (modeleVue.chansons.isNotEmpty()) {
                        Text(
                            text = "${modeleVue.chansons.size} chanson${if (modeleVue.chansons.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ---- Contenu ----
            when {
                modeleVue.enChargement -> LoadingIndicator()

                modeleVue.erreur != null -> ErrorState(
                    message = modeleVue.erreur!!,
                    onRetry = { modeleVue.charger() }
                )

                modeleVue.chansons.isEmpty() -> EmptyState(
                    message = "Aucune chanson disponible pour le moment."
                )

                else -> LazyColumn {
                    itemsIndexed(
                        items = modeleVue.chansons,
                        key = { _, chanson -> chanson.id }
                    ) { index, chanson ->
                        SongRow(
                            song = chanson,
                            index = index,
                            onClick = {
                                fileAttente.setContext(modeleVue.chansons, chanson)
                                navigateur.push(LecteurScreen(songId = chanson.id))
                            },
                            onPlayNow = {
                                fileAttente.setContext(modeleVue.chansons, chanson)
                                navigateur.push(LecteurScreen(songId = chanson.id))
                            },
                            onAddNext = { fileAttente.addNext(chanson) },
                            onAddToQueue = { fileAttente.addToQueue(chanson) }
                        )
                    }

                    if (modeleVue.aEncore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (modeleVue.chargementSuivant) {
                                    CircularProgressIndicator(
                                        color = KaloyPurple,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { modeleVue.chargerPlus() },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = KaloyPurple
                                        )
                                    ) {
                                        Text("Charger plus")
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}
