package com.kaloy.app.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.NotificationItem
import com.kaloy.app.data.model.PreferenceNotification
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================
// Preferences de notification
//
// Un interrupteur par type. Le serveur connait huit types ; l'ecran n'affiche
// que ceux qu'un code produit reellement, listes dans
// PreferenceNotification.TYPES_REGLABLES. Les six autres existent en base mais
// aucun producteur ne les emet : les montrer donnerait des interrupteurs sans
// effet, ce qui se remarque au premier essai et fait douter du reste.
//
// Le mecanisme serveur, lui, respecte la preference de n'importe quel type. Le
// jour ou FOLLOW sera emis, il suffira d'allonger cette liste.
// ============================================================

class PreferencesNotificationsViewModel : ViewModel() {
    private val api = KaloyApi()

    /** Etat par type, limite aux types reglables. */
    var preferences by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    /** Type dont l'enregistrement est en cours : son interrupteur se verrouille. */
    var enregistrementEnCours by mutableStateOf<String?>(null)
        private set

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val recues = api.getMesPreferencesNotification().data ?: emptyList()
                // Le serveur renvoie les huit types ; on ne garde que ceux que
                // l'ecran sait presenter. Un type reglable absent de la reponse
                // — base incomplete — vaut actif, comme cote serveur.
                preferences = PreferenceNotification.TYPES_REGLABLES.associateWith { type ->
                    recues.firstOrNull { it.type == type }?.isEnabled ?: true
                }
            } catch (e: Exception) {
                erreur = "Impossible de charger les préférences : ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    /**
     * Bascule un type.
     *
     * L'interrupteur se deplace tout de suite, puis revient si le serveur
     * refuse. Contrairement a une reponse d'invitation, un reglage est sans
     * consequence et reversible : attendre la confirmation donnerait un
     * interrupteur qui colle, pour aucun gain.
     */
    fun basculer(type: String, actif: Boolean) {
        if (enregistrementEnCours != null) return
        val precedent = preferences[type] ?: true

        viewModelScope.launch {
            enregistrementEnCours = type
            erreur = null
            preferences = preferences + (type to actif)
            try {
                api.definirPreferenceNotification(type, actif)
            } catch (e: Exception) {
                preferences = preferences + (type to precedent)
                erreur = e.message ?: "Le réglage n'a pas pu être enregistré."
            } finally {
                enregistrementEnCours = null
            }
        }
    }
}

class EcranPreferencesNotificationsVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: PreferencesNotificationsViewModel =
            viewModel(key = "preferences-notifications") {
                PreferencesNotificationsViewModel()
            }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Préférences", color = KaloyTextPrimary) },
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
                when {
                    modeleVue.enChargement -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = KaloyPurple
                        )
                    }

                    modeleVue.erreur != null && modeleVue.preferences.isEmpty() -> {
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

                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Choisissez ce que vous souhaitez recevoir. "
                                        + "Un type désactivé n'apparaîtra plus dans vos notifications.",
                                color = KaloyTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )

                            modeleVue.erreur?.let { texte ->
                                Text(text = texte, color = KaloyPink)
                            }

                            PreferenceNotification.TYPES_REGLABLES.forEach { type ->
                                LignePreference(
                                    titre = libellePourType(type),
                                    explication = explicationPourType(type),
                                    actif = modeleVue.preferences[type] ?: true,
                                    verrouille = modeleVue.enregistrementEnCours != null,
                                    onBasculer = { modeleVue.basculer(type, it) }
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
private fun LignePreference(
    titre: String,
    explication: String,
    actif: Boolean,
    verrouille: Boolean,
    onBasculer: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titre,
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = explication,
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = actif,
                onCheckedChange = onBasculer,
                enabled = !verrouille,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KaloyTextPrimary,
                    checkedTrackColor = KaloyPurple
                )
            )
        }
    }
}

/**
 * Ce que l'utilisateur perd en coupant un type.
 *
 * Formule du point de vue de la consequence et non du mecanisme : « vous ne
 * saurez plus » se comprend sans connaitre la notion de type de notification.
 */
private fun explicationPourType(type: String): String = when (type) {
    NotificationItem.TYPE_INVITATION ->
        "Quand un organisateur vous invite à jouer."
    NotificationItem.TYPE_REPONSE ->
        "Quand un artiste accepte ou refuse une invitation que vous avez envoyée."
    else ->
        "Notifications de ce type."
}
