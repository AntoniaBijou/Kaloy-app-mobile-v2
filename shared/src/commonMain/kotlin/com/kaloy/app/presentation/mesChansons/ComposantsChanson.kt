package com.kaloy.app.presentation.mesChansons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import com.kaloy.app.data.api.OptionChoixDto
import com.kaloy.app.presentation.common.MediaSelectionne
import com.kaloy.app.ui.theme.*

// ============================================================
// Les briques du formulaire de declaration.
//
// Separees de l'ecran parce qu'elles ne portent aucun etat metier : on leur
// passe la valeur et le rappel, et elles n'ont rien a savoir de la chanson en
// cours de saisie.
// ============================================================

@Composable
fun TitreSection(texte: String) {
    Text(
        text = texte,
        color = KaloyPurpleLight,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun ChampTexte(
    valeur: String,
    onChange: (String) -> Unit,
    etiquette: String,
    aide: String? = null,
    lignes: Int = 1,
    erreur: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valeur,
            onValueChange = onChange,
            label = { Text(etiquette) },
            singleLine = lignes == 1,
            minLines = lignes,
            isError = erreur != null,
            colors = couleursChampChanson(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        val sousTexte = erreur ?: aide
        if (sousTexte != null) {
            Text(
                text = sousTexte,
                color = if (erreur != null) KaloyRed else KaloyTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Une liste a choix unique, alimentee par la base.
 *
 * [choixVide] permet de ne rien choisir quand le champ est facultatif : sans
 * lui, la premiere valeur serait selectionnee par defaut et l'artiste
 * rattacherait sa chanson a un album au hasard.
 */
@Composable
fun SelecteurListe(
    etiquette: String,
    options: List<OptionChoixDto>,
    idChoisi: Long?,
    choixVide: String? = null,
    aide: String? = null,
    onChoisir: (Long?) -> Unit
) {
    var ouvert by remember { mutableStateOf(false) }
    val libelle = options.firstOrNull { it.id == idChoisi }?.libelle
        ?: choixVide ?: "À choisir"

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { ouvert = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = etiquette,
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = libelle,
                        color = if (idChoisi == null) KaloyTextSecondary else KaloyTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = KaloyTextMuted
                )
            }
        }
        if (aide != null) {
            Text(
                text = aide,
                color = KaloyTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }

    if (ouvert) {
        AlertDialog(
            onDismissRequest = { ouvert = false },
            containerColor = KaloyDarkCard,
            title = { Text(etiquette, color = KaloyTextPrimary) },
            confirmButton = {
                TextButton(onClick = { ouvert = false }) {
                    Text("Fermer", color = KaloyTextSecondary)
                }
            },
            text = {
                Column {
                    if (choixVide != null) {
                        LigneChoix(
                            libelle = choixVide,
                            choisi = idChoisi == null,
                            onClick = { onChoisir(null); ouvert = false }
                        )
                    }
                    if (options.isEmpty()) {
                        Text(
                            text = "Aucune valeur disponible.",
                            color = KaloyTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    options.forEach { option ->
                        LigneChoix(
                            libelle = option.libelle,
                            choisi = option.id == idChoisi,
                            onClick = { onChoisir(option.id); ouvert = false }
                        )
                    }
                }
            }
        )
    }
}

/**
 * Une liste a choix multiple : plusieurs genres par chanson.
 *
 * La table song_genres est une table de liaison, et le catalogue en compte
 * deja plus que de chansons. Un choix unique aurait donc contredit ce qui est
 * en base.
 */
@Composable
fun SelecteurGenres(
    options: List<OptionChoixDto>,
    idsChoisis: List<Long>,
    onBasculer: (Long) -> Unit
) {
    var ouvert by remember { mutableStateOf(false) }
    val choisis = options.filter { it.id in idsChoisis }

    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { ouvert = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Genres",
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (choisis.isEmpty()) "Aucun genre"
                    else choisis.joinToString(", ") { it.libelle },
                    color = if (choisis.isEmpty()) KaloyTextSecondary else KaloyTextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = KaloyTextMuted
            )
        }
    }

    if (ouvert) {
        AlertDialog(
            onDismissRequest = { ouvert = false },
            containerColor = KaloyDarkCard,
            title = { Text("Genres", color = KaloyTextPrimary) },
            confirmButton = {
                TextButton(onClick = { ouvert = false }) {
                    Text("Terminé", color = KaloyPurpleLight)
                }
            },
            text = {
                Column {
                    options.forEach { option ->
                        LigneChoix(
                            libelle = option.libelle,
                            choisi = option.id in idsChoisis,
                            // La boite reste ouverte : on en coche souvent
                            // plusieurs de suite.
                            onClick = { onBasculer(option.id) }
                        )
                    }
                }
            }
        )
    }
}

/**
 * Une liste de noms deja saisis, plus la possibilite d'en taper un nouveau.
 *
 * La liste stricte ne convenait pas : les colonnes author_composer et
 * musical_arranger sont du texte libre, il n'existe aucune table de
 * reference, et musical_arranger etait nul sur les 31 chansons du catalogue.
 * Une liste fermee aurait donc ete vide, et personne n'aurait jamais pu y
 * ajouter le premier nom. Ici chaque nom saisi rejoint la liste des suivants.
 */
@Composable
fun SelecteurAvecSaisie(
    etiquette: String,
    valeur: String,
    propositions: List<String>,
    onChanger: (String) -> Unit
) {
    var ouvert by remember { mutableStateOf(false) }
    var saisieLibre by remember { mutableStateOf(false) }
    var brouillon by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable {
                // Si rien n'existe encore en base, la liste serait une boite
                // vide : on ouvre directement la saisie.
                saisieLibre = propositions.isEmpty()
                brouillon = valeur
                ouvert = true
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = etiquette,
                        color = KaloyTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = valeur.ifBlank { "Non renseigné" },
                        color = if (valeur.isBlank()) KaloyTextSecondary else KaloyTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = KaloyTextMuted
                )
            }
        }
    }

    if (ouvert) {
        AlertDialog(
            onDismissRequest = { ouvert = false },
            containerColor = KaloyDarkCard,
            title = { Text(etiquette, color = KaloyTextPrimary) },
            confirmButton = {
                if (saisieLibre) {
                    TextButton(onClick = {
                        onChanger(brouillon.trim())
                        ouvert = false
                    }) { Text("Valider", color = KaloyPurpleLight) }
                } else {
                    TextButton(onClick = { ouvert = false }) {
                        Text("Fermer", color = KaloyTextSecondary)
                    }
                }
            },
            dismissButton = if (saisieLibre) {
                { TextButton(onClick = { ouvert = false }) {
                    Text("Annuler", color = KaloyTextSecondary)
                } }
            } else null,
            text = {
                if (saisieLibre) {
                    OutlinedTextField(
                        value = brouillon,
                        onValueChange = { brouillon = it },
                        label = { Text("Nouveau nom") },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = {
                            onChanger(brouillon.trim())
                            ouvert = false
                        }),
                        colors = couleursChampChanson(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Column {
                        LigneChoix(
                            libelle = "Non renseigné",
                            choisi = valeur.isBlank(),
                            onClick = { onChanger(""); ouvert = false }
                        )
                        propositions.forEach { nom ->
                            LigneChoix(
                                libelle = nom,
                                choisi = nom == valeur,
                                onClick = { onChanger(nom); ouvert = false }
                            )
                        }
                        HorizontalDivider(color = KaloyDarkElevated)
                        LigneChoix(
                            libelle = "Saisir un nouveau nom",
                            choisi = false,
                            onClick = { brouillon = valeur; saisieLibre = true }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun LigneChoix(libelle: String, choisi: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = libelle,
            color = if (choisi) KaloyPurpleLight else KaloyTextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (choisi) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = KaloyPurpleLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Une ligne cliquable qui ouvre le selecteur de date. */
@Composable
fun LigneDate(etiquette: String, date: String?, onOuvrir: () -> Unit, onEffacer: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onOuvrir)) {
                Text(
                    text = etiquette,
                    color = KaloyTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = date ?: "Non renseignée",
                    color = if (date == null) KaloyTextSecondary else KaloyTextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (date != null) {
                TextButton(onClick = onEffacer) {
                    Text("Effacer", color = KaloyTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun couleursChampChanson(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = KaloyTextPrimary,
    unfocusedTextColor = KaloyTextPrimary,
    focusedBorderColor = KaloyPurple,
    unfocusedBorderColor = KaloyDarkElevated,
    focusedLabelColor = KaloyPurple,
    unfocusedLabelColor = KaloyTextMuted,
    cursorColor = KaloyPurple,
    errorBorderColor = KaloyRed,
    errorLabelColor = KaloyRed
)

// ============================================================
// Fichier audio
// ============================================================

/** Taille acceptee par le serveur : 200 Mo, comme UploadController. */
const val TAILLE_AUDIO_MAX: Long = 200L * 1024L * 1024L

/**
 * Les types que le serveur accepte pour un audio.
 *
 * Dupliquer la liste cote mobile n'est pas satisfaisant, mais l'alternative
 * est pire : laisser l'artiste choisir un fichier que le serveur refusera
 * APRES l'envoi, c'est-a-dire apres plusieurs minutes sur un reseau mobile.
 * La verification locale ne remplace pas celle du serveur, elle l'anticipe.
 */
private val TYPES_AUDIO = setOf("audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a")

/**
 * Pourquoi ce fichier ne convient pas, ou null s'il convient.
 *
 * Verifie sur ce que le selecteur renvoie — taille et type sont connus des la
 * selection — donc avant toute copie et tout envoi.
 */
fun refusAudio(media: MediaSelectionne): String? = when {
    media.taille <= 0L ->
        "Ce fichier est vide ou illisible."

    media.taille > TAILLE_AUDIO_MAX ->
        "Fichier trop volumineux : ${tailleLisible(media.taille)} pour 200 Mo au maximum."

    media.typeMime.lowercase() !in TYPES_AUDIO ->
        "Format non accepté : ${media.typeMime}. Choisissez un MP3 ou un M4A."

    else -> null
}

/** Une taille en octets, lisible. */
fun tailleLisible(octets: Long): String = when {
    octets >= 1024L * 1024L -> "${octets / (1024L * 1024L)} Mo"
    octets >= 1024L -> "${octets / 1024L} Ko"
    else -> "$octets o"
}

// ============================================================
// Duree
// ============================================================

/**
 * « 3:45 » ou « 225 » vers un nombre de secondes, ou null si la saisie n'est
 * pas lisible.
 *
 * Les deux formes sont acceptees parce qu'on ne sait pas laquelle l'artiste
 * tapera, et que refuser « 3:45 » serait absurde : c'est ainsi qu'une duree
 * s'ecrit sur une pochette.
 */
fun dureeEnSecondes(saisie: String): Int? {
    val propre = saisie.trim()
    if (propre.isEmpty()) return null
    if (':' in propre) {
        val morceaux = propre.split(':')
        if (morceaux.size != 2) return null
        val minutes = morceaux[0].trim().toIntOrNull() ?: return null
        val secondes = morceaux[1].trim().toIntOrNull() ?: return null
        if (minutes < 0 || secondes < 0 || secondes > 59) return null
        return minutes * 60 + secondes
    }
    return propre.toIntOrNull()
}

/** Vrai si la saisie est vide, ou lisible et strictement positive. */
fun dureeAcceptable(saisie: String): Boolean {
    if (saisie.isBlank()) return true
    val secondes = dureeEnSecondes(saisie) ?: return false
    // La base refuse zero ou moins : contrainte ck_songs_duration.
    return secondes > 0
}
