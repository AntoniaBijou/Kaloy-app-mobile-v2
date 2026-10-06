package com.kaloy.app.presentation.organisation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaloy.app.data.api.CreneauDto
import com.kaloy.app.data.api.NouveauLieuDto
import com.kaloy.app.data.model.Artist
import com.kaloy.app.data.model.Venue
import com.kaloy.app.ui.theme.*

/**
 * Saisie d'un creneau : qui joue, ou, et quand.
 *
 * Le meme dialogue sert a la creation de l'evenement et a l'ajout ulterieur
 * d'invitations. Deux formulaires separes auraient diverge a la premiere
 * evolution.
 *
 * [dateMin] et [dateMax] sont les dates de l'evenement : le serveur refuse un
 * creneau qui tombe en dehors, autant ne pas laisser l'utilisateur le composer.
 */
@Composable
fun DialogueCreneau(
    lieux: List<Venue>,
    dateMin: String?,
    dateMax: String?,
    onAnnuler: () -> Unit,
    onValider: (CreneauDto, String) -> Unit
) {
    var artiste by remember { mutableStateOf<Artist?>(null) }
    var lieu by remember { mutableStateOf<Venue?>(null) }
    var creationLieu by remember { mutableStateOf(false) }
    var nomLieu by remember { mutableStateOf("") }
    var localisationLieu by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<String?>(null) }
    var heureDebut by remember { mutableStateOf("") }
    var heureFin by remember { mutableStateOf("") }
    var titre by remember { mutableStateOf("") }
    var calendrierOuvert by remember { mutableStateOf(false) }

    val lieuRenseigne = if (creationLieu) nomLieu.isNotBlank() else lieu != null
    val finCorrecte = heureFin.isBlank() || (heureValide(heureFin) && heureFin > heureDebut)
    val complet = artiste != null && lieuRenseigne && date != null &&
        heureValide(heureDebut) && finCorrecte

    if (calendrierOuvert) {
        SelecteurDate(
            titre = "Jour du concert",
            dateChoisie = date,
            dateMin = dateMin,
            dateMax = dateMax,
            onChoisir = { date = it; calendrierOuvert = false },
            onFermer = { calendrierOuvert = false }
        )
    }

    AlertDialog(
        onDismissRequest = onAnnuler,
        containerColor = KaloyDarkCard,
        title = { Text("Inviter un artiste", color = KaloyTextPrimary) },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler", color = KaloyTextSecondary) }
        },
        confirmButton = {
            TextButton(
                enabled = complet,
                onClick = {
                    val choisi = artiste ?: return@TextButton
                    val jour = date ?: return@TextButton
                    onValider(
                        CreneauDto(
                            idArtiste = choisi.id,
                            idLieu = if (creationLieu) null else lieu?.id,
                            nouveauLieu = if (creationLieu) {
                                NouveauLieuDto(
                                    nom = nomLieu.trim(),
                                    localisation = localisationLieu.trim().ifBlank { null }
                                )
                            } else null,
                            debut = horodatage(jour, heureDebut),
                            fin = if (heureValide(heureFin)) horodatage(jour, heureFin) else null,
                            titre = titre.trim().ifBlank { null }
                        ),
                        // Le nom de scene accompagne le creneau pour que la liste
                        // en cours de saisie reste lisible sans rappeler l'API.
                        choisi.stageName
                    )
                }
            ) {
                Text("Ajouter", color = if (complet) KaloyPurple else KaloyTextMuted)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelecteurArtiste(artisteChoisi = artiste, onChoisir = { artiste = it })

                Spacer(Modifier.height(12.dp))

                SelecteurLieu(
                    lieux = lieux,
                    lieuChoisi = lieu,
                    nouveauNom = nomLieu,
                    nouvelleLocalisation = localisationLieu,
                    creationEnCours = creationLieu,
                    onChoisirLieu = { lieu = it },
                    onBasculerCreation = { creationLieu = it },
                    onNouveauNom = { nomLieu = it },
                    onNouvelleLocalisation = { localisationLieu = it }
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { calendrierOuvert = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = if (date == null) KaloyTextSecondary else KaloyTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = date ?: "Choisir le jour",
                        color = if (date == null) KaloyTextSecondary else KaloyTextPrimary
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampHeure(
                        libelle = "Début",
                        valeur = heureDebut,
                        onChangement = { heureDebut = it },
                        modifier = Modifier.weight(1f)
                    )
                    ChampHeure(
                        libelle = "Fin (facultatif)",
                        valeur = heureFin,
                        onChangement = { heureFin = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (heureFin.isNotBlank() && !finCorrecte) {
                    Text(
                        text = "L'heure de fin doit suivre l'heure de début.",
                        color = KaloyPink,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = titre,
                    onValueChange = { titre = it },
                    label = { Text("Intitulé du passage (facultatif)") },
                    placeholder = { Text("Scène principale J1") },
                    singleLine = true,
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
