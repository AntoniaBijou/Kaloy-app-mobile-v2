package com.kaloy.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.common.BarreDuBas
import com.kaloy.app.ui.theme.KaloyTheme

@Composable
fun App() {
    KaloyTheme {
        // La barre du bas est posee ICI, au-dessus du Navigator, et non dans
        // chaque ecran. Avant, deux ecrans sur vingt-cinq la posaient : la
        // barre de lecture n'existait que sur l'accueil, et la barre de
        // navigation disparaissait sur l'onglet « Moi ».
        //
        // L'ecran courant occupe toute la place restante, la barre prend la
        // sienne en dessous. Un Column et non une superposition : la barre ne
        // doit pas recouvrir le bas du contenu, sinon le dernier element
        // d'une liste reste inatteignable.
        Navigator(screen = WelcomeScreen()) { navigateur ->
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    CurrentScreen()
                }
                BarreDuBas(navigateur)
            }
        }
    }
}
