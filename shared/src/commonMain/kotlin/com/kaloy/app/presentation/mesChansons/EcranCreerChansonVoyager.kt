package com.kaloy.app.presentation.mesChansons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.envoi.EnvoyeurParMorceaux
import com.kaloy.app.core.envoi.MemoireEnvois
import com.kaloy.app.data.api.CreerChansonDto
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.OptionsChansonDto
import com.kaloy.app.presentation.common.SourceMedia
import com.kaloy.app.presentation.common.rememberSelecteurMedia
import com.russhwolf.settings.Settings
import org.koin.mp.KoinPlatform
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Declaration d'une chanson : tous ses champs dans un seul formulaire.
 *
 * Un seul envoi et non une creation puis une completion : la creation est
 * transactionnelle cote serveur, donc un echec en cours de route ne laisse
 * pas une chanson a moitie declaree.
 *
 * Tout l'etat est dans le modele de vue et non dans des `remember` : Voyager
 * detruit les composables au `push`, et ouvrir le selecteur de date ou la
 * recherche de paroles effacerait la saisie.
 */
class CreerChansonViewModel : ViewModel() {
    private val api = KaloyApi()

    /**
     * L'envoi par morceaux ecrit au Sprint 5 pour les videos d'evenement.
     *
     * Reutilise tel quel : un MP3 de quatre minutes pese 4 a 10 Mo, ce qui
     * echoue assez souvent sur un reseau mobile faible pour que la reprise
     * serve. Chaque fichier a sa propre cible, « chanson:audio » et les
     * autres, ce qui lui donne sa propre case en memoire d'envoi : les trois
     * montent et se reprennent sans se chasser l'un l'autre.
     */
    private val envoyeur = EnvoyeurParMorceaux(
        api = api,
        memoire = MemoireEnvois(KoinPlatform.getKoin().get<Settings>())
    )

    /**
     * Les trois fichiers demandes, chacun avec son etat.
     *
     * Le karaoke et le playback sont demandes ici et non dans un ecran de
     * modification, qui n'existe pas encore : sans cela les deux champs
     * seraient inatteignables.
     */
    val audio = EnvoiFichier(
        envoyeur, viewModelScope, "chanson:audio",
        libelle = "Choisir un fichier audio",
        aide = "MP3 ou M4A, 200 Mo au maximum. Facultatif : une chanson " +
            "peut être déclarée sur son seul texte."
    )

    /**
     * Le karaoke accepte la video, les deux autres non.
     *
     * Un karaoke affiche les paroles : c'est une video dans la plupart des
     * cas, et celui du jeu de donnees du projet en est une. L'audio de la
     * chanson, lui, reste de l'audio — le formulaire a deja un champ separe
     * pour l'adresse de la video, et accepter une video dans audio_url
     * brouillerait les deux colonnes.
     */
    val karaoke = EnvoiFichier(
        envoyeur, viewModelScope, "chanson:karaoke",
        libelle = "Choisir un fichier karaoké",
        aide = "Audio ou vidéo. Une vidéo MP4 convient, c'est la forme " +
            "habituelle d'un karaoké : les paroles s'affichent.",
        source = SourceMedia.AUDIO_OU_VIDEO_FICHIER,
        refus = ::refusKaraoke
    )

    val playback = EnvoiFichier(
        envoyeur, viewModelScope, "chanson:playback",
        libelle = "Choisir un fichier playback",
        aide = "L'accompagnement seul, pour une interprétation sur scène."
    )

    private val fichiers = listOf(audio, karaoke, playback)

    // ---------------------------------------------------- les champs saisis
    var titre by mutableStateOf("")
    var paroles by mutableStateOf("")
    var solfa by mutableStateOf("")
    var langue by mutableStateOf("mg")

    var urlVideo by mutableStateOf("")
    var auteurCompositeur by mutableStateOf("")
    var arrangeur by mutableStateOf("")
    var duree by mutableStateOf("")
    var dateSortie by mutableStateOf<String?>(null)
    var idAlbum by mutableStateOf<Long?>(null)
    var idsGenres by mutableStateOf<List<Long>>(emptyList())
        private set

    // ---------------------------------------------------- les listes
    var options by mutableStateOf(OptionsChansonDto())
        private set
    var optionsEnErreur by mutableStateOf(false)
        private set

