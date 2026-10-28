package com.kaloy.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.ListeningHistorySearchDto
import com.kaloy.app.data.api.UserIdDto
import com.kaloy.app.core.util.Calendrier
import com.kaloy.app.core.util.aujourdHuiLocal
import com.kaloy.app.data.model.*
import com.kaloy.app.presentation.artist.EcranDetailArtisteVoyager
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.common.BarreNavigationPrincipale
import com.kaloy.app.presentation.common.OngletPrincipal
import com.kaloy.app.presentation.evenement.EcranDetailEvenementVoyager
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.presentation.search.EcranRechercheVoyager
import com.kaloy.app.presentation.queue.MiniPlayer
import com.kaloy.app.presentation.queue.UpNextSheet
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.ui.components.*
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ============================================================
// ViewModel de l'écran d'accueil (variables en français)
// ============================================================

class AccueilViewModel : ViewModel() {
    private val api = KaloyApi()

    // --- Données affichées ---
    var artistes by mutableStateOf<List<Artist>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var evenementsAVenir by mutableStateOf<List<Event>>(emptyList())
        private set
    var ecoutesRecentes by mutableStateOf<List<ListeningHistory>>(emptyList())
        private set

    // --- États ---
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        chargerDonnees()
    }

    /**
     * Evenements en cours ou a venir dans les trente prochains jours.
     *
     * Trente jours glissants et non le mois civil : un filtre sur le mois en
     * cours viderait la section les derniers jours du mois, ce qui ne se voit
     * pas en developpement et saute aux yeux en demonstration.
     *
     * Le tri et le filtre sont ici parce que /events ne fait ni l'un ni
     * l'autre : il renvoie tout, dans l'ordre des identifiants.
     */
    private fun aVenirDansLeMois(evenements: List<Event>): List<Event> {
        val aujourdHui = aujourdHuiLocal()
        val limite = Calendrier.dansNJours(aujourdHui, JOURS_A_VENIR) ?: return emptyList()
        return evenements
            // Sur endDate : un evenement commence hier et fini demain est
            // toujours en cours, il a sa place dans la liste.
            .filter { (it.endDate ?: it.startDate).orEmpty() >= aujourdHui }
            .filter { (it.startDate ?: "") <= limite }
            .sortedBy { it.startDate ?: "" }
    }

    fun chargerDonnees() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val resultatArtistes = try { api.getArtists(size = 20) } catch (e: Exception) { null }
                val resultatAlbums = try { api.getAlbums(size = 10) } catch (e: Exception) { null }
                // Classement de la semaine, a la place de la liste brute des
                // chansons : le serveur compte les ecoutes et renvoie le tri.
                val resultatChansons = try {
                    api.getClassement(jours = JOURS_CLASSEMENT, limite = TAILLE_CLASSEMENT)
                } catch (e: Exception) { null }
                val resultatEvenements = try { api.getEvents(size = 50) } catch (e: Exception) { null }

                // Simuler utilisateur ID = 1
                val resultatHistorique = try {
                    api.getListeningHistory(
                        query = ListeningHistorySearchDto(UserIdDto(1L)),
                        size = 10
                    )
                } catch (e: Exception) { null }

                artistes = resultatArtistes?.data?.content ?: emptyList()
                albums = resultatAlbums?.data?.content ?: emptyList()
                chansons = resultatChansons?.data ?: emptyList()
                evenementsAVenir = aVenirDansLeMois(resultatEvenements?.data?.content.orEmpty())

                // Garder les chansons uniques dans l'historique
                ecoutesRecentes = (resultatHistorique?.data?.content ?: emptyList())
                    .distinctBy { it.song?.id }

                if (artistes.isEmpty() && albums.isEmpty() && chansons.isEmpty()) {
                    erreur = "Aucune donnée disponible. Vérifiez que le backend est démarré."
                }
            } catch (e: Exception) {
                erreur = "Erreur de connexion: ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    companion object {
        /** Fenetre glissante du classement : « la semaine » = 7 derniers jours. */
        const val JOURS_CLASSEMENT = 7
        const val TAILLE_CLASSEMENT = 10

        /** Fenetre glissante des evenements mis en avant sur l'accueil. */
        const val JOURS_A_VENIR = 30
    }
}

