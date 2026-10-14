package com.kaloy.app.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kaloy.app.data.model.NotificationItem
import com.kaloy.app.presentation.evenement.EcranDetailEvenementVoyager
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

// ============================================================
// Centre de notifications
//
// L'ecran lit /notifications/me, qui porte tous les types — invitations,
// reponses recues, et ce que le backend produira ensuite. Il lit en plus
// /invitations, parce qu'une notification ne transporte qu'une ligne de texte
// et une cible : le lieu, l'evenement et les boutons Accepter / Refuser
// demandent l'objet Concert complet, retrouve par targetId.
//
// Deux appels plutot qu'un, donc, mais lances en parallele et deja existants.
// L'alternative — enrichir la notification cote serveur — aurait fait entrer
// la notion de concert dans une brique voulue generique.
// ============================================================

class NotificationsViewModel : ViewModel() {
    private val api = KaloyApi()

    var notifications by mutableStateOf<List<NotificationItem>>(emptyList())
        private set

    /**
     * Concerts des invitations encore ouvertes, par identifiant.
     *
     * /invitations n'expose que celles qui sont en attente et a venir. Une
     * notification d'invitation absente de cette carte est donc une invitation
     * deja repondue ou perimee : sa carte s'affiche sans boutons, ce qui est
     * exactement le comportement voulu — on garde la trace, on n'y agit plus.
     */
    var invitationsOuvertes by mutableStateOf<Map<Long, Concert>>(emptyMap())
        private set

    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    /** Notification en cours de traitement : evite un double appui sur un bouton. */
    var reponseEnCours by mutableStateOf<Long?>(null)
        private set

    /** Message de confirmation affiche apres une reponse. */
    var message by mutableStateOf<String?>(null)
        private set

    val nombreNonLues: Int get() = notifications.count { !it.isRead }

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                // En parallele : l'un ne depend pas de l'autre, et l'ecran
                // n'affiche rien avant d'avoir les deux.
                val lesNotifications = async { api.getMesNotifications().data?.content ?: emptyList() }
                val lesInvitations = async { api.getMesInvitations().data ?: emptyList() }

