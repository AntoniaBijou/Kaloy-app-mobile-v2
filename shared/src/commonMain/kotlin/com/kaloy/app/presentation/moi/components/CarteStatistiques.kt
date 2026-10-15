package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.ui.theme.*

/**
 * Acces aux statistiques depuis l'onglet « Moi », pour un compte artiste.
 *
 * Le chiffre des 30 derniers jours figure sur la carte : c'est celui qu'un
 * artiste veut voir d'un coup d'oeil, sans ouvrir l'ecran. Le total depuis le
 * debut est moins parlant au quotidien — il ne bouge presque pas d'un jour a
 * l'autre — et reste a l'interieur.
 */
@Composable
fun CarteStatistiques(
    ecoutes30Jours: Long?,
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
                imageVector = Icons.Filled.BarChart,
                contentDescription = null,
                tint = KaloyPurpleLight,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Statistiques",
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    // null tant que l'appel n'a pas abouti : on reste muet
                    // plutot que d'afficher un zero qui serait pris pour un
                    // resultat.
                    text = when (ecoutes30Jours) {
                        null -> "Écoutes, auditeurs et classement de vos titres"
                        0L -> "Aucune écoute sur les 30 derniers jours"
                        1L -> "1 écoute sur les 30 derniers jours"
                        else -> "$ecoutes30Jours écoutes sur les 30 derniers jours"
                    },
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("›", color = KaloyTextMuted, fontSize = 20.sp)
        }
    }
}
