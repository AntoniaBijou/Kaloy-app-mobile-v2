package com.kaloy.app.core.network

import com.kaloy.app.core.session.AuthSessionManager
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// Téléphone physique (WiFi local — même réseau que le PC)
const val BASE_URL = "http://192.168.1.129:8087/mozika"
// Émulateur Android  → "http://10.0.2.2:8087/mozika"
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
