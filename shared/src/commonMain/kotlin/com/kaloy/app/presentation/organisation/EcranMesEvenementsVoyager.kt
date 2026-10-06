package com.kaloy.app.presentation.organisation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Festival
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
import com.kaloy.app.core.util.aujourdHuiLocal
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Event
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

class MesEvenementsViewModel : ViewModel() {
    private val api = KaloyApi()

    var evenements by mutableStateOf<List<Event>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                evenements = api.getMesEvenements().data ?: emptyList()
            } catch (e: Exception) {
                erreur = "Impossible de charger vos événements : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }
}

class EcranMesEvenementsVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: MesEvenementsViewModel = viewModel(key = "mes_evenements") {
            MesEvenementsViewModel()
        }

        // On recharge a chaque retour sur l'ecran : un evenement vient peut-etre
        // d'etre cree, ou un invite d'accepter.
        LaunchedEffect(Unit) { modeleVue.charger() }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Mes événements", color = KaloyTextPrimary) },
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
                    onClick = { navigateur.push(EcranCreerEvenementVoyager()) },
                    containerColor = KaloyPurple,
                    contentColor = KaloyTextPrimary
                ) {
                    Text("+ Créer")
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

                    modeleVue.evenements.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Festival,
                            contentDescription = null,
                            tint = KaloyTextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Vous n'organisez aucun événement pour l'instant.",
                            color = KaloyTextSecondary
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(modeleVue.evenements, key = { "evenement_${it.id}" }) { evenement ->
                            CarteEvenement(
                                evenement = evenement,
                                onClick = {
                                    navigateur.push(EcranProgrammationVoyager(idEvenement = evenement.id))
                                }
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
private fun CarteEvenement(evenement: Event, onClick: () -> Unit) {
    val aujourdHui = aujourdHuiLocal()
    // Comparaison de chaines : au format AAAA-MM-JJ, l'ordre alphabetique est
    // l'ordre chronologique.
    val passe = (evenement.endDate ?: evenement.startDate ?: "") < aujourdHui

    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = evenement.name,
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = periodeLisible(evenement),
                    color = if (passe) KaloyTextMuted else KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (passe) {
                    Text(
                        text = "Terminé",
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text("›", color = KaloyTextMuted, fontSize = 20.sp)
        }
    }
}

private fun periodeLisible(evenement: Event): String {
    val debut = evenement.startDate ?: return ""
    val fin = evenement.endDate
    return if (fin == null || fin == debut) "Le $debut" else "Du $debut au $fin"
}
