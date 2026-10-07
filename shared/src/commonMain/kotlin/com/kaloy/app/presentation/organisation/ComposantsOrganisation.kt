package com.kaloy.app.presentation.organisation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaloy.app.core.util.Calendrier
import com.kaloy.app.core.util.aujourdHuiLocal
import com.kaloy.app.data.api.KaloyApi
import com.kaloy.app.data.api.ArtistSearch
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Venue
import com.kaloy.app.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================
// Saisie d'une date
//
// Le projet n'a pas de DatePicker : la grille ecrite pour le calendrier de la
// fiche artiste sert ici de selecteur. Taper « 2027-03-12 » au clavier sur un
// telephone serait penible et produirait des dates invalides.
// ============================================================

@Composable
fun SelecteurDate(
    titre: String,
    dateChoisie: String?,
    /** Bornes facultatives, au format AAAA-MM-JJ : un creneau doit tomber dans son evenement. */
    dateMin: String? = null,
    dateMax: String? = null,
    onChoisir: (String) -> Unit,
    onFermer: () -> Unit
) {
    // On ouvre sur le mois de la date deja choisie, sinon sur celui de la borne
    // basse, sinon sur le mois courant : dans tous les cas la premiere chose
    // affichee contient une date valide.
    val depart = Calendrier.decouper(dateChoisie ?: dateMin ?: aujourdHuiLocal())
        ?: Triple(2026, 1, 1)
    var annee by remember { mutableStateOf(depart.first) }
    var mois by remember { mutableStateOf(depart.second) }

    AlertDialog(
        onDismissRequest = onFermer,
        containerColor = KaloyDarkCard,
        title = { Text(titre, color = KaloyTextPrimary) },
        confirmButton = {
            TextButton(onClick = onFermer) { Text("Fermer", color = KaloyTextSecondary) }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val precedent = Calendrier.moisPrecedent(annee, mois)
                        annee = precedent.first; mois = precedent.second
                    }) { Text("‹", fontSize = 22.sp, color = KaloyTextPrimary) }

                    Text(
                        text = "${Calendrier.nomDuMois(mois)} $annee",
                        color = KaloyTextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = {
                        val suivant = Calendrier.moisSuivant(annee, mois)
                        annee = suivant.first; mois = suivant.second
                    }) { Text("›", fontSize = 22.sp, color = KaloyTextPrimary) }
                }

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

                val decalage = Calendrier.jourSemaineDuPremier(annee, mois)
                val nombreJours = Calendrier.joursDansMois(annee, mois)
                val semaines = (decalage + nombreJours + 6) / 7

                for (semaine in 0 until semaines) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (position in 0 until 7) {
                            val jour = semaine * 7 + position - decalage + 1
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (jour in 1..nombreJours) {
                                    val date = Calendrier.formater(annee, mois, jour)
                                    // Comparaison de chaines : au format AAAA-MM-JJ,
                                    // l'ordre alphabetique est l'ordre chronologique.
                                    val autorisee = (dateMin == null || date >= dateMin) &&
                                        (dateMax == null || date <= dateMax)
                                    CaseJourSelectionnable(
                                        jour = jour,
                                        choisie = date == dateChoisie,
                                        autorisee = autorisee,
                                        onClick = { if (autorisee) onChoisir(date) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CaseJourSelectionnable(
    jour: Int,
    choisie: Boolean,
    autorisee: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(if (choisie) KaloyPurple else Color.Transparent)
            .clickable(enabled = autorisee, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$jour",
            style = MaterialTheme.typography.bodyMedium,
            // Une date hors bornes reste lisible mais eteinte : la masquer
            // laisserait un trou dans la grille, plus deroutant qu'utile.
            color = if (autorisee) KaloyTextPrimary else KaloyTextMuted.copy(alpha = 0.4f),
            fontWeight = if (choisie) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ============================================================
// Choix d'un artiste a inviter
// ============================================================

@Composable
fun SelecteurArtiste(
    artisteChoisi: Artist?,
    onChoisir: (Artist) -> Unit
) {
    val api = remember { KaloyApi() }
    val porteeComposition = rememberCoroutineScope()
    var saisie by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var recherche by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = saisie,
            onValueChange = { texte ->
                saisie = texte
                porteeComposition.launch {
                    // En dessous de deux lettres, la recherche renverrait la
                    // moitie du catalogue sans aider personne.
                    if (texte.length < 2) {
                        resultats = emptyList()
                        return@launch
                    }
                    recherche = true
                    resultats = try {
                        api.searchArtists(ArtistSearch(stageName = texte), size = 8)
                            .data?.content ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    } finally {
                        recherche = false
                    }
                }
            },
            label = { Text("Rechercher un artiste") },
            singleLine = true,
            colors = couleursChamp(),
            modifier = Modifier.fillMaxWidth()
        )

        if (artisteChoisi != null) {
            Text(
                text = "Choisi : ${artisteChoisi.stageName}",
                color = KaloyCyan,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (recherche) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = KaloyPurple)
        }

        resultats.forEach { artiste ->
            Text(
                text = artiste.stageName,
                color = KaloyTextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChoisir(artiste)
                        saisie = artiste.stageName
                        resultats = emptyList()
                    }
                    .padding(vertical = 8.dp)
            )
            HorizontalDivider(color = KaloyDarkElevated)
        }
    }
}

// ============================================================
// Choix du lieu : une salle connue, ou une nouvelle
// ============================================================

@Composable
fun SelecteurLieu(
    lieux: List<Venue>,
    lieuChoisi: Venue?,
    nouveauNom: String,
    nouvelleLocalisation: String,
    creationEnCours: Boolean,
    onChoisirLieu: (Venue?) -> Unit,
    onBasculerCreation: (Boolean) -> Unit,
    onNouveauNom: (String) -> Unit,
    onNouvelleLocalisation: (String) -> Unit
) {
    Column {
        Text("Lieu", color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)

        if (!creationEnCours) {
            lieux.forEach { lieu ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onChoisirLieu(lieu) }
                ) {
                    RadioButton(
                        selected = lieuChoisi?.id == lieu.id,
                        onClick = { onChoisirLieu(lieu) },
                        colors = RadioButtonDefaults.colors(selectedColor = KaloyPurple)
                    )
                    Column {
                        Text(lieu.name, color = KaloyTextPrimary)
                        if (!lieu.location.isNullOrBlank()) {
                            Text(
                                lieu.location,
                                color = KaloyTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { onBasculerCreation(true); onChoisirLieu(null) }) {
                Text("+ Ajouter un lieu absent de la liste", color = KaloyCyan)
            }
        } else {
            OutlinedTextField(
                value = nouveauNom,
                onValueChange = onNouveauNom,
                label = { Text("Nom du lieu") },
                singleLine = true,
                colors = couleursChamp(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nouvelleLocalisation,
                onValueChange = onNouvelleLocalisation,
                label = { Text("Ville ou quartier (facultatif)") },
                singleLine = true,
                colors = couleursChamp(),
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { onBasculerCreation(false) }) {
                Text("Choisir plutôt un lieu existant", color = KaloyCyan)
            }
        }
    }
}

// ============================================================
// Saisie d'une heure
// ============================================================

/**
 * Champ « HH:MM ».
 *
 * La saisie est filtree au fil de la frappe plutot que validee a l'envoi :
 * l'utilisateur ne peut pas composer une heure impossible, donc il n'y a pas
 * de message d'erreur a lui montrer.
 */
@Composable
fun ChampHeure(
    libelle: String,
    valeur: String,
    onChangement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = { saisie ->
            val chiffres = saisie.filter { it.isDigit() }.take(4)
            val formatee = when {
                chiffres.length <= 2 -> chiffres
                else -> chiffres.substring(0, 2) + ":" + chiffres.substring(2)
            }
            onChangement(formatee)
        },
        label = { Text(libelle) },
        placeholder = { Text("20:30") },
        singleLine = true,
        colors = couleursChamp(),
        modifier = modifier
    )
}

/** Vrai si la chaine est une heure complete et valide. */
fun heureValide(heure: String): Boolean {
    val morceaux = heure.split(":")
    if (morceaux.size != 2) return false
    val h = morceaux[0].toIntOrNull() ?: return false
    val m = morceaux[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59 && morceaux[1].length == 2
}

/** « 2027-03-12 » + « 20:30 » -> « 2027-03-12T20:30 », format attendu par l'API. */
fun horodatage(date: String, heure: String): String = "${date}T$heure"

@Composable
fun couleursChamp(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = KaloyTextPrimary,
    unfocusedTextColor = KaloyTextPrimary,
    focusedBorderColor = KaloyPurple,
    unfocusedBorderColor = KaloyDarkElevated,
    focusedLabelColor = KaloyPurple,
    unfocusedLabelColor = KaloyTextMuted,
    cursorColor = KaloyPurple
)
