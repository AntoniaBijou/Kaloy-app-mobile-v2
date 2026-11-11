package com.kaloy.app.presentation.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import com.kaloy.app.ui.theme.fondKaloy
import com.kaloy.app.presentation.home.HomeScreen
import com.kaloy.app.presentation.moi.MoiScreen
import com.kaloy.app.presentation.playlist.PlaylistScreen
import com.kaloy.app.presentation.radio.RadioScreen
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject

/**
 * Les trois onglets principaux, cote a cote, que l'on fait glisser.
 *
 * Avant, Accueil, Recherche et Moi etaient trois ecrans empiles dans le
 * Navigator, et la barre du bas sautait de l'un a l'autre par un `popUntil`
 * ou un `push`. On ne pouvait donc pas glisser entre eux : il n'y avait
 * jamais qu'un seul ecran a l'image.
 *
 * Ils sont desormais les trois pages d'un meme ecran, dans l'ordre de la
 * barre du bas. Le contenu suit le doigt, et la barre reste synchronisee dans
 * les deux sens -- voir [EtatBarreDuBas].
 *
 * **Les modeles de vue survivent au glissement.** Une page sortie de l'ecran
 * est liberee par le pager, mais `viewModel()` est adosse au proprietaire de
 * l'Activity -- le projet n'en declare aucun autre -- donc revenir sur un
 * onglet retrouve le meme modele, avec ses donnees. Sans cela, chaque
 * aller-retour aurait relance les appels reseau.
 *
 * **Les ecrans de detail restent empiles par-dessus.** Une fiche de chanson
 * ou un lecteur se poussent sur le Navigator comme avant, au-dessus de cet
 * ecran : seuls les trois onglets vivent dans le pager.
 *
 * @param username recopie vers l'accueil, qui l'affiche.
 * @param isVisitor recopie vers l'accueil, qui masque alors la barre du bas.
 * @param ongletInitial la page ouverte a l'arrivee.
 */
data class EcranPrincipal(
    val username: String = "Utilisateur",
    val isVisitor: Boolean = false,
    val ongletInitial: OngletPrincipal = OngletPrincipal.ACCUEIL
) : Screen {

    @Composable
    override fun Content() {
        val etatBarre = koinInject<EtatBarreDuBas>()
        val onglets = OngletPrincipal.entries

        val etatPages = rememberPagerState(initialPage = ongletInitial.ordinal) {
            onglets.size
        }

        // Le glissement remonte vers la barre, pour qu'elle marque le bon
        // onglet. currentPage et non targetPage : on ne veut pas que l'onglet
        // actif change au premier millimetre d'un geste encore hesitant.
        LaunchedEffect(etatPages.currentPage) {
            etatBarre.onglet = onglets[etatPages.currentPage]
        }

        // Et la barre redescend vers le pager. Un onglet demande est consomme
        // puis remis a nul, sinon le meme ordre se rejouerait a chaque
        // recomposition et bloquerait le glissement.
        //
        // snapshotFlow et NON LaunchedEffect(etatBarre.ongletDemande).
        //
        // Avec la demande pour cle, la remettre a nul CHANGEAIT la cle, ce qui
        // annulait la coroutine avant que animateScrollToPage n'ait commence :
        // toucher une icone effacait la demande sans jamais l'executer, et la
        // barre du bas ne naviguait plus du tout.
        //
        // Ici le collecteur vit le temps de la composition. Le passage a nul
        // emet une valeur de plus, que filterNotNull ecarte, et le defilement
        // va jusqu'au bout.
        LaunchedEffect(Unit) {
            snapshotFlow { etatBarre.ongletDemande }
                .filterNotNull()
                .collect { demande ->
                    etatBarre.ongletDemande = null
                    if (demande.ordinal != etatPages.currentPage) {
                        etatPages.animateScrollToPage(demande.ordinal)
                    }
                }
        }

        // LE FOND EST ICI, sur le conteneur, et non sur chaque page.
        //
        // Pose page par page, il partait avec elle : pendant un glissement le
        // decor se deplacait au lieu de rester en place, et la page qui
        // entrait -- encore en cours de composition -- laissait un vide non
        // peint, visible comme une grande zone claire.
        //
        // Sur le conteneur, il est peint une fois et ne bouge jamais : les
        // trois pages glissent par-dessus un decor immobile.
        HorizontalPager(
            state = etatPages,
            modifier = Modifier.fillMaxSize().fondKaloy()
        ) { page ->
            // Les ecrans sont instancies ici a chaque composition de la page.
            // Sans risque : ils ne portent aucun etat eux-memes, tout vit dans
            // les modeles de vue, qui eux sont partages.
            when (onglets[page]) {
                OngletPrincipal.ACCUEIL ->
                    HomeScreen(username = username, isVisitor = isVisitor).Content()

                OngletPrincipal.PLAYLIST -> PlaylistScreen().Content()

                OngletPrincipal.RADIO -> RadioScreen().Content()

                OngletPrincipal.MOI -> MoiScreen().Content()
            }
        }
    }
}
