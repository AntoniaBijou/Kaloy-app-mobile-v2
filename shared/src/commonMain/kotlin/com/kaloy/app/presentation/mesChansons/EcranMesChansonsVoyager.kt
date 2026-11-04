package com.kaloy.app.presentation.mesChansons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

class MesChansonsViewModel : ViewModel() {
    private val api = KaloyApi()

    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    /** La chanson dont la suppression est a confirmer, nulle si aucune. */
    var aSupprimer by mutableStateOf<Song?>(null)

    var messageSuppression by mutableStateOf<String?>(null)

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                chansons = api.getMesChansons().data ?: emptyList()
            } catch (e: Exception) {
                erreur = "Impossible de charger vos chansons : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    fun supprimer(chanson: Song) {
        viewModelScope.launch {
            aSupprimer = null
            try {
                api.supprimerChanson(chanson.id)
                chansons = chansons.filterNot { it.id == chanson.id }
            } catch (e: Exception) {
                // Une chanson deja ecoutee, ou rangee dans une playlist, ne
                // peut pas etre effacee : les tables listening_history,
                // playlist_songs, editorial_playlist_songs et up_next_queue la
                // referencent sans effacement en cascade. Le message le dit
                // plutot que de laisser croire a une panne.
                messageSuppression = "Suppression impossible : cette chanson est " +
                    "peut-être déjà écoutée ou rangée dans une playlist."
            }
        }
    }
}

class EcranMesChansonsVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: MesChansonsViewModel = viewModel(key = "mes_chansons") {
            MesChansonsViewModel()
        }

        // On recharge a chaque retour : une chanson vient peut-etre d'etre
        // declaree sur l'ecran suivant.
        LaunchedEffect(Unit) { modeleVue.charger() }

        modeleVue.aSupprimer?.let { chanson ->
            AlertDialog(
                onDismissRequest = { modeleVue.aSupprimer = null },
                containerColor = KaloyDarkCard,
                title = { Text("Supprimer cette chanson ?", color = KaloyTextPrimary) },
                text = {
                    Text(
                        "« ${chanson.title} » sera effacée définitivement.",
                        color = KaloyTextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { modeleVue.supprimer(chanson) }) {
                        Text("Supprimer", color = KaloyRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modeleVue.aSupprimer = null }) {
                        Text("Annuler", color = KaloyTextSecondary)
                    }
                }
            )
        }

        modeleVue.messageSuppression?.let { message ->
            AlertDialog(
                onDismissRequest = { modeleVue.messageSuppression = null },
                containerColor = KaloyDarkCard,
                title = { Text("Suppression refusée", color = KaloyTextPrimary) },
                text = { Text(message, color = KaloyTextSecondary) },
                confirmButton = {
                    TextButton(onClick = { modeleVue.messageSuppression = null }) {
                        Text("Fermer", color = KaloyTextSecondary)
                    }
                }
            )
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Mes chansons", color = KaloyTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { navigateur.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaloyDarkBg)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { navigateur.push(EcranCreerChansonVoyager()) },
                    containerColor = KaloyPurple,
                    contentColor = KaloyTextPrimary
                ) {
                    Text("+ Déclarer")
                }
            }
        ) { espacement ->
            Box(
                modifier = Modifier.fillMaxSize().padding(espacement).background(KaloyDarkBg)
            ) {
                when {
                    modeleVue.enChargement -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = KaloyPurple
                    )

                    modeleVue.erreur != null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(modeleVue.erreur!!, color = KaloyTextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { modeleVue.charger() },
                            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                        ) { Text("Réessayer") }
                    }

                    modeleVue.chansons.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = KaloyTextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Vous n'avez déclaré aucune chanson.",
                            color = KaloyTextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Un titre et des paroles suffisent pour commencer.",
                            color = KaloyTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(modeleVue.chansons, key = { "chanson_${it.id}" }) { chanson ->
                            CarteChanson(
                                chanson = chanson,
                                onSupprimer = { modeleVue.aSupprimer = chanson }
                            )
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteChanson(chanson: Song, onSupprimer: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chanson.title,
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                chanson.album?.let {
                    Text(
                        text = it.title,
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = contenuLisible(chanson),
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onSupprimer) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Supprimer",
                    tint = KaloyTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Ce que la chanson porte reellement.
 *
 * Plus utile que la duree ou la date : l'artiste declare souvent un texte seul,
 * et ce qu'il veut savoir en revenant sur la liste, c'est ce qui reste a
 * completer.
 */
private fun contenuLisible(chanson: Song): String {
    val pieces = buildList {
        if (!chanson.lyrics.isNullOrBlank()) add("paroles")
        if (!chanson.solfa.isNullOrBlank()) add("solfa")
        if (!chanson.audioUrl.isNullOrBlank()) add("audio")
        if (!chanson.videoUrl.isNullOrBlank()) add("vidéo")
        if (!chanson.karaokeAudioUrl.isNullOrBlank()) add("karaoké")
        if (!chanson.playbackUrl.isNullOrBlank()) add("playback")
    }
    return if (pieces.isEmpty()) "Aucun contenu" else pieces.joinToString(" · ")
}
