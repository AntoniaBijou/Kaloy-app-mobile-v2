package com.kaloy.app.di

import com.kaloy.app.core.audio.AndroidAudioPlayerController
import com.kaloy.app.core.audio.AudioPlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    factory<AudioPlayerController> { AndroidAudioPlayerController(androidContext()) }
}
