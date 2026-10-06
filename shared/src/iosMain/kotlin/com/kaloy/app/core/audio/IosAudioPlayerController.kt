package com.kaloy.app.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
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
 * Lecture audio sur iOS, via AVPlayer.
 *
 * Pendant de [AndroidAudioPlayerController], qui s'appuie sur Media3. AVPlayer
 * lit indifferemment un fichier local et un flux HTTP, ce qui suffit au
 * catalogue : les chansons sont servies par le backend.
 *
 * NON VERIFIE A L'EXECUTION. Ce fichier compile — la chaine Kotlin/Native
 * fonctionne depuis Windows — mais lancer l'application iOS demande Xcode sur
 * macOS. Tout ce qui releve du comportement reel (son effectivement audible,
 * interruption par un appel, lecture en arriere-plan) reste a verifier.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayerController : AudioPlayerController {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var lecteur: AVPlayer? = null

    /** Jeton de l'observateur de progression, a retirer avant de liberer le lecteur. */
    private var observateur: Any? = null

    override fun play(url: String) {
        val adresse = NSURL.URLWithString(url)
        if (adresse == null) {
            _errorMessage.value = "Adresse de lecture invalide."
            return
        }

        libererLecteur()
        configurerSessionAudio()

        val nouveau = AVPlayer(uRL = adresse)
        lecteur = nouveau
        observerProgression(nouveau)

        nouveau.play()
        _isPlaying.value = true
        _statusMessage.value = "Lecture en cours"
        _errorMessage.value = null
    }

    override fun pause() {
        lecteur?.pause()
        _isPlaying.value = false
        _statusMessage.value = "En pause"
    }

    override fun resume() {
        lecteur?.play()
        _isPlaying.value = true
        _statusMessage.value = "Lecture en cours"
    }

    override fun seekTo(positionMs: Long) {
        // CMTime est un rationnel : 1000 pour denominateur donne la milliseconde,
        // la precision dans laquelle le reste de l'application raisonne.
        lecteur?.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
        _currentPositionMs.value = positionMs
    }

    override fun release() {
        libererLecteur()
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _statusMessage.value = ""
    }

    /**
     * Sans categorie Playback, iOS coupe le son quand l'interrupteur silencieux
     * est active — comportement correct pour un bip de notification, absurde
     * pour une application musicale.
     */
    private fun configurerSessionAudio() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }

    /** Une mise a jour par demi-seconde : assez fluide pour une barre de progression. */
    private fun observerProgression(lecteurCourant: AVPlayer) {
        observateur = lecteurCourant.addPeriodicTimeObserverForInterval(
            interval = CMTimeMakeWithSeconds(0.5, 1000),
            queue = dispatch_get_main_queue(),
            usingBlock = { temps ->
                val secondes = CMTimeGetSeconds(temps)
                if (!secondes.isNaN()) {
                    _currentPositionMs.value = (secondes * 1000).toLong()
                }
                lireDuree(lecteurCourant)
            }
        )
    }

    /**
     * La duree n'est pas connue au moment du play : AVPlayer doit d'abord lire
     * l'en-tete du flux. On la relit donc a chaque tick tant qu'elle est absente.
     */
    private fun lireDuree(lecteurCourant: AVPlayer) {
        if (_durationMs.value > 0L) return
        val element: AVPlayerItem = lecteurCourant.currentItem ?: return
        val secondes = CMTimeGetSeconds(element.duration)
        if (!secondes.isNaN() && secondes > 0) {
            _durationMs.value = (secondes * 1000).toLong()
        }
    }

    private fun libererLecteur() {
        val courant = lecteur ?: return
        observateur?.let { courant.removeTimeObserver(it) }
        observateur = null
        courant.pause()
        lecteur = null
    }
}
