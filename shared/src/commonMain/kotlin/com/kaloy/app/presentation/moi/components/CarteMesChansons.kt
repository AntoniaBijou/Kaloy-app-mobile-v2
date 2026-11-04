package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.ui.theme.*

/**
 * Acces aux chansons que l'artiste a declarees, depuis l'onglet « Moi ».
 *
 * Placee avant les evenements : declarer son repertoire est le premier geste
 * d'un artiste sur la plateforme, organiser un concert vient apres.
 */
@Composable
fun CarteMesChansons(
    nombreChansons: Int,
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
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = KaloyPurpleLight,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mes chansons",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (nombreChansons) {
                        0 -> "Déclarer une chanson de votre répertoire"
                        1 -> "1 chanson déclarée"
                        else -> "$nombreChansons chansons déclarées"
                    },
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("›", color = KaloyTextMuted, fontSize = 20.sp)
        }
    }
}
