package com.kaloy.app.core.network

import com.kaloy.app.core.session.AuthSessionManager
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// BASE_URL construit depuis SERVER_IP généré par Gradle à partir de local.properties
// Pour changer l'IP : modifier adresse_ip dans Kaloy-app-mobile-v2/local.properties puis rebuild
val BASE_URL = "http://$SERVER_IP:8087/mozika"
// Émulateur Android  → SERVER_IP = "10.0.2.2" dans local.properties
// LocalTunnel        → "https://<subdomain>.loca.lt/mozika"

fun createHttpClient(sessionManager: AuthSessionManager): HttpClient = HttpClient {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(Logging) {
        level = LogLevel.BODY
    }
}
