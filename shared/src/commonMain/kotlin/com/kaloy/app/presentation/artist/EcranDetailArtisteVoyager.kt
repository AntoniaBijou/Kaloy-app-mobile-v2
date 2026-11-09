package com.kaloy.app.presentation.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.util.Calendrier
import com.kaloy.app.core.util.aujourdHuiLocal
import com.kaloy.app.data.api.ArtistIdDto
import com.kaloy.app.data.api.ConcertSearch
import com.kaloy.app.data.api.StatutParticipationIdDto
import com.kaloy.app.data.api.FollowSearch
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.UserIdDto
import com.kaloy.app.data.model.*
import com.kaloy.app.ui.components.*
import com.kaloy.app.presentation.album.EcranDetailAlbumVoyager
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.calendrier.GrilleCalendrier
import com.kaloy.app.presentation.evenement.EcranDetailEvenementVoyager
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

// ============================================================
// ViewModel détail artiste (variables en français)
// ============================================================

class DetailArtisteViewModel(private val idArtiste: Long) : ViewModel() {
    private val api = KaloyApi()
    private val gestionSession: AuthSessionManager = KoinPlatform.getKoin().get()

    var artiste by mutableStateOf<Artist?>(null)
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var chansons by mutableStateOf<List<Song>>(emptyList())
        private set
    var membres by mutableStateOf<List<ArtistGroupMember>>(emptyList())
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    // --- Sprint 3 : follow / unfollow ---
    var estAbonne by mutableStateOf(false)
        private set
    var nombreAbonnes by mutableStateOf(0L)
        private set
    var basculeEnCours by mutableStateOf(false)
        private set

    // Id du follow existant, nécessaire pour le DELETE. Null si on ne suit pas.
    private var idFollow: Long? = null

    // --- Sprint 4 : calendrier mensuel (concerts confirmes de l'artiste) ---

    /**
     * Concerts regroupes par date « AAAA-MM-JJ ».
     *
     * La grille interroge chacune de ses cases pour savoir s'il s'y passe
     * quelque chose : une table indexee par date evite de reparcourir toute la
     * liste des concerts une trentaine de fois par mois affiche.
     */
    var concertsParDate by mutableStateOf<Map<String, List<Concert>>>(emptyMap())
        private set

    /** Mois affiche par la grille ; on ouvre sur le mois courant. */
    var anneeAffichee by mutableStateOf(0)
        private set
    var moisAffiche by mutableStateOf(1)
        private set

    /** Jour sur lequel l'utilisateur a appuye, null tant qu'il n'a rien touche. */
    var dateSelectionnee by mutableStateOf<String?>(null)
        private set

    /**
     * Onglet affiche. Ici et non dans le composable : Voyager detruit
     * celui-ci quand on empile la fiche d'un evenement par-dessus, et un
     * remember y serait perdu — on revenait sur Chansons apres chaque
     * aller-retour. Le ViewModel, lui, survit.
     */
    internal var ongletActif by mutableStateOf(OngletArtiste.CHANSONS)
        private set

    /** Date du jour dans le fuseau du telephone : repere entre passe et a venir. */
    val aujourdHui: String = aujourdHuiLocal()

    /** Un visiteur non connecté ne peut pas suivre : on masque le bouton. */
    val peutSuivre: Boolean get() = gestionSession.isLoggedIn()

    init {
        // Le mois d'ouverture se deduit de la date du jour. Si celle-ci etait
        // illisible, la grille resterait sur janvier de l'an 0 plutot que de
        // planter : ce repli ne devrait jamais servir.
        val dateDuJour = Calendrier.decouper(aujourdHui)
        if (dateDuJour != null) {
            anneeAffichee = dateDuJour.first
            moisAffiche = dateDuJour.second
        }
        chargerArtiste()
    }

    fun chargerArtiste() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val resultatArtiste = api.getArtistById(idArtiste)
                artiste = resultatArtiste.data

                val resultatAlbums = try { api.getArtistAlbums(idArtiste, size = 20) } catch (_: Exception) { null }
                albums = resultatAlbums?.data?.content ?: emptyList()

                // Seules les 5 chansons les plus ecoutees sont affichees, et
                // c'est le serveur qui les classe : compter les ecoutes ici
                // aurait demande une requete par chanson.
                val resultatChansons = try {
                    api.getTopChansonsArtiste(idArtiste, limite = NOMBRE_CHANSONS_AFFICHEES)
                } catch (_: Exception) {
                    null
                }
                chansons = resultatChansons?.data ?: emptyList()

