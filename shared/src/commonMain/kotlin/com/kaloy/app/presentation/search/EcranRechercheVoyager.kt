package com.kaloy.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.data.api.AlbumSearch
import com.kaloy.app.data.api.ArtistSearch
import com.kaloy.app.data.api.GenreSearch
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.SongSearch
import com.kaloy.app.data.model.Album
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.model.Song
import com.kaloy.app.data.repository.HistoriqueRechercheRepository
import com.kaloy.app.presentation.album.EcranDetailAlbumVoyager
import com.kaloy.app.presentation.artist.EcranDetailArtisteVoyager
import com.kaloy.app.presentation.chanson.EcranDetailChansonVoyager
import com.kaloy.app.presentation.genre.EcranGenreVoyager
import com.kaloy.app.ui.components.*
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ============================================================
// Filtres par type de résultat
// ============================================================

enum class OngletRecherche(val libelle: String) {
    TOUT("Tout"),
    ARTISTES("Artistes"),
    CHANSONS("Chansons"),
    ALBUMS("Albums"),
    GENRES("Genres")
}

/** Origine d'une suggestion, pour afficher la bonne icône. */
enum class OrigineSuggestion { HISTORIQUE, ARTISTE, CHANSON, ALBUM }

data class Suggestion(val texte: String, val origine: OrigineSuggestion)

// ============================================================
// ViewModel de recherche globale (variables en français)
// ============================================================

class RechercheViewModel(
    private val historiqueRepo: HistoriqueRechercheRepository
) : ViewModel() {

    private val api = KaloyApi()
    private var tacheRecherche: Job? = null

    var rechercheTexte by mutableStateOf("")
        private set
    var ongletActif by mutableStateOf(OngletRecherche.TOUT)
        private set

    var artistes by mutableStateOf<List<Artist>>(emptyList())
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var genres by mutableStateOf<List<Genre>>(emptyList())
        private set

    var enCoursDeRecherche by mutableStateOf(false)
        private set
    var aCherche by mutableStateOf(false)
        private set

    var historique by mutableStateOf<List<String>>(emptyList())
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set

    /** Masquées dès que l'utilisateur valide ou ouvre un résultat. */
    var suggestionsVisibles by mutableStateOf(true)
        private set

    val totalResultats: Int
        get() = artistes.size + chansons.size + albums.size + genres.size

    init {
        historique = historiqueRepo.lireLocal()
        // Récupère l'historique distant si l'utilisateur est connecté.
        viewModelScope.launch {
            historique = historiqueRepo.synchroniserDepuisBackend()
            recalculerSuggestions()
        }
    }

    fun changerOnglet(onglet: OngletRecherche) {
        ongletActif = onglet
    }

    fun surChangementTexte(nouveauTexte: String) {
        rechercheTexte = nouveauTexte
        suggestionsVisibles = true
        recalculerSuggestions()

        // Debounce : attend 400ms après la dernière frappe.
        tacheRecherche?.cancel()
        if (nouveauTexte.trim().length >= 2) {
            tacheRecherche = viewModelScope.launch {
                delay(400)
                effectuerRecherche(nouveauTexte.trim())
            }
        } else {
            viderResultats()
        }
    }

    /** Validation clavier ou clic sur une suggestion : on fige et on historise. */
    fun validerRecherche(texte: String = rechercheTexte) {
        val nettoye = texte.trim()
        if (nettoye.isBlank()) return

        rechercheTexte = nettoye
        suggestionsVisibles = false
        tacheRecherche?.cancel()
        tacheRecherche = viewModelScope.launch {
            if (nettoye.length >= 2) effectuerRecherche(nettoye)
            historiser(nettoye)
        }
    }

    /** Ouverture d'un résultat : la recherche a abouti, on l'enregistre. */
    fun surOuvertureResultat() {
        suggestionsVisibles = false
        val nettoye = rechercheTexte.trim()
        if (nettoye.isNotBlank()) {
            viewModelScope.launch { historiser(nettoye) }
        }
    }

    fun supprimerDeLHistorique(texte: String) {
        historique = historiqueRepo.supprimer(texte)
        recalculerSuggestions()
    }

    fun viderHistorique() {
        historiqueRepo.vider()
        historique = emptyList()
        recalculerSuggestions()
    }

    private suspend fun historiser(texte: String) {
        historiqueRepo.enregistrer(texte)
        historique = historiqueRepo.lireLocal()
    }

    private fun viderResultats() {
        artistes = emptyList()
        chansons = emptyList()
        albums = emptyList()
        genres = emptyList()
        aCherche = false
    }

    /**
     * Les 4 recherches partent en parallèle : tous les onglets sont remplis
     * en une seule passe, changer d'onglet ne relance aucune requête.
     * Chaque appel est isolé — un type qui échoue n'annule pas les autres.
     */
    private suspend fun effectuerRecherche(texteRecherche: String) = coroutineScope {
        enCoursDeRecherche = true
        try {
            val tacheArtistes = async {
                runCatching {
                    api.searchArtists(ArtistSearch(stageName = texteRecherche), size = 20).data?.content
                }.getOrNull() ?: emptyList()
            }
            val tacheChansons = async {
                runCatching {
                    api.rechercherChansons(SongSearch(title = texteRecherche), size = 20).data?.content
                }.getOrNull() ?: emptyList()
            }
            val tacheAlbums = async {
                runCatching {
                    api.rechercherAlbums(AlbumSearch(title = texteRecherche), size = 20).data?.content
                }.getOrNull() ?: emptyList()
            }
            val tacheGenres = async {
                runCatching {
                    api.rechercherGenres(GenreSearch(name = texteRecherche), size = 20).data?.content
                }.getOrNull() ?: emptyList()
            }

            artistes = tacheArtistes.await()
            chansons = tacheChansons.await()
            albums = tacheAlbums.await()
            genres = tacheGenres.await()

            aCherche = true
            recalculerSuggestions()
        } finally {
            enCoursDeRecherche = false
        }
    }

    /**
     * Auto-complétion : l'historique correspondant d'abord (l'utilisateur
     * retrouve ce qu'il a déjà cherché), puis les libellés réels du backend.
     */
    private fun recalculerSuggestions() {
        val saisie = rechercheTexte.trim()

        if (saisie.isBlank()) {
            suggestions = emptyList()
            return
        }

        val depuisHistorique = historique
            .filter { it.contains(saisie, ignoreCase = true) && !it.equals(saisie, ignoreCase = true) }
            .take(3)
            .map { Suggestion(it, OrigineSuggestion.HISTORIQUE) }

        val depuisResultats = buildList {
            artistes.forEach { add(Suggestion(it.stageName, OrigineSuggestion.ARTISTE)) }
            chansons.forEach { add(Suggestion(it.title, OrigineSuggestion.CHANSON)) }
            albums.forEach { add(Suggestion(it.title, OrigineSuggestion.ALBUM)) }
        }
            .filter { it.texte.contains(saisie, ignoreCase = true) && !it.texte.equals(saisie, ignoreCase = true) }
            .distinctBy { it.texte.lowercase() }
            .take(5)

        suggestions = (depuisHistorique + depuisResultats).distinctBy { it.texte.lowercase() }
    }
}

