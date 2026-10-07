package com.kaloy.app.presentation.calendrier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaloy.app.core.util.Calendrier
import com.kaloy.app.ui.theme.*

/**
 * Grille mensuelle des concerts, avec une pastille sur les jours occupes.
 *
 * Extraite de la fiche artiste pour servir aussi a l'ecran « Mon calendrier » :
 * deux copies auraient diverge des la premiere retouche.
 *
 * Une seule pastille par date, quel que soit le nombre de concerts — le detail
 * se lit ailleurs.
 */
@Composable
fun GrilleCalendrier(
    annee: Int,
    mois: Int,
    aujourdHui: String,
    datesOccupees: Set<String>,
    dateSelectionnee: String?,
    onMoisPrecedent: () -> Unit,
    onMoisSuivant: () -> Unit,
    onJourClique: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMoisPrecedent) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Mois précédent",
                    tint = KaloyTextPrimary
                )
            }

            Text(
                text = "${Calendrier.nomDuMois(mois)} $annee",
                style = MaterialTheme.typography.titleMedium,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onMoisSuivant) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Mois suivant",
                    tint = KaloyTextPrimary
                )
            }
        }

        // Initiales des jours, semaine commencant le lundi.
        Row(modifier = Modifier.fillMaxWidth()) {
            Calendrier.JOURS_SEMAINE.forEach { initiale ->
                Text(
                    text = initiale,
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // La grille est assemblee ligne par ligne. Un LazyVerticalGrid ne peut
        // pas etre imbrique dans une LazyColumn : deux defilements verticaux
        // l'un dans l'autre sont interdits.
        val decalage = Calendrier.jourSemaineDuPremier(annee, mois)
        val nombreJours = Calendrier.joursDansMois(annee, mois)
        val nombreSemaines = (decalage + nombreJours + 6) / 7

        for (semaine in 0 until nombreSemaines) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (position in 0 until 7) {
                    // Les cases avant le 1er ou apres le dernier jour restent
                    // vides : elles alignent la grille sur les jours de la semaine.
                    val jour = semaine * 7 + position - decalage + 1
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (jour in 1..nombreJours) {
                            val date = Calendrier.formater(annee, mois, jour)
                            CaseJour(
                                jour = jour,
                                estAujourdHui = date == aujourdHui,
                                estSelectionnee = date == dateSelectionnee,
                                aUnEvenement = date in datesOccupees,
                                // Comparaison de chaines : au format AAAA-MM-JJ,
                                // l'ordre alphabetique est l'ordre chronologique.
                                estPasse = date < aujourdHui,
                                onClick = { onJourClique(date) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LegendeCalendrier()
    }
}

@Composable
private fun CaseJour(
    jour: Int,
    estAujourdHui: Boolean,
    estSelectionnee: Boolean,
    aUnEvenement: Boolean,
    estPasse: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (estSelectionnee) KaloyPurple.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$jour",
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                estAujourdHui -> KaloyCyan
                estPasse -> KaloyTextMuted
                else -> KaloyTextPrimary
            },
            fontWeight = if (estAujourdHui || estSelectionnee) FontWeight.Bold else FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(3.dp))

        // La pastille garde sa place meme quand il n'y a rien : sans cela, les
        // chiffres des jours occupes et des jours vides ne seraient pas alignes.
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !aUnEvenement -> Color.Transparent
                        estPasse -> KaloyTextMuted
                        else -> KaloyPurple
                    }
                )
        )
    }
}

@Composable
private fun LegendeCalendrier() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EntreeLegende(couleur = KaloyPurple, libelle = "À venir")
        Spacer(modifier = Modifier.width(16.dp))
        EntreeLegende(couleur = KaloyTextMuted, libelle = "Passé")
    }
}

@Composable
private fun EntreeLegende(couleur: Color, libelle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(couleur)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = libelle,
            style = MaterialTheme.typography.bodySmall,
            color = KaloyTextMuted
        )
    }
}
