package com.kaloy.app.presentation.auth.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.kaloy.app.data.repository.AuthRepository
import com.kaloy.app.presentation.auth.gate.AnnulerActionSiSortieDuFlux
import com.kaloy.app.presentation.auth.gate.AuthGateCoordinator
import com.kaloy.app.presentation.auth.gate.EcranFluxAuthentification
import com.kaloy.app.presentation.auth.otp.OtpScreen
import com.kaloy.app.presentation.common.SansBarreDuBas
import com.kaloy.app.presentation.common.rememberSingleImagePicker
import com.kaloy.app.ui.theme.KaloyCarteVoilee
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyNuit
import com.kaloy.app.ui.theme.KaloyOrange
import com.kaloy.app.ui.theme.KaloyRed
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary
import com.kaloy.app.ui.theme.fondKaloy
import org.koin.compose.koinInject

data class RegisterArtistDetailsScreen(
    val email: String,
    val phone: String,
    val password: String,
    val otpChannel: String,
    val artistType: String
) : Screen, SansBarreDuBas, EcranFluxAuthentification {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AnnulerActionSiSortieDuFlux(navigator, koinInject<AuthGateCoordinator>())
        val repository = koinInject<AuthRepository>()
        val viewModel = remember { RegisterViewModel(repository) }
        val uiState by viewModel.uiState.collectAsState()

        DisposableEffect(viewModel) {
            onDispose { viewModel.dispose() }
        }

        var stageName by remember { mutableStateOf("") }
        var stageNameError by remember { mutableStateOf("") }
        var activeSinceYear by remember { mutableStateOf("") }
        var bio by remember { mutableStateOf("") }
        var photoUrl by remember { mutableStateOf("") }
        val pickProfilePhoto = rememberSingleImagePicker { uri ->
            photoUrl = uri.orEmpty()
        }

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

        LaunchedEffect(uiState) {
            if (uiState is RegisterUiState.Success) {
                val response = (uiState as RegisterUiState.Success).response
                viewModel.resetUiState()
                navigator.push(OtpScreen(userId = response.userId, email = email))
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
                    .fillMaxHeight(0.90f)
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
                        text = "Profil artiste",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily(Font(Res.font.baloo2, FontWeight.Bold))
                        ),
                        fontWeight = FontWeight.Bold,
                        color = KaloyTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Présentez-vous à la communauté.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaloyTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = stageName,
                        onValueChange = { stageName = it; stageNameError = "" },
                        label = { Text("Nom de scène *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = KaloyTextSecondary
                            )
                        },
                        isError = stageNameError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = formeChamp,
                        colors = couleursChamp
                    )
                    if (stageNameError.isNotEmpty()) {
                        Text(
                            text = stageNameError,
                            color = KaloyRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = activeSinceYear,
                        onValueChange = { activeSinceYear = it },
                        label = { Text("Actif depuis (année)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = null,
                                tint = KaloyTextSecondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = formeChamp,
                        colors = couleursChamp
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Biographie (optionnel)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = formeChamp,
                        colors = couleursChamp
                    )

                    OutlinedButton(
                        onClick = { pickProfilePhoto() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = formeChamp,
                        border = BorderStroke(1.dp, KaloyTextSecondary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyTextSecondary)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (photoUrl.isBlank()) "Téléverser une photo de profil"
                            else "Changer la photo de profil"
                        )
                    }

                    if (photoUrl.isNotBlank()) {
                        Text(
                            text = "Photo sélectionnée ✓",
                            color = KaloyOrange,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (uiState is RegisterUiState.Error) {
                        Text(
                            text = (uiState as RegisterUiState.Error).message,
                            color = KaloyRed,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            if (stageName.isBlank()) {
                                stageNameError = "Le nom de scène est requis"
                                return@Button
                            }
                            viewModel.registerArtist(
                                email = email,
                                password = password,
                                phone = phone.ifBlank { null },
                                otpChannel = otpChannel,
                                artistType = artistType,
                                stageName = stageName,
                                activeSinceYear = activeSinceYear,
                                bio = bio,
                                photoUrl = photoUrl
                            )
                        },
                        enabled = uiState !is RegisterUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = formeChamp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaloyOrange,
                            contentColor = KaloyNuit
                        )
                    ) {
                        if (uiState is RegisterUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = KaloyNuit
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Créer mon compte",
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
}
