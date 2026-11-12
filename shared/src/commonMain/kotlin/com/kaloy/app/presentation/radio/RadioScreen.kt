package com.kaloy.app.presentation.radio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.data.repository.RadioRepository
import com.kaloy.app.data.repository.TypeRadio
import com.kaloy.app.ui.components.EmptyState
import com.kaloy.app.ui.components.SectionHeader
import com.kaloy.app.ui.theme.*
import org.koin.compose.koinInject

class RadioScreen : Screen {

    @Composable
    override fun Content() {
        val navigateur = LocalNavigator.currentOrThrow
        val repository = koinInject<RadioRepository>()
        val fileAttente = koinInject<QueueManager>()
        val modeleVue = remember { RadioViewModel(repository, fileAttente) }

        DisposableEffect(Unit) { onDispose { modeleVue.dispose() } }

        // Dialogue d'erreur de lancement
        val etatLancement = modeleVue.etatLancement
        if (etatLancement is RadioViewModel.EtatLancement.Erreur) {
            AlertDialog(
                onDismissRequest = { modeleVue.dismissErreur() },
                containerColor = KaloyDarkCard,
                title = {
                    Text("Erreur", color = KaloyTextPrimary, fontWeight = FontWeight.SemiBold)
                },
                text = {
                    Text(etatLancement.message, color = KaloyTextSecondary)
                },
                confirmButton = {
                    TextButton(onClick = { modeleVue.dismissErreur() }) {
                        Text("OK", color = KaloyPurple)
                    }
                }
            )
        }

        Scaffold(
            containerColor = KaloyDarkBg
        ) { espaceInterieur ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espaceInterieur),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ---- En-tête gradient ----
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to KaloyPurple.copy(alpha = 0.8f),
                                    0.6f to KaloyPink.copy(alpha = 0.4f),
                                    1.0f to KaloyDarkBg
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Radio,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Radio",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Lancez la radio par genre ou par artiste",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                // ---- Carte recherche par genre ----
                item {
                    CarteRechercheGenre(modeleVue = modeleVue)
                }

                // ---- Séparateur OU ----
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = KaloyTextMuted.copy(alpha = 0.4f))
                        Text(
                            text = "  OU  ",
                            color = KaloyTextMuted,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = KaloyTextMuted.copy(alpha = 0.4f))
                    }
                }

                // ---- Carte recherche par artiste ----
                item {
                    CarteRechercheArtiste(modeleVue = modeleVue)
                }

                // ---- Bouton Lancer le mode Radio ----
                item {
                    AnimatedVisibility(
                        visible = modeleVue.sourceSelectionnee != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (etatLancement is RadioViewModel.EtatLancement.Chargement) {
                                CircularProgressIndicator(color = KaloyPurple)
                            } else {
                                Button(
                                    onClick = { modeleVue.lancerRadio(navigateur) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                                ) {
                                    Icon(Icons.Filled.Radio, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Lancer le mode Radio",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Section radios récentes ----
                item {
                    SectionHeader(
                        title = "Récemment écoutées",
                        icon = Icons.Filled.History
                    )
                }

                if (modeleVue.historique.isEmpty()) {
                    item {
                        EmptyState(
                            message = "Vous n'avez pas encore écouté de radio. Choisissez un genre ou un artiste pour commencer."
                        )
                    }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(modeleVue.historique) { entree ->
                                CarteRadioRecente(
                                    entree = entree,
                                    onClick = { modeleVue.relancerDepuisHistorique(entree, navigateur) }
                                )
                            }
                        }
                    }
                }

                // ---- Spacer bas ----
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun CarteRechercheGenre(modeleVue: RadioViewModel) {
    val estDesactive = modeleVue.modeActif == RadioViewModel.ModeRecherche.ARTISTE
    val aSelectionne = modeleVue.sourceSelectionnee is RadioViewModel.SourceRadio.ParGenre

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (estDesactive) KaloyDarkCard.copy(alpha = 0.5f) else KaloyDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Par genre musical",
                color = if (estDesactive) KaloyTextMuted else KaloyTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = modeleVue.texteGenre,
                onValueChange = { modeleVue.surSaisieGenre(it) },
                enabled = !estDesactive,
                placeholder = {
                    Text(
                        "Jazz, Afrobeat, Hip-hop…",
                        color = KaloyTextMuted
                    )
                },
                trailingIcon = if (aSelectionne || modeleVue.texteGenre.isNotBlank()) {
                    {
                        IconButton(onClick = { modeleVue.effacerSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = KaloyTextMuted)
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaloyPurple,
                    unfocusedBorderColor = KaloyTextMuted.copy(alpha = 0.4f),
                    disabledBorderColor = KaloyTextMuted.copy(alpha = 0.2f),
                    focusedTextColor = KaloyTextPrimary,
                    unfocusedTextColor = KaloyTextPrimary,
                    disabledTextColor = KaloyTextMuted,
                    cursorColor = KaloyPurple
                ),
                singleLine = true
            )

            // Suggestions de genres
            AnimatedVisibility(visible = modeleVue.suggestionsGenre.isNotEmpty() && !estDesactive) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    modeleVue.suggestionsGenre.forEach { genre ->
                        Text(
                            text = genre.name,
                            color = KaloyTextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { modeleVue.selectionnerGenre(genre) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider(color = KaloyTextMuted.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteRechercheArtiste(modeleVue: RadioViewModel) {
    val estDesactive = modeleVue.modeActif == RadioViewModel.ModeRecherche.GENRE
    val aSelectionne = modeleVue.sourceSelectionnee is RadioViewModel.SourceRadio.ParArtiste

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (estDesactive) KaloyDarkCard.copy(alpha = 0.5f) else KaloyDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Par artiste",
                color = if (estDesactive) KaloyTextMuted else KaloyTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = modeleVue.texteArtiste,
                onValueChange = { modeleVue.surSaisieArtiste(it) },
                enabled = !estDesactive,
                placeholder = {
                    Text(
                        "Nom de l'artiste…",
                        color = KaloyTextMuted
                    )
                },
                trailingIcon = if (aSelectionne || modeleVue.texteArtiste.isNotBlank()) {
                    {
                        IconButton(onClick = { modeleVue.effacerSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = KaloyTextMuted)
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaloyPurple,
                    unfocusedBorderColor = KaloyTextMuted.copy(alpha = 0.4f),
                    disabledBorderColor = KaloyTextMuted.copy(alpha = 0.2f),
                    focusedTextColor = KaloyTextPrimary,
                    unfocusedTextColor = KaloyTextPrimary,
                    disabledTextColor = KaloyTextMuted,
                    cursorColor = KaloyPurple
                ),
                singleLine = true
            )

            // Suggestions d'artistes
            AnimatedVisibility(visible = modeleVue.suggestionsArtiste.isNotEmpty() && !estDesactive) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    modeleVue.suggestionsArtiste.forEach { artiste ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { modeleVue.selectionnerArtiste(artiste) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (artiste.photoUrl != null) {
                                AsyncImage(
                                    model = artiste.photoUrl,
                                    contentDescription = artiste.stageName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(KaloyPurple, KaloyPink)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = artiste.stageName,
                                color = KaloyTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(color = KaloyTextMuted.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteRadioRecente(
    entree: com.kaloy.app.data.repository.RadioHistoriqueEntree,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                if (entree.type == TypeRadio.ARTISTE && entree.photoUrl != null) {
                    AsyncImage(
                        model = entree.photoUrl,
                        contentDescription = entree.nom,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(KaloyPurple.copy(alpha = 0.6f), KaloyPink.copy(alpha = 0.4f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                // Badge type
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(KaloyDarkSurface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (entree.type == TypeRadio.GENRE) "Genre" else "Artiste",
                        color = KaloyPurpleLight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = entree.nom,
                color = KaloyTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
