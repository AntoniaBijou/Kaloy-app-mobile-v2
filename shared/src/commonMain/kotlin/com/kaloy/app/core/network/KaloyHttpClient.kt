package com.kaloy.app.core.network

import com.kaloy.app.core.session.AuthSessionManager
import io.ktor.client.*
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
    install(Logging) {
        level = LogLevel.BODY
    }
}
