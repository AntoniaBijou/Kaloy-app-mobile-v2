package com.kaloy.app.core.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

/**
 * iOS : NSCalendar travaille par defaut dans le fuseau du telephone.
 *
 * On assemble la chaine a la main plutot que d'utiliser NSDateFormatter :
 * celui-ci suit la langue de l'appareil et pourrait produire un autre format
 * que « AAAA-MM-JJ », que le reste du code attend.
 *
 * Non verifie a l'execution : la compilation iOS est possible depuis Windows,
 * pas l'execution.
 */
actual fun aujourdHuiLocal(): String {
    val calendrier = NSCalendar.currentCalendar
    val composants = calendrier.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = NSDate()
    )
    val annee = composants.year.toInt()
    val mois = composants.month.toInt()
    val jour = composants.day.toInt()
    return "$annee-${deuxChiffres(mois)}-${deuxChiffres(jour)}"
}

private fun deuxChiffres(valeur: Int): String = if (valeur < 10) "0$valeur" else "$valeur"