                chargerEtatAbonnement()
                chargerConcerts()
            } catch (e: Exception) {
                erreur = "Impossible de charger l'artiste: ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }

    /**
     * Calendrier de l'artiste (Sprint 4).
     *
     * Un concert est le creneau d'un artiste dans un evenement, et porte aussi
     * le statut de sa participation. On ne montre donc que les CONFIRMED : un
     * PENDING est une invitation non repondue, un DECLINED un refus.
     *
     * La version precedente affichait deux listes et faisait deux appels bornes
     * sur startTimeMin / startTimeMax. La grille, elle, doit pouvoir montrer
     * n'importe quel mois : on rapatrie donc tous les concerts en une fois, puis
     * on les regroupe par jour. Le volume le permet — un artiste joue quelques
     * dizaines de fois — et cela evite un appel reseau a chaque coup de fleche.
     *
     * Un echec reste silencieux : le calendrier est secondaire sur cette fiche.
     */
    private suspend fun chargerConcerts() {
        // L'id de CONFIRMED est lu depuis la table de reference plutot que code
        // en dur : rien ne garantit l'ordre des insertions en base.
        val idConfirme = try {
            api.getStatutsParticipation().data?.content
                ?.firstOrNull { it.name == STATUT_CONFIRME }?.id
        } catch (_: Exception) {
            null
        } ?: return

        val concerts = try {
            api.rechercherConcerts(
                ConcertSearch(
                    artist = ArtistIdDto(idArtiste),
                    statut = StatutParticipationIdDto(idConfirme)
                ),
                size = TAILLE_PAGE_CONCERTS,
                sortParam = "startTime,asc"
            ).data?.content ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // Un concert sans horaire n'a pas de case ou aller : on l'ecarte plutot
        // que de l'accrocher a une date arbitraire.
        concertsParDate = concerts
            .filter { !it.startTime.isNullOrBlank() }
            .groupBy { it.startTime!!.substringBefore('T') }
    }

    fun afficherMoisPrecedent() {
        val (annee, mois) = Calendrier.moisPrecedent(anneeAffichee, moisAffiche)
        changerMois(annee, mois)
    }

    fun afficherMoisSuivant() {
        val (annee, mois) = Calendrier.moisSuivant(anneeAffichee, moisAffiche)
        changerMois(annee, mois)
    }

    private fun changerMois(annee: Int, mois: Int) {
        anneeAffichee = annee
        moisAffiche = mois
        // La date selectionnee appartenait au mois qu'on quitte : la conserver
        // afficherait sous la grille un jour qui n'y figure plus.
        dateSelectionnee = null
    }

    /** Un second appui sur le meme jour replie le detail. */
    fun selectionnerDate(date: String) {
        dateSelectionnee = if (dateSelectionnee == date) null else date
    }

    internal fun choisirOnglet(onglet: OngletArtiste) {
        ongletActif = onglet
    }

    fun concertsDuJour(date: String): List<Concert> = concertsParDate[date] ?: emptyList()

    /**
     * Renseigne le compteur d'abonnés et, si l'utilisateur est connecte, indique
     * s'il suit deja cet artiste. Les deux informations viennent du meme endpoint
     * /follows/search, avec un filtre plus ou moins restrictif.
     *
     * Un echec ici ne doit pas faire echouer l'affichage de la fiche : on
     * retombe silencieusement sur « non abonne, 0 abonne ».
     */
    private suspend fun chargerEtatAbonnement() {
        // Compteur : on ne veut que le total, d'ou size = 1.
        nombreAbonnes = try {
            api.rechercherFollows(
                FollowSearch(artist = ArtistIdDto(idArtiste)),
                size = 1
            ).data?.nombreTotal ?: 0L
        } catch (_: Exception) {
            0L
        }

        if (!peutSuivre) {
            estAbonne = false
            idFollow = null
            return
        }

        val monFollow = try {
            api.rechercherFollows(
                FollowSearch(
                    clientUser = UserIdDto(gestionSession.getUserId()),
                    artist = ArtistIdDto(idArtiste)
                ),
                size = 1
            ).data?.content?.firstOrNull()
        } catch (_: Exception) {
            null
        }

        idFollow = monFollow?.id
        estAbonne = monFollow != null
    }

    /**
     * Bascule l'abonnement. L'interface est mise a jour immediatement, puis
     * remise dans son etat precedent si l'appel reseau echoue.
     */
    fun basculerAbonnement() {
        if (!peutSuivre || basculeEnCours) return

        viewModelScope.launch {
            basculeEnCours = true

            val etatPrecedent = estAbonne
            val followPrecedent = idFollow
            val comptePrecedent = nombreAbonnes

            // Mise a jour optimiste
            estAbonne = !etatPrecedent
            nombreAbonnes = if (etatPrecedent) (comptePrecedent - 1).coerceAtLeast(0) else comptePrecedent + 1

            try {
                if (etatPrecedent) {
                    val aSupprimer = followPrecedent
                        ?: throw IllegalStateException("Abonnement introuvable")
                    api.deleteFollow(aSupprimer)
                    idFollow = null
                } else {
                    val cree = api.creerFollow(
                        idUtilisateur = gestionSession.getUserId(),
                        idArtiste = idArtiste
                    )
                    idFollow = cree.data?.id
                }
            } catch (_: Exception) {
                // Retour a l'etat d'avant le clic
                estAbonne = etatPrecedent
                idFollow = followPrecedent
                nombreAbonnes = comptePrecedent
            } finally {
                basculeEnCours = false
            }
        }
    }

    companion object {
        private const val STATUT_CONFIRME = "CONFIRMED"

        /**
         * Tous les concerts de l'artiste doivent tenir dans une seule page :
         * 200 est large au regard de la realite (quelques dizaines de dates) et
         * dispense de gerer la pagination pour une grille qu'on veut complete.
         */
        private const val TAILLE_PAGE_CONCERTS = 200

        /** La fiche ne montre qu'un apercu, pas tout le catalogue. */
        const val NOMBRE_CHANSONS_AFFICHEES = 5
    }
}

// ============================================================
// Écran détail artiste Voyager (Sprint 2 — intégré depuis Ancien)
// ============================================================

/** Les deux onglets de la fiche. L'ordre est celui de la barre. */
internal enum class OngletArtiste(val label: String) {
    CHANSONS("Chansons"),
    CALENDRIER("Calendrier")
}

data class EcranDetailArtisteVoyager(val idArtiste: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val fileAttente: QueueManager = KoinPlatform.getKoin().get()
        val modeleVue: DetailArtisteViewModel = viewModel(key = "artiste_$idArtiste") {
            DetailArtisteViewModel(idArtiste)
        }

        when {
            modeleVue.enChargement -> {
                SqueletteFicheArtiste(onRetour = { navigateur.pop() })
            }
            modeleVue.erreur != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = modeleVue.erreur!!,
                        onRetry = { modeleVue.chargerArtiste() }
                    )
                }
            }
            modeleVue.artiste != null -> {
                val artisteDetail = modeleVue.artiste!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // ---- Header artiste avec gradient ----
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 360dp et non 280 : l'en-tete doit loger le bouton
                                // retour, le bloc avatar/nom ET le bouton d'abonnement.
                                // A 280dp ce dernier debordait et restait invisible.
                                .height(360.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            KaloyPurple,
                                            KaloyPink.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .safeContentPadding(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Bouton retour
                                IconButton(
                                    onClick = { navigateur.pop() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Retour",
                                        tint = Color.White
                                    )
                                }

                                // Info artiste
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(KaloyPurpleDark, KaloyPink)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = artisteDetail.stageName.take(2).uppercase(),
                                            fontSize = 36.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = artisteDetail.stageName,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (artisteDetail.isCertified) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Verified,
                                                    contentDescription = "Artiste certifié",
                                                    tint = KaloyCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = artisteDetail.artistType?.name ?: "Artiste",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        artisteDetail.activeSinceYear?.let {
                                            Text(
                                                text = "Actif depuis $it",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // ---- Abonnement (Sprint 3) ----
                                BoutonAbonnement(
                                    estAbonne = modeleVue.estAbonne,
                                    nombreAbonnes = modeleVue.nombreAbonnes,
                                    peutSuivre = modeleVue.peutSuivre,
                                    enCours = modeleVue.basculeEnCours,
                                    onClick = { modeleVue.basculerAbonnement() }
                                )
                            }
                        }
                    }

                    // ---- Biographie ----
                    artisteDetail.bio?.let { biographie ->
                        if (biographie.isNotBlank()) {
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Biographie",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = KaloyTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = biographie,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = KaloyTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // ---- Barre d'onglets ----
                    // stickyHeader et non item : la barre reste visible
                    // pendant qu'on fait defiler les chansons ou le mois.
                    stickyHeader {
                        SecondaryTabRow(
                            selectedTabIndex = modeleVue.ongletActif.ordinal,
                            containerColor = KaloyDarkSurface,
                            contentColor = KaloyPurpleLight,
                            divider = { HorizontalDivider(color = KaloyDarkElevated) }
                        ) {
                            OngletArtiste.entries.forEach { onglet ->
                                Tab(
                                    selected = onglet == modeleVue.ongletActif,
                                    onClick = { modeleVue.choisirOnglet(onglet) },
                                    text = {
                                        Text(
                                            text = onglet.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (onglet == modeleVue.ongletActif) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    selectedContentColor = KaloyPurpleLight,
                                    unselectedContentColor = KaloyTextMuted
                                )
                            }
                        }
                    }

                    if (modeleVue.ongletActif == OngletArtiste.CHANSONS) {
                        // ---- Albums ----
                        if (modeleVue.albums.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Albums (${modeleVue.albums.size})",
                                    icon = Icons.Filled.Album
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.albums) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = {
                                                navigateur.push(EcranDetailAlbumVoyager(idAlbum = album.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ---- Chansons ----
                        if (modeleVue.chansons.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Les plus écoutées", icon = Icons.Filled.MusicNote)
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
                                        navigateur.push(LecteurScreen(songId = chanson.id))
                                    }
                                )
                                if (index < modeleVue.chansons.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = KaloyDarkElevated
                                    )
                                }
                            }
                        }

                        // Sans ce message l'onglet s'ouvrirait vide et aurait
                        // l'air casse.
                        if (modeleVue.albums.isEmpty() && modeleVue.chansons.isEmpty()) {
                            item { MessageAucuneChanson() }
                        }
                    }

                    if (modeleVue.ongletActif == OngletArtiste.CALENDRIER) {
                        // ---- Calendrier mensuel des concerts confirmés (Sprint 4) ----
                        item {
                            SectionHeader(title = "Calendrier", icon = Icons.Filled.CalendarMonth)
                        }
                        item {
                            GrilleCalendrier(
                                annee = modeleVue.anneeAffichee,
                                mois = modeleVue.moisAffiche,
                                aujourdHui = modeleVue.aujourdHui,
                                datesOccupees = modeleVue.concertsParDate.keys,
                                dateSelectionnee = modeleVue.dateSelectionnee,
                                onMoisPrecedent = { modeleVue.afficherMoisPrecedent() },
                                onMoisSuivant = { modeleVue.afficherMoisSuivant() },
                                // Pas de raccourci vers la fiche : un appui sur un
                                // jour ouvre le detail sous la grille, et c'est de la
                                // qu'on ouvre l'evenement. Le meme jour donnait sinon
                                // deux affichages differents selon qu'on y arrivait
                                // par un appui ou par un retour depuis la fiche.
                                onJourClique = { date -> modeleVue.selectionnerDate(date) }
                            )
                        }

                        // ---- Détail du jour sélectionné ----
                        modeleVue.dateSelectionnee?.let { dateChoisie ->
                            val concertsDuJour = modeleVue.concertsDuJour(dateChoisie)
                            if (concertsDuJour.isEmpty()) {
                                item { MessageAucunEvenement() }
                            } else {
                                items(
                                    items = concertsDuJour,
                                    // Prefixe obligatoire : les identifiants de concert
                                    // et de chanson se croisent dans la meme LazyColumn,
                                    // et deux clés identiques la feraient planter.
                                    key = { "concert_${it.id}" }
                                ) { concert ->
                                    LigneConcert(
                                        concert = concert,
                                        estAVenir = dateChoisie >= modeleVue.aujourdHui,
                                        onClick = {
                                            concert.event?.let {
                                                navigateur.push(EcranDetailEvenementVoyager(idEvenement = it.id))
                                            }
                                        }
                                    )
                                }
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
}

// ============================================================
// Bouton d'abonnement + compteur (Sprint 3)
// ============================================================

@Composable
private fun BoutonAbonnement(
    estAbonne: Boolean,
    nombreAbonnes: Long,
    peutSuivre: Boolean,
    enCours: Boolean,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Le compteur reste visible meme pour un visiteur non connecte.
        Column {
            Text(
                text = "$nombreAbonnes",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (nombreAbonnes > 1) "abonnés" else "abonné",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        if (peutSuivre) {
            Spacer(modifier = Modifier.width(20.dp))

            Button(
                onClick = onClick,
                enabled = !enCours,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    // Abonne : bouton discret. Non abonne : appel a l'action.
                    containerColor = if (estAbonne) Color.White.copy(alpha = 0.15f) else Color.White,
                    contentColor = if (estAbonne) Color.White else KaloyPurple,
                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                if (enCours) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = if (estAbonne) "Suivi" else "Suivre",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageAucuneChanson() {
    Text(
        text = "Aucune chanson pour le moment",
        style = MaterialTheme.typography.bodyMedium,
        color = KaloyTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    )
}

@Composable
private fun MessageAucunEvenement() {
    Text(
        text = "Aucun événement ni concert prévu",
        style = MaterialTheme.typography.bodyMedium,
        color = KaloyTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    )
}

// ============================================================
// Ligne de concert du calendrier (Sprint 4)
// ============================================================

@Composable
private fun LigneConcert(
    concert: Concert,
    estAVenir: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pastille de date : les concerts a venir ressortent en violet, les
        // passes restent en gris pour ne pas attirer l'oeil inutilement.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (estAVenir) {
                        Brush.linearGradient(listOf(KaloyPurple, KaloyPink))
                    } else {
                        Brush.linearGradient(listOf(KaloyDarkElevated, KaloyDarkCard))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val (jour, mois) = jourEtMois(concert.startTime)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = jour,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (estAVenir) Color.White else KaloyTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mois,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (estAVenir) Color.White.copy(alpha = 0.8f) else KaloyTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Le titre du concert est facultatif : on retombe alors sur le
                // nom de l'evenement parent, toujours renseigne.
                text = concert.title ?: concert.event?.name ?: "Concert",
                style = MaterialTheme.typography.bodyMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            val lieu = concert.venue?.let { v ->
                if (v.location.isNullOrBlank()) v.name else "${v.name} — ${v.location}"
            }
            if (!lieu.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = KaloyTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lieu,
                        style = MaterialTheme.typography.bodySmall,
                        color = KaloyTextSecondary
                    )
                }
            }
        }

        Text("›", color = KaloyTextMuted, fontSize = 20.sp)
    }
}

/**
 * Extrait le jour et le mois abrege d'un horodatage ISO (« 2026-06-14T20:00 »).
 * Renvoie des tirets si la date est absente ou mal formee, plutot que de
 * laisser planter l'affichage.
 */
private fun jourEtMois(iso: String?): Pair<String, String> {
    if (iso.isNullOrBlank()) return "--" to "---"
    val morceaux = iso.substringBefore('T').split("-")
    if (morceaux.size != 3) return "--" to "---"
    val mois = listOf(
        "JAN", "FÉV", "MAR", "AVR", "MAI", "JUIN",
        "JUIL", "AOÛT", "SEP", "OCT", "NOV", "DÉC"
    )
    val indexMois = morceaux[1].toIntOrNull()?.minus(1) ?: return morceaux[2] to "---"
    return morceaux[2] to (mois.getOrNull(indexMois) ?: "---")
}

// ============================================================
// Squelette affiche pendant le chargement
// ============================================================

/**
 * Silhouette de la fiche, le temps que les appels reseau reviennent.
 *
 * L'ecran affichait auparavant une page vide avec un indicateur au centre.
 * Sur le theme clair, « vide » voulait dire blanc casse : on passait d'une
 * page blanche a la fiche, sans transition.
 *
 * Le degrade et le bouton retour sont les memes que ceux de la vraie fiche, et
 * ils ne dependent d'aucune donnee : on peut donc les dessiner tout de suite.
 * Le reste est remplace par des blocs gris aux dimensions de ce qui va
 * arriver, ce qui evite aussi que la page saute au moment du remplissage.
 */
@Composable
private fun SqueletteFicheArtiste(onRetour: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            KaloyPurple,
                            KaloyPink.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .safeContentPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Le retour reste utilisable : rester coince sur un ecran de
                // chargement parce que le reseau tarde serait pire que l'attente.
                IconButton(
                    onClick = onRetour,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    BlocFantome(largeur = 100.dp, hauteur = 100.dp, arrondi = 50.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        BlocFantome(largeur = 180.dp, hauteur = 28.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        BlocFantome(largeur = 110.dp, hauteur = 16.dp)
                    }
                }

                BlocFantome(largeur = 150.dp, hauteur = 40.dp, arrondi = 20.dp)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            BlocFantome(largeur = 120.dp, hauteur = 20.dp)
            Spacer(modifier = Modifier.height(12.dp))
            BlocFantome(largeur = 320.dp, hauteur = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            BlocFantome(largeur = 280.dp, hauteur = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            BlocFantome(largeur = 200.dp, hauteur = 14.dp)
        }
    }
}

/** Rectangle gris qui tient la place d'un contenu pas encore arrive. */
@Composable
private fun BlocFantome(
    largeur: Dp,
    hauteur: Dp,
    arrondi: Dp = 6.dp
) {
    Box(
        modifier = Modifier
            .width(largeur)
            .height(hauteur)
            .clip(RoundedCornerShape(arrondi))
            // Sur le blanc comme sur le sombre, un gris a 20 % se voit sans
            // attirer l'oeil : ce n'est pas du contenu, c'est une attente.
            .background(KaloyTextMuted.copy(alpha = 0.2f))
    )
}
