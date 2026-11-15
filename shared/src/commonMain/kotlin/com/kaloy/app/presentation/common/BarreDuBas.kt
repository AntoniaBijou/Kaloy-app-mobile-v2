package com.kaloy.app.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.presentation.auth.gate.AuthGateCoordinator
import com.kaloy.app.presentation.auth.gate.AuthGateHost
import com.kaloy.app.presentation.auth.gate.executerOuDemanderAuthentification
import com.kaloy.app.presentation.chanson.EcranToutesChansonsVoyager
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.lecteur.LecteurViewModel
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.presentation.playlist.PlaylistScreen
import com.kaloy.app.presentation.queue.MiniPlayer
import com.kaloy.app.presentation.queue.UpNextSheet
import com.kaloy.app.presentation.radio.RadioScreen
import org.koin.compose.koinInject

/**
 * Un ecran qui ne veut aucune barre en bas.
 *
 * Les ecrans d'authentification -- on n'est pas connecte, rien ne joue -- et
 * le lecteur plein ecran, ou proposer un raccourci vers le lecteur n'aurait
 * pas de sens.
 *
 * Une interface a implementer plutot qu'une liste tenue ici : un ecran cree
 * plus tard recoit la barre sans rien faire, et celui qui n'en veut pas le
 * declare a l'endroit ou on le lira, c'est-a-dire chez lui.
 */
interface SansBarreDuBas

/**
 * Permet a un ecran de masquer la barre de lecture le temps d'un moment precis.
 *
 * Un seul usage aujourd'hui : la saisie de recherche. Tant que le clavier est
 * ouvert et que les suggestions s'affichent, la barre mangerait la place ; des
 * que les resultats sont la, elle revient, parce que parcourir des resultats
 * est justement un moment ou l'on ecoute.
 *
 * Un objet partage et non un CompositionLocal : l'ecran de recherche est un
 * ENFANT de la barre dans l'arbre de composition, et un enfant ne peut pas
 * renseigner un CompositionLocal lu par son parent.
 *
 * L'ecran qui met ce drapeau a vrai doit le remettre a faux en partant, sans
 * quoi la barre resterait masquee sur l'ecran suivant.
 */
class EtatBarreDuBas {
    var saisieEnCours by mutableStateOf(false)

    /**
     * Vrai en mode visiteur, ou aucune barre ne s'affiche.
     *
     * Le mode visiteur est un parametre de l'ecran d'accueil, pas un etat
     * de session : seul cet ecran le connait, et il le recopie ici pour
     * que la barre -- qui vit au-dessus de lui -- puisse en tenir compte.
     */
    var estVisiteur by mutableStateOf(false)

    /**
     * L'onglet affiche par [com.kaloy.app.presentation.common.EcranPrincipal].
     *
     * Le pager le renseigne a chaque glissement, et la barre le lit pour
     * marquer le bon onglet. Il passe par ici et non par le Navigator parce
     * que les trois onglets ne sont plus trois ecrans : ils sont trois pages
     * d'un seul, et `navigateur.lastItem` ne sait donc plus laquelle est a
     * l'image.
     */
    var onglet by mutableStateOf(OngletPrincipal.ACCUEIL)

    /**
     * Un onglet demande par la barre, que le pager consomme puis remet a nul.
     *
     * Le sens inverse du precedent. La remise a nul est indispensable : sans
     * elle, le meme ordre se rejouerait a chaque recomposition et ramenerait
     * sans cesse sur l'onglet demande, rendant le glissement impossible.
     */
    var ongletDemande by mutableStateOf<OngletPrincipal?>(null)
}

/**
 * La barre du bas, posee une seule fois pour toute l'application.
 *
 * Avant, chaque ecran posait la sienne. Deux ecrans sur vingt-cinq le
 * faisaient reellement : la barre de lecture n'existait que sur l'accueil, et
 * la barre de navigation disparaissait des qu'on ouvrait l'onglet « Moi » --
 * l'ecran du profil n'en posait pas, et plus rien ne ramenait a l'accueil hors
 * le bouton retour.
 *
 * Posee ici, elle est vraie partout sans que chaque ecran ait a y penser.
 */
