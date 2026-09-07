package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaloy.app.ui.theme.*

@Composable
fun ProfileHeader(
    displayName: String,
    photoUrl: String?,
    emailVerificationStatus: String,
    phoneVerificationStatus: String?,
    accountStatus: String?,
    onEditPhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(KaloyPurpleDark, KaloyDarkSurface))
            )
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(photoUrl = photoUrl, displayName = displayName)
                IconButton(
                    onClick = onEditPhoto,
                    modifier = Modifier
                        .size(30.dp)
                        .background(KaloyPurple, CircleShape)
                        .border(2.dp, KaloyDarkSurface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Modifier la photo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = displayName.ifBlank { "—" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = KaloyTextPrimary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerificationChip(
                    label = "Email",
                    isVerified = emailVerificationStatus.contains("Vérifié", ignoreCase = true)
                )
                if (phoneVerificationStatus != null) {
                    VerificationChip(
                        label = "Tél.",
                        isVerified = phoneVerificationStatus.contains("Vérifié", ignoreCase = true)
                    )
                }
                if (accountStatus != null) {
                    StatusChip(label = accountStatus)
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(photoUrl: String?, displayName: String) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Photo de profil",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .border(3.dp, KaloyPurple, CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(KaloyPurple, KaloyPink)))
                .border(3.dp, KaloyPurpleLight.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VerificationChip(label: String, isVerified: Boolean) {
    val (bgColor, textColor, icon) = if (isVerified) {
        Triple(KaloyGreen.copy(alpha = 0.2f), KaloyGreen, Icons.Default.CheckCircle)
    } else {
        Triple(KaloyOrange.copy(alpha = 0.2f), KaloyOrange, Icons.Default.Error)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(12.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}

@Composable
private fun StatusChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KaloyRed.copy(alpha = 0.2f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = KaloyRed,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
