package com.kaloy.app.di

import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.IosAudioPlayerController
import com.kaloy.app.core.audio.IosMediaPlayerController
import com.kaloy.app.core.audio.MediaPlayerController
import com.kaloy.app.core.offline.IosOfflineFileStore
import com.kaloy.app.core.offline.OfflineFileStore
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Dependances propres a iOS.
 *
 * Pendant de androidModule, avec les memes portees.
 *
 * Le commentaire precedent decrivait un montage qui n'a jamais existe : il
 * annoncait un lecteur recree pour l'ecran chanson et un lecteur unique pour
 * le mini-player. En realite la barre du bas ET l'ecran lecteur injectent le
 * meme type, AudioPlayerController, et MediaPlayerController n'est injecte
 * nulle part -- le controleur Android cree le sien directement.
 */
val iosModule = module {
    // UN SEUL controleur pour toute l'application, et non un par injection.
    //
    // En factory, la barre du bas et l'ecran lecteur en recevaient chacun un
    // neuf -- et un de plus a chaque ouverture du lecteur. Trois consequences :
    //
    //   - la piste en cours est une variable d'instance : un controleur neuf
    //     la croit vide, donc rouvrir le lecteur relancait la chanson depuis
    //     le debut au lieu de reprendre ou elle en etait ;
    //   - chaque instance ouvre une connexion au service et lance une boucle
    //     de sondage de 250 ms que rien n'arrete, personne n'appelant
    //     release() nulle part dans l'application ;
    //   - cote iOS, chaque instance possede son propre AVPlayer : le bouton
    //     de la barre ne commandait pas ce que l'on entendait.
    //
    // Cote Android le defaut passait presque inapercu, toutes les instances
    // se branchant sur le meme service Media3 : seul l'etat local divergeait.
    single<AudioPlayerController> { IosAudioPlayerController() }
    single<MediaPlayerController> { IosMediaPlayerController() }
    single<OfflineFileStore> { IosOfflineFileStore() }
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
