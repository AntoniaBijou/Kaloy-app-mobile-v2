package com.kaloy.app.core.util

/**
 * Calculs de calendrier, ecrits a la main.
 *
 * Le projet n'embarque aucune bibliotheque de dates : ces fonctions couvrent
 * le strict necessaire pour dessiner une grille mensuelle, sans dependance
 * supplementaire.
 *
 * Les dates circulent partout sous forme de chaines « AAAA-MM-JJ », comme
 * celles que renvoie l'API.
 */
object Calendrier {

    val NOMS_MOIS = listOf(
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    )

    /** Semaine commencant le lundi, comme en usage a Madagascar et en Europe. */
    val JOURS_SEMAINE = listOf("L", "M", "M", "J", "V", "S", "D")

    fun estBissextile(annee: Int): Boolean =
        (annee % 4 == 0 && annee % 100 != 0) || annee % 400 == 0

    fun joursDansMois(annee: Int, mois: Int): Int = when (mois) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (estBissextile(annee)) 29 else 28
        else -> 30
    }

    /**
     * Jour de la semaine du 1er du mois, 0 = lundi ... 6 = dimanche.
     *
     * Formule de Zeller, qui donne le jour de la semaine sans avoir a compter
     * les jours depuis une date de reference.
     */
    fun jourSemaineDuPremier(annee: Int, mois: Int): Int {
        // Zeller considere janvier et fevrier comme les 13e et 14e mois de
        // l'annee precedente : c'est ce qui lui permet d'ignorer la position
        // variable du 29 fevrier.
        var m = mois
        var a = annee
        if (m < 3) {
            m += 12
            a -= 1
        }
        val k = a % 100
        val j = a / 100
        val h = (1 + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
        // Zeller renvoie 0 = samedi ; on ramene a 0 = lundi.
        return (h + 5) % 7
    }

    /** « 2026-08-21 » -> Triplet(2026, 8, 21), ou null si la chaine est inexploitable. */
    fun decouper(dateIso: String?): Triple<Int, Int, Int>? {
        if (dateIso.isNullOrBlank()) return null
        val morceaux = dateIso.substringBefore('T').split("-")
        if (morceaux.size != 3) return null
        val annee = morceaux[0].toIntOrNull() ?: return null
        val mois = morceaux[1].toIntOrNull() ?: return null
        val jour = morceaux[2].toIntOrNull() ?: return null
        return Triple(annee, mois, jour)
    }

    /** Assemble une date au format attendu partout ailleurs. */
    fun formater(annee: Int, mois: Int, jour: Int): String =
        "$annee-${deuxChiffres(mois)}-${deuxChiffres(jour)}"

    fun moisPrecedent(annee: Int, mois: Int): Pair<Int, Int> =
        if (mois == 1) (annee - 1) to 12 else annee to (mois - 1)

    fun moisSuivant(annee: Int, mois: Int): Pair<Int, Int> =
        if (mois == 12) (annee + 1) to 1 else annee to (mois + 1)

    fun nomDuMois(mois: Int): String = NOMS_MOIS.getOrElse(mois - 1) { "" }

    private fun deuxChiffres(valeur: Int): String = if (valeur < 10) "0$valeur" else "$valeur"
}
