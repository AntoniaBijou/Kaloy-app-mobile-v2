package com.kaloy.app.presentation.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.kaloy.app.ui.theme.KaloyRed
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import com.kaloy.app.ui.theme.fondKaloy
import org.koin.compose.koinInject

class RegisterStep1Screen : Screen, SansBarreDuBas, EcranFluxAuthentification {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AnnulerActionSiSortieDuFlux(navigator, koinInject<AuthGateCoordinator>())

        var email by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var accountType by remember { mutableStateOf("CLIENT") }
        val otpChannel = "EMAIL"
        var emailError by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf("") }
        var motDePasseVisible by remember { mutableStateOf(false) }

        val formeChamp = RoundedCornerShape(14.dp)
        val couleursChamp = OutlinedTextFieldDefaults.colors(
            focusedTextColor = KaloyTextPrimary,
            unfocusedTextColor = KaloyTextPrimary,
            focusedBorderColor = KaloyOrange,
            unfocusedBorderColor = KaloyTextSecondary,
            focusedLabelColor = KaloyOrange,
            unfocusedLabelColor = KaloyTextSecondary,
            focusedContainerColor = KaloyDarkElevated,
            unfocusedContainerColor = KaloyDarkElevated,
            errorBorderColor = KaloyRed,
            errorLabelColor = KaloyRed
        )

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

            // Carte bottom sheet — coins ronds en haut, plat en bas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
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
                        text = "Créer un compte",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily(Font(Res.font.baloo2, FontWeight.Bold))
                        ),
                        fontWeight = FontWeight.Bold,
                        color = KaloyTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Rejoignez la communauté Kaloy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = "" },
                        label = { Text("Email *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                tint = KaloyTextSecondary
                            )
                        },
                        isError = emailError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = formeChamp,
                        colors = couleursChamp
                    )
                    if (emailError.isNotEmpty()) {
                        Text(
                            text = emailError,
                            color = KaloyRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone (optionnel)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                tint = KaloyTextSecondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = formeChamp,
                        colors = couleursChamp
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = "" },
                        label = { Text("Mot de passe *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = KaloyTextSecondary
                            )
                        },
                        visualTransformation = if (motDePasseVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { motDePasseVisible = !motDePasseVisible }) {
                                Icon(
                                    imageVector = if (motDePasseVisible) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = if (motDePasseVisible) {
                                        "Masquer le mot de passe"
                                    } else {
                                        "Afficher le mot de passe"
                                    },
                                    tint = if (motDePasseVisible) KaloyOrange else KaloyTextSecondary
                                )
                            }
                        },
                        isError = passwordError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = formeChamp,
                        colors = couleursChamp
                    )
                    if (passwordError.isNotEmpty()) {
                        Text(
                            text = passwordError,
                            color = KaloyRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    // Section type de compte
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Type de compte",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = KaloyTextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = accountType == "CLIENT",
                                onClick = { accountType = "CLIENT" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = KaloyOrange,
                                    unselectedColor = KaloyTextSecondary
                                )
                            )
                            Text("Client", color = KaloyTextPrimary)
                            Spacer(modifier = Modifier.width(24.dp))
                            RadioButton(
                                selected = accountType == "ARTISTE",
                                onClick = { accountType = "ARTISTE" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = KaloyOrange,
                                    unselectedColor = KaloyTextSecondary
                                )
                            )
                            Text("Artiste", color = KaloyTextPrimary)
                        }
                    }

                    Button(
                        onClick = {
                            var valid = true
                            if (email.isBlank() || !email.contains("@")) {
                                emailError = "Email invalide"
                                valid = false
                            }
                            if (password.length < 6) {
                                passwordError = "Minimum 6 caractères"
                                valid = false
                            }
                            if (!valid) return@Button

                            if (accountType == "CLIENT") {
                                navigator.push(
                                    RegisterClientScreen(
                                        email = email,
                                        phone = phone,
                                        password = password,
                                        otpChannel = otpChannel
                                    )
                                )
                            } else {
                                navigator.push(
                                    RegisterArtistTypeScreen(
                                        email = email,
                                        phone = phone,
                                        password = password,
                                        otpChannel = otpChannel
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = formeChamp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaloyOrange,
                            contentColor = KaloyNuit
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Continuer",
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

                    // Séparateur OU
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = KaloyTextSecondary.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "  OU  ",
                            color = KaloyTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = KaloyTextSecondary.copy(alpha = 0.3f)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Vous avez déjà un compte ?",
                            style = MaterialTheme.typography.bodySmall,
                            color = KaloyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = { navigator.pop() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Se connecter",
                                color = KaloyOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
