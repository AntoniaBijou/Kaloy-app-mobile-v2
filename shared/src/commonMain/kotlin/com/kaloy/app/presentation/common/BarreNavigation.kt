package com.kaloy.app.presentation.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kaloy.app.ui.theme.KaloyDarkSurface
import com.kaloy.app.ui.theme.KaloyOrange
import com.kaloy.app.ui.theme.KaloyTextMuted

/** Les quatre destinations de la barre du bas. */
enum class OngletPrincipal(val libelle: String) {
    ACCUEIL("Accueil"),
    PLAYLIST("Playlist"),
    RADIO("Radio"),
    MOI("Moi")
}

/**
 * Barre de navigation principale.
 *
 * Elle a longtemps vecu dans le Scaffold de l'accueil. La recherche et le
 * profil etant des ecrans empiles par-dessus, ils la recouvraient : elle
 * disparaissait des qu'on quittait l'accueil.
 *
 * L'onglet actif est passe par l'ecran qui pose la barre, il n'est pas garde
 * en memoire ici. C'est ce qui corrige l'ancien travers ou « Accueil » restait
 * allume pendant qu'on etait ailleurs : un `remember` dans l'accueil n'etait
 * jamais mis a jour par les ecrans suivants.
 *
 * Chaque ecran fournit ses propres actions, et passe une action vide pour son
 * propre onglet.
 */
@Composable
fun BarreNavigationPrincipale(
    ongletActif: OngletPrincipal,
    onAccueil: () -> Unit,
    onPlaylist: () -> Unit,
    onRadio: () -> Unit,
    onMoi: () -> Unit
) {
    NavigationBar(containerColor = KaloyDarkSurface) {
        Onglet(OngletPrincipal.ACCUEIL, ongletActif, Icons.Default.Home, "Accueil", onAccueil)
        Onglet(OngletPrincipal.PLAYLIST, ongletActif, Icons.Default.QueueMusic, "Playlist", onPlaylist)
        Onglet(OngletPrincipal.RADIO, ongletActif, Icons.Default.Radio, "Radio", onRadio)
        Onglet(OngletPrincipal.MOI, ongletActif, Icons.Default.Person, "Mon profil", onMoi)
    }
}

@Composable
private fun RowScope.Onglet(
    onglet: OngletPrincipal,
    actif: OngletPrincipal,
    icone: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = onglet == actif,
        onClick = onClick,
        icon = { Icon(icone, contentDescription = description) },
        label = { Text(onglet.libelle) },
        // L'onglet actif est en ORANGE et non dans la couleur principale.
        //
        // Mesure : l'indigo #32317F sur le fond de la barre #191A3E donne un
        // contraste de 1,49 -- la meme couleur a l'oeil. Le defaut existait
        // deja avec l'ancien violet, a 2,99, soit sous le seuil de 3 : il
        // etait seulement moins visible. La nouvelle palette l'a rendu franc,
        // et il compte davantage depuis que cette barre s'affiche sur les
        // trois onglets et non sur le seul accueil.
        //
        // L'orange monte a 4,68, et c'est l'usage que la maquette lui reserve :
        // dans « type de background.jpeg », l'element de navigation actif et
        // la pastille de filtre selectionnee sont orange.
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = KaloyOrange,
            selectedTextColor = KaloyOrange,
            indicatorColor = KaloyOrange.copy(alpha = 0.15f),
            unselectedIconColor = KaloyTextMuted,
            unselectedTextColor = KaloyTextMuted
        )
    )
}
