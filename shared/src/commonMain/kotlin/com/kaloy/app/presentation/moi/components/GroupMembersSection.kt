package com.kaloy.app.presentation.moi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.kaloy.app.data.dto.me.GroupMemberDto
import com.kaloy.app.data.dto.me.InstrumentRoleDto
import com.kaloy.app.ui.theme.*

@Composable
fun GroupMembersSection(
    members: List<GroupMemberDto>,
    instrumentRoles: List<InstrumentRoleDto>,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onChangeMemberStatus: (Long) -> Unit,
    onDeleteMember: (Long) -> Unit
) {
    ProfileSection(title = "Membres du groupe") {
        Button(
            onClick = onAddMember,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ajouter un membre")
        }

        if (members.isEmpty()) {
            Text(
                text = "Aucun membre pour le moment.",
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            members.forEach { member ->
                MemberCard(
                    member = member,
                    onEdit = { onEditMember(member.id) },
                    onChangeStatus = { onChangeMemberStatus(member.id) },
                    onDelete = { onDeleteMember(member.id) }
                )
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: GroupMemberDto,
    onEdit: () -> Unit,
    onChangeStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = member.status == "ACTIF"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyDarkElevated)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MemberAvatar(photoUrl = member.photoUrl, name = member.fullName ?: "?")

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.fullName ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KaloyTextPrimary
                )
                if (!member.roleInstrumentLabel.isNullOrBlank()) {
                    Text(
                        text = member.roleInstrumentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) KaloyGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = if (isActive) "ACTIF" else "ANCIEN MEMBRE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) KaloyGreen else KaloyTextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = KaloyPurpleLight, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onChangeStatus, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = "Statut",
                        tint = if (isActive) KaloyOrange else KaloyGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = KaloyRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MemberAvatar(photoUrl: String?, name: String) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(KaloyPurpleDark, KaloyPink))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
