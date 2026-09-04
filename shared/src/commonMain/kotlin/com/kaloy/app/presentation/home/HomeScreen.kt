package com.kaloy.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.ui.theme.*
import org.koin.compose.koinInject

data class HomeScreen(
    val username: String = "Utilisateur",
    val isVisitor: Boolean = false
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sessionManager = koinInject<AuthSessionManager>()
        val displayName = if (isVisitor) username else sessionManager.getDisplayName().ifBlank { username }

        LaunchedEffect(Unit) {
            if (!isVisitor && !sessionManager.isLoggedIn()) {
                navigator.replace(WelcomeScreen())
            }
        }

        Scaffold(
            bottomBar = {
                if (!isVisitor) {
                    NavigationBar(containerColor = KaloyDarkSurface) {
                        NavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = {
                                Icon(Icons.Default.Home, contentDescription = "Accueil")
                            },
                            label = { Text("Accueil") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = {
                                Icon(Icons.Default.Search, contentDescription = "Recherche")
                            },
                            label = { Text("Recherche") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { navigator.push(MoiScreen()) },
                            icon = {
                                Icon(Icons.Default.Person, contentDescription = "Mon profil")
                            },
                            label = { Text("Moi") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                    }
                }
            },
            containerColor = KaloyDarkBg
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = KaloyDarkCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Bienvenue",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = KaloyTextPrimary
                        )

                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = KaloyPurple,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Explorez la musique malgache et découvrez des artistes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KaloyTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (isVisitor) {
                            Button(
                                onClick = { navigator.replace(WelcomeScreen()) },
                                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Créer un compte")
                            }
                        }
                    }
                }
            }
        }
    }
}
