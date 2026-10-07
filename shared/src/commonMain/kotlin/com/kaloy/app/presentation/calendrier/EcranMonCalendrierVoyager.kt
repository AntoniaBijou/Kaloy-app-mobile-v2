package com.kaloy.app.presentation.calendrier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.util.Calendrier
import com.kaloy.app.core.util.aujourdHuiLocal
import com.kaloy.app.data.api.CreerConcertDto
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Concert
import com.kaloy.app.data.model.Venue
import com.kaloy.app.presentation.evenement.EcranDetailEvenementVoyager
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Calendrier personnel de l'artiste connecte.
 *
 * Il voit la meme chose que le public sur sa fiche, mais depuis son espace :
 * sur fond sombre, sans sa biographie ni ses albums, et sans avoir a se
 * chercher lui-meme dans la recherche.
 */
class MonCalendrierViewModel : ViewModel() {
    private val api = KaloyApi()

    var concertsParDate by mutableStateOf<Map<String, List<Concert>>>(emptyMap())
        private set
    var anneeAffichee by mutableStateOf(0)
        private set
    var moisAffiche by mutableStateOf(1)
        private set
    var dateSelectionnee by mutableStateOf<String?>(null)
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var operationEnCours by mutableStateOf(false)
        private set

    /** Lieux proposes dans le formulaire, avant d'en creer un nouveau. */
    var lieux by mutableStateOf<List<Venue>>(emptyList())
        private set

    val aujourdHui: String = aujourdHuiLocal()

    init {
        Calendrier.decouper(aujourdHui)?.let { date ->
            anneeAffichee = date.first
            moisAffiche = date.second
        }
        charger()
        chargerLieux()
    }

    private fun chargerLieux() {
        viewModelScope.launch {
            lieux = try {
                api.getLieux().data?.content ?: emptyList()
            } catch (_: Exception) {
                // La liste vide reste utilisable : l'artiste creera son lieu.
                emptyList()
            }
        }
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                // Le serveur ne renvoie que les concerts confirmes, et deduit
                // l'artiste du jeton : rien a filtrer ni a resoudre ici.
                concertsParDate = (api.getMonCalendrier().data ?: emptyList())
                    .filter { !it.startTime.isNullOrBlank() }
                    .groupBy { it.startTime!!.substringBefore('T') }
            } catch (e: Exception) {
                erreur = "Impossible de charger votre calendrier : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    fun afficherMoisPrecedent() {
        val (annee, mois) = Calendrier.moisPrecedent(anneeAffichee, moisAffiche)
        changerMois(annee, mois)
    }

    fun afficherMoisSuivant() {
        val (annee, mois) = Calendrier.moisSuivant(anneeAffichee, moisAffiche)
        changerMois(annee, mois)
    }

    private fun changerMois(annee: Int, mois: Int) {
        anneeAffichee = annee
        moisAffiche = mois
        // La selection appartenait au mois qu'on quitte.
        dateSelectionnee = null
    }

    /** Un second appui sur le meme jour replie le detail. */
    fun selectionnerDate(date: String) {
        dateSelectionnee = if (dateSelectionnee == date) null else date
    }

    fun concertsDuJour(date: String): List<Concert> = concertsParDate[date] ?: emptyList()

    /** Nombre total de concerts confirmes, toutes dates confondues. */
    val nombreConcerts: Int get() = concertsParDate.values.sumOf { it.size }

    fun declarerConcert(requete: CreerConcertDto) {
        if (operationEnCours) return
        viewModelScope.launch {
            operationEnCours = true
            message = null
            erreur = null
            try {
                val cree = api.creerMonConcert(requete)
                message = "Concert ajouté à votre calendrier."
                // On relit tout plutot que d'inserer la ligne a la main : c'est
                // le serveur qui decide du statut et de la date retenue.
                charger()
                // Le mois du nouveau concert s'affiche, sans quoi l'artiste
                // resterait sur un mois ou rien n'a change.
                cree.data?.startTime?.let { debut ->
                    Calendrier.decouper(debut)?.let { date ->
                        anneeAffichee = date.first
                        moisAffiche = date.second
                        dateSelectionnee = debut.substringBefore('T')
                    }
                }
            } catch (e: Exception) {
                erreur = e.message ?: "Le concert n'a pas pu être créé."
            } finally {
                operationEnCours = false
            }
        }
    }

    fun annulerConcert(idConcert: Long) {
        if (operationEnCours) return
        viewModelScope.launch {
            operationEnCours = true
            message = null
            erreur = null
            try {
                api.supprimerMonConcert(idConcert)
                message = "Concert annulé."
                charger()
            } catch (e: Exception) {
                erreur = e.message ?: "L'annulation n'a pas pu être enregistrée."
            } finally {
                operationEnCours = false
            }
        }
    }

    fun effacerMessages() {
        message = null
        erreur = null
    }
}

class EcranMonCalendrierVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: MonCalendrierViewModel = viewModel(key = "mon_calendrier") {
            MonCalendrierViewModel()
        }

        var dialogueConcert by remember { mutableStateOf(false) }
        var concertAAnnuler by remember { mutableStateOf<Concert?>(null) }

        if (dialogueConcert) {
            DialogueConcert(
                lieux = modeleVue.lieux,
                onAnnuler = { dialogueConcert = false },
                onValider = { requete ->
                    modeleVue.declarerConcert(requete)
                    dialogueConcert = false
                }
            )
        }

        // L'annulation est irreversible : on la fait confirmer.
        concertAAnnuler?.let { concert ->
            AlertDialog(
                onDismissRequest = { concertAAnnuler = null },
                containerColor = KaloyDarkCard,
                title = { Text("Annuler ce concert ?", color = KaloyTextPrimary) },
                text = {
                    Text(
                        text = "« ${concert.title ?: "Concert"} » du " +
                            "${concert.startTime?.take(10) ?: ""} sera retiré de votre calendrier. " +
                            "Cette action est définitive.",
                        color = KaloyTextSecondary
                    )
                },
                dismissButton = {
                    TextButton(onClick = { concertAAnnuler = null }) {
                        Text("Garder", color = KaloyTextSecondary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        modeleVue.annulerConcert(concert.id)
                        concertAAnnuler = null
                    }) {
                        Text("Annuler le concert", color = KaloyPink)
                    }
                }
            )
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Mon calendrier", color = KaloyTextPrimary) },
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
                    onClick = { dialogueConcert = true },
                    containerColor = KaloyPurple,
                    contentColor = KaloyTextPrimary
                ) {
                    Text(if (modeleVue.operationEnCours) "…" else "+ Concert")
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

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            GrilleCalendrier(
                                annee = modeleVue.anneeAffichee,
                                mois = modeleVue.moisAffiche,
                                aujourdHui = modeleVue.aujourdHui,
                                datesOccupees = modeleVue.concertsParDate.keys,
                                dateSelectionnee = modeleVue.dateSelectionnee,
                                onMoisPrecedent = { modeleVue.afficherMoisPrecedent() },
                                onMoisSuivant = { modeleVue.afficherMoisSuivant() },
                                onJourClique = { modeleVue.selectionnerDate(it) }
                            )
                        }

                        modeleVue.message?.let { texte ->
                            item { Text(texte, color = KaloyCyan, modifier = Modifier.padding(top = 12.dp)) }
                        }
                        modeleVue.erreur?.let { texte ->
                            item { Text(texte, color = KaloyPink, modifier = Modifier.padding(top = 12.dp)) }
                        }

                        item { Spacer(Modifier.height(16.dp)) }

                        // Contrairement a la fiche publique, appuyer sur un jour
                        // n'ouvre pas directement la fiche de l'evenement : ici
                        // l'artiste consulte son agenda, il veut d'abord voir ce
                        // qu'il a ce jour-la.
                        val date = modeleVue.dateSelectionnee
                        if (date == null) {
                            item { ResumeAgenda(nombre = modeleVue.nombreConcerts) }
                        } else {
                            val duJour = modeleVue.concertsDuJour(date)
                            if (duJour.isEmpty()) {
                                item { AucunConcert() }
                            } else {
                                items(duJour, key = { "agenda_${it.id}" }) { concert ->
                                    CarteConcert(
                                        concert = concert,
                                        // Un concert sans evenement est un
                                        // concert qu'il a declare seul : lui
                                        // seul peut l'annuler, et seulement
                                        // s'il est encore a venir.
                                        annulable = concert.event == null &&
                                            (concert.startTime?.substringBefore('T')
                                                ?: "") >= modeleVue.aujourdHui,
                                        onClick = {
                                            concert.event?.let {
                                                navigateur.push(
                                                    EcranDetailEvenementVoyager(idEvenement = it.id)
                                                )
                                            }
                                        },
                                        onAnnuler = { concertAAnnuler = concert }
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
private fun ResumeAgenda(nombre: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = KaloyTextMuted,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (nombre) {
                0 -> "Aucun concert confirmé pour l'instant."
                1 -> "1 concert confirmé. Appuyez sur une date pour le voir."
                else -> "$nombre concerts confirmés. Appuyez sur une date pour les voir."
            },
            color = KaloyTextSecondary
        )
    }
}

@Composable
private fun AucunConcert() {
    Text(
        text = "Aucun concert ce jour-là.",
        color = KaloyTextSecondary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun CarteConcert(
    concert: Concert,
    annulable: Boolean,
    onClick: () -> Unit,
    onAnnuler: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            // Un concert d'evenement porte deux noms : celui de l'affiche et
            // celui du passage. Un concert declare seul n'en a qu'un, et
            // l'afficher deux fois donnait « Soiree acoustique » en titre comme
            // en sous-titre.
            val titrePrincipal = concert.event?.name ?: concert.title ?: "Concert"
            Text(
                text = titrePrincipal,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            concert.title?.takeIf { it != titrePrincipal }?.let {
                Text(it, color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                // On tronque avant de remplacer le T : le remplacement allonge
                // la chaine, et couper apres mangerait les minutes.
                text = concert.startTime?.take(16)?.replace("T", " à ") ?: "",
                color = KaloyTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            concert.venue?.let { lieu ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = KaloyTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = lieu.name,
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (annulable) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAnnuler, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = KaloyPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Annuler ce concert", color = KaloyPink)
                }
            }
        }
    }
}
