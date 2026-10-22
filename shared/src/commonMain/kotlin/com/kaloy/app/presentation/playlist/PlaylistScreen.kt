package com.kaloy.app.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.repository.PlaylistRepository
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.lecteur.LecteurViewModel
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.presentation.playlist.components.*
import com.kaloy.app.presentation.queue.MiniPlayer
import com.kaloy.app.ui.components.*
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

class PlaylistScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val repository = koinInject<PlaylistRepository>()
        val sessionManager = koinInject<AuthSessionManager>()
        val fileAttente = koinInject<QueueManager>()
        val lecteur = koinInject<LecteurViewModel>()

        val modeleVue = remember { PlaylistViewModel(repository, sessionManager) }
        DisposableEffect(Unit) { onDispose { modeleVue.dispose() } }

        val uiState by modeleVue.uiState.collectAsState()
        val operationState by modeleVue.operationState.collectAsState()
        val activeDialog by modeleVue.activeDialog.collectAsState()
        val mergePreview by modeleVue.mergePreview.collectAsState()
        val generationState by modeleVue.generationState.collectAsState()
        val requeteRecherche by modeleVue.requeteRecherche.collectAsState()
        val critereTri by modeleVue.critereTri.collectAsState()

        val chansonCourante by fileAttente.currentSong.collectAsState()
        val chansonsSuivantes by fileAttente.upNext.collectAsState()
        val historique by fileAttente.history.collectAsState()
        val lectureActive by lecteur.isPlaying.collectAsState()
        val positionCourante by lecteur.currentPositionMs.collectAsState()

        // Réaction aux états d'opération
        LaunchedEffect(operationState) {
            when (operationState) {
                is PlaylistOperationState.NeedsRelogin -> navigateur.replaceAll(WelcomeScreen())
                is PlaylistOperationState.Success, is PlaylistOperationState.Error -> {
                    delay(2000)
                    modeleVue.resetOperationState()
                }
                else -> {}
            }
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mes Playlists",
                            fontWeight = FontWeight.Bold,
                            color = KaloyTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigateur.pop() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Retour",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { modeleVue.afficherDialog(ActiveDialogPlaylist.FilterSort) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Filtres et tri",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = KaloyDarkSurface
                    )
                )
            },
            bottomBar = {
                Column {
                    chansonCourante?.let { chanson ->
                        MiniPlayer(
                            song = chanson,
                            isPlaying = lectureActive,
                            onClick = { navigateur.push(LecteurScreen(chanson.id)) },
                            onTogglePlay = { lecteur.togglePlayPause() },
                            onPrevious = { lecteur.jouerPrecedent() },
                            onNext = { lecteur.jouerSuivant() },
                            canPrevious = historique.isNotEmpty() || positionCourante > 3_000L,
                            canNext = chansonsSuivantes.isNotEmpty()
                        )
                    }
                    NavigationBar(containerColor = KaloyDarkSurface) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { navigateur.pop() },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                            label = { Text("Accueil") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                        NavigationBarItem(
                            selected = true,
                            onClick = { },
                            icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Playlist") },
                            label = { Text("Playlist") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { navigateur.push(MoiScreen()) },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Mon profil") },
                            label = { Text("Moi") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KaloyPurple,
                                selectedTextColor = KaloyPurple,
                                indicatorColor = KaloyPurple.copy(alpha = 0.15f),
                                unselectedIconColor = KaloyTextMuted,
                                unselectedTextColor = KaloyTextMuted
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KaloyDarkBg)
                    .padding(paddingValues)
            ) {
                // ---- Barre de recherche interne ----
                item {
                    OutlinedTextField(
                        value = requeteRecherche,
                        onValueChange = { modeleVue.rechercherQuery(it) },
                        placeholder = {
                            Text("Rechercher dans mes playlists...", color = KaloyTextMuted)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = KaloyTextMuted)
                        },
                        trailingIcon = {
                            if (requeteRecherche.isNotEmpty()) {
                                IconButton(onClick = { modeleVue.rechercherQuery("") }) {
                                    Icon(Icons.Default.Clear, "Effacer", tint = KaloyTextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaloyPurple,
                            unfocusedBorderColor = KaloyDarkElevated,
                            focusedTextColor = KaloyTextPrimary,
                            unfocusedTextColor = KaloyTextPrimary,
                            cursorColor = KaloyPurple,
                            focusedContainerColor = KaloyDarkCard,
                            unfocusedContainerColor = KaloyDarkCard
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                // ---- Actions rapides ----
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { modeleVue.afficherDialog(ActiveDialogPlaylist.CreatePlaylist) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Créer", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { modeleVue.afficherDialog(ActiveDialogPlaylist.Merge()) },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaloyPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryAdd,
                                contentDescription = null,
                                tint = KaloyPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fusionner", color = KaloyPurple, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ---- Contenu dynamique ----
                when (val etat = uiState) {
                    PlaylistUiState.Loading -> {
                        item { LoadingIndicator(modifier = Modifier.padding(top = 32.dp)) }
                    }
                    is PlaylistUiState.Error -> {
                        item {
                            ErrorState(
                                message = etat.message,
                                onRetry = { modeleVue.charger() }
                            )
                        }
                    }
                    is PlaylistUiState.Success -> {
                        // Playlists éditoriales (carrousel)
                        if (etat.editoriales.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Playlists à la une",
                                    icon = Icons.Default.Star,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(etat.editoriales, key = { it.id }) { pl ->
                                        EditorialPlaylistCard(playlist = pl, onClick = {})
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // En-tête section "Mes Playlists" + tri
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = KaloyPurpleLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Mes Playlists",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = KaloyTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { modeleVue.afficherDialog(ActiveDialogPlaylist.FilterSort) }
                                ) {
                                    Text(
                                        text = "Trier ▾",
                                        color = KaloyPurpleLight,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Liste vide
                        if (etat.mesPlaylists.isEmpty()) {
                            item {
                                EmptyState(
                                    message = if (requeteRecherche.isNotEmpty())
                                        "Aucune playlist ne correspond à \"$requeteRecherche\""
                                    else
                                        "Vous n'avez pas encore de playlists"
                                )
                            }
                        } else {
                            // Items playlist
                            items(etat.mesPlaylists, key = { it.id }) { pl ->
                                PlaylistItemRow(
                                    item = pl,
                                    limiteAtteinte = etat.nombreTelecharges >= 10,
                                    onPlay = { navigateur.push(PlaylistDetailScreen(pl.id, pl.name)) },
                                    onDownload = { modeleVue.telechargerPlaylist(pl.id) },
                                    onMoreClick = {
                                        modeleVue.afficherDialog(ActiveDialogPlaylist.Options(pl))
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = KaloyDarkElevated
                                )
                            }
                        }
                    }
                }

                // Indicateur d'opération en cours
                if (operationState is PlaylistOperationState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = KaloyPurple,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Message succès/erreur opération
                when (val op = operationState) {
                    is PlaylistOperationState.Success -> item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = KaloyGreen.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = op.message,
                                color = KaloyGreen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    is PlaylistOperationState.Error -> item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = KaloyRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = op.message,
                                color = KaloyRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    else -> {}
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }

        // ---- Dialogs ----
        val successState = uiState as? PlaylistUiState.Success
        val mesPlaylists = successState?.mesPlaylists ?: emptyList()

        when (val d = activeDialog) {
            ActiveDialogPlaylist.CreatePlaylist -> CreatePlaylistDialog(
                state = generationState,
                onGenerate = modeleVue::genererPlaylists,
                onSave = modeleVue::conserverPlaylistGeneree,
                onDismiss = { modeleVue.fermerDialog() }
            )
            ActiveDialogPlaylist.FilterSort -> FilterSortBottomSheet(
                critereActuel = critereTri,
                onCritereChange = { modeleVue.trierPar(it) },
                onDismiss = { modeleVue.fermerDialog() }
            )
            is ActiveDialogPlaylist.Merge -> MergePlaylistsDialog(
                playlists = mesPlaylists,
                preSelected = d.preSelected,
                previewState = mergePreview,
                isMerging = operationState is PlaylistOperationState.Loading,
                onSelectionChanged = modeleVue::chargerApercuFusion,
                onMerge = { ids, nom -> modeleVue.fusionnerPlaylists(ids, nom) },
                onDismiss = { modeleVue.fermerDialog() }
            )
            is ActiveDialogPlaylist.Options -> PlaylistOptionsSheet(
                playlist = d.playlist,
                onModifier = { modeleVue.afficherDialog(ActiveDialogPlaylist.ModifierPlaylist(d.playlist)) },
                onPartager = { },
                onFusionner = {
                    modeleVue.afficherDialog(ActiveDialogPlaylist.Merge(listOf(d.playlist.id)))
                },
                onSupprimer = {
                    modeleVue.afficherDialog(ActiveDialogPlaylist.ConfirmDelete(d.playlist.id))
                },
                onDismiss = { modeleVue.fermerDialog() }
            )
            is ActiveDialogPlaylist.ModifierPlaylist -> ModifierPlaylistDialog(
                playlist = d.playlist,
                onModifier = { nom, desc -> modeleVue.modifierPlaylist(d.playlist.id, nom, desc) },
                onDismiss = { modeleVue.fermerDialog() }
            )
            is ActiveDialogPlaylist.ConfirmDelete -> {
                val nomPlaylist = mesPlaylists.find { it.id == d.id }?.name ?: "cette playlist"
                ConfirmDeletePlaylistDialog(
                    nom = nomPlaylist,
                    onConfirm = { modeleVue.supprimerPlaylist(d.id) },
                    onDismiss = { modeleVue.fermerDialog() }
                )
            }
            ActiveDialogPlaylist.None -> {}
        }

    }
}
