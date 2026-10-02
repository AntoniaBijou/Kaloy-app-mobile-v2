package com.kaloy.app.presentation.notifications

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
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Concert
import com.kaloy.app.presentation.evenement.EcranDetailEvenementVoyager
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================
// Centre de notifications
//
// Pour l'instant, la seule chose qui notifie un artiste est une invitation a
// participer a un evenement. L'ecran est structure pour en accueillir d'autres
// (Sprint 7) sans etre repense.
// ============================================================

class NotificationsViewModel : ViewModel() {
    private val api = KaloyApi()

    var invitations by mutableStateOf<List<Concert>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    /** Invitation en cours de traitement : evite un double appui sur un bouton. */
    var reponseEnCours by mutableStateOf<Long?>(null)
        private set

    /** Message de confirmation affiche apres une reponse. */
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
                invitations = api.getMesInvitations().data ?: emptyList()
            } catch (e: Exception) {
                erreur = "Impossible de charger les invitations : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    /**
     * Enregistre la reponse, puis retire l'invitation de la liste.
     *
     * Contrairement au bouton d'abonnement, la mise a jour n'est pas optimiste :
     * une reponse est definitive et le serveur peut la refuser (invitation deja
     * traitee, date passee). Retirer la ligne avant d'avoir la confirmation
     * donnerait a croire que c'est fait alors que rien n'a change en base.
     */
    fun repondre(invitation: Concert, accepte: Boolean) {
        if (reponseEnCours != null) return

        viewModelScope.launch {
            reponseEnCours = invitation.id
            message = null
            try {
                api.repondreInvitation(
                    idConcert = invitation.id,
                    statut = if (accepte) STATUT_ACCEPTE else STATUT_REFUSE
                )
                invitations = invitations.filterNot { it.id == invitation.id }
                message = if (accepte) {
                    "Invitation acceptée — le concert est dans votre calendrier."
                } else {
                    "Invitation refusée."
                }
            } catch (e: Exception) {
                erreur = e.message ?: "La réponse n'a pas pu être enregistrée."
            } finally {
                reponseEnCours = null
            }
        }
    }

    fun effacerMessage() {
        message = null
        erreur = null
    }

    companion object {
        const val STATUT_ACCEPTE = "CONFIRMED"
        const val STATUT_REFUSE = "DECLINED"
    }
}

class EcranNotificationsVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: NotificationsViewModel = viewModel(key = "notifications") {
            NotificationsViewModel()
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Notifications", color = KaloyTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { navigateur.pop() }) {
                            Text("←", fontSize = 22.sp, color = KaloyTextPrimary)
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
                when {
                    modeleVue.enChargement -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = KaloyPurple
                        )
                    }

                    modeleVue.erreur != null && modeleVue.invitations.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = modeleVue.erreur!!,
                                color = KaloyTextSecondary
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

                    modeleVue.invitations.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔔", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = modeleVue.message ?: "Aucune invitation en attente",
                                color = KaloyTextSecondary
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Le message de la reponse precedente reste visible
                            // tant que l'utilisateur n'a pas quitte l'ecran : il
                            // n'y a pas de Snackbar dans cette application.
                            modeleVue.message?.let { texte ->
                                item {
                                    Text(
                                        text = texte,
                                        color = KaloyCyan,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                            modeleVue.erreur?.let { texte ->
                                item {
                                    Text(text = texte, color = KaloyPink)
                                }
                            }

                            item {
                                Text(
                                    text = "Invitations (${modeleVue.invitations.size})",
                                    color = KaloyTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            items(modeleVue.invitations, key = { it.id }) { invitation ->
                                CarteInvitation(
                                    invitation = invitation,
                                    enCours = modeleVue.reponseEnCours == invitation.id,
                                    onVoirEvenement = {
                                        invitation.event?.let {
                                            navigateur.push(EcranDetailEvenementVoyager(idEvenement = it.id))
                                        }
                                    },
                                    onAccepter = { modeleVue.repondre(invitation, accepte = true) },
                                    onRefuser = { modeleVue.repondre(invitation, accepte = false) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteInvitation(
    invitation: Concert,
    enCours: Boolean,
    onVoirEvenement: () -> Unit,
    onAccepter: () -> Unit,
    onRefuser: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = invitation.event?.name ?: "Événement",
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            invitation.title?.let { titre ->
                Text(
                    text = titre,
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "📅 ${dateEtHeure(invitation.startTime)}",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            invitation.venue?.let { lieu ->
                val adresse = if (lieu.location.isNullOrBlank()) lieu.name else "${lieu.name} — ${lieu.location}"
                Text(
                    text = "📍 $adresse",
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (invitation.event != null) {
                TextButton(onClick = onVoirEvenement, contentPadding = PaddingValues(0.dp)) {
                    Text("Voir l'événement", color = KaloyCyan)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAccepter,
                    enabled = !enCours,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                ) {
                    if (enCours) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = KaloyTextPrimary
                        )
                    } else {
                        Text("Accepter")
                    }
                }

                // Le refus est volontairement moins appuye que l'acceptation :
                // il est definitif et ne peut pas etre repris.
                OutlinedButton(
                    onClick = onRefuser,
                    enabled = !enCours,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyTextSecondary)
                ) {
                    Text("Refuser")
                }
            }
        }
    }
}

/**
 * « 2026-12-20T19:00:00 » -> « 20/12/2026 à 19:00 ».
 * Renvoie « date inconnue » plutot que de planter si le format surprend.
 */
private fun dateEtHeure(iso: String?): String {
    if (iso.isNullOrBlank()) return "date inconnue"
    val date = iso.substringBefore('T').split("-")
    if (date.size != 3) return "date inconnue"
    val heure = iso.substringAfter('T', "").take(5)
    val jour = "${date[2]}/${date[1]}/${date[0]}"
    return if (heure.isBlank()) jour else "$jour à $heure"
}
