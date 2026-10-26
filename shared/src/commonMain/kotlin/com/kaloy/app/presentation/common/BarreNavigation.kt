package com.kaloy.app.presentation.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kaloy.app.ui.theme.KaloyDarkSurface
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyTextMuted

/** Les trois destinations de la barre du bas. */
enum class OngletPrincipal(val libelle: String) {
    ACCUEIL("Accueil"),
    RECHERCHE("Recherche"),
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
    onRecherche: () -> Unit,
    onMoi: () -> Unit
) {
    NavigationBar(containerColor = KaloyDarkSurface) {
        Onglet(OngletPrincipal.ACCUEIL, ongletActif, Icons.Default.Home, "Accueil", onAccueil)
        Onglet(OngletPrincipal.RECHERCHE, ongletActif, Icons.Default.Search, "Recherche", onRecherche)
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
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = KaloyPurple,
            selectedTextColor = KaloyPurple,
            indicatorColor = KaloyPurple.copy(alpha = 0.15f),
            unselectedIconColor = KaloyTextMuted,
            unselectedTextColor = KaloyTextMuted
        )
    )
}
