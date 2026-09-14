package com.kaloy.app.core.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Horodatage au format attendu par le backend (java.time.LocalDateTime) :
 * "yyyy-MM-ddTHH:mm:ss", en UTC.
 *
 * Conversion faite à la main (algorithme civil-from-days de H. Hinnant) pour
 * éviter d'ajouter kotlinx-datetime au projet juste pour ça.
 */
@OptIn(ExperimentalTime::class)
fun maintenantIso(): String {
    val millis = Clock.System.now().toEpochMilliseconds()

    val jours = millis.floorDiv(86_400_000L)
    val resteMs = millis.mod(86_400_000L)

    val heures = (resteMs / 3_600_000L).toInt()
    val minutes = ((resteMs % 3_600_000L) / 60_000L).toInt()
    val secondes = ((resteMs % 60_000L) / 1_000L).toInt()

    val z = jours + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val jour = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
    val mois = (if (mp < 10L) mp + 3L else mp - 9L).toInt()
    val annee = (yoe + era * 400L + if (mois <= 2) 1L else 0L).toInt()

    return "${pad(annee, 4)}-${pad(mois, 2)}-${pad(jour, 2)}" +
        "T${pad(heures, 2)}:${pad(minutes, 2)}:${pad(secondes, 2)}"
}

private fun pad(valeur: Int, taille: Int): String = valeur.toString().padStart(taille, '0')
