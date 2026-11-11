package com.kaloy.app.di

import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.IosAudioPlayerController
import com.kaloy.app.core.audio.IosMediaPlayerController
import com.kaloy.app.core.audio.MediaPlayerController
import com.kaloy.app.core.offline.IosOfflineFileStore
import com.kaloy.app.core.offline.OfflineFileStore
import com.kaloy.app.core.session.IosSecureCredentialStore
import com.kaloy.app.core.session.SecureCredentialStore
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
    single<SecureCredentialStore> { IosSecureCredentialStore() }
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