// ============================================================
// Écran de recherche globale — Sprint 2
// ============================================================

class EcranRechercheVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val historiqueRepo = koinInject<HistoriqueRechercheRepository>()
        val modeleVue: RechercheViewModel = viewModel { RechercheViewModel(historiqueRepo) }
        val gestionnaireFocus = LocalFocusManager.current

        /** Tout passage vers un écran de détail historise la recherche en cours. */
        fun ouvrir(destination: Screen) {
            modeleVue.surOuvertureResultat()
            navigateur.push(destination)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
        ) {
            // ---- Barre de recherche ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateur.pop() }) {
                    Text("←", fontSize = 24.sp, color = KaloyTextPrimary)
                }

                OutlinedTextField(
                    value = modeleVue.rechercheTexte,
                    onValueChange = { modeleVue.surChangementTexte(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Rechercher...", color = KaloyTextMuted) },
                    trailingIcon = {
                        if (modeleVue.rechercheTexte.isNotEmpty()) {
                            IconButton(onClick = { modeleVue.surChangementTexte("") }) {
                                Text("✕", color = KaloyTextMuted, fontSize = 16.sp)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            modeleVue.validerRecherche()
                            gestionnaireFocus.clearFocus()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaloyPurple,
                        unfocusedBorderColor = KaloyDarkElevated,
                        focusedTextColor = KaloyTextPrimary,
                        unfocusedTextColor = KaloyTextPrimary,
                        cursorColor = KaloyPurple,
                        focusedContainerColor = KaloyDarkCard,
                        unfocusedContainerColor = KaloyDarkCard
                    )
                )
            }

            when {
                // ---- Champ vide : recherches récentes ----
                modeleVue.rechercheTexte.isBlank() -> PanneauHistorique(
                    historique = modeleVue.historique,
                    onRelancer = {
                        modeleVue.surChangementTexte(it)
                        modeleVue.validerRecherche(it)
                        gestionnaireFocus.clearFocus()
                    },
                    onSupprimer = { modeleVue.supprimerDeLHistorique(it) },
                    onToutEffacer = { modeleVue.viderHistorique() }
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    // ---- Auto-complétion pendant la saisie ----
                    if (modeleVue.suggestionsVisibles && modeleVue.suggestions.isNotEmpty()) {
                        BandeauSuggestions(
                            suggestions = modeleVue.suggestions,
                            onChoisir = {
                                modeleVue.validerRecherche(it)
                                gestionnaireFocus.clearFocus()
                            }
                        )
                    }

                    // ---- Filtres par type ----
                    if (modeleVue.aCherche) {
                        BarreOnglets(
                            ongletActif = modeleVue.ongletActif,
                            onSelection = { modeleVue.changerOnglet(it) }
                        )
                    }

                    when {
                        modeleVue.enCoursDeRecherche -> LoadingIndicator()

                        !modeleVue.aCherche -> EmptyState(
                            message = "Tapez au moins 2 caractères pour rechercher"
                        )

                        modeleVue.totalResultats == 0 -> EmptyState(
                            message = "Aucun résultat pour \"${modeleVue.rechercheTexte}\""
                        )

                        else -> ListeResultats(
                            modeleVue = modeleVue,
                            onArtiste = { ouvrir(EcranDetailArtisteVoyager(idArtiste = it.id)) },
                            onChanson = { ouvrir(EcranDetailChansonVoyager(idChanson = it.id)) },
                            onAlbum = { ouvrir(EcranDetailAlbumVoyager(idAlbum = it.id)) },
                            onGenre = { ouvrir(EcranGenreVoyager(idGenre = it.id, nomGenre = it.name)) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Recherches récentes (champ vide)
// ============================================================

@Composable
private fun PanneauHistorique(
    historique: List<String>,
    onRelancer: (String) -> Unit,
    onSupprimer: (String) -> Unit,
    onToutEffacer: () -> Unit
) {
    if (historique.isEmpty()) {
        EmptyState(message = "Vos recherches récentes apparaîtront ici")
        return
    }

    LazyColumn {
        item {
            SectionHeader(
                title = "🕘 Recherches récentes",
                action = "Tout effacer",
                onAction = onToutEffacer
            )
        }
        items(items = historique, key = { it }) { entree ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRelancer(entree) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🕘", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = entree,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KaloyTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onSupprimer(entree) }) {
                    Text("✕", color = KaloyTextMuted, fontSize = 14.sp)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = KaloyDarkElevated
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ============================================================
// Suggestions d'auto-complétion
// ============================================================

@Composable
private fun BandeauSuggestions(
    suggestions: List<Suggestion>,
    onChoisir: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KaloyDarkCard)
    ) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChoisir(suggestion.texte) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (suggestion.origine) {
                        OrigineSuggestion.HISTORIQUE -> "🕘"
                        OrigineSuggestion.ARTISTE -> "🎤"
                        OrigineSuggestion.CHANSON -> "🎵"
                        OrigineSuggestion.ALBUM -> "💿"
                    },
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = suggestion.texte,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KaloyTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text("↖", color = KaloyTextMuted, fontSize = 14.sp)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

// ============================================================
// Barre d'onglets (filtres par type)
// ============================================================

@Composable
private fun BarreOnglets(
    ongletActif: OngletRecherche,
    onSelection: (OngletRecherche) -> Unit
) {
    SecondaryScrollableTabRow(
        selectedTabIndex = ongletActif.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = KaloyPurpleLight,
        edgePadding = 16.dp,
        divider = { HorizontalDivider(color = KaloyDarkElevated) }
    ) {
        OngletRecherche.entries.forEach { onglet ->
            Tab(
                selected = onglet == ongletActif,
                onClick = { onSelection(onglet) },
                text = {
                    Text(
                        text = onglet.libelle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (onglet == ongletActif) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selectedContentColor = KaloyPurpleLight,
                unselectedContentColor = KaloyTextMuted
            )
        }
    }
}

// ============================================================
// Résultats, selon l'onglet actif
// ============================================================

@Composable
private fun ListeResultats(
    modeleVue: RechercheViewModel,
    onArtiste: (Artist) -> Unit,
    onChanson: (Song) -> Unit,
    onAlbum: (Album) -> Unit,
    onGenre: (Genre) -> Unit
) {
    val apercu = 3 // nb d'éléments par section dans l'onglet « Tout »

    LazyColumn {
        val onglet = modeleVue.ongletActif

        // ---------- Artistes ----------
        if (onglet == OngletRecherche.TOUT || onglet == OngletRecherche.ARTISTES) {
            val liste = if (onglet == OngletRecherche.TOUT) modeleVue.artistes.take(apercu) else modeleVue.artistes
            if (liste.isNotEmpty()) {
                item { SectionHeader(title = "🎤 Artistes (${modeleVue.artistes.size})") }
                items(items = liste, key = { "artiste-${it.id}" }) { artiste ->
                    LigneRechercheArtiste(artiste = artiste, onClick = { onArtiste(artiste) })
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = KaloyDarkElevated
                    )
                }
            }
        }

        // ---------- Chansons ----------
        if (onglet == OngletRecherche.TOUT || onglet == OngletRecherche.CHANSONS) {
            val liste = if (onglet == OngletRecherche.TOUT) modeleVue.chansons.take(apercu) else modeleVue.chansons
            if (liste.isNotEmpty()) {
                item { SectionHeader(title = "🎵 Chansons (${modeleVue.chansons.size})") }
                itemsIndexed(items = liste, key = { _, c -> "chanson-${c.id}" }) { index, chanson ->
                    SongRow(song = chanson, index = index, onClick = { onChanson(chanson) })
                }
            }
        }

        // ---------- Albums ----------
        if (onglet == OngletRecherche.TOUT || onglet == OngletRecherche.ALBUMS) {
            if (modeleVue.albums.isNotEmpty()) {
                item { SectionHeader(title = "💿 Albums (${modeleVue.albums.size})") }
                if (onglet == OngletRecherche.TOUT) {
                    // Carrousel, comme sur l'accueil.
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = modeleVue.albums, key = { it.id }) { album ->
                                AlbumCard(album = album, onClick = { onAlbum(album) })
                            }
                        }
                    }
                } else {
                    items(items = modeleVue.albums, key = { "album-${it.id}" }) { album ->
                        LigneRechercheAlbum(album = album, onClick = { onAlbum(album) })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = KaloyDarkElevated
                        )
                    }
                }
            }
        }

        // ---------- Genres ----------
        if (onglet == OngletRecherche.TOUT || onglet == OngletRecherche.GENRES) {
            if (modeleVue.genres.isNotEmpty()) {
                item { SectionHeader(title = "🏷️ Genres (${modeleVue.genres.size})") }
                items(items = modeleVue.genres, key = { "genre-${it.id}" }) { genre ->
                    LigneRechercheGenre(genre = genre, onClick = { onGenre(genre) })
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = KaloyDarkElevated
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ============================================================
// Lignes de résultat
// ============================================================

@Composable
private fun LigneRechercheArtiste(
    artiste: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    brush = Brush.linearGradient(colors = listOf(KaloyPurple, KaloyPink)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = artiste.stageName.take(2).uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artiste.stageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KaloyTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (artiste.isCertified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("✓", color = KaloyCyan, fontSize = 14.sp)
                }
            }
            Text(
                text = artiste.artistType?.name ?: "Artiste",
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextSecondary
            )
        }

        TextButton(onClick = onClick) {
            Text("Voir", color = KaloyPurpleLight)
        }
    }
}

@Composable
private fun LigneRechercheAlbum(
    album: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.linearGradient(colors = listOf(KaloyPurple, KaloyCyan))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("💿", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = album.artist?.stageName ?: "Artiste inconnu",
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextSecondary
            )
        }
    }
}

@Composable
private fun LigneRechercheGenre(
    genre: Genre,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    brush = Brush.linearGradient(colors = listOf(KaloyPink, KaloyPurpleLight)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🏷️", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = genre.name,
            style = MaterialTheme.typography.bodyMedium,
            color = KaloyTextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Text("›", color = KaloyTextMuted, fontSize = 20.sp)
    }
}
