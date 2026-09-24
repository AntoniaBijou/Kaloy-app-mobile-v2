package com.kaloy.app.presentation.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.core.util.maintenantIso
import com.kaloy.app.data.api.ArtistIdDto
import com.kaloy.app.data.api.ConcertSearch
import com.kaloy.app.data.api.StatutParticipationIdDto
import com.kaloy.app.data.api.FollowSearch
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.UserIdDto
import com.kaloy.app.data.model.*
import com.kaloy.app.ui.components.*
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

    // --- Sprint 4 : calendrier (concerts confirmes de l'artiste) ---
    var concertsAVenir by mutableStateOf<List<Concert>>(emptyList())
        private set
    var concertsPasses by mutableStateOf<List<Concert>>(emptyList())
        private set

    /** Un visiteur non connecté ne peut pas suivre : on masque le bouton. */
    val peutSuivre: Boolean get() = gestionSession.isLoggedIn()

    init {
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

                val resultatChansons = try { api.getArtistSongs(idArtiste, size = 30) } catch (_: Exception) { null }
                chansons = resultatChansons?.data?.content ?: emptyList()

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
     * Le decoupage passe / a venir se fait en deux appels bornes sur
     * startTimeMin / startTimeMax, plutot qu'en rapatriant tout pour trier ici.
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

        val maintenant = maintenantIso()

        concertsAVenir = try {
            api.rechercherConcerts(
                ConcertSearch(
                    artist = ArtistIdDto(idArtiste),
                    statut = StatutParticipationIdDto(idConfirme),
                    debutMin = maintenant
                ),
                size = 20,
                sortParam = "startTime,asc"
            ).data?.content ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        concertsPasses = try {
            api.rechercherConcerts(
                ConcertSearch(
                    artist = ArtistIdDto(idArtiste),
                    statut = StatutParticipationIdDto(idConfirme),
                    debutMax = maintenant
                ),
                size = 20,
                // Les plus recents d'abord : un concert d'il y a un mois
                // interesse davantage qu'un concert d'il y a trois ans.
                sortParam = "startTime,desc"
            ).data?.content ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

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
    }
}

// ============================================================
// Écran détail artiste Voyager (Sprint 2 — intégré depuis Ancien)
// ============================================================

data class EcranDetailArtisteVoyager(val idArtiste: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: DetailArtisteViewModel = viewModel(key = "artiste_$idArtiste") {
            DetailArtisteViewModel(idArtiste)
        }

        when {
            modeleVue.enChargement -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
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
                                    Text("←", fontSize = 24.sp, color = Color.White)
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
                                                Text("✓", color = KaloyCyan, fontSize = 18.sp)
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

                    // ---- Albums ----
                    if (modeleVue.albums.isNotEmpty()) {
                        item {
                            SectionHeader(title = "💿 Albums (${modeleVue.albums.size})")
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

                    // ---- Chansons ----
                    if (modeleVue.chansons.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🎵 Chansons (${modeleVue.chansons.size})")
                        }
                        items(
                            count = modeleVue.chansons.size,
                            key = { modeleVue.chansons[it].id }
                        ) { index ->
                            val chanson = modeleVue.chansons[index]
                            SongRow(
                                song = chanson,
                                index = index,
                                onClick = { /* TODO: navigation chanson */ }
                            )
                            if (index < modeleVue.chansons.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = KaloyDarkElevated
                                )
                            }
                        }
                    }

                    // ---- Calendrier : concerts confirmés (Sprint 4) ----
                    if (modeleVue.concertsAVenir.isNotEmpty()) {
                        item {
                            SectionHeader(title = "📅 Concerts à venir (${modeleVue.concertsAVenir.size})")
                        }
                        items(modeleVue.concertsAVenir, key = { it.id }) { concert ->
                            LigneConcert(
                                concert = concert,
                                estAVenir = true,
                                onClick = {
                                    concert.event?.let {
                                        navigateur.push(EcranDetailEvenementVoyager(idEvenement = it.id))
                                    }
                                }
                            )
                        }
                    }

                    if (modeleVue.concertsPasses.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🕘 Concerts passés (${modeleVue.concertsPasses.size})")
                        }
                        items(modeleVue.concertsPasses, key = { it.id }) { concert ->
                            LigneConcert(
                                concert = concert,
                                estAVenir = false,
                                onClick = {
                                    concert.event?.let {
                                        navigateur.push(EcranDetailEvenementVoyager(idEvenement = it.id))
                                    }
                                }
                            )
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
                        text = if (estAbonne) "Suivi ✓" else "Suivre",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
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
                Text(
                    text = "📍 $lieu",
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextSecondary
                )
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
