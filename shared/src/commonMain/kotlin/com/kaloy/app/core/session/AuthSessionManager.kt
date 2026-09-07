package com.kaloy.app.core.session

import com.kaloy.app.data.dto.AuthResponse
import com.russhwolf.settings.Settings

class AuthSessionManager(private val settings: Settings) {
    private var tokenInMemory: String? = null
    private var userIdInMemory: Long? = null
    private var emailInMemory: String? = null
    private var roleInMemory: String? = null

    fun saveSession(response: AuthResponse) {
        tokenInMemory = response.token
        userIdInMemory = response.userId
        emailInMemory = response.email
        roleInMemory = response.role
        settings.putString(KEY_TOKEN, response.token)
        settings.putLong(KEY_USER_ID, response.userId)
        settings.putString(KEY_EMAIL, response.email)
        settings.putString(KEY_ROLE, response.role)
    }

    fun getToken(): String? = tokenInMemory ?: settings.getStringOrNull(KEY_TOKEN)

    fun getUserId(): Long = userIdInMemory ?: settings.getLong(KEY_USER_ID, -1L)

    fun getRole(): String? = roleInMemory ?: settings.getStringOrNull(KEY_ROLE)

    fun getEmail(): String? = emailInMemory ?: settings.getStringOrNull(KEY_EMAIL)

    fun getDisplayName(): String {
        val email = settings.getStringOrNull(KEY_EMAIL)
        return email?.substringBefore("@")?.ifBlank { "Utilisateur" } ?: "Utilisateur"
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        tokenInMemory = null
        userIdInMemory = null
        emailInMemory = null
        roleInMemory = null
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_EMAIL)
        settings.remove(KEY_ROLE)
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_ROLE = "auth_role"
    }
}
