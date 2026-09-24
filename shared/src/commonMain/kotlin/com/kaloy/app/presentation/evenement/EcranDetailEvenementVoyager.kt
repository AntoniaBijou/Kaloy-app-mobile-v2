package com.kaloy.app.presentation.evenement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.api.CommentSearch
import com.kaloy.app.data.api.ConcertSearch
import com.kaloy.app.data.api.EventMediaSearch
import com.kaloy.app.data.api.EvenementIdDto
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.LikeSearch
import com.kaloy.app.data.api.ReferenceIdDto
import com.kaloy.app.data.api.ReportSearch
import com.kaloy.app.data.api.UserIdDto
import com.kaloy.app.data.model.Comment
import com.kaloy.app.data.model.Concert
import com.kaloy.app.data.model.Event
import com.kaloy.app.data.model.EventMedia
import com.kaloy.app.presentation.artist.EcranDetailArtisteVoyager
import com.kaloy.app.presentation.common.MediaChoisi
import com.kaloy.app.presentation.common.SmartVideoPlayerComposable
import com.kaloy.app.presentation.common.SourceMedia
import com.kaloy.app.presentation.common.rememberSelecteurMedia
import com.kaloy.app.ui.components.EmptyState
import com.kaloy.app.ui.components.ErrorState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.components.SectionHeader
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

// ============================================================
// ViewModel — fiche evenement (Sprint 4) + interactions (Sprint 3)
// ============================================================

class DetailEvenementViewModel(private val idEvenement: Long) : ViewModel() {
    private val api = KaloyApi()
    private val gestionSession: AuthSessionManager = KoinPlatform.getKoin().get()

    var evenement by mutableStateOf<Event?>(null)
        private set

    // Tous les concerts de l'evenement, quel que soit leur statut. On s'en sert
    // pour deduire deux choses que l'entite Event ne porte pas : les lieux et
    // les artistes participants.
    var concerts by mutableStateOf<List<Concert>>(emptyList())
        private set

    var medias by mutableStateOf<List<EventMedia>>(emptyList())
        private set

    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    // --- Sprint 3 : likes ---
    var nombreLikes by mutableStateOf(0L)
        private set
    var jAime by mutableStateOf(false)
        private set
    var likeEnCours by mutableStateOf(false)
        private set
    // Id de mon like, necessaire pour le DELETE. Null si je n'aime pas.
    private var idMonLike: Long? = null

    // --- Sprint 3 : commentaires ---
    private var tousLesCommentaires by mutableStateOf<List<Comment>>(emptyList())
    // Commentaires que j'ai signales : masques a mes yeux en attendant la
    // moderation, sans les retirer pour les autres.
    private var idsSignales by mutableStateOf<Set<Long>>(emptySet())

    val commentaires: List<Comment>
        get() = tousLesCommentaires.filter { it.id !in idsSignales }

    var saisie by mutableStateOf("")
        private set
    var envoiEnCours by mutableStateOf(false)
        private set

    // Retour a afficher apres une action (erreur, ou confirmation de signalement).
    var messageAction by mutableStateOf<String?>(null)
        private set

    // Ids lus dans les tables de reference au chargement.
    private var idCibleEvenement: Long? = null
    private var idCibleCommentaire: Long? = null
    private var idStatutEnAttente: Long? = null
    private var idTypePhoto: Long? = null

    // --- Sprint 5 : ajout d'une photo a la galerie ---
    var envoiPhotoEnCours by mutableStateOf(false)
        private set

    /** L'ajout n'est possible que connecte, et si le type PHOTO a ete resolu. */
    val peutAjouterPhoto: Boolean get() = estConnecte && idTypePhoto != null

    /**
     * Programme de l'evenement : les concerts confirmes, groupes par jour puis
     * par scene. Deux scenes peuvent jouer en meme temps, d'ou le regroupement
     * plutot qu'une simple liste chronologique.
     */
    val programme: List<JourDeProgramme>
        get() = concerts
            .filter { it.status?.name == STATUT_CONFIRME && it.startTime != null }
            .sortedBy { it.startTime }
            .groupBy { it.startTime!!.substringBefore('T') }
            .map { (jour, concertsDuJour) ->
                JourDeProgramme(
                    jour = jour,
                    scenes = concertsDuJour
                        .groupBy { it.venue?.name ?: "Scène à préciser" }
                        .map { (scene, concertsScene) -> SceneDeProgramme(scene, concertsScene) }
                )
            }

