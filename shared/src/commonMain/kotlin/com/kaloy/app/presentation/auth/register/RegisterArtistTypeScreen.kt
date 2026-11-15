package com.kaloy.app.presentation.auth.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kaloy.shared.generated.resources.Res
import kaloy.shared.generated.resources.baloo2
import org.jetbrains.compose.resources.Font
import com.kaloy.app.presentation.auth.gate.AnnulerActionSiSortieDuFlux
import com.kaloy.app.presentation.auth.gate.AuthGateCoordinator
import com.kaloy.app.presentation.auth.gate.EcranFluxAuthentification
import com.kaloy.app.presentation.common.SansBarreDuBas
import com.kaloy.app.ui.theme.KaloyCarteVoilee
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyNuit
import com.kaloy.app.ui.theme.KaloyOrange
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import com.kaloy.app.ui.theme.fondKaloy
import org.koin.compose.koinInject

data class RegisterArtistTypeScreen(
    val email: String,
    val phone: String,
    val password: String,
    val otpChannel: String
) : Screen, SansBarreDuBas, EcranFluxAuthentification {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AnnulerActionSiSortieDuFlux(navigator, koinInject<AuthGateCoordinator>())
        var artistType by remember { mutableStateOf("SOLO") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .fondKaloy()
        ) {
            // Bouton retour flottant sur le fond photo
            TextButton(
                onClick = { navigator.pop() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = KaloyTextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retour", color = KaloyTextSecondary)
            }

            // Carte bottom sheet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.70f)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(containerColor = KaloyCarteVoilee)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Text(
                        text = "Type d'artiste",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily(Font(Res.font.baloo2, FontWeight.Bold))
                        ),
                        fontWeight = FontWeight.Bold,
                        color = KaloyTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Êtes-vous un artiste solo ou en groupe ?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card SOLO
                    Card(
                        onClick = { artistType = "SOLO" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (artistType == "SOLO")
                                KaloyOrange.copy(alpha = 0.15f)
                            else
                                KaloyDarkElevated
                        ),
                        border = if (artistType == "SOLO")
                            BorderStroke(1.5.dp, KaloyOrange)
                        else
                            BorderStroke(1.dp, KaloyTextSecondary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = artistType == "SOLO",
                                onClick = { artistType = "SOLO" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = KaloyOrange,
                                    unselectedColor = KaloyTextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Solo",
                                    fontWeight = FontWeight.SemiBold,
                                    color = KaloyTextPrimary
                                )
                                Text(
                                    text = "Artiste individuel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KaloyTextSecondary
                                )
                            }
                        }
                    }

                    // Card GROUPE
                    Card(
                        onClick = { artistType = "GROUP" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (artistType == "GROUP")
                                KaloyOrange.copy(alpha = 0.15f)
                            else
                                KaloyDarkElevated
                        ),
                        border = if (artistType == "GROUP")
                            BorderStroke(1.5.dp, KaloyOrange)
                        else
                            BorderStroke(1.dp, KaloyTextSecondary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = artistType == "GROUP",
                                onClick = { artistType = "GROUP" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = KaloyOrange,
                                    unselectedColor = KaloyTextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Groupe",
                                    fontWeight = FontWeight.SemiBold,
                                    color = KaloyTextPrimary
                                )
                                Text(
                                    text = "Groupe musical ou collectif",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KaloyTextSecondary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            navigator.push(
                                RegisterArtistDetailsScreen(
                                    email = email,
                                    phone = phone,
                                    password = password,
                                    otpChannel = otpChannel,
                                    artistType = artistType
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaloyOrange,
                            contentColor = KaloyNuit
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Suivant",
                                modifier = Modifier.align(Alignment.Center),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}
