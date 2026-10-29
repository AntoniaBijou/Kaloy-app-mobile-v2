package com.kaloy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kaloy.app.core.audio.AudioPlayerController
import com.kaloy.app.di.androidModule
import com.kaloy.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val koin = startKoin {
            androidContext(this@MainActivity)
            modules(appModule, androidModule)
        }.koin
        super.onCreate(savedInstanceState)

        // Establish the Media3 service connection before the user requests a track.
        koin.get<AudioPlayerController>()

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}