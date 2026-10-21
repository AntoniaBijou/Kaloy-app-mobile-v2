package com.kaloy.app.di

import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.IosAudioPlayerController
import com.kaloy.app.core.audio.IosMediaPlayerController
import com.kaloy.app.core.audio.MediaPlayerController
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Dependances propres a iOS.
 *
 * Pendant de androidModule. Les deux lecteurs y figurent avec les memes portees
 * que cote Android : le lecteur audio reste unique afin de survivre a la
 * navigation et de partager son etat entre le lecteur complet et le mini-player.
 */
val iosModule = module {
    single<AudioPlayerController> { IosAudioPlayerController() }
    single<MediaPlayerController> { IosMediaPlayerController() }
}

/**
 * Demarre Koin cote iOS.
 *
 * Sans cet appel, l'application compilait et se lancait, mais tombait des le
 * premier koinInject. Pire : KaloyApi lit le jeton dans un try/catch qui
 * renvoie null en cas d'echec, donc toutes les requetes partaient sans
 * authentification et revenaient en 403, sans message d'erreur.
 *
 * Appele depuis MainViewController, c'est-a-dire a la creation de la vue
 * Compose, et non depuis Swift : le point d'entree reste ainsi unique et
 * partage avec le reste du code Kotlin.
 */
fun demarrerKoinIos() {
    startKoin {
        modules(appModule, iosModule)
    }
}
