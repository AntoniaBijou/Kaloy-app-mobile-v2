@file:OptIn(ExperimentalMaterial3Api::class)

package com.kaloy.app.presentation.moi.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.data.dto.me.GroupMemberDto
import com.kaloy.app.data.dto.me.InstrumentRoleDto
import com.kaloy.app.presentation.common.rememberSingleImagePicker
import com.kaloy.app.ui.theme.*

// ── Informations personnelles ─────────────────────────────────────────────────

@Composable
fun EditPersonalInfoDialog(
    currentFirstName: String?,
    currentLastName: String?,
    currentUserName: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (String?, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var firstName by remember { mutableStateOf(currentFirstName ?: "") }
    var lastName by remember { mutableStateOf(currentLastName ?: "") }
    var userName by remember { mutableStateOf(currentUserName ?: "") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Informations personnelles", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KaloyTextField(value = firstName, label = "Prénom", onValueChange = { firstName = it })
                KaloyTextField(value = lastName, label = "Nom", onValueChange = { lastName = it })
                KaloyTextField(value = userName, label = "Nom d'utilisateur", onValueChange = { userName = it })
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(firstName, lastName, userName) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Profil artiste ────────────────────────────────────────────────────────────

@Composable
fun EditArtistProfileDialog(
    currentStageName: String?,
    currentBio: String?,
    currentYear: Int?,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (String?, String?, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var stageName by remember { mutableStateOf(currentStageName ?: "") }
    var bio by remember { mutableStateOf(currentBio ?: "") }
    var year by remember { mutableStateOf(currentYear?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Profil artistique", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KaloyTextField(value = stageName, label = "Nom de scène", onValueChange = { stageName = it })
                KaloyTextField(
                    value = bio,
                    label = "Biographie",
                    onValueChange = { bio = it },
                    singleLine = false,
                    minLines = 3
                )
                KaloyTextField(
                    value = year,
                    label = "Année de début de carrière",
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) year = it },
                    keyboardType = KeyboardType.Number
                )
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(stageName, bio, year.toIntOrNull()) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Photo ─────────────────────────────────────────────────────────────────────

@Composable
fun EditPhotoDialog(
    currentPhotoUrl: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var photoUrl by remember { mutableStateOf(currentPhotoUrl ?: "") }
    val imagePicker = rememberSingleImagePicker { uri ->
        if (!uri.isNullOrBlank()) photoUrl = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Photo de profil", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { imagePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
                ) {
                    Text(if (photoUrl.isBlank()) "Choisir depuis la galerie" else "Changer la photo")
                }
                if (photoUrl.isNotBlank()) {
                    Text("Photo sélectionnée", color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (photoUrl.isNotBlank()) onConfirm(photoUrl) },
                enabled = !isLoading && photoUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Changement d'email (2 étapes) ────────────────────────────────────────────

@Composable
fun ChangeEmailDialog(
    step: Int,
    pendingEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSendCode: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newEmail by remember { mutableStateOf(pendingEmail) }
    var otpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = {
            Text(
                text = if (step == 1) "Changer l'email" else "Confirmer le nouvel email",
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 1) {
                    Text(
                        "Un code de vérification sera envoyé à la nouvelle adresse email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                    KaloyTextField(
                        value = newEmail,
                        label = "Nouvel email",
                        onValueChange = { newEmail = it },
                        keyboardType = KeyboardType.Email
                    )
                } else {
                    Text(
                        "Entrez le code à 6 chiffres envoyé à $newEmail",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                    OtpInputRow(value = otpCode, onValueChange = { otpCode = it })
                    Text(
                        "Après confirmation, vous serez déconnecté et devrez vous reconnecter avec le nouvel email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyOrange
                    )
                }
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) onSendCode(newEmail)
                    else onConfirmCode(otpCode)
                },
                enabled = !isLoading && (if (step == 1) newEmail.contains("@") else otpCode.length == 6),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (step == 1) "Envoyer le code" else "Confirmer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Changement de téléphone (2 étapes) ───────────────────────────────────────

@Composable
fun ChangePhoneDialog(
    step: Int,
    pendingPhone: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSendCode: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPhone by remember { mutableStateOf(pendingPhone) }
    var otpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = {
            Text(
                text = if (step == 1) "Changer le téléphone" else "Confirmer le numéro",
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 1) {
                    Text(
                        "Un code de vérification sera envoyé par SMS au nouveau numéro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                    KaloyTextField(
                        value = newPhone,
                        label = "Nouveau numéro",
                        onValueChange = { newPhone = it },
                        keyboardType = KeyboardType.Phone
                    )
                } else {
                    Text(
                        "Entrez le code à 6 chiffres envoyé par SMS au $newPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                    OtpInputRow(value = otpCode, onValueChange = { otpCode = it })
                }
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) onSendCode(newPhone)
                    else onConfirmCode(otpCode)
                },
                enabled = !isLoading && (if (step == 1) newPhone.isNotBlank() else otpCode.length == 6),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (step == 1) "Envoyer le code" else "Confirmer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Membres du groupe ─────────────────────────────────────────────────────────

@Composable
fun AddMemberDialog(
    instrumentRoles: List<InstrumentRoleDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (String, Long, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var selectedRoleId by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val selectedRole = instrumentRoles.find { it.id == selectedRoleId }
    val imagePicker = rememberSingleImagePicker { uri ->
        if (!uri.isNullOrBlank()) photoUrl = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Ajouter un membre", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KaloyTextField(value = fullName, label = "Nom complet", onValueChange = { fullName = it })
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedRole?.label ?: "Choisir un rôle/instrument",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rôle / Instrument") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaloyPurple,
                            unfocusedBorderColor = KaloyDarkElevated,
                            focusedTextColor = KaloyTextPrimary,
                            unfocusedTextColor = KaloyTextPrimary,
                            focusedLabelColor = KaloyPurple,
                            unfocusedLabelColor = KaloyTextSecondary,
                            focusedContainerColor = KaloyDarkElevated,
                            unfocusedContainerColor = KaloyDarkElevated
                        ),
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        instrumentRoles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.label) },
                                onClick = { selectedRoleId = role.id; expanded = false }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { imagePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
                ) {
                    Text(if (photoUrl.isBlank()) "Choisir une photo (optionnel)" else "Changer la photo")
                }
                if (photoUrl.isNotBlank()) {
                    Text("Photo sélectionnée", color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedRoleId?.let { onConfirm(fullName, it, photoUrl.takeIf { it.isNotBlank() }) } },
                enabled = !isLoading && fullName.isNotBlank() && selectedRoleId != null,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

@Composable
fun EditMemberDialog(
    member: GroupMemberDto,
    instrumentRoles: List<InstrumentRoleDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (String?, Long?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var fullName by remember { mutableStateOf(member.fullName ?: "") }
    var photoUrl by remember { mutableStateOf(member.photoUrl ?: "") }
    var selectedRoleId by remember { mutableStateOf(member.roleInstrumentId) }
    var expanded by remember { mutableStateOf(false) }
    val selectedRole = instrumentRoles.find { it.id == selectedRoleId }
    val imagePicker = rememberSingleImagePicker { uri ->
        if (!uri.isNullOrBlank()) photoUrl = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Modifier le membre", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KaloyTextField(value = fullName, label = "Nom complet", onValueChange = { fullName = it })
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedRole?.label ?: member.roleInstrumentLabel ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rôle / Instrument") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaloyPurple,
                            unfocusedBorderColor = KaloyDarkElevated,
                            focusedTextColor = KaloyTextPrimary,
                            unfocusedTextColor = KaloyTextPrimary,
                            focusedLabelColor = KaloyPurple,
                            unfocusedLabelColor = KaloyTextSecondary,
                            focusedContainerColor = KaloyDarkElevated,
                            unfocusedContainerColor = KaloyDarkElevated
                        ),
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        instrumentRoles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.label) },
                                onClick = { selectedRoleId = role.id; expanded = false }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { imagePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyPurple)
                ) {
                    Text(if (photoUrl.isBlank()) "Choisir une photo (optionnel)" else "Changer la photo")
                }
                if (photoUrl.isNotBlank()) {
                    Text("Photo sélectionnée", color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                errorMessage?.let {
                    Text(it, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        fullName.takeIf { it.isNotBlank() },
                        selectedRoleId,
                        photoUrl.takeIf { it.isNotBlank() }
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

@Composable
fun MemberStatusDialog(
    member: GroupMemberDto,
    isLoading: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val isCurrentlyActive = member.status == "ACTIF"
    val targetLabel = if (isCurrentlyActive) "ANCIEN MEMBRE" else "ACTIF"
    val targetStatusId = if (isCurrentlyActive) 2L else 1L

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Changer le statut", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Passer ${member.fullName ?: "ce membre"} en « $targetLabel » ?",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(targetStatusId) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(targetLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Confirmations danger ──────────────────────────────────────────────────────

@Composable
fun ConfirmDeleteMemberDialog(
    memberName: String?,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Supprimer le membre", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Supprimer définitivement ${memberName ?: "ce membre"} du groupe ?",
                color = KaloyTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyRed)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

@Composable
fun ConfirmLogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = { Text("Se déconnecter", color = KaloyTextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("Voulez-vous vous déconnecter de votre compte ?", color = KaloyTextSecondary) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = KaloyOrange)) {
                Text("Se déconnecter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = KaloyTextSecondary) }
        }
    )
}

@Composable
fun ConfirmDeleteAccountDialog(isLoading: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = KaloyDarkCard,
        title = { Text("Supprimer le compte", color = KaloyRed, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cette action est définitive et irréversible.", color = KaloyRed, fontWeight = FontWeight.SemiBold)
                Text(
                    "Toutes vos données seront supprimées et vous ne pourrez pas récupérer votre compte.",
                    color = KaloyTextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyRed)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Supprimer définitivement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun KaloyTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KaloyPurple,
            unfocusedBorderColor = KaloyDarkElevated,
            focusedTextColor = KaloyTextPrimary,
            unfocusedTextColor = KaloyTextPrimary,
            focusedLabelColor = KaloyPurple,
            unfocusedLabelColor = KaloyTextSecondary,
            focusedContainerColor = KaloyDarkElevated,
            unfocusedContainerColor = KaloyDarkElevated
        )
    )
}

@Composable
fun OtpInputRow(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onValueChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(6) { index ->
                    val char = value.getOrNull(index)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .border(
                                width = 2.dp,
                                color = if (value.length == index) KaloyPurple else KaloyDarkElevated,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            color = KaloyTextPrimary
                        )
                    }
                }
            }
        }
    )
}
