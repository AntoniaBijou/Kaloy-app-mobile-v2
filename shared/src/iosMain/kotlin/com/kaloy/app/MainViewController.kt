package com.kaloy.app

import androidx.compose.ui.window.ComposeUIViewController
import com.kaloy.app.di.demarrerKoinIos

/**
 * Koin n'a pas d'API publique stable pour savoir s'il tourne deja : on garde
 * donc l'information ici. MainViewController etant l'unique point d'entree de
 * l'application iOS, ce drapeau suffit.
 */
private var koinDemarre = false

/**
 * Point d'entree iOS, appele depuis ContentView.swift.
 *
 * Koin est demarre ici, avant la composition. SwiftUI peut recreer la vue —
 * changement d'orientation, retour d'arriere-plan — et un second startKoin
 * leverait une exception : d'ou le garde-fou.
 */
fun MainViewController() = ComposeUIViewController {
    if (!koinDemarre) {
        demarrerKoinIos()
        koinDemarre = true
    }
    App()
}
