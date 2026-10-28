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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kaloy.app.core.network.urlMedia
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
import com.kaloy.app.core.envoi.EnvoiEnAttente
import com.kaloy.app.core.envoi.EnvoyeurParMorceaux
import com.kaloy.app.core.envoi.MemoireEnvois
import com.kaloy.app.presentation.common.MediaSelectionne
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

    // Settings vient de Koin, comme pour AuthSessionManager : la memoire des
    // envois doit survivre a la fermeture de l'application.
    private val envoyeur = EnvoyeurParMorceaux(
        api, MemoireEnvois(KoinPlatform.getKoin().get())
    )

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

    // Memes regles pour les medias : signale par moi, masque a mes yeux.
    private var idsMediasSignales by mutableStateOf<Set<Long>>(emptySet())

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
    private var idCibleMedia: Long? = null
    private var idStatutEnAttente: Long? = null
    private var idTypePhoto: Long? = null
    private var idTypeVideo: Long? = null

    // --- Sprint 5 : ajout d'une photo a la galerie ---
    var envoiPhotoEnCours by mutableStateOf(false)
        private set

    /** L'ajout n'est possible que connecte, et si le type PHOTO a ete resolu. */
    // Photo OU video : un seul des deux types suffit pour ouvrir le menu, et
    // le bouton du type absent affichera un message plutot que rien.
    val peutAjouterPhoto: Boolean
        get() = estConnecte && (idTypePhoto != null || idTypeVideo != null)

    /** Etape courante de l'envoi, pour la barre de progression. */
    var etapeEnvoi by mutableStateOf<EnvoyeurParMorceaux.Etape?>(null)
        private set

    /** Envoi interrompu retrouve a l'ouverture de l'ecran, s'il y en a un. */
    var envoiEnAttente by mutableStateOf<EnvoiEnAttente?>(null)
        private set

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

    private val mediasVisibles: List<EventMedia>
        get() = medias.filter { it.id !in idsMediasSignales }

    val photos: List<EventMedia> get() = mediasVisibles.filter { it.mediaType?.name == TYPE_PHOTO }
    val videos: List<EventMedia> get() = mediasVisibles.filter { it.mediaType?.name == TYPE_VIDEO }

    /** Vrai quand la galerie n'a plus rien a montrer, signalements deduits. */
    val galerieVide: Boolean get() = mediasVisibles.isEmpty()

    init {
        charger()
        // Avant le chargement reseau : la reprise ne depend que de ce qui est
        // retenu localement, et l'utilisateur doit la voir meme si le serveur
        // est injoignable — c'est justement le cas ou l'envoi a ete coupe.
        chercherEnvoiInterrompu()
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

            idsSignales = mesSignalements(idCibleCommentaire)
            idsMediasSignales = mesSignalements(idCibleMedia)
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
            idCibleMedia = cibles.firstOrNull { it.name == CIBLE_MEDIA }?.id

            idStatutEnAttente = api.getStatutsSignalement().data?.content.orEmpty()
                .firstOrNull { it.name == STATUT_EN_ATTENTE }?.id

            try {
                val types = api.getTypesMedia().data?.content.orEmpty()
                idTypePhoto = types.firstOrNull { it.name == TYPE_PHOTO }?.id
                idTypeVideo = types.firstOrNull { it.name == TYPE_VIDEO }?.id
            } catch (_: Exception) {
                idTypePhoto = null
                idTypeVideo = null
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

    /**
     * Ce que j'ai deja signale pour un type de cible donne.
     *
     * Sans cette relecture, un media signale reapparaitrait a la prochaine
     * ouverture de l'ecran : le masquage ne vit que dans l'etat de la page.
     */
    private suspend fun mesSignalements(cible: Long?): Set<Long> {
        if (cible == null) return emptySet()
        return try {
            api.rechercherSignalements(
                ReportSearch(
                    reporter = UserIdDto(gestionSession.getUserId()),
                    targetType = ReferenceIdDto(cible)
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

    /** Signale une photo ou une video de la galerie. */
    fun signalerMedia(media: EventMedia, motif: String) {
        val cibleMedia = idCibleMedia
        val statut = idStatutEnAttente
        if (!estConnecte || cibleMedia == null || statut == null) {
            messageAction = "Le signalement n'est pas disponible pour le moment."
            return
        }
        viewModelScope.launch {
            try {
                api.signaler(gestionSession.getUserId(), cibleMedia, media.id, motif, statut)
                idsMediasSignales = idsMediasSignales + media.id
                messageAction = "Merci, le contenu a été signalé."
            } catch (_: Exception) {
                messageAction = "Le signalement n'a pas pu être envoyé."
            }
        }
    }

    /**
     * Ajoute une photo ou une video a la galerie, par envoi en morceaux.
     *
     * Le type de media est deduit du typeMime reel et non du bouton presse : un
     * selecteur mixte peut rendre une video alors qu'on visait une photo, et
     * c'est le contenu qui doit decider.
     *
     * Deux temps, comme avant : le fichier est envoye, puis le media cree avec
     * l'URL obtenue. Si la seconde etape echoue, le fichier reste sur le serveur
     * sans etre reference — sans transaction cote backend, on ne peut pas faire
     * mieux.
     */
    fun ajouterMedia(media: MediaSelectionne) {
        if (!estConnecte || envoiPhotoEnCours) return

        val estVideo = media.typeMime.startsWith("video/")
        val idType = if (estVideo) idTypeVideo else idTypePhoto
        if (idType == null) {
            messageAction = if (estVideo) {
                "Les vidéos ne sont pas acceptées pour le moment."
            } else {
                "Les photos ne sont pas acceptées pour le moment."
            }
            return
        }

        viewModelScope.launch {
            envoiPhotoEnCours = true
            envoiEnAttente = null
            try {
                envoyeur.envoyer(media, idEvenement) { etape ->
                    traiterEtape(etape, idType, estVideo)
                }
            } finally {
                envoiPhotoEnCours = false
            }
        }
    }

    /**
     * Cherche un envoi interrompu, a l'ouverture de l'ecran.
     *
     * On ne le reprend pas tout seul : un envoi de 400 Mo sur reseau mobile se
     * decide, il ne se declenche pas a l'insu de l'utilisateur.
     */
    fun chercherEnvoiInterrompu() {
        val attente = envoyeur.envoiRetenu() ?: return
        if (attente.idEvenement == idEvenement) {
            envoiEnAttente = attente
        }
    }

    /** Reprend l'envoi interrompu. Le serveur dit ou il s'etait arrete. */
    fun reprendreEnvoi() {
        val attente = envoiEnAttente ?: return
        if (envoiPhotoEnCours) return

        val estVideo = attente.typeMime.startsWith("video/")
        val idType = (if (estVideo) idTypeVideo else idTypePhoto) ?: return

        viewModelScope.launch {
            envoiPhotoEnCours = true
            try {
                envoyeur.reprendre(attente) { etape ->
                    traiterEtape(etape, idType, estVideo)
                }
            } finally {
                envoiPhotoEnCours = false
            }
        }
    }

    /** Abandonne l'envoi en attente et supprime la copie locale. */
    fun abandonnerEnvoi() {
        envoyeur.abandonner()
        envoiEnAttente = null
        etapeEnvoi = null
    }

    private fun traiterEtape(
        etape: EnvoyeurParMorceaux.Etape,
        idType: Long,
        estVideo: Boolean
    ) {
        etapeEnvoi = etape
        when (etape) {
            is EnvoyeurParMorceaux.Etape.Termine -> {
                envoiEnAttente = null
                viewModelScope.launch { rattacher(etape.url, idType, estVideo) }
            }
            // Un echec reprenable laisse la session en memoire, donc l'ecran
            // peut proposer « Reprendre ». Un echec definitif l'efface.
            is EnvoyeurParMorceaux.Etape.Echec ->
                envoiEnAttente = if (etape.reprenable) envoyeur.envoiRetenu() else null
            else -> Unit
        }
    }

    private suspend fun rattacher(url: String, idType: Long, estVideo: Boolean) {
        try {
            api.creerMediaEvenement(
                idEvenement = idEvenement,
                idUtilisateur = gestionSession.getUserId(),
                idTypeMedia = idType,
                url = url
            )
            rechargerMedias()
            messageAction = if (estVideo) {
                "Vidéo ajoutée à la galerie."
            } else {
                "Photo ajoutée à la galerie."
            }
            etapeEnvoi = null
        } catch (e: Exception) {
            messageAction = e.message ?: "Le média n'a pas pu être rattaché à l'événement."
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
        private const val CIBLE_MEDIA = "MEDIA"
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
        var mediaASignaler by remember { mutableStateOf<EventMedia?>(null) }
        var commentaireASupprimer by remember { mutableStateOf<Comment?>(null) }

        // Sections repliees a l'ouverture : la fiche tient alors sur un
        // ecran, et on descend vers ce qu'on cherche au lieu de faire
        // defiler toute la galerie pour atteindre les commentaires.
        var galerieOuverte by remember { mutableStateOf(false) }
        var commentairesOuverts by remember { mutableStateOf(false) }
        // Un jour du programme a la fois : l'identifiant du jour ouvert,
        // null si tous sont replies. Volontairement pas un Set — ouvrir un
        // jour referme le precedent, ce qui garde la liste lisible.
        var jourOuvert by remember { mutableStateOf<String?>(null) }

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
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Retour",
                                        tint = Color.White
                                    )
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

                    // ---- Programme, par jour puis par scene (Sprint 5) ----
                    //
                    // Il a absorbe les sections « Lieux » et « Artistes confirmes »,
                    // qui disaient a plat ce qu'on lit ici organise : la scene porte
                    // le lieu, la ligne porte l'artiste et son horaire.
                    if (modeleVue.programme.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Programme", icon = Icons.Filled.Event)
                        }
                        modeleVue.programme.forEach { journee ->
                            val ouvert = jourOuvert == journee.jour
                            item(key = "jour_${journee.jour}") {
                                EnteteRepliable(
                                    titre = dateLisible(journee.jour) ?: journee.jour,
                                    ouvert = ouvert,
                                    couleur = KaloyPurple,
                                    onBasculer = {
                                        jourOuvert = if (ouvert) null else journee.jour
                                    }
                                )
                            }
                            if (ouvert) {
                                journee.scenes.forEach { scene ->
                                    item(key = "scene_${journee.jour}_${scene.scene}") {
                                        Row(
                                            modifier = Modifier.padding(
                                                start = 32.dp, end = 16.dp, top = 8.dp, bottom = 2.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Place,
                                                contentDescription = null,
                                                tint = KaloyTextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = scene.scene,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = KaloyTextMuted
                                            )
                                        }
                                    }
                                    items(scene.concerts, key = { "c_${it.id}" }) { concert ->
                                        LigneProgramme(
                                            concert = concert,
                                            onClick = {
                                                concert.artist?.let {
                                                    navigateur.push(
                                                        EcranDetailArtisteVoyager(idArtiste = it.id)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // ---- Galerie post-evenement ----
                    item {
                        EnteteRepliable(
                            titre = "Galerie (${modeleVue.photos.size + modeleVue.videos.size})",
                            ouvert = galerieOuverte,
                            icone = Icons.Filled.PhotoCamera,
                            onBasculer = { galerieOuverte = !galerieOuverte }
                        )
                    }

                    if (galerieOuverte) {
                        item {
                            BoutonAjoutMedia(
                                visible = modeleVue.peutAjouterPhoto,
                                enCours = modeleVue.envoiPhotoEnCours,
                                etape = modeleVue.etapeEnvoi,
                                envoiEnAttente = modeleVue.envoiEnAttente != null,
                                onMediaSelectionne = { media -> modeleVue.ajouterMedia(media) },
                                onReprendre = { modeleVue.reprendreEnvoi() },
                                onAbandonner = { modeleVue.abandonnerEnvoi() }
                            )
                        }

                        if (modeleVue.galerieVide) {
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
                                            VignettePhoto(
                                                media = photo,
                                                peutSignaler = modeleVue.estConnecte,
                                                onSignaler = { mediaASignaler = photo }
                                            )
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
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(modeleVue.videos, key = { it.id }) { video ->
                                            VignetteVideo(
                                                media = video,
                                                peutSignaler = modeleVue.estConnecte,
                                                onSignaler = { mediaASignaler = video }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }

                    }

                    // ---- Commentaires (Sprint 3) ----
                    item {
                        EnteteRepliable(
                            titre = "Commentaires (${modeleVue.commentaires.size})",
                            ouvert = commentairesOuverts,
                            icone = Icons.Filled.Chat,
                            onBasculer = { commentairesOuverts = !commentairesOuverts }
                        )
                    }

                    if (commentairesOuverts) {

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
                titre = "Signaler ce commentaire",
                motifs = MOTIFS_COMMENTAIRE,
                onAnnuler = { commentaireASignaler = null },
                onEnvoyer = { motif ->
                    modeleVue.signalerCommentaire(commentaire, motif)
                    commentaireASignaler = null
                }
            )
        }

        mediaASignaler?.let { media ->
            val estVideo = media.mediaType?.name == "VIDEO"
            DialogueSignalement(
                titre = if (estVideo) "Signaler cette vidéo" else "Signaler cette photo",
                motifs = MOTIFS_MEDIA,
                onAnnuler = { mediaASignaler = null },
                onEnvoyer = { motif ->
                    modeleVue.signalerMedia(media, motif)
                    mediaASignaler = null
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
            Icon(
                imageVector = if (jAime) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (jAime) "Retirer le like" else "Aimer",
                tint = if (jAime) KaloyPink else Color.White,
                modifier = Modifier.size(18.dp)
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

/** Motifs proposes selon ce qu'on signale. « Autre » exige une precision. */
private val MOTIFS_COMMENTAIRE =
    listOf("Spam", "Propos offensants", "Harcèlement", "Hors sujet", "Autre")

private val MOTIFS_MEDIA = listOf(
    "Contenu choquant",
    "Nudité",
    "Violence",
    "Droits d'auteur",
    "Hors sujet",
    "Autre"
)

@Composable
private fun DialogueSignalement(
    titre: String,
    motifs: List<String>,
    onAnnuler: () -> Unit,
    onEnvoyer: (String) -> Unit
) {
    var motifChoisi by remember { mutableStateOf<String?>(null) }
    var precision by remember { mutableStateOf("") }

    // « Autre » n'a de sens qu'avec une precision : sans elle, la moderation
    // ne saurait pas quoi examiner.
    val precisionObligatoire = motifChoisi == "Autre"
    val envoiPossible = motifChoisi != null && (!precisionObligatoire || precision.isNotBlank())

    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
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
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Artiste certifié",
                        tint = KaloyCyan,
                        modifier = Modifier.size(14.dp)
                    )
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

/**
 * Tuile video, au format exact de la tuile photo.
 *
 * Le lecteur occupait auparavant toute la largeur sur 220 dp, empile
 * verticalement : deux presentations sans rapport dans une meme galerie.
 *
 * Le drapeau de signalement revient sur la tuile, comme pour une photo. Il
 * etait sous le lecteur pour ne pas masquer ses commandes ; a cette taille
 * il n'y a plus de commandes a masquer, et garder un bouton texte sous
 * chaque tuile casserait l'alignement de la rangee.
 */
@Composable
private fun VignetteVideo(
    media: EventMedia,
    peutSignaler: Boolean,
    onSignaler: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(TAILLE_TUILE)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        SmartVideoPlayerComposable(
            url = urlMedia(media.url) ?: "",
            modifier = Modifier.fillMaxSize()
        )

        if (peutSignaler) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onSignaler),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = "Signaler cette vidéo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Cote d'une tuile de galerie. Photos et videos partagent la meme. */
private val TAILLE_TUILE = 140.dp

@Composable
private fun VignettePhoto(
    media: EventMedia,
    peutSignaler: Boolean,
    onSignaler: () -> Unit
) {
    // Le degrade reste visible tant que l'image n'est pas chargee, et en cas
    // d'echec (photos de demonstration heberges hors ligne, par exemple).
    Box(
        modifier = Modifier
            .size(TAILLE_TUILE)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(KaloyPurpleDark, KaloyCyan))
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = KaloyTextMuted,
            modifier = Modifier.size(32.dp)
        )
        AsyncImage(
            model = urlMedia(media.url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Cale a gauche et non au centre : sur une photo large, le centrage
            // rogne les deux bords, alors que le sujet d'une photo de concert
            // prise de cote s'y trouve souvent.
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxSize()
        )

        if (peutSignaler) {
            // Pastille sombre sous l'icone : un drapeau blanc pose sur une
            // photo claire serait invisible.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onSignaler),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = "Signaler cette photo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Ajout d'une photo ou d'une video a la galerie.
 *
 * Quatre entrees a plat plutot que deux suivies d'un second choix : un appui de
 * moins. Et l'etape en cours est affichee en clair, parce qu'un envoi de 400 Mo
 * dure plusieurs minutes — sans progression, l'utilisateur conclut a un blocage
 * et ferme l'application.
 */
@Composable
private fun BoutonAjoutMedia(
    visible: Boolean,
    enCours: Boolean,
    etape: EnvoyeurParMorceaux.Etape?,
    envoiEnAttente: Boolean,
    onMediaSelectionne: (MediaSelectionne) -> Unit,
    onReprendre: () -> Unit,
    onAbandonner: () -> Unit
) {
    if (!visible) return

    var choixOuvert by remember { mutableStateOf(false) }
    val ouvrirSelecteur = rememberSelecteurMedia { media ->
        if (media != null) onMediaSelectionne(media)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { choixOuvert = true },
            enabled = !enCours,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
        ) {
            Text(
                text = if (enCours) "Envoi en cours…" else "＋ Ajouter une photo ou une vidéo",
                color = Color.White
            )
        }

        EtapeEnvoi(etape)

        // Proposee seulement hors envoi : pendant, le bouton n'aurait aucun sens.
        if (envoiEnAttente && !enCours) {
            Text(
                text = "Un envoi a été interrompu.",
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReprendre,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaloyCyan)
                ) {
                    Text("Reprendre", color = Color.White)
                }
                OutlinedButton(
                    onClick = onAbandonner,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Abandonner", color = KaloyTextSecondary)
                }
            }
        }
    }

    if (choixOuvert) {
        AlertDialog(
            onDismissRequest = { choixOuvert = false },
            title = { Text("Ajouter à la galerie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        SourceMedia.PHOTO_CAMERA to "Prendre une photo",
                        SourceMedia.VIDEO_CAMERA to "Filmer une vidéo",
                        SourceMedia.PHOTO_GALERIE to "Choisir une photo",
                        SourceMedia.VIDEO_GALERIE to "Choisir une vidéo"
                    ).forEach { (source, libelle) ->
                        TextButton(onClick = {
                            choixOuvert = false
                            ouvrirSelecteur(source)
                        }) {
                            Text(libelle, color = KaloyPurple)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choixOuvert = false }) { Text("Annuler") }
            }
        )
    }
}

/**
 * Etat de l'envoi, en clair.
 *
 * La preparation est distinguee de l'envoi : copier 400 Mo prend plusieurs
 * secondes pendant lesquelles rien ne part sur le reseau, et une barre figee a
 * 0 % ferait croire a une panne.
 */
@Composable
private fun EtapeEnvoi(etape: EnvoyeurParMorceaux.Etape?) {
    when (etape) {
        null -> Unit

        is EnvoyeurParMorceaux.Etape.Preparation -> LigneEtape("Préparation du fichier…")

        is EnvoyeurParMorceaux.Etape.Envoi -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Envoi ${etape.pourcentage} %",
                    color = KaloyTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { etape.pourcentage / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = KaloyPurple,
                    trackColor = KaloyDarkElevated
                )
            }
        }

        is EnvoyeurParMorceaux.Etape.Assemblage -> LigneEtape("Finalisation…")

        is EnvoyeurParMorceaux.Etape.Termine -> LigneEtape("Envoi terminé.")

        is EnvoyeurParMorceaux.Etape.Echec -> Text(
            text = etape.message,
            color = KaloyPink,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LigneEtape(texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = KaloyPurple
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = texte, color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** Une ligne du programme : horaire et artiste. */
@Composable
private fun LigneProgramme(concert: Concert, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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

// ============================================================
// En-tete d'une section qu'on peut replier
// ============================================================

/**
 * Le chevron n'est pas decoratif : sans lui, rien ne distingue un titre d'un
 * titre sur lequel on peut appuyer, et la section repliee passe pour une
 * section vide.
 */
@Composable
private fun EnteteRepliable(
    titre: String,
    ouvert: Boolean,
    onBasculer: () -> Unit,
    icone: ImageVector? = null,
    couleur: Color = KaloyTextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBasculer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icone?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = KaloyPurple,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = titre,
            style = MaterialTheme.typography.titleSmall,
            color = couleur,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (ouvert) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (ouvert) "Replier" else "Dérouler",
            tint = KaloyTextMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}