    // ---------------------------------------------------- etats d'ecran
    var selecteurDateOuvert by mutableStateOf(false)
    var envoiEnCours by mutableStateOf(false)
        private set
    var erreur by mutableStateOf<String?>(null)
    var chansonCreee by mutableStateOf(false)
        private set

    // ---------------------------------------------------- recherche de paroles
    var rechercheOuverte by mutableStateOf(false)
    var rechercheEnCours by mutableStateOf(false)
        private set
    var candidats by mutableStateOf<List<CandidatAffiche>>(emptyList())
        private set
    var messageRecherche by mutableStateOf<String?>(null)
        private set
    var apercu by mutableStateOf<ApercuParoles?>(null)
        private set

    data class CandidatAffiche(val titre: String, val artiste: String, val url: String)
    data class ApercuParoles(val texte: String, val source: String, val lignes: Int)

    init {
        chargerOptions()
    }

    private fun chargerOptions() {
        viewModelScope.launch {
            try {
                options = api.getOptionsChanson().data ?: OptionsChansonDto()
                optionsEnErreur = false
            } catch (_: Exception) {
                // Les listes vides restent utilisables : le titre, les paroles
                // et le solfa se saisissent sans elles, et c'est le minimum
                // exige pour declarer une chanson.
                optionsEnErreur = true
            }
        }
    }

    fun basculerGenre(idGenre: Long) {
        idsGenres = if (idGenre in idsGenres) idsGenres - idGenre else idsGenres + idGenre
    }

    /**
     * Le fichier choisi est verifie puis envoye tout de suite.
     *
     * Verifie avant la moindre copie : la taille et le type sont connus des la
     * selection, donc un fichier trop gros ou d'un format refuse est ecarte
     * immediatement, et non apres plusieurs minutes d'envoi.
     *
     * L'envoi part sans attendre l'enregistrement de la chanson : l'artiste
     * continue de remplir le formulaire pendant que le fichier monte. Le
     * chemin obtenu n'est rattache a rien jusqu'au POST — si l'artiste
     * abandonne, le fichier reste sur le disque du serveur sans etre
     * reference. C'est une limite connue, la meme que pour les medias
     * d'evenement.
     */
    /** Vrai des qu'un des trois fichiers monte : on n'enregistre pas alors. */
    val envoiFichierEnCours: Boolean get() = fichiers.any { it.enCours }

    // ---------------------------------------------------- validation
    val titreValide: Boolean get() = titre.isNotBlank() && titre.length <= 150
    val contenuPresent: Boolean get() = paroles.isNotBlank() || solfa.isNotBlank()
    val dureeValide: Boolean get() = dureeAcceptable(duree)

    val peutEnregistrer: Boolean
        get() = titreValide && contenuPresent && dureeValide &&
            !envoiEnCours && !envoiFichierEnCours

    fun enregistrer() {
        if (!peutEnregistrer) return
        viewModelScope.launch {
            envoiEnCours = true
            erreur = null
            try {
                api.creerChanson(
                    CreerChansonDto(
                        titre = titre.trim(),
                        paroles = paroles.ifBlank { null },
                        solfa = solfa.ifBlank { null },
                        langue = langue,
                        urlAudio = audio.url.ifBlank { null },
                        urlVideo = urlVideo.ifBlank { null },
                        urlKaraoke = karaoke.url.ifBlank { null },
                        urlPlayback = playback.url.ifBlank { null },
                        auteurCompositeur = auteurCompositeur.ifBlank { null },
                        arrangeur = arrangeur.ifBlank { null },
                        dureeSecondes = dureeEnSecondes(duree),
                        dateSortie = dateSortie,
                        idAlbum = idAlbum,
                        idsGenres = idsGenres
                    )
                )
                chansonCreee = true
            } catch (e: Exception) {
                erreur = e.message ?: "La déclaration a échoué."
            } finally {
                envoiEnCours = false
            }
        }
    }

    // ---------------------------------------------------- paroles
    fun chercherParoles(nomArtiste: String?) {
        if (titre.isBlank()) {
            messageRecherche = "Saisissez d'abord le titre de la chanson."
            return
        }
        viewModelScope.launch {
            rechercheEnCours = true
            messageRecherche = null
            candidats = emptyList()
            apercu = null
            try {
                val trouves = api.rechercherParoles(titre.trim(), nomArtiste).data ?: emptyList()
                candidats = trouves.map {
                    CandidatAffiche(titre = it.titre, artiste = it.artiste, url = it.url)
                }
                if (candidats.isEmpty()) {
                    messageRecherche = "Aucun résultat. Vous pouvez saisir les paroles à la main."
                }
            } catch (_: Exception) {
                // Le site de paroles est extérieur et tombe. L'echec ne prive
                // que de la recherche : la saisie a la main reste possible.
                messageRecherche = "Le site de paroles ne répond pas. " +
                    "Vous pouvez saisir les paroles à la main."
            } finally {
                rechercheEnCours = false
            }
        }
    }