// ============================================================
// Écran d'accueil Voyager (Sprint 0 — intégré depuis Ancien)
// ============================================================

data class HomeScreen(
    val username: String = "Utilisateur",
    val isVisitor: Boolean = false
) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val gestionSession = koinInject<AuthSessionManager>()
        val modeleVue: AccueilViewModel = viewModel { AccueilViewModel() }
        val fileAttente = koinInject<QueueManager>()
        val lecteurAudio = koinInject<AudioPlayerController>()
        val chansonCourante by fileAttente.currentSong.collectAsState()
        val chansonsSuivantes by fileAttente.upNext.collectAsState()
        val lectureActive by lecteurAudio.isPlaying.collectAsState()
        var afficherFile by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (!isVisitor && !gestionSession.isLoggedIn()) {
                navigateur.replace(WelcomeScreen())
            }
        }

        Scaffold(
            bottomBar = {
                if (!isVisitor) {
                    Column {
                        chansonCourante?.let { chanson ->
                            MiniPlayer(
                                song = chanson,
                                isPlaying = lectureActive,
                                onClick = { navigateur.push(LecteurScreen(chanson.id)) },
                                onTogglePlay = {
                                    if (lectureActive) lecteurAudio.pause() else lecteurAudio.resume()
                                },
                                onQueueClick = { afficherFile = true }
                            )
                        }
                        BarreNavigationPrincipale(
                            ongletActif = OngletPrincipal.ACCUEIL,
                            onAccueil = {},
                            onRecherche = { navigateur.push(EcranRechercheVoyager()) },
                            onMoi = { navigateur.push(MoiScreen()) }
                        )
                    }
                }
            },
            containerColor = KaloyDarkBg
        ) { espaceInterieur ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espaceInterieur)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // ---- Barre de recherche, posee sur le degrade ----
                //
                // Le titre est parti, le degrade reste : il porte l'identite de
                // l'ecran. La hauteur passe de 200 a 120 dp, juste de quoi loger
                // la barre et laisser la couleur se fondre dans le fond.
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        KaloyPurple.copy(alpha = 0.8f),
                                        KaloyPink.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                            // safeContentPadding avant le padding : c'est lui qui
                            // ecarte la barre d'etat, et sans lui le champ de
                            // recherche passerait sous l'heure et la batterie.
                            .safeContentPadding()
                            // Aucune hauteur fixe. Avec .height(120.dp), les
                            // encoches etaient retranchees DEDANS : il restait
                            // ~38 dp au bouton, qui en demande 40, et son texte
                            // etait rogne. La boite s'ajuste donc a son contenu,
                            // et le bas de marge laisse le degrade se fondre.
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        OutlinedButton(
                            onClick = { navigateur.push(EcranRechercheVoyager()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = KaloyTextSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = KaloyTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rechercher artistes, albums, chansons...",
                                modifier = Modifier.weight(1f),
                                color = KaloyTextMuted
                            )
                        }
                    }
                }

                // ---- Contenu ----
                when {
                    modeleVue.enChargement -> {
                        item { LoadingIndicator() }
                    }
                    modeleVue.erreur != null -> {
                        item {
                            ErrorState(
                                message = modeleVue.erreur!!,
                                onRetry = { modeleVue.chargerDonnees() }
                            )
                        }
                    }
                    else -> {
                        // Récemment écouté
                        if (modeleVue.ecoutesRecentes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Récemment écouté", icon = Icons.Filled.History)
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(modeleVue.ecoutesRecentes) { historiqueObj ->
                                        historiqueObj.song?.let { chanson ->
                                            Column(
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .clickable { navigateur.push(LecteurScreen(songId = chanson.id)) },
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(100.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(KaloyPurple, KaloyPink)
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.MusicNote,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = chanson.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = KaloyTextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = chanson.artist?.stageName ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = KaloyTextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }

                        // Evenements des trente prochains jours, tous artistes
                        if (modeleVue.evenementsAVenir.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Événements à venir", icon = Icons.Filled.Event)
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.evenementsAVenir, key = { it.id }) { evenement ->
                                        CarteEvenement(
                                            evenement = evenement,
                                            onClick = {
                                                navigateur.push(
                                                    EcranDetailEvenementVoyager(idEvenement = evenement.id)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Artistes populaires
                        if (modeleVue.artistes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Artistes", icon = Icons.Filled.Mic)
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.artistes) { artiste ->
                                        ArtistCard(
                                            artist = artiste,
                                            onClick = {
                                                navigateur.push(EcranDetailArtisteVoyager(idArtiste = artiste.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Albums récents
                        if (modeleVue.albums.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Albums", icon = Icons.Filled.Album)
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.albums) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = { /* TODO: navigation album */ }
                                        )
                                    }
                                }
                            }
                        }

                        // Chansons
                        if (modeleVue.chansons.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Top de la semaine", icon = Icons.Filled.EmojiEvents)
                            }
                            items(
                                count = modeleVue.chansons.size,
                                key = { modeleVue.chansons[it].id }
                            ) { index ->
                                val chanson = modeleVue.chansons[index]
                                SongRow(
                                    song = chanson,
                                    index = index,
                                    onClick = {
                                        fileAttente.setContext(modeleVue.chansons, chanson)
                                        navigateur.push(LecteurScreen(songId = chanson.id))
                                    },
                                    onMoreClick = {
                                        fileAttente.playNow(chanson)
                                    },
                                    onPlayNow = {
                                        fileAttente.setContext(modeleVue.chansons, chanson)
                                        navigateur.push(LecteurScreen(songId = chanson.id))
                                    },
                                    onAddNext = { fileAttente.addNext(chanson) },
                                    onAddToQueue = { fileAttente.addToQueue(chanson) }
                                )
                                if (index < modeleVue.chansons.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = KaloyDarkElevated
                                    )
                                }
                            }
                        }

                        // Bouton visiteur
                        if (isVisitor) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { navigateur.replace(WelcomeScreen()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text("Créer un compte")
                                }
                            }
                        }

                        // Espacement en bas
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
        if (afficherFile) {
            UpNextSheet(
                currentSong = chansonCourante,
                songs = chansonsSuivantes,
                onDismiss = { afficherFile = false },
                onPlay = { chanson ->
                    fileAttente.playFromQueue(chanson)
                    afficherFile = false
                    navigateur.push(LecteurScreen(chanson.id))
                },
                onRemove = fileAttente::remove,
                onClear = fileAttente::clear
            )
        }
    }
}

// ============================================================
// Carte d'un evenement a venir, sur l'accueil
// ============================================================

/**
 * Le modele Event ne porte pas de lieu : les salles sont rattachees aux
 * creneaux, pas a l'evenement. On montre donc la date et l'organisateur, et le
 * detail renseigne le reste.
 */
@Composable
private fun CarteEvenement(evenement: Event, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(220.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = periodeLisible(evenement.startDate, evenement.endDate),
                style = MaterialTheme.typography.labelLarge,
                color = KaloyCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = evenement.name,
                style = MaterialTheme.typography.titleSmall,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            evenement.createdByArtist?.stageName?.let { organisateur ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Par $organisateur",
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Abreviations usuelles. Tronquer « Octobre » a quatre lettres donnait « octo »,
 * qui n'est pas une abreviation mais un mot coupe.
 */
private val MOIS_COURTS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)

/** « 7 nov. » seul, ou « 27 oct. — 31 oct. » quand l'evenement dure. */
private fun periodeLisible(debut: String?, fin: String?): String {
    val d = Calendrier.decouper(debut) ?: return ""
    val jourDebut = "${d.third} ${MOIS_COURTS.getOrElse(d.second - 1) { "" }}"
    val f = Calendrier.decouper(fin)
    if (f == null || fin == debut) return jourDebut
    val jourFin = "${f.third} ${MOIS_COURTS.getOrElse(f.second - 1) { "" }}"
    return "$jourDebut — $jourFin"
}
