package com.kaloy.app.di

import com.kaloy.app.core.audio.AndroidAudioPlayerController
import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.core.audio.AndroidMediaPlayerController
import com.kaloy.app.core.audio.MediaPlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    factory<AudioPlayerController> { AndroidAudioPlayerController(androidContext()) }
    single<MediaPlayerController> { AndroidMediaPlayerController(androidContext()) }
}
