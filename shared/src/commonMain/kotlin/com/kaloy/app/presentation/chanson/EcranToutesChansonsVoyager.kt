package com.kaloy.app.presentation.chanson

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.kaloy.app.presentation.common.BarreNavigationPrincipale
import com.kaloy.app.presentation.common.OngletPrincipal
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.presentation.playlist.PlaylistScreen
import com.kaloy.app.presentation.radio.RadioScreen
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
        val gestionnaireFocus = LocalFocusManager.current

        var filtreTexte by remember { mutableStateOf("") }
        var suggestionsVisibles by remember { mutableStateOf(false) }

        val chansonsFiltrees = remember(filtreTexte, modeleVue.chansons) {
            if (filtreTexte.isBlank()) modeleVue.chansons
            else modeleVue.chansons.filter { chanson ->
                chanson.title.contains(filtreTexte, ignoreCase = true) ||
                chanson.artist?.stageName?.contains(filtreTexte, ignoreCase = true) == true
            }
        }

        val suggestions = remember(filtreTexte, modeleVue.chansons) {
            if (filtreTexte.isBlank()) emptyList()
            else modeleVue.chansons
                .filter { it.title.contains(filtreTexte, ignoreCase = true) }
                .take(5)
                .map { it.title }
        }

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                BarreNavigationPrincipale(
                    ongletActif = OngletPrincipal.ACCUEIL,
                    onAccueil = { navigateur.pop() },
                    onPlaylist = { navigateur.push(PlaylistScreen()) },
                    onRadio = { navigateur.push(RadioScreen()) },
                    onMoi = { navigateur.push(MoiScreen()) }
                )
            }
        ) { espaceInterieur ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fondKaloy()
                    .padding(espaceInterieur)
            ) {
                // ---- En-tête ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(colors = listOf(KaloyPurple, KaloyPink))
                        )
                        .safeContentPadding()
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
                                text = "${chansonsFiltrees.size} chanson${if (chansonsFiltrees.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // ---- Barre de filtre ----
                OutlinedTextField(
                    value = filtreTexte,
                    onValueChange = {
                        filtreTexte = it
                        suggestionsVisibles = it.isNotBlank()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Filtrer les chansons...", color = KaloyTextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = KaloyTextMuted
                        )
                    },
                    trailingIcon = {
                        if (filtreTexte.isNotEmpty()) {
                            IconButton(onClick = {
                                filtreTexte = ""
                                suggestionsVisibles = false
                                gestionnaireFocus.clearFocus()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Effacer", tint = KaloyTextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        suggestionsVisibles = false
                        gestionnaireFocus.clearFocus()
                    }),
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

                // ---- Suggestions ----
                if (suggestionsVisibles && suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            suggestions.forEach { titre ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            filtreTexte = titre
                                            suggestionsVisibles = false
                                            gestionnaireFocus.clearFocus()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = KaloyPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = titre,
                                        color = KaloyTextPrimary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
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

                    chansonsFiltrees.isEmpty() -> EmptyState(
                        message = if (filtreTexte.isBlank()) "Aucune chanson disponible pour le moment."
                                  else "Aucune chanson pour \"$filtreTexte\""
                    )

                    else -> LazyColumn {
                        itemsIndexed(
                            items = chansonsFiltrees,
                            key = { _, chanson -> chanson.id }
                        ) { index, chanson ->
                            SongRow(
                                song = chanson,
                                index = index,
                                onClick = {
                                    fileAttente.setContext(chansonsFiltrees, chanson)
                                    navigateur.push(LecteurScreen(songId = chanson.id))
                                },
                                onPlayNow = {
                                    fileAttente.setContext(chansonsFiltrees, chanson)
                                    navigateur.push(LecteurScreen(songId = chanson.id))
                                },
                                onAddNext = { fileAttente.addNext(chanson) },
                                onAddToQueue = { fileAttente.addToQueue(chanson) }
                            )
                        }

                        if (modeleVue.aEncore && filtreTexte.isBlank()) {
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

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}
