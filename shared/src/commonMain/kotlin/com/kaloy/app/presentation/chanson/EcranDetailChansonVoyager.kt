package com.kaloy.app.presentation.chanson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.model.Song
import com.kaloy.app.ui.components.ErrorState
import com.kaloy.app.ui.components.LoadingIndicator
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

class DetailChansonViewModel(private val idChanson: Long) : ViewModel() {
    private val api = KaloyApi()

    var chanson by mutableStateOf<Song?>(null)
        private set
    var enChargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    init {
        chargerChanson()
    }

    fun chargerChanson() {
        viewModelScope.launch {
            enChargement = true
            erreur = null
            try {
                val resultatChanson = api.getSongById(idChanson)
                chanson = resultatChanson.data
            } catch (e: Exception) {
                erreur = "Impossible de charger la chanson: ${e.message}"
            } finally {
                enChargement = false
            }
        }
    }
}

// -----------------------------------------------------------------
// Composant pour simuler un lecteur YouTube encapsulé (MVP ou WebView future)
// -----------------------------------------------------------------
@Composable
fun LecteurVideoYouTube(url: String) {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "▶ Lecteur YouTube",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { uriHandler.openUri(url) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Ouvrir dans le navigateur", color = Color.White)
            }
        }
    }
}

// -----------------------------------------------------------------
// Écran détail chanson
// -----------------------------------------------------------------
data class EcranDetailChansonVoyager(val idChanson: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val modeleVue: DetailChansonViewModel = viewModel(key = "chanson_$idChanson") {
            DetailChansonViewModel(idChanson)
        }

        when {
            modeleVue.enChargement -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            modeleVue.erreur != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = modeleVue.erreur!!,
                        onRetry = { modeleVue.chargerChanson() }
                    )
                }
            }
            modeleVue.chanson != null -> {
                val chansonDetail = modeleVue.chanson!!
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(scrollState)
                ) {
                    // ---- Pochette et Header (avec bouton de lecture local mocké) ----
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        KaloyPurple.copy(alpha = 0.7f),
                                        KaloyPink.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Bouton de retour en haut
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .safeContentPadding(),
                            contentAlignment = Alignment.TopStart
                        ) {
                            IconButton(
                                onClick = { navigateur.pop() },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                            ) {
                                Text("←", fontSize = 24.sp, color = Color.White)
                            }
                        }

                        // Contenu central (pochette simulée et play audio)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(KaloyDarkElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("♪", fontSize = 80.sp, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Info rapide
                            Text(
                                text = chansonDetail.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = KaloyTextPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = chansonDetail.artist?.stageName ?: "Artiste inconnu",
                                style = MaterialTheme.typography.titleMedium,
                                color = KaloyTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // ---- Actions & Métadonnées ----
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Bouton audio
                            Button(
                                onClick = { /* TODO: Lecture audio interne */ },
                                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("▶  Écouter (Audio)")
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = { /* TODO: Like / Ajouter playlist */ },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaloyTextPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("❤  Ajouter")
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Lecteur vidéo YouTube (S'il y a un lien vidéo)
                        if (!chansonDetail.videoUrl.isNullOrBlank()) {
                            Text(
                                text = "Clip Vidéo",
                                style = MaterialTheme.typography.titleMedium,
                                color = KaloyTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LecteurVideoYouTube(url = chansonDetail.videoUrl)
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Autres Métadonnées
                        Text(
                            text = "Informations",
                            style = MaterialTheme.typography.titleMedium,
                            color = KaloyTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Album", color = KaloyTextSecondary)
                            Text(chansonDetail.album?.title ?: "Single", color = KaloyTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Durée", color = KaloyTextSecondary)
                            val min = (chansonDetail.durationSeconds ?: 0) / 60
                            val sec = (chansonDetail.durationSeconds ?: 0) % 60
                            Text("$min:${sec.toString().padStart(2, '0')}", color = KaloyTextPrimary)
                        }
                        if (chansonDetail.authorComposer != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Auteur / Compositeur", color = KaloyTextSecondary)
                                Text(chansonDetail.authorComposer, color = KaloyTextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Paroles
                        Text(
                            text = "Paroles",
                            style = MaterialTheme.typography.titleMedium,
                            color = KaloyTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!chansonDetail.lyrics.isNullOrBlank()) {
                            Text(
                                text = chansonDetail.lyrics,
                                style = MaterialTheme.typography.bodyMedium,
                                color = KaloyTextSecondary,
                                lineHeight = 24.sp
                            )
                        } else {
                            Text(
                                text = "Les paroles ne sont pas disponibles pour cette chanson.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KaloyTextMuted,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