                notifications = lesNotifications.await()
                invitationsOuvertes = lesInvitations.await().associateBy { it.id }
            } catch (e: Exception) {
                erreur = "Impossible de charger les notifications : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    /**
     * Enregistre la reponse a une invitation, puis marque la notification lue.
     *
     * La mise a jour n'est pas optimiste : une reponse est definitive et le
     * serveur peut la refuser (deja traitee, date passee). Retirer les boutons
     * avant d'avoir la confirmation donnerait a croire que c'est fait alors que
     * rien n'a change en base.
     *
     * Le marquage en lu suit la reponse et non l'inverse : si le marquage
     * echoue, la reponse est quand meme enregistree, et seule la pastille reste
     * fausse jusqu'au prochain chargement.
     */
    fun repondre(notification: NotificationItem, idConcert: Long, accepte: Boolean) {
        if (reponseEnCours != null) return

        viewModelScope.launch {
            reponseEnCours = notification.id
            message = null
            try {
                api.repondreInvitation(
                    idConcert = idConcert,
                    statut = if (accepte) STATUT_ACCEPTE else STATUT_REFUSE
                )
                // L'invitation n'est plus ouverte : les boutons disparaissent,
                // la notification reste dans la liste comme trace.
                invitationsOuvertes = invitationsOuvertes - idConcert
                marquerLueLocalement(notification.id)
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

    /** Marque une notification lue au moment ou l'utilisateur la consulte. */
    fun marquerLue(notification: NotificationItem) {
        if (notification.isRead) return
        viewModelScope.launch {
            marquerLueLocalement(notification.id)
        }
    }

    fun toutMarquerLu() {
        if (nombreNonLues == 0) return
        viewModelScope.launch {
            try {
                api.marquerToutesNotificationsLues()
                notifications = notifications.map { it.copy(isRead = true) }
            } catch (_: Exception) {
                // Sans consequence : la pastille se corrigera au prochain
                // chargement. Afficher une erreur pour un marquage de lecture
                // serait disproportionne.
            }
        }
    }

    private suspend fun marquerLueLocalement(idNotification: Long) {
        try {
            api.marquerNotificationLue(idNotification)
            notifications = notifications.map {
                if (it.id == idNotification) it.copy(isRead = true) else it
            }
        } catch (_: Exception) {
            // Idem : un marquage rate ne doit pas interrompre l'utilisateur.
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    actions = {
                        // N'apparait que s'il y a quelque chose a marquer : une
                        // action sans effet desoriente plus qu'elle ne sert.
                        if (modeleVue.nombreNonLues > 0) {
                            IconButton(onClick = { modeleVue.toutMarquerLu() }) {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = "Tout marquer lu",
                                    tint = KaloyTextSecondary
                                )
                            }
                        }
                        // Les reglages des notifications sont ici plutot que dans
                        // une sixieme carte de l'onglet « Moi », qui en compte
                        // deja cinq : on regle ce qu'on est en train de lire.
                        IconButton(
                            onClick = { navigateur.push(EcranPreferencesNotificationsVoyager()) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Préférences de notification",
                                tint = KaloyTextSecondary
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
                when {
                    modeleVue.enChargement -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = KaloyPurple
                        )
                    }

                    modeleVue.erreur != null && modeleVue.notifications.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = modeleVue.erreur!!, color = KaloyTextSecondary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { modeleVue.charger() },
                                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }

                    modeleVue.notifications.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = KaloyTextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = modeleVue.message ?: "Aucune notification",
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
                                item { Text(text = texte, color = KaloyPink) }
                            }

                            items(modeleVue.notifications, key = { it.id }) { notification ->
                                val concert = notification.targetId
                                    ?.takeIf { notification.estInvitation }
                                    ?.let { modeleVue.invitationsOuvertes[it] }

                                if (concert != null) {
                                    CarteInvitation(
                                        notification = notification,
                                        invitation = concert,
                                        enCours = modeleVue.reponseEnCours == notification.id,
                                        onVoirEvenement = {
                                            concert.event?.let {
                                                modeleVue.marquerLue(notification)
                                                navigateur.push(
                                                    EcranDetailEvenementVoyager(idEvenement = it.id)
                                                )
                                            }
                                        },
                                        onAccepter = {
                                            modeleVue.repondre(notification, concert.id, accepte = true)
                                        },
                                        onRefuser = {
                                            modeleVue.repondre(notification, concert.id, accepte = false)
                                        }
                                    )
                                } else {
                                    CarteNotificationSimple(
                                        notification = notification,
                                        onClick = { modeleVue.marquerLue(notification) }
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

/**
 * Carte d'une invitation encore ouverte : tous les details, et les deux boutons.
 *
 * Le contenu vient de l'objet Concert et non du texte de la notification : la
 * notification ne porte qu'une phrase, sans le lieu ni le lien vers
 * l'evenement.
 */
@Composable
private fun CarteInvitation(
    notification: NotificationItem,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!notification.isRead) {
                    PastilleNonLue()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = invitation.event?.name ?: "Événement",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            invitation.title?.let { titre ->
                Text(
                    text = titre,
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LigneDetail(
                icone = Icons.Filled.CalendarMonth,
                texte = dateEtHeure(invitation.startTime)
            )

            invitation.venue?.let { lieu ->
                val adresse = if (lieu.location.isNullOrBlank()) lieu.name
                else "${lieu.name} — ${lieu.location}"
                LigneDetail(icone = Icons.Filled.Place, texte = adresse)
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
 * Carte d'une notification sur laquelle il n'y a rien a faire.
 *
 * Trois cas y aboutissent : un type autre qu'une invitation, une invitation
 * deja repondue, et une invitation dont la date est passee. Dans les deux
 * derniers, la trace reste visible mais sans boutons — le serveur refuserait
 * de toute facon une seconde reponse.
 */
@Composable
private fun CarteNotificationSimple(
    notification: NotificationItem,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconePourType(notification.type),
                contentDescription = null,
                tint = KaloyPurpleLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = libellePourType(notification.type),
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = notification.content,
                    color = KaloyTextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!notification.isRead) {
                Spacer(modifier = Modifier.width(8.dp))
                PastilleNonLue()
            }
        }
    }
}

/** Point plein signalant une notification non lue. */
@Composable
private fun PastilleNonLue() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(KaloyCyan)
    )
}

/**
 * Libelle d'un type de notification.
 *
 * Les types inconnus tombent sur « Notification » plutot que d'afficher le nom
 * technique : le backend peut en ajouter sans que l'application soit remise a
 * jour, et « NEW_RELEASE » a l'ecran serait plus deroutant qu'un mot generique.
 */
internal fun libellePourType(type: String): String = when (type) {
    NotificationItem.TYPE_INVITATION -> "Invitation"
    NotificationItem.TYPE_REPONSE -> "Réponse à votre invitation"
    "FOLLOW" -> "Nouvel abonné"
    "NEW_RELEASE" -> "Nouveauté"
    "EVENT_REMINDER" -> "Rappel d'événement"
    "EVENT_INVITATION" -> "Invitation à un événement"
    "COMMENT_REPLY" -> "Réponse à un commentaire"
    "VERIFICATION" -> "Vérification de compte"
    else -> "Notification"
}

private fun iconePourType(type: String): ImageVector = when (type) {
    NotificationItem.TYPE_INVITATION,
    NotificationItem.TYPE_REPONSE,
    "EVENT_REMINDER",
    "EVENT_INVITATION" -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.Notifications
}

/**
 * Ligne de detail d'une invitation : une icone, puis le texte.
 *
 * Les deux lignes — date et lieu — partagent la meme mise en forme ; les ecrire
 * deux fois aurait fini par les faire diverger.
 */
@Composable
private fun LigneDetail(icone: ImageVector, texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = KaloyTextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = texte,
            color = KaloyTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
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
