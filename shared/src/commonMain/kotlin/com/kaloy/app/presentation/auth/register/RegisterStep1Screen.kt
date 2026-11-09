package com.kaloy.app.presentation.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.ui.theme.KaloyDarkCard
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyPurpleLight
import com.kaloy.app.ui.theme.KaloyRed
import com.kaloy.app.ui.theme.KaloyTextMuted
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import com.kaloy.app.ui.theme.fondKaloy

class RegisterStep1Screen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        var email by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var accountType by remember { mutableStateOf("CLIENT") }
        var otpChannel by remember { mutableStateOf("EMAIL") }
        var emailError by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf("") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .fondKaloy()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Créer un compte",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = KaloyTextPrimary
                )

                Text(
                    text = "Rejoignez la communauté Kaloy et accordez votre identité musicale.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KaloyTextSecondary,
                    textAlign = TextAlign.Start
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = KaloyDarkCard.copy(alpha = 0.92f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; emailError = "" },
                            label = { Text("Email *") },
                            isError = emailError.isNotEmpty(),
                            supportingText = { if (emailError.isNotEmpty()) Text(emailError) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = KaloyTextPrimary,
                                unfocusedTextColor = KaloyTextPrimary,
                                focusedBorderColor = KaloyPurpleLight,
                                unfocusedBorderColor = KaloyTextMuted,
                                focusedContainerColor = KaloyDarkElevated,
                                unfocusedContainerColor = KaloyDarkElevated,
                                cursorColor = KaloyPurpleLight,
                                focusedLabelColor = KaloyPurpleLight,
                                unfocusedLabelColor = KaloyTextSecondary,
                                errorBorderColor = KaloyRed
                            )
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Numéro de téléphone / Contact") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = KaloyTextPrimary,
                                unfocusedTextColor = KaloyTextPrimary,
                                focusedBorderColor = KaloyPurpleLight,
                                unfocusedBorderColor = KaloyTextMuted,
                                focusedContainerColor = KaloyDarkElevated,
                                unfocusedContainerColor = KaloyDarkElevated,
                                cursorColor = KaloyPurpleLight,
                                focusedLabelColor = KaloyPurpleLight,
                                unfocusedLabelColor = KaloyTextSecondary
                            )
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; passwordError = "" },
                            label = { Text("Mot de passe *") },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = passwordError.isNotEmpty(),
                            supportingText = { if (passwordError.isNotEmpty()) Text(passwordError) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = KaloyTextPrimary,
                                unfocusedTextColor = KaloyTextPrimary,
                                focusedBorderColor = KaloyPurpleLight,
                                unfocusedBorderColor = KaloyTextMuted,
                                focusedContainerColor = KaloyDarkElevated,
                                unfocusedContainerColor = KaloyDarkElevated,
                                cursorColor = KaloyPurpleLight,
                                focusedLabelColor = KaloyPurpleLight,
                                unfocusedLabelColor = KaloyTextSecondary,
                                errorBorderColor = KaloyRed
                            )
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = KaloyDarkCard.copy(alpha = 0.92f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Type de compte",
                            style = MaterialTheme.typography.labelLarge,
                            color = KaloyTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = accountType == "CLIENT",
                                onClick = { accountType = "CLIENT" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = KaloyPurpleLight,
                                    unselectedColor = KaloyTextMuted
                                )
                            )
                            Text("Client", color = KaloyTextPrimary, modifier = Modifier.padding(end = 16.dp))
                            RadioButton(
                                selected = accountType == "ARTISTE",
                                onClick = { accountType = "ARTISTE" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = KaloyPurpleLight,
                                    unselectedColor = KaloyTextMuted
                                )
                            )
                            Text("Artiste", color = KaloyTextPrimary)
                        }
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
                        if (otpChannel == "SMS" && phone.isBlank()) {
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
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                ) {
                    Text("Continuer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
