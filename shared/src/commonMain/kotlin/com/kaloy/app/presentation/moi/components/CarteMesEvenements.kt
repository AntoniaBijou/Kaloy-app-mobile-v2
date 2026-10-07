package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.ui.theme.*

/**
 * Acces aux evenements que l'artiste organise, depuis l'onglet « Moi ».
 *
 * Pas de pastille ici, contrairement aux notifications : organiser n'est pas
 * une sollicitation a traiter, c'est un espace ou l'on se rend de son plein gre.
 */
@Composable
fun CarteMesEvenements(
    nombreEvenements: Int,
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
            Icon(
                imageVector = Icons.Filled.Festival,
                contentDescription = null,
                tint = KaloyPurpleLight,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mes événements",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (nombreEvenements) {
                        0 -> "Créer un événement et inviter des artistes"
                        1 -> "1 événement organisé"
                        else -> "$nombreEvenements événements organisés"
                    },
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("›", color = KaloyTextMuted, fontSize = 20.sp)
        }
    }
}
