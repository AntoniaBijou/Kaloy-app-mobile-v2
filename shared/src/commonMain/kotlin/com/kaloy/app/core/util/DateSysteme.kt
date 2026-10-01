package com.kaloy.app.core.util

/**
 * Date du jour dans le fuseau du telephone, au format « AAAA-MM-JJ ».
 *
 * Pourquoi ne pas reutiliser [maintenantIso] : celle-ci travaille en UTC.
 * A Madagascar (UTC+3), entre 21h et minuit, la date UTC est encore celle de
 * la veille — le calendrier entourerait alors le mauvais jour tous les soirs.
 *
 * Chaque plateforme sait donner sa date locale ; on la lui demande plutot que
 * d'appliquer un decalage fixe, qui serait faux des qu'on quitte Madagascar.
 */
expect fun aujourdHuiLocal(): String
