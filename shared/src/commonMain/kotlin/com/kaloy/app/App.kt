package com.kaloy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.home.HomeScreen
import com.kaloy.app.ui.theme.KaloyTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val sessionManager = koinInject<AuthSessionManager>()
    val initialScreen: Screen = remember(sessionManager) {
        if (sessionManager.isLoggedIn()) {
            HomeScreen(username = sessionManager.getDisplayName())
        } else {
            WelcomeScreen()
        }
    }

    KaloyTheme {
        Navigator(screen = initialScreen)
    }
}