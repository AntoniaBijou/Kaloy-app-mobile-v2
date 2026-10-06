package com.kaloy.app.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

/**
 * Lecteur du mini-player, cote iOS.
 *
 * Pendant de [AndroidMediaPlayerController]. Il expose un etat unique plutot
 * que plusieurs flux separes, parce que l'interface n'affiche qu'une chose a
 * la fois : en chargement, en lecture, en pause ou en erreur.
 *
 * NON VERIFIE A L'EXECUTION : compile, mais jamais lance faute de macOS.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMediaPlayerController : MediaPlayerController {

    private val _state = MutableStateFlow<MediaPlayerStatus>(MediaPlayerStatus.Idle)
    override val state: StateFlow<MediaPlayerStatus> = _state.asStateFlow()

    private var lecteur: AVPlayer? = null
    private var observateur: Any? = null
    private var positionMs: Long = 0L
    private var dureeMs: Long = 0L

    override fun setMedia(url: String, metadata: MediaMetadata) {
        val adresse = NSURL.URLWithString(url)
        if (adresse == null) {
            _state.value = MediaPlayerStatus.Error("Adresse de lecture invalide.")
            return
        }

        liberer()
        _state.value = MediaPlayerStatus.Loading

        configurerSessionAudio()

        val nouveau = AVPlayer(uRL = adresse)
        lecteur = nouveau
        positionMs = 0L
        // La duree annoncee par les metadonnees sert de valeur d'attente, le
        // temps qu'AVPlayer lise l'en-tete du flux et donne la vraie.
        dureeMs = metadata.durationMs

        observateur = nouveau.addPeriodicTimeObserverForInterval(
            interval = CMTimeMakeWithSeconds(0.5, 1000),
            queue = dispatch_get_main_queue(),
            usingBlock = { temps ->
                val secondes = CMTimeGetSeconds(temps)
                if (!secondes.isNaN()) positionMs = (secondes * 1000).toLong()

                if (dureeMs <= 0L) {
                    nouveau.currentItem?.let { element ->
                        val totalSecondes = CMTimeGetSeconds(element.duration)
                        if (!totalSecondes.isNaN() && totalSecondes > 0) {
                            dureeMs = (totalSecondes * 1000).toLong()
                        }
                    }
                }
                rafraichirEtat()
            }
        )
    }

    override fun play() {
        val courant = lecteur ?: return
        courant.play()
        _state.value = MediaPlayerStatus.Playing(positionMs, dureeMs)
    }

    override fun pause() {
        val courant = lecteur ?: return
        courant.pause()
        _state.value = MediaPlayerStatus.Paused(positionMs, dureeMs)
    }

    override fun seekTo(positionMs: Long) {
        lecteur?.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
        this.positionMs = positionMs
        rafraichirEtat()
    }

    override fun release() {
        liberer()
        _state.value = MediaPlayerStatus.Idle
    }

    /** Conserve la distinction lecture / pause : seules les valeurs changent. */
    private fun rafraichirEtat() {
        _state.value = when (_state.value) {
            is MediaPlayerStatus.Paused -> MediaPlayerStatus.Paused(positionMs, dureeMs)
            else -> MediaPlayerStatus.Playing(positionMs, dureeMs)
        }
    }

    private fun configurerSessionAudio() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }

    private fun liberer() {
        val courant = lecteur ?: return
        observateur?.let { courant.removeTimeObserver(it) }
        observateur = null
        courant.pause()
        lecteur = null
        positionMs = 0L
        dureeMs = 0L
    }
}
