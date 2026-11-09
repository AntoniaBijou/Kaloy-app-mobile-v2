package com.kaloy.app.di

import com.kaloy.app.core.audio.AndroidAudioPlayerController
import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.AndroidMediaPlayerController
import com.kaloy.app.core.audio.MediaPlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
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
    single<AudioPlayerController> { AndroidAudioPlayerController(androidContext()) }
    single<MediaPlayerController> { AndroidMediaPlayerController(androidContext()) }
}
