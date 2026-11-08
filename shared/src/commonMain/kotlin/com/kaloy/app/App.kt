package com.kaloy.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.kaloy.app.core.network.refreshStoredSession
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.repository.MeRepository
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.home.HomeScreen
import com.kaloy.app.ui.theme.KaloyTheme
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

@Composable
fun App() {
    val sessionManager = koinInject<AuthSessionManager>()
    val meRepository = koinInject<MeRepository>()
    val httpClient = koinInject<HttpClient>()
    var initialScreen by remember { mutableStateOf<Screen?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var startupAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessionManager, startupAttempt) {
        initialScreen = null
        startupError = null

        if (!sessionManager.hasStoredSession()) {
            sessionManager.clear()
            initialScreen = WelcomeScreen()
            return@LaunchedEffect
        }

        try {
            if (
                sessionManager.getToken().isNullOrBlank() &&
                !refreshStoredSession(httpClient, sessionManager)
            ) {
                sessionManager.clear()
                initialScreen = WelcomeScreen()
                return@LaunchedEffect
            }

            meRepository.getProfile()
            initialScreen = HomeScreen(username = sessionManager.getDisplayName())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error.message == "SESSION_EXPIRED" || !sessionManager.hasStoredSession()) {
                sessionManager.clear()
                initialScreen = WelcomeScreen()
            } else {
                startupError =
                    "Impossible de vérifier la session. Vérifiez votre connexion puis réessayez."
            }
        }
    }

    KaloyTheme {
        when {
            initialScreen != null -> Navigator(screen = initialScreen!!)
            startupError != null -> SessionStartupError(
                message = startupError!!,
                onRetry = { startupAttempt += 1 },
                onContinueAsVisitor = { initialScreen = WelcomeScreen() }
            )
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun SessionStartupError(
    message: String,
    onRetry: () -> Unit,
    onContinueAsVisitor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Réessayer")
        }
        OutlinedButton(
            onClick = onContinueAsVisitor,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Continuer sans compte")
        }
    }
}