@Composable
fun BarreDuBas(navigateur: Navigator) {
    val ecran = navigateur.lastItem
    AuthGateHost(navigateur, koinInject<AuthGateCoordinator>())
    if (ecran is SansBarreDuBas) return

    val etatBarre = koinInject<EtatBarreDuBas>()
    if (etatBarre.estVisiteur) return

    val sessionManager = koinInject<AuthSessionManager>()
    val authGateCoordinator = koinInject<AuthGateCoordinator>()
    val fileAttente = koinInject<QueueManager>()
    val lecteurAudio = koinInject<AudioPlayerController>()
    val lecteurModeleVue = koinInject<LecteurViewModel>()

    val chansonCourante by fileAttente.currentSong.collectAsState()
    val chansonsSuivantes by fileAttente.upNext.collectAsState()
    val historique by fileAttente.history.collectAsState()
    val lectureActive by lecteurAudio.isPlaying.collectAsState()

    var afficherFile by remember { mutableStateOf(false) }

    val onglet = ongletDe(ecran, etatBarre)

    Column {
        val chanson = chansonCourante
        if (chanson != null && !etatBarre.saisieEnCours) {
            MiniPlayer(
                song = chanson,
                isPlaying = lectureActive,
                onClick = {
                    executerOuDemanderAuthentification(
                        sessionManager,
                        authGateCoordinator,
                        "écouter cette chanson"
                    ) {
                        navigateur.push(LecteurScreen(chanson.id))
                    }
                },
                onTogglePlay = {
                    if (lectureActive) {
                        lecteurAudio.pause()
                    } else {
                        executerOuDemanderAuthentification(
                            sessionManager,
                            authGateCoordinator,
                            "reprendre la lecture"
                        ) {
                            lecteurAudio.resume()
                        }
                    }
                },
                onPrevious = {
                    executerOuDemanderAuthentification(
                        sessionManager,
                        authGateCoordinator,
                        "lancer la chanson précédente"
                    ) {
                        lecteurModeleVue.jouerPrecedent()
                    }
                },
                onNext = {
                    executerOuDemanderAuthentification(
                        sessionManager,
                        authGateCoordinator,
                        "lancer la chanson suivante"
                    ) {
                        lecteurModeleVue.jouerSuivant()
                    }
                },
                canPrevious = historique.isNotEmpty(),
                canNext = chansonsSuivantes.isNotEmpty(),
                // L'application dessine bord a bord : sans marge, la barre
                // passerait sous la barre de gestes d'Android. Le Scaffold
                // s'en chargeait tant que la barre vivait dans son
                // bottomBar ; hors de lui, plus personne ne le fait.
                //
                // Seulement quand aucune barre de navigation ne la suit :
                // NavigationBar applique deja cette marge lui-meme, et la
                // cumuler laisserait un vide sous les onglets.
                modifier = if (onglet == null) Modifier.navigationBarsPadding() else Modifier
            )
        }

        onglet?.let { actif ->
            BarreNavigationPrincipale(
                ongletActif = actif,
                // On ne navigue plus : on demande une page, et le pager y
                // glisse. L'ancien allerA empilait ou depilait des ecrans, ce
                // qui n'a plus de sens quand les trois vivent ensemble.
                onAccueil = {
                    if (ecran is EcranToutesChansonsVoyager) {
                        navigateur.pop()
                    } else {
                        etatBarre.ongletDemande = OngletPrincipal.ACCUEIL
                    }
                },
                onPlaylist = {
                    executerOuDemanderAuthentification(
                        sessionManager,
                        authGateCoordinator,
                        "gérer vos playlists"
                    ) {
                        if (ecran is EcranToutesChansonsVoyager) {
                            navigateur.push(PlaylistScreen())
                        } else {
                            etatBarre.ongletDemande = OngletPrincipal.PLAYLIST
                        }
                    }
                },
                onRadio = {
                    executerOuDemanderAuthentification(
                        sessionManager,
                        authGateCoordinator,
                        "utiliser le mode radio"
                    ) {
                        if (ecran is EcranToutesChansonsVoyager) {
                            navigateur.push(RadioScreen())
                        } else {
                            etatBarre.ongletDemande = OngletPrincipal.RADIO
                        }
                    }
                },
                onMoi = {
                    executerOuDemanderAuthentification(
                        sessionManager,
                        authGateCoordinator,
                        "accéder à votre espace personnel"
                    ) {
                        if (ecran is EcranToutesChansonsVoyager) {
                            navigateur.push(MoiScreen())
                        } else {
                            etatBarre.ongletDemande = OngletPrincipal.MOI
                        }
                    }
                }
            )
        }
    }

    if (afficherFile) {
        UpNextSheet(
            currentSong = chansonCourante,
            songs = chansonsSuivantes,
            onDismiss = { afficherFile = false },
            onPlay = { choisie ->
                executerOuDemanderAuthentification(
                    sessionManager,
                    authGateCoordinator,
                    "lancer une chanson de la file d'attente"
                ) {
                    fileAttente.playFromQueue(choisie)
                    afficherFile = false
                    navigateur.push(LecteurScreen(choisie.id))
                }
            },
            onRemove = { chanson ->
                executerOuDemanderAuthentification(
                    sessionManager,
                    authGateCoordinator,
                    "modifier la file d'attente"
                ) {
                    fileAttente.remove(chanson)
                }
            },
            onClear = {
                executerOuDemanderAuthentification(
                    sessionManager,
                    authGateCoordinator,
                    "vider la file d'attente"
                ) {
                    fileAttente.clear()
                }
            }
        )
    }
}

/**
 * L'onglet a allumer, ou null si l'ecran courant n'est pas un onglet.
 *
 * Deduit de l'ecran plutot que retenu en memoire. Un `remember` ne survivrait
 * pas a la navigation, et c'est l'ancien travers que le commentaire de
 * BarreNavigationPrincipale decrit : « Accueil » restait allume pendant qu'on
 * etait ailleurs.
 */
/**
 * L'onglet a marquer, ou null quand la barre de navigation ne s'affiche pas.
 *
 * Un seul ecran porte les onglets desormais, et c'est lui qui sait laquelle
 * de ses trois pages est a l'image. Sur tout autre ecran -- une fiche, un
 * lecteur, un formulaire -- il n'y a pas d'onglet actif.
 */
private fun ongletDe(ecran: Screen, etatBarre: EtatBarreDuBas): OngletPrincipal? =
    when (ecran) {
        is EcranPrincipal -> etatBarre.onglet
        is EcranToutesChansonsVoyager -> OngletPrincipal.ACCUEIL
        else -> null
    }

/**
 * Va vers un onglet sans faire grossir la pile.
 *
 * Si l'ecran vise est deja dans la pile, on y revient ; sinon on l'empile.
 * L'ecran de recherche faisait deja ce raisonnement a la main, avec un `pop()`
 * qui supposait que l'accueil etait juste en dessous. Depuis que le profil
 * porte lui aussi la barre, cette hypothese ne tient plus : revenir du profil
 * par un `pop()` aurait ramene sur la recherche, pas sur l'accueil.
 */
