package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.ui.theme.*

/**
 * Acces au centre de notifications depuis l'onglet « Moi ».
 *
 * La pastille n'apparait que s'il y a quelque chose a traiter : une pastille a
 * zero attirerait l'oeil sans rien signaler.
 */
@Composable
fun CarteNotifications(
    nombreEnAttente: Int,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔔", fontSize = 22.sp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (nombreEnAttente) {
                        0 -> "Aucune invitation en attente"
                        1 -> "1 invitation en attente de réponse"
                        else -> "$nombreEnAttente invitations en attente de réponse"
                    },
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (nombreEnAttente > 0) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(KaloyPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$nombreEnAttente",
                        color = KaloyTextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text("›", color = KaloyTextMuted, fontSize = 20.sp)
        }
    }
}
