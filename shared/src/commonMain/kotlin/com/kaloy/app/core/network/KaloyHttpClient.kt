package com.kaloy.app.core.network

import com.kaloy.app.core.session.AuthSessionManager
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

val BASE_URL = "http://$SERVER_IP:8087/mozika"

fun createHttpClient(sessionManager: AuthSessionManager): HttpClient = HttpClient {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    /**
     * Sans ce plugin, Ktor s'en remet aux delais du moteur, souvent dix
     * secondes. C'est trop court pour un morceau de 2 Mo sur un reseau mobile
     * faible : l'envoi echouerait alors qu'il progressait.
     *
     * Le delai de requete est volontairement absent : il bornerait la duree
     * TOTALE d'un appel, ce qui couperait l'envoi d'un gros morceau en cours de
     * route. Seule l'inactivite est bornee, par socketTimeout — un reseau
     * reellement coupe est detecte en 60 secondes, un reseau lent mais vivant
     * n'est pas interrompu.
     */
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
    }

    install(Logging) {
        /**
         * HEADERS et non BODY. Deux raisons, dont une bloquante.
         *
         * Bloquante : l'envoi par morceaux transmet 2 Mo de binaire par
         * requete, 250 fois pour une video de 500 Mo. Avec BODY, Ktor tenterait
         * de rendre chacun de ces morceaux en texte dans le journal — un demi
         * gigaoctet de sortie, et l'application a genoux.
         *
         * L'autre : BODY journalise aussi les corps de requete d'authentification,
         * donc les mots de passe en clair dans logcat.
         */
        level = LogLevel.HEADERS
    }
}
