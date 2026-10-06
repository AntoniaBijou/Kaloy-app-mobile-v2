package com.kaloy.app.presentation.organisation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kaloy.app.data.api.CreerEvenementDto
import com.kaloy.app.data.api.CreneauDto
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Venue
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

/** Un creneau en cours de saisie, accompagne du nom de scene pour l'affichage. */
data class CreneauSaisi(val dto: CreneauDto, val nomArtiste: String)

class CreerEvenementViewModel : ViewModel() {
    private val api = KaloyApi()

    var nom by mutableStateOf("")
    var description by mutableStateOf("")
    var dateDebut by mutableStateOf<String?>(null)
    var dateFin by mutableStateOf<String?>(null)
    var creneaux by mutableStateOf<List<CreneauSaisi>>(emptyList())
        private set

    var lieux by mutableStateOf<List<Venue>>(emptyList())
        private set
    var envoiEnCours by mutableStateOf(false)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set
    var idEvenementCree by mutableStateOf<Long?>(null)
        private set

    init {
        chargerLieux()
    }

    private fun chargerLieux() {
        viewModelScope.launch {
            lieux = try {
                api.getLieux().data?.content ?: emptyList()
            } catch (_: Exception) {
                // La liste vide reste utilisable : l'organisateur creera son lieu.
                emptyList()
            }
        }
    }

    fun ajouterCreneau(creneau: CreneauSaisi) {
        creneaux = creneaux + creneau
    }

    fun retirerCreneau(index: Int) {
        creneaux = creneaux.filterIndexed { position, _ -> position != index }
    }

    /** Le nom et les deux dates suffisent : la programmation peut venir ensuite. */
    val peutEnregistrer: Boolean
        get() = nom.isNotBlank() && dateDebut != null && dateFin != null &&
            dateFin!! >= dateDebut!! && !envoiEnCours

    fun enregistrer() {
        if (!peutEnregistrer) return

        viewModelScope.launch {
            envoiEnCours = true
            erreur = null
            try {
                val cree = api.creerEvenement(
                    CreerEvenementDto(
                        nom = nom.trim(),
                        description = description.trim().ifBlank { null },
                        dateDebut = dateDebut!!,
                        dateFin = dateFin!!,
                        creneaux = creneaux.map { it.dto }
                    )
                )
                idEvenementCree = cree.data?.id
            } catch (e: Exception) {
                // Le message vient du serveur : il nomme le creneau fautif, ce
                // qu'un message generique ne ferait pas.
                erreur = e.message ?: "L'événement n'a pas pu être créé."
            } finally {
                envoiEnCours = false
            }
        }
    }
}

class EcranCreerEvenementVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: CreerEvenementViewModel = viewModel(key = "creer_evenement") {
            CreerEvenementViewModel()
        }

        var calendrierDebut by remember { mutableStateOf(false) }
        var calendrierFin by remember { mutableStateOf(false) }
        var dialogueCreneau by remember { mutableStateOf(false) }

        // Une fois l'evenement cree, on ouvre sa programmation : c'est de la
        // qu'on complete l'affiche, et cela confirme visuellement la creation.
        LaunchedEffect(modeleVue.idEvenementCree) {
            modeleVue.idEvenementCree?.let { id ->
                navigateur.replace(EcranProgrammationVoyager(idEvenement = id))
            }
        }

        if (calendrierDebut) {
            SelecteurDate(
                titre = "Premier jour",
                dateChoisie = modeleVue.dateDebut,
                dateMin = aujourdHuiLocal(),
                onChoisir = {
                    modeleVue.dateDebut = it
                    // Une date de fin anterieure n'a plus de sens : on la suit.
                    if (modeleVue.dateFin == null || modeleVue.dateFin!! < it) modeleVue.dateFin = it
                    calendrierDebut = false
                },
                onFermer = { calendrierDebut = false }
            )
        }

        if (calendrierFin) {
            SelecteurDate(
                titre = "Dernier jour",
                dateChoisie = modeleVue.dateFin,
                dateMin = modeleVue.dateDebut ?: aujourdHuiLocal(),
                onChoisir = { modeleVue.dateFin = it; calendrierFin = false },
                onFermer = { calendrierFin = false }
            )
        }

        if (dialogueCreneau) {
            DialogueCreneau(
                lieux = modeleVue.lieux,
                dateMin = modeleVue.dateDebut,
                dateMax = modeleVue.dateFin,
                onAnnuler = { dialogueCreneau = false },
                onValider = { dto, nomArtiste ->
                    modeleVue.ajouterCreneau(CreneauSaisi(dto, nomArtiste))
                    dialogueCreneau = false
                }
            )
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Créer un événement", color = KaloyTextPrimary) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacement)
                    .background(KaloyDarkBg)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = modeleVue.nom,
                    onValueChange = { modeleVue.nom = it },
                    label = { Text("Nom de l'événement") },
                    singleLine = true,
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = modeleVue.description,
                    onValueChange = { modeleVue.description = it },
                    label = { Text("Description (facultatif)") },
                    minLines = 3,
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { calendrierDebut = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = modeleVue.dateDebut ?: "Du…",
                            color = if (modeleVue.dateDebut == null) KaloyTextSecondary else KaloyTextPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = { calendrierFin = true },
                        enabled = modeleVue.dateDebut != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = modeleVue.dateFin ?: "Au…",
                            color = if (modeleVue.dateFin == null) KaloyTextSecondary else KaloyTextPrimary
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Programmation (${modeleVue.creneaux.size})",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Facultatif : vous pourrez inviter d'autres artistes plus tard.",
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(8.dp))

                modeleVue.creneaux.forEachIndexed { index, creneau ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(creneau.nomArtiste, color = KaloyTextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = creneau.dto.debut.replace("T", " à "),
                                    color = KaloyTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { modeleVue.retirerCreneau(index) }) {
                                Text("Retirer", color = KaloyPink)
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { dialogueCreneau = true },
                    enabled = modeleVue.dateDebut != null && modeleVue.dateFin != null
                ) {
                    Text(
                        text = "+ Inviter un artiste",
                        color = if (modeleVue.dateDebut != null) KaloyCyan else KaloyTextMuted
                    )
                }

                modeleVue.erreur?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = KaloyPink, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { modeleVue.enregistrer() },
                    enabled = modeleVue.peutEnregistrer,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (modeleVue.envoiEnCours) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = KaloyTextPrimary
                        )
                    } else {
                        Text("Créer l'événement")
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
