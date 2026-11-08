package com.kaloy.app.core.network

import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.AuthResponse
import com.kaloy.app.data.dto.RefreshRequest
import com.kaloy.app.data.dto.RestResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

val BASE_URL = "http://$SERVER_IP:8087/mozika"

private val refreshLock = Mutex()
private val skipRefreshKey = AttributeKey<Boolean>("skipSessionRefresh")

fun createHttpClient(sessionManager: AuthSessionManager): HttpClient {
    lateinit var httpClient: HttpClient
    httpClient = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }

        install(HttpSend)

        install(Logging) {
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }

    httpClient.plugin(HttpSend).intercept { request ->
        val authorization = request.headers[HttpHeaders.Authorization]
        val call = execute(request)
        if (
            call.response.status != HttpStatusCode.Unauthorized ||
            request.attributes.getOrNull(skipRefreshKey) == true ||
            authorization.isNullOrBlank() ||
            !authorization.startsWith("Bearer ")
        ) {
            return@intercept call
        }

        val rejectedToken = authorization.removePrefix("Bearer ")
        val replacement = refreshLock.withLock {
            if (sessionManager.getToken() != rejectedToken) {
                sessionManager.getToken()
            } else {
                refreshSession(httpClient, sessionManager)
            }
        } ?: return@intercept call

        request.headers.remove(HttpHeaders.Authorization)
        request.headers.append(HttpHeaders.Authorization, "Bearer $replacement")
        execute(request)
    }
    return httpClient
}

private suspend fun refreshSession(
    client: HttpClient,
    sessionManager: AuthSessionManager
): String? {
    val refreshToken = sessionManager.getRefreshToken()
    if (refreshToken.isNullOrBlank()) {
        sessionManager.requireReauthentication()
        return null
    }

    val response = client.post("$BASE_URL/auth/refresh") {
        attributes.put(skipRefreshKey, true)
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(refreshToken))
    }
    if (response.status == HttpStatusCode.Unauthorized) {
        sessionManager.requireReauthentication()
        return null
    }
    check(response.status.value in 200..299) {
        "Session refresh failed with HTTP ${response.status.value}."
    }

    val rest = response.body<RestResponse>()
    val data = rest.data ?: error("Refresh response did not contain session tokens.")
    val refreshedSession = Json.decodeFromJsonElement(
        serializer<AuthResponse>(),
        data
    )
    val nextRefreshToken = refreshedSession.refreshToken
        ?: error("Refresh response did not contain a replacement refresh token.")
    sessionManager.updateTokens(refreshedSession.token, nextRefreshToken)
    return refreshedSession.token
}

suspend fun refreshStoredSession(
    client: HttpClient,
    sessionManager: AuthSessionManager
): Boolean = refreshSession(client, sessionManager) != null