    fun ouvrirApercu(candidat: CandidatAffiche) {
        viewModelScope.launch {
            rechercheEnCours = true
            messageRecherche = null
            try {
                val trouvees = api.getTexteParoles(candidat.url).data
                apercu = if (trouvees == null || trouvees.texte.isBlank()) {
                    messageRecherche = "Le texte n'a pas pu être lu sur cette page."
                    null
                } else {
                    ApercuParoles(
                        texte = trouvees.texte,
                        source = trouvees.url,
                        lignes = trouvees.lignes
                    )
                }
            } catch (_: Exception) {
                messageRecherche = "Le texte n'a pas pu être téléchargé."
            } finally {
                rechercheEnCours = false
            }
        }
    }

    /** L'artiste retient l'apercu : il remplit le champ, et reste corrigeable. */
    fun retenirApercu() {
        apercu?.let { paroles = it.texte }
        apercu = null
        candidats = emptyList()
        rechercheOuverte = false
    }

    fun fermerRecherche() {
        rechercheOuverte = false
        apercu = null
        candidats = emptyList()
        messageRecherche = null
    }
}

class EcranCreerChansonVoyager : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: CreerChansonViewModel = viewModel(key = "creer_chanson") {
            CreerChansonViewModel()
        }

        // Une fois la chanson declaree, on revient a la liste, qui se recharge
        // a son affichage.
        LaunchedEffect(modeleVue.chansonCreee) {
            if (modeleVue.chansonCreee) navigateur.pop()
        }

        if (modeleVue.selecteurDateOuvert) {
            com.kaloy.app.presentation.organisation.SelecteurDate(
                titre = "Date de sortie",
                dateChoisie = modeleVue.dateSortie,
                onChoisir = {
                    modeleVue.dateSortie = it
                    modeleVue.selecteurDateOuvert = false
                },
                onFermer = { modeleVue.selecteurDateOuvert = false }
            )
        }

        if (modeleVue.rechercheOuverte) {
            DialogueRechercheParoles(modeleVue = modeleVue)
        }

        Scaffold(
            containerColor = KaloyDarkBg,
            topBar = {
                TopAppBar(
                    title = { Text("Déclarer une chanson", color = KaloyTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { navigateur.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaloyDarkBg)
                )
            }
        ) { espacement ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacement)
                    .background(KaloyDarkBg)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (modeleVue.optionsEnErreur) {
                    Text(
                        text = "Les listes n'ont pas pu être chargées. Le titre, les paroles " +
                            "et le solfa restent saisissables.",
                        color = KaloyOrange,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                // ------------------------------------------------ l'essentiel
                TitreSection("L'essentiel")
                ChampTexte(
                    valeur = modeleVue.titre,
                    onChange = { modeleVue.titre = it },
                    etiquette = "Titre",
                    aide = "Obligatoire, 150 caractères au plus",
                    erreur = if (modeleVue.titre.length > 150) "Titre trop long" else null
                )
                SelecteurListe(
                    etiquette = "Langue",
                    options = modeleVue.options.langues.mapIndexed { index, code ->
                        com.kaloy.app.data.api.OptionChoixDto(
                            id = index.toLong(),
                            libelle = nomDeLangue(code)
                        )
                    },
                    idChoisi = modeleVue.options.langues.indexOf(modeleVue.langue)
                        .takeIf { it >= 0 }?.toLong(),
                    onChoisir = { position ->
                        position?.let {
                            modeleVue.langue = modeleVue.options.langues[it.toInt()]
                        }
                    }
                )

                // ------------------------------------------------ le contenu
                TitreSection("Le contenu")
                Text(
                    text = "Des paroles ou un solfa : il en faut au moins un.",
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                ChampTexte(
                    valeur = modeleVue.paroles,
                    onChange = { modeleVue.paroles = it },
                    etiquette = "Paroles",
                    lignes = 6
                )
                OutlinedButton(
                    onClick = {
                        modeleVue.rechercheOuverte = true
                        modeleVue.chercherParoles(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Chercher les paroles", color = KaloyPurpleLight)
                }
                ChampTexte(
                    valeur = modeleVue.solfa,
                    onChange = { modeleVue.solfa = it },
                    etiquette = "Solfa",
                    aide = "Saisie en texte. L'envoi d'un fichier n'est pas encore disponible.",
                    lignes = 4
                )
                if (!modeleVue.contenuPresent) {
                    Text(
                        text = "Ni paroles ni solfa : la chanson ne peut pas être déclarée.",
                        color = KaloyOrange,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ------------------------------------------------ rattachement
                TitreSection("Rattachement")
                SelecteurListe(
                    etiquette = "Album",
                    options = modeleVue.options.albums,
                    idChoisi = modeleVue.idAlbum,
                    choixVide = "Aucun album",
                    aide = if (modeleVue.options.albums.isEmpty())
                        "Vous n'avez aucun album enregistré." else null,
                    onChoisir = { modeleVue.idAlbum = it }
                )
                SelecteurGenres(
                    options = modeleVue.options.genres,
                    idsChoisis = modeleVue.idsGenres,
                    onBasculer = { modeleVue.basculerGenre(it) }
                )

                // ------------------------------------------------ details
                TitreSection("Détails")
                SelecteurAvecSaisie(
                    etiquette = "Auteur-compositeur",
                    valeur = modeleVue.auteurCompositeur,
                    propositions = modeleVue.options.auteursCompositeurs,
                    onChanger = { modeleVue.auteurCompositeur = it }
                )
                SelecteurAvecSaisie(
                    etiquette = "Arrangeur",
                    valeur = modeleVue.arrangeur,
                    propositions = modeleVue.options.arrangeurs,
                    onChanger = { modeleVue.arrangeur = it }
                )
                ChampTexte(
                    valeur = modeleVue.duree,
                    onChange = { modeleVue.duree = it },
                    etiquette = "Durée",
                    aide = "Sous la forme 3:45, ou en secondes",
                    erreur = if (modeleVue.dureeValide) null else "Durée illisible"
                )
                LigneDate(
                    etiquette = "Date de sortie",
                    date = modeleVue.dateSortie,
                    onOuvrir = { modeleVue.selecteurDateOuvert = true },
                    onEffacer = { modeleVue.dateSortie = null }
                )

                // ------------------------------------------------ medias
                TitreSection("Médias")
                BlocFichier(envoi = modeleVue.audio)
                ChampTexte(
                    valeur = modeleVue.urlVideo,
                    onChange = { modeleVue.urlVideo = it },
                    etiquette = "Adresse de la vidéo",
                    aide = "255 caractères au plus",
                    erreur = if (modeleVue.urlVideo.length > 255) "Adresse trop longue" else null
                )

                // ------------------------------------- karaoke et playback
                //
                // Dans leur propre section : ce sont des fichiers de travail
                // pour chanter ou jouer sur la chanson, pas la chanson
                // elle-meme, et les melanger aux medias ferait croire a trois
                // versions du meme enregistrement.
                TitreSection("Karaoké et playback")
                BlocFichier(envoi = modeleVue.karaoke)
                BlocFichier(envoi = modeleVue.playback)

                // ------------------------------------------------ envoi
                modeleVue.erreur?.let {
                    Text(
                        text = it,
                        color = KaloyRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { modeleVue.enregistrer() },
                    enabled = modeleVue.peutEnregistrer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaloyPurple,
                        disabledContainerColor = KaloyDarkElevated
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (modeleVue.envoiEnCours) {
                        CircularProgressIndicator(
                            color = KaloyTextPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Déclarer la chanson", color = KaloyTextPrimary)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Le fichier audio : choix, envoi, et ce qu'on en voit.
 *
 * Le champ texte a disparu. L'artiste ne tape plus une adresse — il choisit
 * un fichier sur son telephone, et c'est le chemin rendu par le serveur qui
 * part en base. Le type de stockage n'est plus demande : le serveur sait d'ou
 * vient le fichier puisqu'il l'a recu.
 */
@Composable
private fun BlocFichier(envoi: EnvoiFichier) {
    val ouvrirSelecteur = rememberSelecteurMedia { media ->
        envoi.choisir(media)
    }
    val fichier = envoi.fichier
    val etape = envoi.etape

    Column(modifier = Modifier.fillMaxWidth()) {
        if (fichier == null) {
            OutlinedButton(
                onClick = { ouvrirSelecteur(envoi.source) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(envoi.libelle, color = KaloyPurpleLight)
            }
            Text(
                text = envoi.aide,
                color = KaloyTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fichier.nomFichier,
                                color = KaloyTextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = tailleLisible(fichier.taille),
                                color = KaloyTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { envoi.retirer() }) {
                            Text("Retirer", color = KaloyTextMuted)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    when (etape) {
                        is EnvoyeurParMorceaux.Etape.Preparation ->
                            Text(
                                "Préparation du fichier…",
                                color = KaloyTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )

                        is EnvoyeurParMorceaux.Etape.Envoi -> {
                            LinearProgressIndicator(
                                progress = { etape.pourcentage / 100f },
                                color = KaloyPurple,
                                trackColor = KaloyDarkElevated,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Envoi : ${etape.pourcentage} %",
                                color = KaloyTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        is EnvoyeurParMorceaux.Etape.Assemblage ->
                            Text(
                                "Finalisation…",
                                color = KaloyTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )

                        is EnvoyeurParMorceaux.Etape.Termine ->
                            Text(
                                "Fichier envoyé.",
                                color = KaloyGreen,
                                style = MaterialTheme.typography.bodySmall
                            )

                        is EnvoyeurParMorceaux.Etape.Echec -> {
                            Text(
                                etape.message,
                                color = KaloyRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                            // On ne propose la reprise que si elle a un sens :
                            // un format refuse le sera toujours.
                            if (etape.reprenable) {
                                TextButton(onClick = { envoi.reprendre() }) {
                                    Text("Reprendre l'envoi", color = KaloyPurpleLight)
                                }
                            }
                        }

                        null -> Unit
                    }
                }
            }
        }

        envoi.erreur?.let {
            Text(
                text = it,
                color = KaloyRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * La recherche de paroles, en deux temps.
 *
 * D'abord les candidats, sans leur texte : une recherche sur un mot courant en
 * malgache remonte une vingtaine de chansons d'artistes sans rapport, et
 * telecharger les vingt pages serait autant de pages demandees pour rien.
 * Ensuite l'apercu du seul candidat retenu, que l'artiste relit et corrige.
 */
@Composable
private fun DialogueRechercheParoles(modeleVue: CreerChansonViewModel) {
    val apercu = modeleVue.apercu

    AlertDialog(
        onDismissRequest = { modeleVue.fermerRecherche() },
        containerColor = KaloyDarkCard,
        title = {
            Text(
                text = if (apercu == null) "Paroles trouvées" else "Aperçu",
                color = KaloyTextPrimary
            )
        },
        confirmButton = {
            if (apercu != null) {
                TextButton(onClick = { modeleVue.retenirApercu() }) {
                    Text("Utiliser ce texte", color = KaloyPurpleLight)
                }
            } else {
                TextButton(onClick = { modeleVue.fermerRecherche() }) {
                    Text("Fermer", color = KaloyTextSecondary)
                }
            }
        },
        dismissButton = {
            if (apercu != null) {
                TextButton(onClick = { modeleVue.fermerRecherche() }) {
                    Text("Annuler", color = KaloyTextSecondary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when {
                    modeleVue.rechercheEnCours -> Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = KaloyPurple,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    apercu != null -> {
                        Text(
                            text = "${apercu.lignes} lignes",
                            color = KaloyTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = apercu.texte,
                            color = KaloyTextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        // La provenance reste affichee le temps de la saisie :
                        // un texte repris d'ailleurs doit la porter.
                        Text(
                            text = "Source : ${apercu.source}",
                            color = KaloyTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    else -> {
                        modeleVue.messageRecherche?.let {
                            Text(
                                text = it,
                                color = KaloyTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        modeleVue.candidats.forEach { candidat ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                TextButton(onClick = { modeleVue.ouvrirApercu(candidat) }) {
                                    Column {
                                        Text(
                                            text = candidat.titre,
                                            color = KaloyTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = candidat.artiste,
                                            color = KaloyTextMuted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                HorizontalDivider(color = KaloyDarkElevated)
                            }
                        }
                    }
                }
            }
        }
    )
}

/** Le code de langue tel qu'il est lisible a l'ecran. */
private fun nomDeLangue(code: String): String = when (code) {
    "mg" -> "Malagasy"
    "fr" -> "Français"
    "en" -> "Anglais"
    else -> code
}
