package com.kaloy.app.presentation.organisation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kaloy.app.data.api.CreneauDto
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Concert
import com.kaloy.app.data.model.Event
import com.kaloy.app.data.model.Venue
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

class ProgrammationViewModel(private val idEvenement: Long) : ViewModel() {
    private val api = KaloyApi()

    var evenement by mutableStateOf<Event?>(null)
        private set
    var creneaux by mutableStateOf<List<Concert>>(emptyList())
        private set
    var lieux by mutableStateOf<List<Venue>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set
    var envoiEnCours by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                // L'evenement vient de la liste de mes evenements plutot que de
                // /events/{id} : cela confirme au passage que j'en suis bien
                // l'organisateur, sans appel supplementaire.
                evenement = api.getMesEvenements().data?.firstOrNull { it.id == idEvenement }
                creneaux = api.getProgrammation(idEvenement).data ?: emptyList()
                lieux = try { api.getLieux().data?.content ?: emptyList() } catch (_: Exception) { emptyList() }
            } catch (e: Exception) {
                erreur = "Impossible de charger la programmation : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    fun inviter(creneau: CreneauDto) {
        viewModelScope.launch {
            envoiEnCours = true
            message = null
            erreur = null
            try {
                api.inviterArtistes(idEvenement, listOf(creneau))
                message = "Invitation envoyée."
                // On relit la programmation plutot que d'ajouter la ligne a la
                // main : c'est le serveur qui decide du statut et de l'ordre.
                creneaux = api.getProgrammation(idEvenement).data ?: creneaux
            } catch (e: Exception) {
                erreur = e.message ?: "L'invitation n'a pas pu être envoyée."
            } finally {
                envoiEnCours = false
            }
        }
    }
}

data class EcranProgrammationVoyager(val idEvenement: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: ProgrammationViewModel = viewModel(key = "programmation_$idEvenement") {
            ProgrammationViewModel(idEvenement)
        }

        var dialogueCreneau by remember { mutableStateOf(false) }

        if (dialogueCreneau) {
            DialogueCreneau(
                lieux = modeleVue.lieux,
                dateMin = modeleVue.evenement?.startDate,
                dateMax = modeleVue.evenement?.endDate,
                onAnnuler = { dialogueCreneau = false },
                onValider = { dto, _ ->
                    modeleVue.inviter(dto)
                    dialogueCreneau = false
                }
            )
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = modeleVue.evenement?.name ?: "Programmation",
                            color = KaloyTextPrimary,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigateur.pop() }) {
                            Text("←", fontSize = 22.sp, color = KaloyTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaloyDarkBg)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { dialogueCreneau = true },
                    containerColor = KaloyPurple,
                    contentColor = KaloyTextPrimary
                ) {
                    Text(if (modeleVue.envoiEnCours) "Envoi…" else "+ Inviter")
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

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        modeleVue.evenement?.let { evenement ->
                            item {
                                Column {
                                    Text(
                                        text = periode(evenement),
                                        color = KaloyTextSecondary
                                    )
                                    if (!evenement.description.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = evenement.description,
                                            color = KaloyTextMuted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        modeleVue.message?.let { texte ->
                            item { Text(texte, color = KaloyCyan) }
                        }
                        modeleVue.erreur?.let { texte ->
                            item { Text(texte, color = KaloyPink) }
                        }

                        item {
                            Text(
                                text = "Créneaux (${modeleVue.creneaux.size})",
                                color = KaloyTextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        if (modeleVue.creneaux.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucun artiste invité pour l'instant.",
                                    color = KaloyTextSecondary
                                )
                            }
                        }

                        items(modeleVue.creneaux, key = { "creneau_${it.id}" }) { creneau ->
                            LigneProgrammation(creneau)
                        }

                        // Le bouton flottant recouvre la fin de la liste.
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneProgrammation(creneau: Concert) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = creneau.artist?.stageName ?: "Artiste",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                creneau.title?.let {
                    Text(it, color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = creneau.startTime?.replace("T", " à ")?.take(16) ?: "",
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                creneau.venue?.let { lieu ->
                    Text(
                        text = "📍 ${lieu.name}",
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            EtiquetteStatut(creneau.status?.name)
        }
    }
}

/**
 * Statut de participation, en clair.
 *
 * C'est l'information que l'organisateur vient chercher : qui a repondu, et
 * quoi. Les trois couleurs reprennent celles du calendrier.
 */
@Composable
private fun EtiquetteStatut(statut: String?) {
    val (libelle, couleur) = when (statut) {
        "CONFIRMED" -> "Accepté" to KaloyCyan
        "DECLINED" -> "Refusé" to KaloyPink
        "PENDING" -> "En attente" to KaloyPurple
        else -> "—" to KaloyTextMuted
    }
    Box(
        modifier = Modifier
            .background(couleur.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = libelle, color = couleur, style = MaterialTheme.typography.bodySmall)
    }
}

private fun periode(evenement: Event): String {
    val debut = evenement.startDate ?: return ""
    val fin = evenement.endDate
    return if (fin == null || fin == debut) "Le $debut" else "Du $debut au $fin"
}
