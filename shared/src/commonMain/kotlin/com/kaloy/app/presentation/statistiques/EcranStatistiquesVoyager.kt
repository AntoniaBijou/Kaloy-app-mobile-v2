package com.kaloy.app.presentation.statistiques

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.StatistiquesArtiste
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================
// Statistiques de l'artiste
//
// Ce que SA musique produit, et non ce que lui a ecoute : l'onglet « Activite »
// couvre le second. Les confondre donnerait un ecran ou « 137 ecoutes » ne dit
// plus si l'artiste a ecoute ou s'il a ete ecoute.
// ============================================================

class StatistiquesViewModel : ViewModel() {
    private val api = KaloyApi()

    var statistiques by mutableStateOf<StatistiquesArtiste?>(null)
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
                statistiques = api.getMesStatistiques().data
            } catch (e: Exception) {
                erreur = "Impossible de charger les statistiques : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }
}

class EcranStatistiquesVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: StatistiquesViewModel = viewModel(key = "statistiques") {
            StatistiquesViewModel()
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Mes statistiques", color = KaloyTextPrimary) },
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
            }
        ) { espacement ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacement)
                    .background(KaloyDarkBg)
            ) {
                val stats = modeleVue.statistiques
                when {
                    modeleVue.enChargement -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = KaloyPurple
                        )
                    }

                    stats == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = modeleVue.erreur ?: "Aucune statistique disponible",
                                color = KaloyTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { modeleVue.charger() },
                                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                // Deux rangees de deux plutot qu'une de quatre :
                                // sur la largeur d'un telephone, quatre tuiles
                                // tronqueraient « Auditeurs distincts ».
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Tuile(
                                            valeur = stats.ecoutes30Jours,
                                            libelle = "Écoutes",
                                            precision = "30 derniers jours",
                                            modifier = Modifier.weight(1f)
                                        )
                                        Tuile(
                                            valeur = stats.auditeursDistincts,
                                            libelle = "Auditeurs",
                                            precision = "personnes distinctes",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Tuile(
                                            valeur = stats.abonnes,
                                            libelle = "Abonnés",
                                            precision = "vous suivent",
                                            modifier = Modifier.weight(1f)
                                        )
                                        Tuile(
                                            valeur = stats.chansonsPubliees,
                                            libelle = "Chansons",
                                            precision = "publiées",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "${stats.ecoutesTotal} écoutes depuis le début",
                                    color = KaloyTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            item {
                                Text(
                                    text = "Mes chansons les plus écoutées",
                                    color = KaloyTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            if (stats.topChansons.isEmpty()) {
                                item {
                                    Text(
                                        text = "Vous n'avez pas encore publié de chanson.",
                                        color = KaloyTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                itemsIndexed(stats.topChansons, key = { _, c -> c.id }) { rang, chanson ->
                                    LigneChanson(
                                        rang = rang + 1,
                                        titre = chanson.titre,
                                        ecoutes = chanson.ecoutes
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tuile(
    valeur: Long,
    libelle: String,
    precision: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valeur.toString(),
                color = KaloyPurpleLight,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = libelle,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = precision,
                color = KaloyTextMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LigneChanson(rang: Int, titre: String, ecoutes: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rang",
                color = KaloyTextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp)
            )
            Text(
                text = titre,
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                // Le mot au singulier quand il n'y en a qu'une : une chanson a
                // « 1 écoutes » trahit un affichage qui n'a pas ete relu.
                text = if (ecoutes == 1L) "1 écoute" else "$ecoutes écoutes",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
