package com.kaloy.app.presentation.auth.gate

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.presentation.auth.login.LoginScreen
import com.kaloy.app.presentation.auth.register.RegisterStep1Screen
import com.kaloy.app.ui.theme.KaloyCarteVoilee
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary

interface EcranFluxAuthentification

data class DemandeAuthentification(val action: String)

class AuthGateCoordinator {
    var demande by mutableStateOf<DemandeAuthentification?>(null)
        private set

    private var actionEnAttente: (() -> Unit)? = null

    fun exigerAuthentification(action: String, reprendre: () -> Unit) {
        actionEnAttente = reprendre
        demande = DemandeAuthentification(action)
    }

    fun choisirConnexionOuInscription() {
        demande = null
    }

    fun annuler() {
        demande = null
        actionEnAttente = null
    }

    fun consommerActionEnAttente(): (() -> Unit)? {
        demande = null
        return actionEnAttente.also { actionEnAttente = null }
    }

    fun actionAuthentifieeSansNavigation(): (() -> Unit)? =
        actionEnAttente.also { actionEnAttente = null }
}

fun executerOuDemanderAuthentification(
    sessionManager: AuthSessionManager,
    coordinator: AuthGateCoordinator,
    action: String,
    reprendre: () -> Unit
) {
    if (sessionManager.isLoggedIn()) {
        reprendre()
    } else {
        coordinator.exigerAuthentification(action, reprendre)
    }
}

@Composable
fun AuthGateHost(navigator: Navigator, coordinator: AuthGateCoordinator) {
    val demande = coordinator.demande ?: return

    AlertDialog(
        onDismissRequest = coordinator::annuler,
        containerColor = KaloyCarteVoilee,
        titleContentColor = KaloyTextPrimary,
        textContentColor = KaloyTextSecondary,
        title = {
            Text(
                text = "Un compte pour continuer",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Pour ${demande.action}, connectez-vous ou créez un compte. " +
                    "Une fois votre compte vérifié, vous pourrez profiter de cette fonctionnalité."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    coordinator.choisirConnexionOuInscription()
                    navigator.push(LoginScreen())
                },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                Text("Se connecter")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    coordinator.choisirConnexionOuInscription()
                    navigator.push(RegisterStep1Screen())
                }
            ) {
                Text("S'inscrire", color = KaloyTextPrimary)
            }
        },
        tonalElevation = 6.dp
    )
}

@Composable
fun AnnulerActionSiSortieDuFlux(
    navigator: Navigator,
    coordinator: AuthGateCoordinator
) {
    DisposableEffect(navigator) {
        onDispose {
            if (navigator.lastItem !is EcranFluxAuthentification) {
                coordinator.annuler()
            }
        }
    }
}
