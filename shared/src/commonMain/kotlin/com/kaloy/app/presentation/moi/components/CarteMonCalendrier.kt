package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaloy.app.ui.theme.*

/**
 * Acces au calendrier personnel de l'artiste depuis l'onglet « Moi ».
 *
 * Le calendrier existe deja sur sa fiche publique, mais il devait s'y chercher
 * lui-meme par la recherche pour le consulter. Ici, il y accede directement.
 */
@Composable
fun CarteMonCalendrier(
    nombreConcerts: Int,
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
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = KaloyPurpleLight,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mon calendrier",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (nombreConcerts) {
                        0 -> "Aucun concert confirmé"
                        1 -> "1 concert confirmé"
                        else -> "$nombreConcerts concerts confirmés"
                    },
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = KaloyTextMuted
            )
        }
    }
}
