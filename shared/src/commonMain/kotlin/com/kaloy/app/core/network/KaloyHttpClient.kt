package com.kaloy.app.core.network

import com.kaloy.app.core.session.AuthSessionManager
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// Émulateur Android : 10.0.2.2 est l'alias par lequel l'émulateur joint le
// localhost du PC (« localhost » désignerait l'émulateur lui-même).
const val BASE_URL = "http://10.0.2.2:8087/mozika"
// Tunnel ngrok       → const val BASE_URL = "https://latch-tummy-unfrosted.ngrok-free.dev/mozika"
// Téléphone physique → const val BASE_URL = "http://192.168.88.15:8087/mozika"

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
