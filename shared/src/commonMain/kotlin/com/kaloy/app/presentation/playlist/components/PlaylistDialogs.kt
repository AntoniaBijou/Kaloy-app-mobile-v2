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
import androidx.compose.material.icons.filled.Lock
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
import com.kaloy.app.data.dto.playlist.PlaylistSongUiItem
import com.kaloy.app.data.dto.playlist.PlaylistGenerationProposal
import com.kaloy.app.data.dto.playlist.PlaylistGenerationRequest
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.presentation.playlist.MergePreviewState
import com.kaloy.app.presentation.playlist.PlaylistGenerationUiState
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
    state: PlaylistGenerationUiState,
    onGenerate: (PlaylistGenerationRequest) -> Unit,
    onSave: (PlaylistGenerationRequest, PlaylistGenerationProposal, String) -> Unit,
    onDismiss: () -> Unit
) {
    var dureeMin by remember { mutableStateOf(30f) }
    val includedGenres = remember { mutableStateListOf<Long>() }
    val excludedGenres = remember { mutableStateListOf<Long>() }
    val includedArtists = remember { mutableStateListOf<Long>() }
    val excludedArtists = remember { mutableStateListOf<Long>() }
    val proposalNames = remember { mutableStateMapOf<Int, String>() }
    var showGenres by remember { mutableStateOf(false) }
    var showArtists by remember { mutableStateOf(false) }
    var generatedCriteria by remember { mutableStateOf<PlaylistGenerationRequest?>(null) }

    val options = when (state) {
        is PlaylistGenerationUiState.Ready -> state.genres to state.artists
        is PlaylistGenerationUiState.Generating -> state.genres to state.artists
        is PlaylistGenerationUiState.Proposals -> state.genres to state.artists
        is PlaylistGenerationUiState.Error -> state.genres to state.artists
        else -> emptyList<Genre>() to emptyList<Artist>()
    }
    val proposalsState = state as? PlaylistGenerationUiState.Proposals
    val isGenerating = state is PlaylistGenerationUiState.Generating
    val optionsFailed = state is PlaylistGenerationUiState.Error && !state.optionsLoaded
    val criteria = proposalsState?.criteria ?: generatedCriteria

    fun makeCriteria() = PlaylistGenerationRequest(
        durationMinutes = dureeMin.toInt(),
        includedGenreIds = includedGenres.toList(),
        excludedGenreIds = excludedGenres.toList(),
        includedArtistIds = includedArtists.toList(),
        excludedArtistIds = excludedArtists.toList()
    )

    fun toggleSelection(selected: MutableList<Long>, opposite: MutableList<Long>, id: Long) {
        if (id in selected) selected.remove(id) else {
            selected.add(id)
            opposite.remove(id)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Générer une playlist", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Durée maximale : ${dureeMin.toInt()} min",
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = dureeMin,
                        onValueChange = { dureeMin = it },
                        valueRange = 1f..180f,
                        steps = 178,
                        colors = SliderDefaults.colors(thumbColor = KaloyPurple, activeTrackColor = KaloyPurple)
                    )
                }
                item {
                    TextButton(onClick = { showGenres = !showGenres }) {
                        Text("${if (showGenres) "▾" else "▸"} Genres (${options.first.size})", color = KaloyTextPrimary)
                    }
                    Text("+ inclure · − exclure", color = KaloyTextMuted, style = MaterialTheme.typography.labelSmall)
                }
                if (showGenres) items(options.first, key = { "genre-${it.id}" }) { genre ->
                    GenerationCriterionRow(
                        label = genre.name,
                        included = genre.id in includedGenres,
                        excluded = genre.id in excludedGenres,
                        onInclude = { toggleSelection(includedGenres, excludedGenres, genre.id) },
                        onExclude = { toggleSelection(excludedGenres, includedGenres, genre.id) }
                    )
                }
                item {
                    TextButton(onClick = { showArtists = !showArtists }) {
                        Text("${if (showArtists) "▾" else "▸"} Artistes (${options.second.size})", color = KaloyTextPrimary)
                    }
                }
                if (showArtists) items(options.second, key = { "artist-${it.id}" }) { artist ->
                    GenerationCriterionRow(
                        label = artist.stageName,
                        included = artist.id in includedArtists,
                        excluded = artist.id in excludedArtists,
                        onInclude = { toggleSelection(includedArtists, excludedArtists, artist.id) },
                        onExclude = { toggleSelection(excludedArtists, includedArtists, artist.id) }
                    )
                }
                when (state) {
                    PlaylistGenerationUiState.LoadingOptions -> item {
                        CircularProgressIndicator(color = KaloyPurple)
                    }
                    is PlaylistGenerationUiState.Error -> item {
                        Text(state.message, color = KaloyRed, style = MaterialTheme.typography.bodySmall)
                    }
                    is PlaylistGenerationUiState.Proposals -> {
                        if (state.proposals.isEmpty()) item {
                            Text("Aucune proposition disponible.", color = KaloyTextSecondary)
                        }
                        items(state.proposals, key = { "proposal-${it.proposalNumber}" }) { proposal ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = KaloyDarkElevated),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "Proposition ${proposal.proposalNumber} · ${formatDuration(proposal.totalDurationSeconds)} · ${proposal.songs.size} titres",
                                        color = KaloyTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    proposal.songs.forEach { song ->
                                        Text(
                                            "${song.title} — ${song.artistName}",
                                            color = KaloyTextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    val alreadySaved = proposal.proposalNumber in state.savedProposalNumbers
                                    if (!alreadySaved) {
                                        KaloyTextField(
                                            value = proposalNames[proposal.proposalNumber] ?: "Ma playlist ${proposal.proposalNumber}",
                                            onValueChange = { proposalNames[proposal.proposalNumber] = it },
                                            label = "Nom de la playlist",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            criteria?.let {
                                                onSave(
                                                    it,
                                                    proposal,
                                                    proposalNames[proposal.proposalNumber] ?: "Ma playlist ${proposal.proposalNumber}"
                                                )
                                            }
                                        },
                                        enabled = !alreadySaved && criteria != null,
                                        colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                                    ) {
                                        Text(if (alreadySaved) "Conservée" else "Conserver cette proposition")
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = makeCriteria()
                    generatedCriteria = request
                    onGenerate(request)
                },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = !isGenerating && state !is PlaylistGenerationUiState.LoadingOptions && !optionsFailed
            ) {
                Text(if (isGenerating) "Génération…" else "Générer (max. 3)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

@Composable
private fun GenerationCriterionRow(
    label: String,
    included: Boolean,
    excluded: Boolean,
    onInclude: () -> Unit,
    onExclude: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = KaloyTextPrimary, modifier = Modifier.weight(1f))
        FilterChip(
            selected = included,
            onClick = onInclude,
            label = { Text("+ Inclure") }
        )
        Spacer(Modifier.width(4.dp))
        FilterChip(
            selected = excluded,
            onClick = onExclude,
            label = { Text("− Exclure") }
        )
    }
}

private fun formatDuration(seconds: Int): String =
    "${seconds / 60} min ${seconds % 60} s"

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
    previewState: MergePreviewState,
    isMerging: Boolean,
    onSelectionChanged: (ids: List<Long>) -> Unit,
    onMerge: (ids: List<Long>, nomResultat: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nomResultat by remember { mutableStateOf("") }
    var selection by remember(preSelected) { mutableStateOf(preSelected.toSet()) }
    val idsOrdonnes = playlists.map { it.id }.filter { it in selection }

    LaunchedEffect(selection, playlists) {
        onSelectionChanged(playlists.map { it.id }.filter { it in selection })
    }

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
                Text(
                    text = "Aperçu de la fusion",
                    style = MaterialTheme.typography.titleSmall,
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                when (val preview = previewState) {
                    MergePreviewState.Idle -> Text(
                        text = "Sélectionnez au moins deux playlists pour afficher l'aperçu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextMuted
                    )
                    MergePreviewState.Loading -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = KaloyPurple,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Calcul de l'aperçu…", color = KaloyTextSecondary)
                    }
                    is MergePreviewState.Error -> Text(
                        text = preview.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyRed
                    )
                    is MergePreviewState.Success -> if (preview.sourceIds != idsOrdonnes) {
                        Text(
                            text = "Mise à jour de l'aperçu…",
                            style = MaterialTheme.typography.bodySmall,
                            color = KaloyTextMuted
                        )
                    } else {
                        Text(
                            text = buildString {
                                append("${preview.songs.size} chanson(s) dans le résultat")
                                if (preview.duplicateCount > 0) {
                                    append(" · ${preview.duplicateCount} doublon(s) supprimé(s)")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = KaloyTextSecondary
                        )
                        if (preview.songs.isEmpty()) {
                            Text(
                                text = "Les playlists sélectionnées ne contiennent aucune chanson.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KaloyTextMuted
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 130.dp)) {
                                items(preview.songs, key = { it.songId }) { song ->
                                    MergePreviewSongRow(song)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onMerge(idsOrdonnes, nomResultat.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = idsOrdonnes.size >= 2 &&
                    nomResultat.isNotBlank() &&
                    nomResultat.trim().length <= 150 &&
                    previewState is MergePreviewState.Success &&
                    previewState.sourceIds == idsOrdonnes &&
                    !isMerging
            ) {
                Text(if (isMerging) "Fusion en cours…" else "Confirmer la fusion")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KaloyTextSecondary)
            }
        }
    )
}

@Composable
private fun MergePreviewSongRow(song: PlaylistSongUiItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = KaloyPurple,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                text = song.artistName,
                color = KaloyTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
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
    visibilityActionLabel: String = "Partager",
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
            OptionsSheetItem(
                if (playlist.visibility == "PUBLIC") Icons.Filled.Lock else Icons.Filled.Share,
                visibilityActionLabel,
                onPartager,
                onDismiss
            )
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
            .clickable { onDismiss(); onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = couleur, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = couleur, style = MaterialTheme.typography.bodyMedium)
    }
}

// ============================================================
// Dialog — Modifier une playlist
// ============================================================

@Composable
fun ModifierPlaylistDialog(
    playlist: PlaylistUiItem,
    onModifier: (nom: String, description: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaloyDarkCard,
        title = {
            Text("Modifier la playlist", color = KaloyTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                KaloyTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = "Nom de la playlist",
                    modifier = Modifier.fillMaxWidth()
                )
                KaloyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optionnel)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nom.isNotBlank()) onModifier(nom.trim(), description.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = nom.isNotBlank()
            ) {
                Text("Enregistrer")
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