    /** Un visiteur voit les compteurs et les commentaires, sans pouvoir agir. */
    val estConnecte: Boolean get() = gestionSession.isLoggedIn()

    /** Les interactions ne sont possibles qu'une fois les references resolues. */
    val interactionsDisponibles: Boolean get() = idCibleEvenement != null

    fun estDeMoi(commentaire: Comment): Boolean =
        estConnecte && commentaire.authorUser?.id == gestionSession.getUserId()

    /** Artistes dont la participation est confirmee, sans doublon. */
    val artistesConfirmes: List<Concert>
        get() = concerts
            .filter { it.status?.name == STATUT_CONFIRME && it.artist != null }
            .distinctBy { it.artist?.id }

    /** Lieux distincts ou se tient l'evenement. */
    val lieux: List<String>
        get() = concerts
            .mapNotNull { it.venue }
            .distinctBy { it.id }
            .map { lieu ->
                if (lieu.location.isNullOrBlank()) lieu.name else "${lieu.name} — ${lieu.location}"
            }

    val photos: List<EventMedia> get() = medias.filter { it.mediaType?.name == TYPE_PHOTO }
    val videos: List<EventMedia> get() = medias.filter { it.mediaType?.name == TYPE_VIDEO }

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val detail = api.getEventById(idEvenement)
                evenement = detail.data
                    ?: throw IllegalStateException("Événement introuvable")

                // Concerts, medias et interactions ne dependent pas les uns des
                // autres : on les demande en parallele plutot qu'a la suite.
                val tacheConcerts = async {
                    try {
                        api.rechercherConcerts(
                            ConcertSearch(event = EvenementIdDto(idEvenement)),
                            size = 50
                        ).data?.content
                    } catch (_: Exception) {
                        null
                    }
                }
                val tacheMedias = async {
                    try {
                        api.rechercherMediasEvenement(
                            EventMediaSearch(event = EvenementIdDto(idEvenement)),
                            size = 50
                        ).data?.content
                    } catch (_: Exception) {
                        null
                    }
                }
                val tacheInteractions = async { chargerInteractions() }

