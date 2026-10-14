package com.kaloy.app.presentation.playlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.ui.theme.*

@Composable
fun PlaylistItemRow(
    item: PlaylistUiItem,
    limiteAtteinte: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pochette (gradient + icône si pas de coverUrl)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(KaloyPurpleDark, KaloyPink.copy(alpha = 0.8f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Titre + métadonnées
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dureeMin = item.dureeSecondes / 60
            val meta = buildString {
                append("${item.nombreTitres} titre${if (item.nombreTitres != 1) "s" else ""}")
                if (dureeMin > 0) append(" • ${dureeMin} min")
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextSecondary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Indicateur offline téléchargée
        if (item.estTelecharge) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = "Téléchargée",
                tint = KaloyGreen,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        // Bouton lecture
        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Lire",
                tint = KaloyPurpleLight,
                modifier = Modifier.size(22.dp)
            )
        }

        // Bouton téléchargement (seulement si pas encore téléchargée)
        if (!item.estTelecharge) {
            IconButton(
                onClick = onDownload,
                enabled = !limiteAtteinte,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Télécharger",
                    tint = if (limiteAtteinte) KaloyTextMuted else KaloyTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Menu 3 points
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Options",
                tint = KaloyTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
