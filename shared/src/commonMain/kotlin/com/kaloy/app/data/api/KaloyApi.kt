package com.kaloy.app.data.api

import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.core.util.maintenantIso
import com.kaloy.app.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform

class KaloyApi(baseUrl: String = DEFAULT_BASE_URL) {

    companion object {
        val DEFAULT_BASE_URL = BASE_URL
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // Le backend n'ouvre que /auth/** : toute autre route exige un jeton JWT.
    // On le relit à chaque requête plutôt que de le capturer à la construction,
    // pour qu'une connexion ou une déconnexion soit prise en compte aussitôt.
    private fun jetonCourant(): String? = try {
        KoinPlatform.getKoin().get<AuthSessionManager>().getToken()
    } catch (_: Exception) {
        // Koin pas encore démarré (aperçus Compose, tests) : requête anonyme.
        null
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(this@KaloyApi.json)
        }
        install(Logging) {
            level = LogLevel.BODY
        }
        install(DefaultRequest) {
            val jeton = jetonCourant()
            if (!jeton.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $jeton")
            }
        }
    }

    private val apiBaseUrl = baseUrl

    // ============================================================
    // Artists
    // ============================================================

    suspend fun getArtists(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Artist>> {
        return client.get("$apiBaseUrl/artists") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun getArtistById(id: Long): RestResponse<Artist> {
        return client.get("$apiBaseUrl/artists/$id").body()
    }

    suspend fun getArtistAlbums(artistId: Long, page: Int = 0, size: Int = 10): RestResponse<PageResponse<Album>> {
        return client.get("$apiBaseUrl/artists/$artistId/albums") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun getArtistSongs(artistId: Long, page: Int = 0, size: Int = 10): RestResponse<PageResponse<Song>> {
        return client.get("$apiBaseUrl/artists/$artistId/songs") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun getArtistFollows(artistId: Long, page: Int = 0, size: Int = 10): RestResponse<PageResponse<Follow>> {
        return client.get("$apiBaseUrl/artists/$artistId/follows") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    // ============================================================
    // Albums
    // ============================================================

    suspend fun getAlbums(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Album>> {
        return client.get("$apiBaseUrl/albums") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun getAlbumById(id: Long): RestResponse<Album> {
        return client.get("$apiBaseUrl/albums/$id").body()
    }

    suspend fun getAlbumSongs(albumId: Long, page: Int = 0, size: Int = 20): RestResponse<PageResponse<Song>> {
        return client.get("$apiBaseUrl/albums/$albumId/songs") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    // ============================================================
    // Songs
    // ============================================================

    suspend fun getSongs(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Song>> {
        return client.get("$apiBaseUrl/songs") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun getSongById(id: Long): RestResponse<Song> {
        return client.get("$apiBaseUrl/songs/$id").body()
    }

    // ============================================================
    // Events
    // ============================================================

    suspend fun getEvents(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Event>> {
        return client.get("$apiBaseUrl/events") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun getEventById(id: Long): RestResponse<Event> {
        return client.get("$apiBaseUrl/events/$id").body()
    }

    // ============================================================
    // Concerts (Sprint 4 — calendrier d'un artiste)
    // ============================================================

    // Un concert est le creneau d'UN artiste dans un evenement : c'est aussi
    // la demande de participation (statut PENDING / CONFIRMED / DECLINED).
    // Le calendrier d'un artiste n'affiche donc que ses concerts CONFIRMED.
    //
    // sortParam permet d'ordonner : "startTime,asc" pour les concerts a venir,
    // "startTime,desc" pour les passes (les plus recents en premier).
    suspend fun rechercherConcerts(
        requete: ConcertSearch,
        page: Int = 0,
        size: Int = 20,
        sortParam: String = "startTime,asc"
    ): RestResponse<PageResponse<Concert>> {
        return client.post("$apiBaseUrl/concerts/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            parameter("sortParam", sortParam)
            setBody(requete)
        }.body()
    }

    // Les statuts de participation sont une table de reference (3 lignes).
    // On les lit pour resoudre l'id de CONFIRMED par son nom, plutot que de
    // coder « 2 » en dur : l'ordre des insertions en base n'est pas garanti.
    suspend fun getStatutsParticipation(): RestResponse<PageResponse<ParticipationStatuse>> {
        return client.get("$apiBaseUrl/participationstatuses") {
            parameter("page", 0)
            parameter("size", 20)
        }.body()
    }

    // ============================================================
    // Envoi de fichiers (Sprint 5)
    // ============================================================

    /**
     * Televerse une image et renvoie l'URL publique renvoyee par le serveur.
     * event_media.url attend une adresse deja hebergee : il faut donc envoyer
     * le fichier d'abord, puis creer le media avec l'URL obtenue.
     */
    suspend fun televerserFichier(
        octets: ByteArray,
        nomFichier: String,
        typeMime: String
    ): String {
        val reponse: RestResponse<String> = client.submitFormWithBinaryData(
            url = "$apiBaseUrl/uploads",
            formData = formData {
                append(
                    key = "fichier",
                    value = octets,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, typeMime)
                        append(HttpHeaders.ContentDisposition, "filename=\"$nomFichier\"")
                    }
                )
            }
        ).body()

        return reponse.exigerSucces("Echec de l'envoi du fichier").data
            ?: throw IllegalStateException("Le serveur n'a pas renvoyé d'URL.")
    }

    /** Rattache un media deja televerse a un evenement. */
    suspend fun creerMediaEvenement(
        idEvenement: Long,
        idUtilisateur: Long,
        idTypeMedia: Long,
        url: String
    ): RestResponse<EventMedia> {
        val reponse: RestResponse<EventMedia> = client.post("$apiBaseUrl/eventmedias") {
            contentType(ContentType.Application.Json)
            setBody(
                EventMediaCreate(
                    event = EvenementIdDto(idEvenement),
                    uploader = UserIdDto(idUtilisateur),
                    mediaType = ReferenceIdDto(idTypeMedia),
                    url = url,
                    createdAt = maintenantIso()
                )
            )
        }.body()
        return reponse.exigerSucces("Echec de l'enregistrement du média")
    }

    /** Types de media (PHOTO, VIDEO), lus par leur nom comme les autres references. */
    suspend fun getTypesMedia(): RestResponse<PageResponse<MediaType>> {
        return client.get("$apiBaseUrl/mediatypes") {
            parameter("page", 0)
            parameter("size", 20)
        }.body()
    }

    // ============================================================
    // Medias d'evenement (Sprint 4 — galerie post-evenement)
    // ============================================================

    suspend fun rechercherMediasEvenement(
        requete: EventMediaSearch,
        page: Int = 0,
        size: Int = 50,
        sortParam: String = "createdAt,desc"
    ): RestResponse<PageResponse<EventMedia>> {
        return client.post("$apiBaseUrl/eventmedias/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            parameter("sortParam", sortParam)
            setBody(requete)
        }.body()
    }

    // ============================================================
    // Editorial Playlists
    // ============================================================

    suspend fun getEditorialPlaylists(page: Int = 0, size: Int = 10): RestResponse<PageResponse<EditorialPlaylist>> {
        return client.get("$apiBaseUrl/editorialplaylists") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    // ============================================================
    // Genres
    // ============================================================

    suspend fun getGenres(page: Int = 0, size: Int = 50): RestResponse<PageResponse<Genre>> {
        return client.get("$apiBaseUrl/genres") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    // ============================================================
    // Search
    // ============================================================

    suspend fun searchArtists(query: ArtistSearch, page: Int = 0, size: Int = 10): RestResponse<PageResponse<Artist>> {
        return client.post("$apiBaseUrl/artists/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(query)
        }.body()
    }

    suspend fun rechercherChansons(query: SongSearch, page: Int = 0, size: Int = 20): RestResponse<PageResponse<Song>> {
        return client.post("$apiBaseUrl/songs/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(query)
        }.body()
    }

    suspend fun rechercherAlbums(query: AlbumSearch, page: Int = 0, size: Int = 20): RestResponse<PageResponse<Album>> {
        return client.post("$apiBaseUrl/albums/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(query)
        }.body()
    }

    suspend fun rechercherGenres(query: GenreSearch, page: Int = 0, size: Int = 20): RestResponse<PageResponse<Genre>> {
        return client.post("$apiBaseUrl/genres/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(query)
        }.body()
    }

    /** Chansons rattachees a un genre, via la table de liaison song_genres. */
    suspend fun chansonsParGenre(idGenre: Long, page: Int = 0, size: Int = 50): RestResponse<PageResponse<SongGenre>> {
        return client.post("$apiBaseUrl/songgenres/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(SongGenreSearch(genre = GenreIdDto(idGenre)))
        }.body()
    }

    // ============================================================
    // Historique de recherche
    // ============================================================

    suspend fun getHistoriqueRecherche(idUtilisateur: Long, page: Int = 0, size: Int = 10): RestResponse<PageResponse<SearchHistory>> {
        return client.post("$apiBaseUrl/searchhistorys/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            parameter("sortParam", "searchedAt,desc")
            setBody(SearchHistorySearch(user = UserIdDto(idUtilisateur)))
        }.body()
    }

    suspend fun creerHistoriqueRecherche(idUtilisateur: Long, texte: String, horodatage: String): RestResponse<SearchHistory> {
        return client.post("$apiBaseUrl/searchhistorys") {
            contentType(ContentType.Application.Json)
            setBody(
                SearchHistoryCreate(
                    user = UserIdDto(idUtilisateur),
                    queryText = texte,
                    searchedAt = horodatage
                )
            )
        }.body()
    }

    // ============================================================
    // Follows
    // ============================================================

    suspend fun getFollows(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Follow>> {
        return client.get("$apiBaseUrl/follows") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun createFollow(follow: Follow): RestResponse<Follow> {
        return client.post("$apiBaseUrl/follows") {
            contentType(ContentType.Application.Json)
            setBody(follow)
        }.body()
    }

    // Volontairement sans deserialisation : RestResponse<Any> n'est pas
    // serialisable, et tenter de lire le corps levait une exception APRES que
    // le serveur avait supprime la ligne. L'ecran revenait alors a « Suivi »
    // alors que le desabonnement avait bien eu lieu. On se fie au statut HTTP.
    suspend fun deleteFollow(id: Long) {
        val reponse = client.delete("$apiBaseUrl/follows/$id")
        if (!reponse.status.isSuccess()) {
            throw IllegalStateException("Echec du desabonnement (${reponse.status.value})")
        }
    }

    // --- Sprint 3 : follow / unfollow depuis la fiche artiste ---

    // Sert à deux usages selon le filtre passé :
    //  - artiste seul                → compter les abonnés (totalElements)
    //  - artiste + utilisateur       → savoir si l'utilisateur suit déjà, et
    //                                  récupérer l'id du follow pour le supprimer
    suspend fun rechercherFollows(
        requete: FollowSearch,
        page: Int = 0,
        size: Int = 1
    ): RestResponse<PageResponse<Follow>> {
        return client.post("$apiBaseUrl/follows/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(requete)
        }.body()
    }

    // On envoie volontairement un corps minimal (deux identifiants) plutôt que
    // l'objet Follow complet : le backend valide l'entité et un Artist partiel
    // ferait échouer la création.
    suspend fun creerFollow(idUtilisateur: Long, idArtiste: Long): RestResponse<Follow> {
        val reponse: RestResponse<Follow> = client.post("$apiBaseUrl/follows") {
            contentType(ContentType.Application.Json)
            setBody(
                FollowCreate(
                    clientUser = UserIdDto(idUtilisateur),
                    artist = ArtistIdDto(idArtiste),
                    // Le backend refuse la creation sans createdAt
                    // (« Validation failed : CreatedAt cannot be null »).
                    createdAt = maintenantIso()
                )
            )
        }.body()

        // expectSuccess est a false : une 400 ne leve pas d'exception et se
        // deserialise dans la meme enveloppe. Sans ce controle, l'ecran
        // afficherait « Suivi » alors que rien n'a ete enregistre.
        if (reponse.status !in 200..299) {
            throw IllegalStateException("Echec de l'abonnement (${reponse.status}) : ${reponse.message}")
        }
        return reponse
    }

    // ============================================================
    // Comments
    // ============================================================

    suspend fun getComments(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Comment>> {
        return client.get("$apiBaseUrl/comments") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun createComment(comment: Comment): RestResponse<Comment> {
        return client.post("$apiBaseUrl/comments") {
            contentType(ContentType.Application.Json)
            setBody(comment)
        }.body()
    }

    // ============================================================
    // Likes
    // ============================================================

    suspend fun getLikes(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Like>> {
        return client.get("$apiBaseUrl/likes") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    suspend fun createLike(like: Like): RestResponse<Like> {
        return client.post("$apiBaseUrl/likes") {
            contentType(ContentType.Application.Json)
            setBody(like)
        }.body()
    }

    // Meme correctif que deleteFollow : RestResponse<Any> n'est pas serialisable,
    // la lecture du corps levait une exception APRES la suppression cote serveur
    // et l'ecran revenait a « aime » alors que le like n'existait plus.
    suspend fun deleteLike(id: Long) {
        val reponse = client.delete("$apiBaseUrl/likes/$id")
        if (!reponse.status.isSuccess()) {
            throw IllegalStateException("Echec du retrait du like (${reponse.status.value})")
        }
    }

    // ============================================================
    // Interactions sur un evenement (Sprint 3 — likes, commentaires, signalement)
    // ============================================================
    //
    // Likes, commentaires et signalements partagent le meme schema generique :
    // un type de cible (EVENT, COMMENT, ...) + l'id de la cible. Les types sont
    // une table de reference, dont on lit les ids par leur nom plutot que de les
    // coder en dur.
    //
    // Les trois entites exigent createdAt cote backend (« CreatedAt cannot be
    // null ») : on l'envoie systematiquement a la creation.

    suspend fun getCiblesInteraction(): RestResponse<PageResponse<InteractionTarget>> {
        return client.get("$apiBaseUrl/interactiontargets") {
            parameter("page", 0)
            parameter("size", 20)
        }.body()
    }

    suspend fun getStatutsSignalement(): RestResponse<PageResponse<ReportStatuse>> {
        return client.get("$apiBaseUrl/reportstatuses") {
            parameter("page", 0)
            parameter("size", 20)
        }.body()
    }

    // --- Likes ---

    // Selon le filtre : cible seule -> compteur (totalElements) ;
    // cible + utilisateur -> « ai-je deja aime », et l'id a supprimer.
    suspend fun rechercherLikes(
        requete: LikeSearch,
        page: Int = 0,
        size: Int = 1
    ): RestResponse<PageResponse<Like>> {
        return client.post("$apiBaseUrl/likes/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(requete)
        }.body()
    }

    suspend fun aimer(idUtilisateur: Long, idTypeCible: Long, idCible: Long): RestResponse<Like> {
        val reponse: RestResponse<Like> = client.post("$apiBaseUrl/likes") {
            contentType(ContentType.Application.Json)
            setBody(
                LikeCreate(
                    user = UserIdDto(idUtilisateur),
                    targetType = ReferenceIdDto(idTypeCible),
                    targetId = idCible,
                    createdAt = maintenantIso()
                )
            )
        }.body()
        return reponse.exigerSucces("Echec du like")
    }

    // --- Commentaires ---

    suspend fun rechercherCommentaires(
        requete: CommentSearch,
        page: Int = 0,
        size: Int = 50,
        // Les plus recents en premier : c'est ce qu'on lit d'abord sous un evenement.
        sortParam: String = "createdAt,desc"
    ): RestResponse<PageResponse<Comment>> {
        return client.post("$apiBaseUrl/comments/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            parameter("sortParam", sortParam)
            setBody(requete)
        }.body()
    }

    suspend fun publierCommentaire(
        idUtilisateur: Long,
        idTypeCible: Long,
        idCible: Long,
        contenu: String
    ): RestResponse<Comment> {
        val reponse: RestResponse<Comment> = client.post("$apiBaseUrl/comments") {
            contentType(ContentType.Application.Json)
            setBody(
                CommentCreate(
                    author = UserIdDto(idUtilisateur),
                    targetType = ReferenceIdDto(idTypeCible),
                    targetId = idCible,
                    content = contenu,
                    isHidden = false,
                    createdAt = maintenantIso()
                )
            )
        }.body()
        return reponse.exigerSucces("Echec de la publication du commentaire")
    }

    // Sans deserialisation, pour la meme raison que deleteLike / deleteFollow.
    suspend fun supprimerCommentaire(id: Long) {
        val reponse = client.delete("$apiBaseUrl/comments/$id")
        if (!reponse.status.isSuccess()) {
            throw IllegalStateException("Echec de la suppression du commentaire (${reponse.status.value})")
        }
    }

    // --- Signalements ---

    suspend fun signaler(
        idUtilisateur: Long,
        idTypeCible: Long,
        idCible: Long,
        motif: String,
        idStatut: Long
    ): RestResponse<Report> {
        val reponse: RestResponse<Report> = client.post("$apiBaseUrl/reports") {
            contentType(ContentType.Application.Json)
            setBody(
                ReportCreate(
                    reporter = UserIdDto(idUtilisateur),
                    targetType = ReferenceIdDto(idTypeCible),
                    targetId = idCible,
                    reason = motif,
                    status = ReferenceIdDto(idStatut),
                    createdAt = maintenantIso()
                )
            )
        }.body()
        return reponse.exigerSucces("Echec du signalement")
    }

    // Sert a masquer, pour celui qui les a signales, les commentaires qu'il a
    // deja signales : il n'a pas a les revoir en attendant la moderation.
    suspend fun rechercherSignalements(
        requete: ReportSearch,
        page: Int = 0,
        size: Int = 100
    ): RestResponse<PageResponse<Report>> {
        return client.post("$apiBaseUrl/reports/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            setBody(requete)
        }.body()
    }

    // expectSuccess est a false : une 400 se deserialise dans la meme enveloppe
    // sans lever d'exception. On controle donc le statut explicitement, sinon
    // l'ecran afficherait un succes alors que rien n'a ete enregistre.
    private fun <T> RestResponse<T>.exigerSucces(action: String): RestResponse<T> {
        if (status !in 200..299) {
            throw IllegalStateException("$action ($status) : $message")
        }
        return this
    }

    // ============================================================
    // Notifications
    // ============================================================

    suspend fun getNotifications(page: Int = 0, size: Int = 10): RestResponse<PageResponse<Notification>> {
        return client.get("$apiBaseUrl/notifications") {
            parameter("page", page)
            parameter("size", size)
        }.body()
    }

    // ============================================================
    // Reports
    // ============================================================

    suspend fun createReport(report: Report): RestResponse<Report> {
        return client.post("$apiBaseUrl/reports") {
            contentType(ContentType.Application.Json)
            setBody(report)
        }.body()
    }

    // ============================================================
    // Listening History
    // ============================================================

    suspend fun getListeningHistory(query: ListeningHistorySearchDto, page: Int = 0, size: Int = 10, sortParam: String = "listenedAt,desc"): RestResponse<PageResponse<ListeningHistory>> {
        return client.post("$apiBaseUrl/listeninghistorys/search") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("size", size)
            parameter("sortParam", sortParam)
            setBody(query)
        }.body()
    }
}

// DTO de recherche pour POST /artists/search
@kotlinx.serialization.Serializable
data class ArtistSearch(
    val stageName: String? = null,
    val bio: String? = null
)

// DTO de recherche pour POST /listeninghistorys/search
@kotlinx.serialization.Serializable
data class ListeningHistorySearchDto(
    val useridUsers: UserIdDto? = null
)

@kotlinx.serialization.Serializable
data class UserIdDto(
    val id: Long
)

// DTO de recherche pour POST /songs/search
@kotlinx.serialization.Serializable
data class SongSearch(
    val title: String? = null
)

// DTO de recherche pour POST /albums/search
@kotlinx.serialization.Serializable
data class AlbumSearch(
    val title: String? = null
)

// DTO de recherche pour POST /genres/search
@kotlinx.serialization.Serializable
data class GenreSearch(
    val name: String? = null
)

// DTO de recherche pour POST /songgenres/search
@kotlinx.serialization.Serializable
data class SongGenreSearch(
    @kotlinx.serialization.SerialName("genreidGenres") val genre: GenreIdDto? = null
)

@kotlinx.serialization.Serializable
data class GenreIdDto(
    val id: Long
)

// DTO de recherche pour POST /searchhistorys/search
@kotlinx.serialization.Serializable
data class SearchHistorySearch(
    @kotlinx.serialization.SerialName("useridUsers") val user: UserIdDto? = null
)

// DTO de creation pour POST /searchhistorys
@kotlinx.serialization.Serializable
data class SearchHistoryCreate(
    @kotlinx.serialization.SerialName("useridUsers") val user: UserIdDto? = null,
    @kotlinx.serialization.SerialName("queryText") val queryText: String,
    @kotlinx.serialization.SerialName("searchedAt") val searchedAt: String
)


// DTO de recherche pour POST /follows/search (Sprint 3)
@kotlinx.serialization.Serializable
data class FollowSearch(
    @kotlinx.serialization.SerialName("clientuseridUsers") val clientUser: UserIdDto? = null,
    @kotlinx.serialization.SerialName("artistidArtists") val artist: ArtistIdDto? = null
)

// DTO de creation pour POST /follows (Sprint 3)
@kotlinx.serialization.Serializable
data class FollowCreate(
    @kotlinx.serialization.SerialName("clientuseridUsers") val clientUser: UserIdDto,
    @kotlinx.serialization.SerialName("artistidArtists") val artist: ArtistIdDto,
    @kotlinx.serialization.SerialName("createdAt") val createdAt: String
)

@kotlinx.serialization.Serializable
data class ArtistIdDto(
    val id: Long
)

// ============================================================
// DTO de recherche — Sprint 4 (calendrier et galerie)
// ============================================================

// POST /concerts/search
// startTimeMin / startTimeMax servent a decouper passe et a venir en deux
// appels, plutot que de tout rapatrier et de trier sur le mobile.
@kotlinx.serialization.Serializable
data class ConcertSearch(
    @kotlinx.serialization.SerialName("artistidArtists") val artist: ArtistIdDto? = null,
    @kotlinx.serialization.SerialName("eventidEvents") val event: EvenementIdDto? = null,
    @kotlinx.serialization.SerialName("statusidParticipationStatuses") val statut: StatutParticipationIdDto? = null,
    @kotlinx.serialization.SerialName("startTimeMin") val debutMin: String? = null,
    @kotlinx.serialization.SerialName("startTimeMax") val debutMax: String? = null
)

// POST /eventmedias/search
@kotlinx.serialization.Serializable
data class EventMediaSearch(
    @kotlinx.serialization.SerialName("eventidEvents") val event: EvenementIdDto? = null
)

@kotlinx.serialization.Serializable
data class EvenementIdDto(
    val id: Long
)

@kotlinx.serialization.Serializable
data class StatutParticipationIdDto(
    val id: Long
)

// ============================================================
// DTO — Sprint 3 (likes, commentaires, signalements)
// ============================================================

// Reference generique { "id": ... } vers une table de reference
// (interaction_targets, report_statuses).
@kotlinx.serialization.Serializable
data class ReferenceIdDto(
    val id: Long
)

// POST /likes/search
@kotlinx.serialization.Serializable
data class LikeSearch(
    @kotlinx.serialization.SerialName("useridUsers") val user: UserIdDto? = null,
    @kotlinx.serialization.SerialName("targettypeidInteractionTargets") val targetType: ReferenceIdDto? = null,
    @kotlinx.serialization.SerialName("targetId") val targetId: Long? = null
)

// POST /likes
@kotlinx.serialization.Serializable
data class LikeCreate(
    @kotlinx.serialization.SerialName("useridUsers") val user: UserIdDto,
    @kotlinx.serialization.SerialName("targettypeidInteractionTargets") val targetType: ReferenceIdDto,
    @kotlinx.serialization.SerialName("targetId") val targetId: Long,
    @kotlinx.serialization.SerialName("createdAt") val createdAt: String
)

// POST /comments/search
@kotlinx.serialization.Serializable
data class CommentSearch(
    @kotlinx.serialization.SerialName("targettypeidInteractionTargets") val targetType: ReferenceIdDto? = null,
    @kotlinx.serialization.SerialName("targetId") val targetId: Long? = null,
    // false : on exclut cote serveur les commentaires masques par la moderation.
    @kotlinx.serialization.SerialName("isHidden") val isHidden: Boolean? = null
)

// POST /comments
@kotlinx.serialization.Serializable
data class CommentCreate(
    @kotlinx.serialization.SerialName("authoruseridUsers") val author: UserIdDto,
    @kotlinx.serialization.SerialName("targettypeidInteractionTargets") val targetType: ReferenceIdDto,
    @kotlinx.serialization.SerialName("targetId") val targetId: Long,
    val content: String,
    @kotlinx.serialization.SerialName("isHidden") val isHidden: Boolean,
    @kotlinx.serialization.SerialName("createdAt") val createdAt: String
)

// POST /reports/search
@kotlinx.serialization.Serializable
data class ReportSearch(
    @kotlinx.serialization.SerialName("reporteruseridUsers") val reporter: UserIdDto? = null,
    @kotlinx.serialization.SerialName("targettypeidInteractionTargets") val targetType: ReferenceIdDto? = null
)

// POST /reports
@kotlinx.serialization.Serializable
data class ReportCreate(
    @kotlinx.serialization.SerialName("reporteruseridUsers") val reporter: UserIdDto,
    @kotlinx.serialization.SerialName("targettypeidInteractionTargets") val targetType: ReferenceIdDto,
    @kotlinx.serialization.SerialName("targetId") val targetId: Long,
    val reason: String?,
    @kotlinx.serialization.SerialName("statusidReportStatuses") val status: ReferenceIdDto,
    @kotlinx.serialization.SerialName("createdAt") val createdAt: String
)

// POST /eventmedias — Sprint 5 (galerie alimentee depuis l'application)
@kotlinx.serialization.Serializable
data class EventMediaCreate(
    @kotlinx.serialization.SerialName("eventidEvents") val event: EvenementIdDto,
    @kotlinx.serialization.SerialName("uploaderuseridUsers") val uploader: UserIdDto,
    @kotlinx.serialization.SerialName("mediatypeidMediaTypes") val mediaType: ReferenceIdDto,
    val url: String,
    @kotlinx.serialization.SerialName("createdAt") val createdAt: String
)
