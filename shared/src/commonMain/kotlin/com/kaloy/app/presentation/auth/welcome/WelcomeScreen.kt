package com.kaloy.app.presentation.auth.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kaloy.shared.generated.resources.Res
import kaloy.shared.generated.resources.baloo2
import org.jetbrains.compose.resources.Font
import com.kaloy.app.presentation.auth.login.LoginScreen
import com.kaloy.app.presentation.auth.register.RegisterStep1Screen
import com.kaloy.app.presentation.common.SansBarreDuBas
import com.kaloy.app.ui.theme.KaloyCarteVoilee
import com.kaloy.app.ui.theme.KaloyOrange
import com.kaloy.app.ui.theme.KaloyNuit
import com.kaloy.app.ui.theme.KaloyPurpleLight
import com.kaloy.app.ui.theme.KaloyTextSecondary
import com.kaloy.app.ui.theme.fondKaloy

class WelcomeScreen : Screen, SansBarreDuBas {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .fondKaloy()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KaloyCarteVoilee
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        KaloyWordmark()

                        Text(
                            text = "Votre musique, partout.",
                            style = MaterialTheme.typography.titleMedium,
                            color = KaloyTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Découvrez, écoutez et partagez votre univers musical avec une expérience premium.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KaloyTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { navigator.push(RegisterStep1Screen()) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KaloyOrange,
                                contentColor = KaloyNuit
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Créer un compte",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { navigator.push(LoginScreen()) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, KaloyTextSecondary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Se connecter",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Pour bénéficier de toutes les fonctionnalités réservées, créez un compte ou connectez-vous.",
                            color = KaloyTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Text(
                            text = "Continuer en tant que visiteur",
                            color = KaloyOrange,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    navigator.push(
                                        com.kaloy.app.presentation.common.EcranPrincipal(
                                            username = "Visiteur",
                                            isVisitor = true
                                        )
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KaloyWordmark() {
    val style = MaterialTheme.typography.headlineLarge.copy(
        fontFamily = FontFamily(Font(Res.font.baloo2, FontWeight.Black)),
        fontSize = 58.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        "Kaloy".forEach { letter ->
            Text(
                text = letter.toString(),
                style = style.copy(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to KaloyPurpleLight,
                            1f to KaloyOrange
                        )
                    )
                )
            )
        }
    }
}
