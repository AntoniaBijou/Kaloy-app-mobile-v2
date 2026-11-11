package com.kaloy.app.presentation.radio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Genre
import com.kaloy.app.data.repository.RadioHistoriqueEntree
import com.kaloy.app.data.repository.RadioRepository
import com.kaloy.app.data.repository.TypeRadio
import com.kaloy.app.presentation.lecteur.LecteurScreen
import com.kaloy.app.presentation.lecteur.ModeEcoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RadioViewModel(
    private val repository: RadioRepository,
    private val queueManager: QueueManager
) {
    sealed class SourceRadio {
        abstract val id: Long

        data class ParGenre(val genre: Genre) : SourceRadio() {
            override val id: Long get() = genre.id
        }

        data class ParArtiste(val artiste: Artist) : SourceRadio() {
            override val id: Long get() = artiste.id
        }
    }

    enum class ModeRecherche { GENRE, ARTISTE }

    sealed class EtatLancement {
        object Idle : EtatLancement()
        object Chargement : EtatLancement()
        data class Erreur(val message: String) : EtatLancement()
    }

    var texteGenre by mutableStateOf(""); private set
    var texteArtiste by mutableStateOf(""); private set
    var modeActif by mutableStateOf<ModeRecherche?>(null); private set
    var suggestionsGenre by mutableStateOf<List<Genre>>(emptyList()); private set
    var suggestionsArtiste by mutableStateOf<List<Artist>>(emptyList()); private set
    var chargeSuggestions by mutableStateOf(false); private set
    var sourceSelectionnee by mutableStateOf<SourceRadio?>(null); private set
    var etatLancement by mutableStateOf<EtatLancement>(EtatLancement.Idle); private set
    var historique by mutableStateOf<List<RadioHistoriqueEntree>>(emptyList()); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rechercheJob: Job? = null

    init {
        historique = repository.getHistorique()
    }

    fun surSaisieGenre(texte: String) {
        texteGenre = texte
        modeActif = if (texte.isNotBlank()) ModeRecherche.GENRE else null
        if (sourceSelectionnee is SourceRadio.ParGenre) sourceSelectionnee = null
        lancerRechercheGenre(texte)
    }

    fun surSaisieArtiste(texte: String) {
        texteArtiste = texte
        modeActif = if (texte.isNotBlank()) ModeRecherche.ARTISTE else null
        if (sourceSelectionnee is SourceRadio.ParArtiste) sourceSelectionnee = null
        lancerRechercheArtiste(texte)
    }

    private fun lancerRechercheGenre(query: String) {
        rechercheJob?.cancel()
        if (query.isBlank()) {
            suggestionsGenre = emptyList()
            return
        }
        rechercheJob = scope.launch {
            delay(300)
            chargeSuggestions = true
            try {
                suggestionsGenre = repository.rechercherGenres(query)
            } catch (_: Exception) {
                suggestionsGenre = emptyList()
            } finally {
                chargeSuggestions = false
            }
        }
    }

    private fun lancerRechercheArtiste(query: String) {
        rechercheJob?.cancel()
        if (query.isBlank()) {
            suggestionsArtiste = emptyList()
            return
        }
        rechercheJob = scope.launch {
            delay(300)
            chargeSuggestions = true
            try {
                suggestionsArtiste = repository.rechercherArtistes(query)
            } catch (_: Exception) {
                suggestionsArtiste = emptyList()
            } finally {
                chargeSuggestions = false
            }
        }
    }

    fun selectionnerGenre(genre: Genre) {
        texteGenre = genre.name
        sourceSelectionnee = SourceRadio.ParGenre(genre)
        modeActif = ModeRecherche.GENRE
        suggestionsGenre = emptyList()
    }

    fun selectionnerArtiste(artiste: Artist) {
        texteArtiste = artiste.stageName
        sourceSelectionnee = SourceRadio.ParArtiste(artiste)
        modeActif = ModeRecherche.ARTISTE
        suggestionsArtiste = emptyList()
    }

    fun effacerSelection() {
        texteGenre = ""
        texteArtiste = ""
        modeActif = null
        sourceSelectionnee = null
        suggestionsGenre = emptyList()
        suggestionsArtiste = emptyList()
    }

    @OptIn(ExperimentalTime::class)
    fun lancerRadio(navigateur: Navigator) {
        val source = sourceSelectionnee ?: return
        scope.launch {
            etatLancement = EtatLancement.Chargement
            try {
                val chansons = when (source) {
                    is SourceRadio.ParGenre -> repository.getChansonsParGenre(source.genre.id)
                    is SourceRadio.ParArtiste -> repository.getChansonsParArtiste(source.artiste.id)
                }
                val memeles = chansons.shuffled()
                if (memeles.isEmpty()) {
                    etatLancement = EtatLancement.Erreur("Aucune chanson disponible pour cette sélection.")
                    return@launch
                }
                val premiereChanson = memeles.first()
                queueManager.setContext(memeles, premiereChanson)

                val entree = when (source) {
                    is SourceRadio.ParGenre -> RadioHistoriqueEntree(
                        type = TypeRadio.GENRE,
                        sourceId = source.genre.id,
                        nom = source.genre.name,
                        photoUrl = null,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                    is SourceRadio.ParArtiste -> RadioHistoriqueEntree(
                        type = TypeRadio.ARTISTE,
                        sourceId = source.artiste.id,
                        nom = source.artiste.stageName,
                        photoUrl = source.artiste.photoUrl,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                }
                repository.sauvegarderHistorique(entree)
                historique = repository.getHistorique()
                etatLancement = EtatLancement.Idle
                navigateur.push(LecteurScreen(songId = premiereChanson.id, startMode = ModeEcoute.AUDIO))
            } catch (e: Exception) {
                etatLancement = EtatLancement.Erreur(e.message ?: "Erreur lors du lancement.")
            }
        }
    }

    fun relancerDepuisHistorique(entree: RadioHistoriqueEntree, navigateur: Navigator) {
        when (entree.type) {
            TypeRadio.GENRE -> {
                selectionnerGenre(Genre(id = entree.sourceId, name = entree.nom))
                lancerRadio(navigateur)
            }
            TypeRadio.ARTISTE -> {
                selectionnerArtiste(Artist(id = entree.sourceId, stageName = entree.nom, photoUrl = entree.photoUrl))
                lancerRadio(navigateur)
            }
        }
    }

    fun dismissErreur() {
        etatLancement = EtatLancement.Idle
    }

    fun dispose() {
        scope.cancel()
    }
}
