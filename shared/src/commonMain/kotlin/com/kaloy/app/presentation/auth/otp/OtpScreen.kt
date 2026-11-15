package com.kaloy.app.presentation.auth.otp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kaloy.shared.generated.resources.Res
import kaloy.shared.generated.resources.baloo2
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.presentation.auth.gate.AnnulerActionSiSortieDuFlux
import com.kaloy.app.presentation.auth.gate.AuthGateCoordinator
import com.kaloy.app.presentation.auth.gate.EcranFluxAuthentification
import com.kaloy.app.presentation.common.SansBarreDuBas
import com.kaloy.app.ui.theme.KaloyCarteVoilee
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyNuit
import com.kaloy.app.ui.theme.KaloyOrange
import com.kaloy.app.ui.theme.KaloyRed
import com.kaloy.app.ui.theme.KaloyTextMuted
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import com.kaloy.app.ui.theme.fondKaloy
import org.koin.compose.koinInject

data class OtpScreen(
    val userId: Long,
    val email: String,
    val phone: String = "",
    val password: String = "",
    val accountType: String = "",
    val initialChannel: String = "EMAIL"
) : Screen, SansBarreDuBas, EcranFluxAuthentification {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<com.kaloy.app.data.repository.AuthRepository>()
        val sessionManager = koinInject<AuthSessionManager>()
        val authGateCoordinator = koinInject<AuthGateCoordinator>()
        val viewModel = remember { OtpViewModel(repository, userId) }
        val uiState by viewModel.uiState.collectAsState()
        AnnulerActionSiSortieDuFlux(navigator, authGateCoordinator)

        var otpValue by remember { mutableStateOf("") }
        var verificationChannel by remember { mutableStateOf(initialChannel) }
        var resendCooldown by remember { mutableStateOf(0) }
        val focusRequester = remember { FocusRequester() }

        fun maskEmail(rawEmail: String): String {
            val (localPart, domainPart) = rawEmail.split("@", limit = 2)
            val maskedLocal = if (localPart.length <= 1) localPart else "${localPart.first()}***"
            return "$maskedLocal@$domainPart"
        }

        fun maskPhone(rawPhone: String): String {
            return if (rawPhone.length >= 4) rawPhone.take(4) + " ** ** **" else rawPhone
        }

        val headerText = if (verificationChannel == "SMS") {
            if (phone.isNotBlank()) "Entrez le code à 6 chiffres envoyé par SMS au ${maskPhone(phone)}"
            else "Entrez le code à 6 chiffres envoyé par SMS"
        } else {
            "Entrez le code à 6 chiffres envoyé à ${maskEmail(email)}"
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        LaunchedEffect(resendCooldown > 0) {
            if (resendCooldown > 0) {
                delay(1000)
                resendCooldown -= 1
            }
        }

        LaunchedEffect(uiState) {
            if (uiState is OtpUiState.Resent) {
                viewModel.resetUiState()
            }
            if (uiState is OtpUiState.Success) {
                sessionManager.saveSession((uiState as OtpUiState.Success).response)
                val username = (uiState as OtpUiState.Success).response.email.substringBefore("@")
                val actionEnAttente = authGateCoordinator.actionAuthentifieeSansNavigation()
                if (actionEnAttente != null) {
                    navigator.popUntil { screen ->
                        screen !is EcranFluxAuthentification
                    }
                    if (navigator.lastItem is com.kaloy.app.presentation.common.EcranPrincipal) {
                        navigator.replace(
                            com.kaloy.app.presentation.common.EcranPrincipal(username = username)
                        )
                    }
                    actionEnAttente()
                } else {
                    navigator.replaceAll(
                        com.kaloy.app.presentation.common.EcranPrincipal(username = username)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .fondKaloy()
        ) {
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(containerColor = KaloyCarteVoilee)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Vérification",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily(Font(Res.font.baloo2, FontWeight.Bold))
                        ),
                        fontWeight = FontWeight.Bold,
                        color = KaloyTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    BasicTextField(
                        value = otpValue,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.focusRequester(focusRequester),
                        decorationBox = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(6) { index ->
                                    val char = otpValue.getOrNull(index)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .background(KaloyDarkElevated, RoundedCornerShape(12.dp))
                                            .border(
                                                width = 2.dp,
                                                color = if (otpValue.length == index)
                                                    KaloyOrange
                                                else
                                                    KaloyTextMuted,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        Text(
                                            text = char?.toString() ?: "",
                                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                            textAlign = TextAlign.Center,
                                            color = KaloyTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    )

                    when (val state = uiState) {
                        is OtpUiState.Error -> Text(
                            text = state.message,
                            color = KaloyRed,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        is OtpUiState.Resent -> Text(
                            text = "Code renvoyé avec succès.",
                            color = KaloyOrange,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        is OtpUiState.Success -> Text(
                            text = "Compte vérifié !",
                            color = KaloyOrange,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> {}
                    }

                    Button(
                        onClick = { viewModel.verifyOtp(otpValue) },
                        enabled = otpValue.length == 6 && uiState !is OtpUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaloyOrange,
                            contentColor = KaloyNuit
                        )
                    ) {
                        if (uiState is OtpUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = KaloyNuit
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Valider",
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

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Vous n'avez pas reçu le code ?",
                            color = KaloyTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = {
                                if (resendCooldown == 0) {
                                    viewModel.resendOtp()
                                    resendCooldown = 60
                                }
                            },
                            enabled = uiState !is OtpUiState.Loading && resendCooldown == 0
                        ) {
                            val resendLabel = if (verificationChannel == "SMS") {
                                "Renvoyer le code par SMS"
                            } else {
                                "Renvoyer le code par email"
                            }
                            Text(
                                text = if (resendCooldown > 0) "$resendLabel (${resendCooldown}s)" else resendLabel,
                                color = KaloyOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (verificationChannel == "SMS") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Préférez-vous recevoir le code par email ?",
                                color = KaloyTextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(
                                onClick = { verificationChannel = "EMAIL"; otpValue = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Recevoir par email",
                                    color = KaloyOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        if (phone.isNotBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Préférez-vous recevoir le code par SMS ?",
                                    color = KaloyTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(
                                    onClick = { verificationChannel = "SMS"; otpValue = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Recevoir par SMS",
                                        color = KaloyOrange,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Vous n'avez pas renseigné de numéro de téléphone.",
                                    color = KaloyTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = {
                                        navigator.push(
                                            AddPhoneScreen(
                                                userId = userId,
                                                email = email,
                                                password = password,
                                                accountType = accountType
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Compléter mes informations",
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
    }
}
