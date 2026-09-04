package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaloy.app.data.dto.me.ClientProfile
import com.kaloy.app.ui.theme.*

@Composable
fun ClientProfileContent(
    profile: ClientProfile,
    onEditPersonalInfo: () -> Unit,
    onChangeEmail: () -> Unit,
    onChangePhone: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileSection(title = "Informations personnelles") {
            InfoRow(label = "Prénom", value = profile.firstName ?: "—")
            InfoRow(label = "Nom", value = profile.lastName ?: "—")
            InfoRow(label = "Nom d'utilisateur", value = profile.userName?.let { "@$it" } ?: "—")
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onEditPersonalInfo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Modifier mes informations")
            }
        }

        ProfileSection(title = "Sécurité") {
            InfoRow(label = "Email", value = profile.email)
            InfoRow(label = "Statut email", value = profile.emailVerificationStatus)
            InfoRow(label = "Téléphone", value = profile.phone ?: "Non renseigné")
            InfoRow(label = "Statut tél.", value = profile.phoneVerificationStatus)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeEmail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
                ) { Text("Changer email", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(
                    onClick = onChangePhone,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
                ) { Text("Changer tél.", style = MaterialTheme.typography.labelMedium) }
            }
        }

        ProfileSection(title = "Compte") {
            InfoRow(label = "Membre depuis", value = formatMemberSince(profile.memberSince))
        }

        DangerZone(onLogout = onLogout, onDeleteAccount = onDeleteAccount)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = KaloyPurpleLight
            )
            HorizontalDivider(color = KaloyDarkElevated)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = KaloyTextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = KaloyTextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DangerZone(onLogout: () -> Unit, onDeleteAccount: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyRed.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Zone de danger",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = KaloyRed
            )
            HorizontalDivider(color = KaloyRed.copy(alpha = 0.2f))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = KaloyOrange
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Se déconnecter")
            }
            Button(
                onClick = onDeleteAccount,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyRed)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Supprimer mon compte")
            }
        }
    }
}

fun formatMemberSince(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return try {
        raw.substring(0, 10)
    } catch (_: Exception) {
        raw
    }
}
