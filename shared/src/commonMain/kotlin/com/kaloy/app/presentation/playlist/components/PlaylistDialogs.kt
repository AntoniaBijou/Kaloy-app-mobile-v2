package com.kaloy.app.presentation.playlist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaloy.app.data.dto.playlist.PlaylistUiItem
import com.kaloy.app.presentation.playlist.PlaylistSortCritere
import com.kaloy.app.ui.theme.*

// ============================================================
// Champ texte réutilisable (style Kaloy)
// ============================================================

@Composable
fun KaloyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = KaloyTextMuted) },
        singleLine = singleLine,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KaloyPurple,
            unfocusedBorderColor = KaloyDarkElevated,
            focusedTextColor = KaloyTextPrimary,
            unfocusedTextColor = KaloyTextPrimary,
            cursorColor = KaloyPurple,
            focusedContainerColor = KaloyDarkElevated,
            unfocusedContainerColor = KaloyDarkElevated
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ============================================================
// Dialog — Créer une playlist
// ============================================================

@Composable
fun CreatePlaylistDialog(
    onCreate: (nom: String, genreIds: List<Long>, artistIds: List<Long>, dureeMin: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf("") }
    var dureeMin by remember { mutableStateOf(30f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Créer une playlist", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                KaloyTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = "Nom de la playlist",
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        text = "Durée cible : ${dureeMin.toInt()} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = dureeMin,
                        onValueChange = { dureeMin = it },
                        valueRange = 15f..180f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = KaloyPurple,
                            activeTrackColor = KaloyPurple,
                            inactiveTrackColor = KaloyDarkElevated
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("15 min", style = MaterialTheme.typography.labelSmall, color = KaloyTextMuted)
                        Text("180 min", style = MaterialTheme.typography.labelSmall, color = KaloyTextMuted)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nom.isNotBlank()) onCreate(nom.trim(), emptyList(), emptyList(), dureeMin.toInt()) },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = nom.isNotBlank()
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ============================================================
// Bottom Sheet — Filtre / Tri
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortBottomSheet(
    critereActuel: PlaylistSortCritere,
    onCritereChange: (PlaylistSortCritere) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        PlaylistSortCritere.DATE_DESC to "Plus récentes en premier",
        PlaylistSortCritere.DATE_ASC to "Plus anciennes en premier",
        PlaylistSortCritere.NOM_AZ to "Nom (A → Z)",
        PlaylistSortCritere.NOMBRE_TITRES to "Nombre de titres"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkSurface,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Trier par",
                style = MaterialTheme.typography.titleMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            options.forEach { (critere, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCritereChange(critere); onDismiss() }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = critereActuel == critere,
                        onClick = { onCritereChange(critere); onDismiss() },
                        colors = RadioButtonDefaults.colors(selectedColor = KaloyPurple)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = KaloyTextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ============================================================
// Dialog — Fusionner des playlists
// ============================================================

@Composable
fun MergePlaylistsDialog(
    playlists: List<PlaylistUiItem>,
    preSelected: List<Long>,
    onMerge: (ids: List<Long>, nomResultat: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nomResultat by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(preSelected.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Fusionner des playlists", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KaloyTextField(
                    value = nomResultat,
                    onValueChange = { nomResultat = it },
                    label = "Nom de la playlist résultante",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Sélectionner au moins 2 playlists :",
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextSecondary
                )
                if (playlists.isEmpty()) {
                    Text(
                        text = "Aucune playlist disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextMuted
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selection = if (pl.id in selection) selection - pl.id else selection + pl.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pl.id in selection,
                                    onCheckedChange = { checked ->
                                        selection = if (checked) selection + pl.id else selection - pl.id
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = KaloyPurple,
                                        uncheckedColor = KaloyTextMuted
                                    )
                                )
                                Text(
                                    text = pl.name,
                                    color = KaloyTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onMerge(selection.toList(), nomResultat.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = selection.size >= 2 && nomResultat.isNotBlank()
            ) {
                Text("Fusionner")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

// ============================================================
// Bottom Sheet — Options d'une playlist
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsSheet(
    playlist: PlaylistUiItem,
    onModifier: () -> Unit,
    onPartager: () -> Unit,
    onFusionner: () -> Unit,
    onSupprimer: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkSurface,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // En-tête playlist
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = KaloyPurpleLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(playlist.name, color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${playlist.nombreTitres} titre${if (playlist.nombreTitres != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                }
            }
            HorizontalDivider(color = KaloyDarkElevated)
            OptionsSheetItem(Icons.Filled.Edit, "Modifier", onModifier, onDismiss)
            OptionsSheetItem(Icons.Filled.Share, "Partager", onPartager, onDismiss)
            OptionsSheetItem(Icons.Filled.LibraryAdd, "Fusionner", onFusionner, onDismiss)
            OptionsSheetItem(Icons.Filled.Delete, "Supprimer", onSupprimer, onDismiss, destructif = true)
        }
    }
}

@Composable
private fun OptionsSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    destructif: Boolean = false
) {
    val couleur = if (destructif) KaloyRed else KaloyTextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(); onDismiss() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = couleur, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = couleur, style = MaterialTheme.typography.bodyMedium)
    }
}

// ============================================================
// Dialog — Confirmation suppression
// ============================================================

@Composable
fun ConfirmDeletePlaylistDialog(
    nom: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Supprimer la playlist", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "Voulez-vous vraiment supprimer « $nom » ? Cette action est irréversible.",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KaloyRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}
