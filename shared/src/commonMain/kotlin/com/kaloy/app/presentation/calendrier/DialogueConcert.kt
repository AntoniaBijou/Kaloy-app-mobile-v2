package com.kaloy.app.presentation.calendrier

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaloy.app.core.util.aujourdHuiLocal
import com.kaloy.app.data.api.CreerConcertDto
import com.kaloy.app.data.api.NouveauLieuDto
import com.kaloy.app.data.model.Venue
import com.kaloy.app.presentation.organisation.ChampHeure
import com.kaloy.app.presentation.organisation.SelecteurDate
import com.kaloy.app.presentation.organisation.SelecteurLieu
import com.kaloy.app.presentation.organisation.couleursChamp
import com.kaloy.app.presentation.organisation.heureValide
import com.kaloy.app.presentation.organisation.horodatage
import com.kaloy.app.ui.theme.*

/**
 * Declaration d'un concert que l'artiste joue seul.
 *
 * Reprend les composants du formulaire de creneau — selecteur de lieu, de date,
 * champs d'heure — mais sans choix d'artiste : c'est lui. Et sans contrainte de
 * dates d'evenement, puisqu'il n'y a pas d'evenement ; seule la regle « a
 * venir » s'applique, la meme que pour les invitations.
 */
@Composable
fun DialogueConcert(
    lieux: List<Venue>,
    onAnnuler: () -> Unit,
    onValider: (CreerConcertDto) -> Unit
) {
    var titre by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var lieu by remember { mutableStateOf<Venue?>(null) }
    var creationLieu by remember { mutableStateOf(false) }
    var nomLieu by remember { mutableStateOf("") }
    var localisationLieu by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<String?>(null) }
    var heureDebut by remember { mutableStateOf("") }
    var heureFin by remember { mutableStateOf("") }
    var calendrierOuvert by remember { mutableStateOf(false) }

    val lieuRenseigne = if (creationLieu) nomLieu.isNotBlank() else lieu != null
    val finCorrecte = heureFin.isBlank() || (heureValide(heureFin) && heureFin > heureDebut)
    val complet = lieuRenseigne && date != null && heureValide(heureDebut) && finCorrecte

    if (calendrierOuvert) {
        SelecteurDate(
            titre = "Jour du concert",
            dateChoisie = date,
            // Le serveur refuse une date passee : autant ne pas la proposer.
            dateMin = aujourdHuiLocal(),
            onChoisir = { date = it; calendrierOuvert = false },
            onFermer = { calendrierOuvert = false }
        )
    }

    AlertDialog(
        onDismissRequest = onAnnuler,
        containerColor = KaloyDarkCard,
        title = { Text("Déclarer un concert", color = KaloyTextPrimary) },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler", color = KaloyTextSecondary) }
        },
        confirmButton = {
            TextButton(
                enabled = complet,
                onClick = {
                    val jour = date ?: return@TextButton
                    onValider(
                        CreerConcertDto(
                            titre = titre.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            idLieu = if (creationLieu) null else lieu?.id,
                            nouveauLieu = if (creationLieu) {
                                NouveauLieuDto(
                                    nom = nomLieu.trim(),
                                    localisation = localisationLieu.trim().ifBlank { null }
                                )
                            } else null,
                            debut = horodatage(jour, heureDebut),
                            fin = if (heureValide(heureFin)) horodatage(jour, heureFin) else null
                        )
                    )
                }
            ) {
                Text("Créer", color = if (complet) KaloyPurple else KaloyTextMuted)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = titre,
                    onValueChange = { titre = it },
                    label = { Text("Titre (facultatif)") },
                    placeholder = { Text("Concert acoustique") },
                    singleLine = true,
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )

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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (facultatif)") },
                    minLines = 2,
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
