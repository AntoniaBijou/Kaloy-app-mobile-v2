package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaloy.app.data.dto.me.ArtistProfile
import com.kaloy.app.data.dto.me.InstrumentRoleDto
import com.kaloy.app.ui.theme.*

@Composable
fun ArtistProfileContent(
    profile: ArtistProfile,
    instrumentRoles: List<InstrumentRoleDto>,
    onEditArtistProfile: () -> Unit,
    onRequestVerification: () -> Unit,
    onChangeEmail: () -> Unit,
    onChangePhone: () -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onChangeMemberStatus: (Long) -> Unit,
    onDeleteMember: (Long) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileSection(title = "Profil artistique") {
            InfoRow(label = "Nom de scène", value = profile.stageName.ifBlank { "—" })
            InfoRow(label = "Type", value = if (profile.artistType == "GROUP") "Groupe" else "Solo")
            InfoRow(label = "Début de carrière", value = profile.activeSinceYear?.toString() ?: "—")
            InfoRow(label = "Biographie", value = if (profile.bio.isNullOrBlank()) "—" else profile.bio)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onEditArtistProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Modifier le profil artistique")
            }
        }

        ProfileSection(title = "Vérification") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Statut", style = MaterialTheme.typography.bodySmall, color = KaloyTextSecondary)
                VerificationStatusChip(status = profile.verificationStatus)
            }
            if (profile.isCertified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = KaloyCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Compte certifié Kaloy", style = MaterialTheme.typography.bodySmall, color = KaloyCyan)
                }
            }
            if (profile.verificationStatus != "Validé") {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onRequestVerification,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                ) {
                    Text("Demander la vérification")
                }
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

        if (profile.artistType == "GROUP") {
            GroupMembersSection(
                members = profile.members,
                instrumentRoles = instrumentRoles,
                onAddMember = onAddMember,
                onEditMember = onEditMember,
                onChangeMemberStatus = onChangeMemberStatus,
                onDeleteMember = onDeleteMember
            )
        }

        DangerZone(onLogout = onLogout, onDeleteAccount = onDeleteAccount)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun VerificationStatusChip(status: String) {
    val (bgColor, textColor) = when (status) {
        "Validé" -> KaloyGreen.copy(alpha = 0.15f) to KaloyGreen
        "Rejeté" -> KaloyRed.copy(alpha = 0.15f) to KaloyRed
        else -> KaloyOrange.copy(alpha = 0.15f) to KaloyOrange
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bgColor) {
        Text(
            text = status.ifBlank { "En attente" },
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