                concerts = tacheConcerts.await() ?: emptyList()
                medias = tacheMedias.await() ?: emptyList()
                tacheInteractions.await()
            } catch (e: Exception) {
                erreur = e.message ?: "Impossible de charger l'événement."
            } finally {
                enChargement = false
            }
        }
    }

    /**
     * Likes, commentaires et signalements. Un echec ici ne doit pas empecher
     * l'affichage de l'evenement : on retombe sur « 0 like, aucun commentaire ».
     */
    private suspend fun chargerInteractions() {
        if (!resoudreReferences()) return
        val cible = idCibleEvenement ?: return

        nombreLikes = try {
            api.rechercherLikes(
                LikeSearch(targetType = ReferenceIdDto(cible), targetId = idEvenement),
                size = 1
            ).data?.nombreTotal ?: 0L
        } catch (_: Exception) {
            0L
        }

        if (estConnecte) {
            val monLike = try {
                api.rechercherLikes(
                    LikeSearch(
                        user = UserIdDto(gestionSession.getUserId()),
                        targetType = ReferenceIdDto(cible),
                        targetId = idEvenement
                    ),
                    size = 1
                ).data?.content?.firstOrNull()
            } catch (_: Exception) {
                null
            }
            idMonLike = monLike?.id
            jAime = monLike != null

            idsSignales = chargerMesSignalements()
        }

        chargerCommentaires()
    }

    /**
     * Lit les ids des types de cible (EVENT, COMMENT) et du statut PENDING par
     * leur nom, plutot que de les coder en dur : l'ordre des insertions en base
     * n'est pas garanti. Ne refait pas le travail s'il est deja fait.
     */
    private suspend fun resoudreReferences(): Boolean {
        if (idCibleEvenement != null) return true
        return try {
            val cibles = api.getCiblesInteraction().data?.content.orEmpty()
            idCibleEvenement = cibles.firstOrNull { it.name == CIBLE_EVENEMENT }?.id
            idCibleCommentaire = cibles.firstOrNull { it.name == CIBLE_COMMENTAIRE }?.id

            idStatutEnAttente = api.getStatutsSignalement().data?.content.orEmpty()
                .firstOrNull { it.name == STATUT_EN_ATTENTE }?.id

            idTypePhoto = try {
                api.getTypesMedia().data?.content.orEmpty()
                    .firstOrNull { it.name == TYPE_PHOTO }?.id
            } catch (_: Exception) {
                null
            }

            idCibleEvenement != null
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun chargerCommentaires() {
        val cible = idCibleEvenement ?: return
        tousLesCommentaires = try {
            api.rechercherCommentaires(
                CommentSearch(
                    targetType = ReferenceIdDto(cible),
                    targetId = idEvenement,
                    // Les commentaires masques par la moderation ne s'affichent pas.
                    isHidden = false
                )
            ).data?.content ?: emptyList()
        } catch (_: Exception) {
            // Le backend renvoie 404 quand il n'y a aucun resultat : la
            // deserialisation passe (data = null), mais on se protege aussi ici.
            emptyList()
        }
    }

    private suspend fun chargerMesSignalements(): Set<Long> {
        val cibleCommentaire = idCibleCommentaire ?: return emptySet()
        return try {
            api.rechercherSignalements(
                ReportSearch(
                    reporter = UserIdDto(gestionSession.getUserId()),
                    targetType = ReferenceIdDto(cibleCommentaire)
                )
            ).data?.content.orEmpty().map { it.targetId }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // ---------------- Actions ----------------

    /**
     * Bascule le like. L'interface change tout de suite, puis revient a l'etat
     * precedent si l'appel echoue — meme schema que le follow d'un artiste.
     */
    fun basculerLike() {
        val cible = idCibleEvenement
        if (!estConnecte || likeEnCours || cible == null) return

        viewModelScope.launch {
            likeEnCours = true
            val etaitAime = jAime
            val likePrecedent = idMonLike
            val comptePrecedent = nombreLikes

            jAime = !etaitAime
            nombreLikes = if (etaitAime) (comptePrecedent - 1).coerceAtLeast(0) else comptePrecedent + 1

            try {
                if (etaitAime) {
                    val aSupprimer = likePrecedent ?: throw IllegalStateException("Like introuvable")
                    api.deleteLike(aSupprimer)
                    idMonLike = null
                } else {
                    idMonLike = api.aimer(gestionSession.getUserId(), cible, idEvenement).data?.id
                }
            } catch (_: Exception) {
                jAime = etaitAime
                idMonLike = likePrecedent
                nombreLikes = comptePrecedent
                messageAction = "Le like n'a pas pu être enregistré."
            } finally {
                likeEnCours = false
            }
        }
    }

    fun surSaisie(texte: String) {
        saisie = texte.take(LONGUEUR_MAX_COMMENTAIRE)
    }

    fun publierCommentaire() {
        val cible = idCibleEvenement
        val contenu = saisie.trim()
        if (!estConnecte || envoiEnCours || cible == null || contenu.isEmpty()) return

        viewModelScope.launch {
            envoiEnCours = true
            try {
                api.publierCommentaire(gestionSession.getUserId(), cible, idEvenement, contenu)
                saisie = ""
                // On recharge plutot que d'inserer la reponse : l'entite renvoyee
                // a la creation ne porte pas forcement l'auteur complet.
                chargerCommentaires()
            } catch (_: Exception) {
                messageAction = "Le commentaire n'a pas pu être publié."
            } finally {
                envoiEnCours = false
            }
        }
    }

    fun supprimerCommentaire(commentaire: Comment) {
        if (!estDeMoi(commentaire)) return
        viewModelScope.launch {
            try {
                api.supprimerCommentaire(commentaire.id)
                tousLesCommentaires = tousLesCommentaires.filter { it.id != commentaire.id }
            } catch (_: Exception) {
                messageAction = "Le commentaire n'a pas pu être supprimé."
            }
        }
    }

    fun signalerCommentaire(commentaire: Comment, motif: String) {
        val cibleCommentaire = idCibleCommentaire
        val statut = idStatutEnAttente
        if (!estConnecte || estDeMoi(commentaire) || cibleCommentaire == null || statut == null) {
            messageAction = "Le signalement n'est pas disponible pour le moment."
            return
        }
        viewModelScope.launch {
            try {
                api.signaler(gestionSession.getUserId(), cibleCommentaire, commentaire.id, motif, statut)
                idsSignales = idsSignales + commentaire.id
                messageAction = "Merci, le commentaire a été signalé."
            } catch (_: Exception) {
                messageAction = "Le signalement n'a pas pu être envoyé."
            }
        }
    }

    /**
     * Ajoute une photo a la galerie (Sprint 5), en deux temps : le fichier est
     * d'abord televerse, puis le media est cree avec l'URL renvoyee. Si la
     * seconde etape echoue, le fichier reste sur le serveur sans etre
     * reference — sans transaction cote backend, on ne peut pas faire mieux.
     */
    fun ajouterPhoto(media: MediaChoisi) {
        val typePhoto = idTypePhoto
        if (!estConnecte || envoiPhotoEnCours || typePhoto == null) return

        viewModelScope.launch {
            envoiPhotoEnCours = true
            try {
                val url = api.televerserFichier(media.octets, media.nomFichier, media.typeMime)
                api.creerMediaEvenement(
                    idEvenement = idEvenement,
                    idUtilisateur = gestionSession.getUserId(),
                    idTypeMedia = typePhoto,
                    url = url
                )
                rechargerMedias()
                messageAction = "Photo ajoutée à la galerie."
            } catch (e: Exception) {
                messageAction = e.message ?: "La photo n'a pas pu être envoyée."
            } finally {
                envoiPhotoEnCours = false
            }
        }
    }

    private suspend fun rechargerMedias() {
        medias = try {
            api.rechercherMediasEvenement(
                EventMediaSearch(event = EvenementIdDto(idEvenement)),
                size = 50
            ).data?.content ?: emptyList()
        } catch (_: Exception) {
            medias
        }
    }

    fun effacerMessage() {
        messageAction = null
    }

    companion object {
        private const val STATUT_CONFIRME = "CONFIRMED"
        private const val TYPE_PHOTO = "PHOTO"
        private const val TYPE_VIDEO = "VIDEO"
        private const val CIBLE_EVENEMENT = "EVENT"
        private const val CIBLE_COMMENTAIRE = "COMMENT"
        private const val STATUT_EN_ATTENTE = "PENDING"
        const val LONGUEUR_MAX_COMMENTAIRE = 1000
    }
}

/** Une journee du programme, avec ses scenes. */
data class JourDeProgramme(
    val jour: String,
    val scenes: List<SceneDeProgramme>
)

/** Une scene et les concerts qui s'y deroulent ce jour-la. */
data class SceneDeProgramme(
    val scene: String,
    val concerts: List<Concert>
)

// ============================================================
// Ecran fiche evenement
// ============================================================

data class EcranDetailEvenementVoyager(val idEvenement: Long) : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: DetailEvenementViewModel =
            viewModel(key = "evenement_$idEvenement") { DetailEvenementViewModel(idEvenement) }

        // Dialogues : commentaire en cours de signalement / de suppression.
        var commentaireASignaler by remember { mutableStateOf<Comment?>(null) }
        var commentaireASupprimer by remember { mutableStateOf<Comment?>(null) }

        when {
            modeleVue.enChargement -> LoadingIndicator()

            modeleVue.erreur != null -> ErrorState(
                message = modeleVue.erreur!!,
                onRetry = { modeleVue.charger() }
            )

            else -> {
                val evenementDetail = modeleVue.evenement ?: return

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // ---- En-tete ----
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 340dp : l'en-tete loge aussi le bouton like. On
                                // garde de la marge, un en-tete trop court faisait
                                // deja disparaitre le bouton de follow.
                                .height(340.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            KaloyPurple,
                                            KaloyCyan.copy(alpha = 0.5f),
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
                                IconButton(
                                    onClick = { navigateur.pop() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("←", fontSize = 24.sp, color = Color.White)
                                }

                                Column {
                                    Text(
                                        text = evenementDetail.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = periodeLisible(
                                            evenementDetail.startDate,
                                            evenementDetail.endDate
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )

                                    evenementDetail.createdByArtist?.let { organisateur ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Organisé par ${organisateur.stageName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    BoutonLike(
                                        jAime = modeleVue.jAime,
                                        nombre = modeleVue.nombreLikes,
                                        actif = modeleVue.estConnecte && modeleVue.interactionsDisponibles,
                                        enCours = modeleVue.likeEnCours,
                                        onClick = { modeleVue.basculerLike() }
                                    )
                                }
                            }
                        }
                    }

                    // ---- Description ----
                    if (!evenementDetail.description.isNullOrBlank()) {
                        item {
                            SectionHeader(title = "À propos")
                            Text(
                                text = evenementDetail.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = KaloyTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // ---- Lieux ----
                    if (modeleVue.lieux.isNotEmpty()) {
                        item {
                            SectionHeader(title = "📍 Lieux (${modeleVue.lieux.size})")
                        }
                        items(modeleVue.lieux) { lieu ->
                            Text(
                                text = "• $lieu",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KaloyTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // ---- Artistes confirmes ----
                    if (modeleVue.artistesConfirmes.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🎤 Artistes confirmés (${modeleVue.artistesConfirmes.size})")
                        }
                        items(modeleVue.artistesConfirmes, key = { it.id }) { concert ->
                            LigneArtisteParticipant(
                                concert = concert,
                                onClick = {
                                    concert.artist?.let {
                                        navigateur.push(EcranDetailArtisteVoyager(idArtiste = it.id))
                                    }
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // ---- Programme, par jour puis par scene (Sprint 5) ----
                    if (modeleVue.programme.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🗓 Programme")
                        }
                        modeleVue.programme.forEach { journee ->
                            item(key = "jour_${journee.jour}") {
                                Text(
                                    text = dateLisible(journee.jour) ?: journee.jour,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KaloyPurple,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            journee.scenes.forEach { scene ->
                                item(key = "scene_${journee.jour}_${scene.scene}") {
                                    Text(
                                        text = "📍 ${scene.scene}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KaloyTextMuted,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                    )
                                }
                                items(scene.concerts, key = { "c_${it.id}" }) { concert ->
                                    LigneProgramme(concert = concert)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // ---- Galerie post-evenement ----
                    item {
                        SectionHeader(title = "📸 Galerie")
                        BoutonAjoutPhoto(
                            visible = modeleVue.peutAjouterPhoto,
                            enCours = modeleVue.envoiPhotoEnCours,
                            onMediaChoisi = { media -> modeleVue.ajouterPhoto(media) }
                        )
                    }

                    if (modeleVue.medias.isEmpty()) {
                        item {
                            EmptyState(message = "Aucune photo ni vidéo pour cet événement")
                        }
                    } else {
                        if (modeleVue.photos.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Photos (${modeleVue.photos.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KaloyTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(modeleVue.photos, key = { it.id }) { photo ->
                                        VignettePhoto(media = photo)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        if (modeleVue.videos.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Vidéos (${modeleVue.videos.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KaloyTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(modeleVue.videos, key = { it.id }) { video ->
                                SmartVideoPlayerComposable(
                                    url = video.url,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // ---- Commentaires (Sprint 3) ----
                    item {
                        SectionHeader(title = "💬 Commentaires (${modeleVue.commentaires.size})")
                    }

                    item {
                        ZoneSaisieCommentaire(
                            estConnecte = modeleVue.estConnecte,
                            disponible = modeleVue.interactionsDisponibles,
                            saisie = modeleVue.saisie,
                            envoiEnCours = modeleVue.envoiEnCours,
                            onSaisie = { modeleVue.surSaisie(it) },
                            onPublier = { modeleVue.publierCommentaire() }
                        )
                    }

                    if (modeleVue.commentaires.isEmpty()) {
                        item {
                            Text(
                                text = "Aucun commentaire pour l'instant.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KaloyTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(modeleVue.commentaires, key = { it.id }) { commentaire ->
                            LigneCommentaire(
                                commentaire = commentaire,
                                estDeMoi = modeleVue.estDeMoi(commentaire),
                                peutSignaler = modeleVue.estConnecte,
                                onSupprimer = { commentaireASupprimer = commentaire },
                                onSignaler = { commentaireASignaler = commentaire }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // ---- Dialogues ----

        commentaireASupprimer?.let { commentaire ->
            AlertDialog(
                onDismissRequest = { commentaireASupprimer = null },
                title = { Text("Supprimer le commentaire ?") },
                text = { Text("Cette action est définitive.") },
                confirmButton = {
                    TextButton(onClick = {
                        modeleVue.supprimerCommentaire(commentaire)
                        commentaireASupprimer = null
                    }) { Text("Supprimer", color = KaloyRed) }
                },
                dismissButton = {
                    TextButton(onClick = { commentaireASupprimer = null }) { Text("Annuler") }
                }
            )
        }

        commentaireASignaler?.let { commentaire ->
            DialogueSignalement(
                onAnnuler = { commentaireASignaler = null },
                onEnvoyer = { motif ->
                    modeleVue.signalerCommentaire(commentaire, motif)
                    commentaireASignaler = null
                }
            )
        }

        modeleVue.messageAction?.let { message ->
            AlertDialog(
                onDismissRequest = { modeleVue.effacerMessage() },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { modeleVue.effacerMessage() }) { Text("OK") }
                }
            )
        }
    }
}

// ============================================================
// Composants locaux
// ============================================================

@Composable
private fun BoutonLike(
    jAime: Boolean,
    nombre: Long,
    actif: Boolean,
    enCours: Boolean,
    onClick: () -> Unit
) {
    // Un visiteur voit le compteur, mais le bouton reste inerte.
    Surface(
        onClick = onClick,
        enabled = actif && !enCours,
        shape = RoundedCornerShape(24.dp),
        color = if (jAime) Color.White else Color.White.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (jAime) "♥" else "♡",
                fontSize = 18.sp,
                color = if (jAime) KaloyPink else Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$nombre",
                fontWeight = FontWeight.SemiBold,
                color = if (jAime) KaloyPurple else Color.White
            )
        }
    }
}

@Composable
private fun ZoneSaisieCommentaire(
    estConnecte: Boolean,
    disponible: Boolean,
    saisie: String,
    envoiEnCours: Boolean,
    onSaisie: (String) -> Unit,
    onPublier: () -> Unit
) {
    if (!estConnecte) {
        Text(
            text = "Connectez-vous pour commenter.",
            style = MaterialTheme.typography.bodySmall,
            color = KaloyTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = saisie,
            onValueChange = onSaisie,
            enabled = disponible && !envoiEnCours,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Votre commentaire…", color = KaloyTextMuted) },
            shape = RoundedCornerShape(16.dp),
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onPublier,
                enabled = disponible && !envoiEnCours && saisie.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
            ) {
                if (envoiEnCours) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Publier", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LigneCommentaire(
    commentaire: Comment,
    estDeMoi: Boolean,
    peutSignaler: Boolean,
    onSupprimer: () -> Unit,
    onSignaler: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (estDeMoi) "Vous" else nomAuteur(commentaire),
                style = MaterialTheme.typography.bodyMedium,
                color = KaloyPurple,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dateHeureLisible(commentaire.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = KaloyTextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = commentaire.content,
            style = MaterialTheme.typography.bodyMedium,
            color = KaloyTextSecondary
        )

        // Supprimer ses propres commentaires ; signaler ceux des autres.
        if (estDeMoi || peutSignaler) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (estDeMoi) {
                    TextButton(onClick = onSupprimer) {
                        Text("Supprimer", color = KaloyRed, fontSize = 13.sp)
                    }
                } else {
                    TextButton(onClick = onSignaler) {
                        Text("Signaler", color = KaloyTextMuted, fontSize = 13.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = KaloyDarkElevated.copy(alpha = 0.3f))
    }
}

@Composable
private fun DialogueSignalement(
    onAnnuler: () -> Unit,
    onEnvoyer: (String) -> Unit
) {
    val motifs = listOf("Spam", "Propos offensants", "Harcèlement", "Hors sujet", "Autre")
    var motifChoisi by remember { mutableStateOf<String?>(null) }
    var precision by remember { mutableStateOf("") }

    // « Autre » n'a de sens qu'avec une precision : sans elle, la moderation
    // ne saurait pas quoi examiner.
    val precisionObligatoire = motifChoisi == "Autre"
    val envoiPossible = motifChoisi != null && (!precisionObligatoire || precision.isNotBlank())

    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Signaler ce commentaire") },
        text = {
            Column {
                motifs.forEach { motif ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = motifChoisi == motif,
                                onClick = { motifChoisi = motif }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = motifChoisi == motif,
                            onClick = { motifChoisi = motif }
                        )
                        Text(motif)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = precision,
                    onValueChange = { precision = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(if (precisionObligatoire) "Précisez (obligatoire)" else "Précision (facultatif)")
                    },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = envoiPossible,
                onClick = {
                    val motif = motifChoisi ?: return@TextButton
                    val raison = if (precision.isBlank()) motif else "$motif — ${precision.trim()}"
                    onEnvoyer(raison)
                }
            ) { Text("Signaler") }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler") }
        }
    )
}

@Composable
private fun LigneArtisteParticipant(
    concert: Concert,
    onClick: () -> Unit
) {
    val artiste = concert.artist ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    brush = Brush.linearGradient(listOf(KaloyPurple, KaloyPink)),
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
            // Le titre du concert precise le creneau de l'artiste dans
            // l'evenement ; a defaut on retombe sur le lieu.
            val sousTitre = concert.title ?: concert.venue?.name
            if (!sousTitre.isNullOrBlank()) {
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextSecondary
                )
            }
        }

        Text("›", color = KaloyTextMuted, fontSize = 20.sp)
    }
}

@Composable
private fun VignettePhoto(media: EventMedia) {
    // Le degrade reste visible tant que l'image n'est pas chargee, et en cas
    // d'echec (photos de demonstration heberges hors ligne, par exemple).
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(KaloyPurpleDark, KaloyCyan))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("🖼", fontSize = 32.sp)
        AsyncImage(
            model = media.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Ajout d'une photo : prise de vue ou choix dans la galerie. Le selecteur est
 * multiplateforme (implementations Android et iOS distinctes).
 */
@Composable
private fun BoutonAjoutPhoto(
    visible: Boolean,
    enCours: Boolean,
    onMediaChoisi: (MediaChoisi) -> Unit
) {
    if (!visible) return

    var choixOuvert by remember { mutableStateOf(false) }
    val ouvrirSelecteur = rememberSelecteurMedia { media ->
        if (media != null) onMediaChoisi(media)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Button(
            onClick = { choixOuvert = true },
            enabled = !enCours,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
        ) {
            if (enCours) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Envoi…", color = Color.White)
            } else {
                Text("＋ Ajouter une photo", color = Color.White)
            }
        }
    }

    if (choixOuvert) {
        AlertDialog(
            onDismissRequest = { choixOuvert = false },
            title = { Text("Ajouter une photo") },
            text = { Text("Prendre une photo maintenant, ou en choisir une existante ?") },
            confirmButton = {
                TextButton(onClick = {
                    choixOuvert = false
                    ouvrirSelecteur(SourceMedia.CAMERA)
                }) { Text("Appareil photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    choixOuvert = false
                    ouvrirSelecteur(SourceMedia.GALERIE)
                }) { Text("Galerie") }
            }
        )
    }
}

/** Une ligne du programme : horaire et artiste. */
@Composable
private fun LigneProgramme(concert: Concert) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = heureLisible(concert.startTime),
            style = MaterialTheme.typography.bodyMedium,
            color = KaloyCyan,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = concert.artist?.stageName ?: "Artiste à confirmer",
                style = MaterialTheme.typography.bodyMedium,
                color = KaloyTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            concert.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextMuted
                )
            }
        }
    }
}

// ============================================================
// Mise en forme
// ============================================================

/**
 * Nom affiche pour l'auteur d'un commentaire. L'entite User ne porte pas de
 * pseudo : on utilise la partie de l'email avant « @ ».
 */
private fun nomAuteur(commentaire: Comment): String {
    val email = commentaire.authorUser?.email
    return if (email.isNullOrBlank()) "Utilisateur" else email.substringBefore('@')
}

/**
 * Les dates arrivent en ISO (« 2026-06-14 »). On les affiche en jour/mois/annee
 * et on evite de repeter la date quand l'evenement tient sur une seule journee.
 */
private fun periodeLisible(debut: String?, fin: String?): String {
    val d = dateLisible(debut)
    val f = dateLisible(fin)
    return when {
        d == null && f == null -> "Dates à préciser"
        d != null && f != null && d != f -> "Du $d au $f"
        d != null -> "Le $d"
        else -> "Le $f"
    }
}

private fun dateLisible(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    // On ne garde que la partie date, un LocalDateTime pouvant arriver ici.
    val partieDate = iso.substringBefore('T')
    val morceaux = partieDate.split("-")
    if (morceaux.size != 3) return partieDate
    return "${morceaux[2]}/${morceaux[1]}/${morceaux[0]}"
}

/** « 2026-09-22T10:05:31 » -> « 22/09/2026 à 10:05 ». */
private fun dateHeureLisible(iso: String?): String {
    val date = dateLisible(iso) ?: return ""
    val heure = iso?.substringAfter('T', "")?.take(5).orEmpty()
    return if (heure.length == 5) "$date à $heure" else date
}

/** « 2026-08-21T18:00:00 » -> « 18:00 ». */
private fun heureLisible(iso: String?): String {
    if (iso.isNullOrBlank()) return "--:--"
    val heure = iso.substringAfter('T', "").take(5)
    return if (heure.length == 5) heure else "--:--"
}
