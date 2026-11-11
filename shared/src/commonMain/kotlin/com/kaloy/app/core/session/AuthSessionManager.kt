package com.kaloy.app.core.session

import com.kaloy.app.data.dto.AuthResponse
import com.russhwolf.settings.Settings

class AuthSessionManager(
    private val settings: Settings,
    private val secureCredentialStore: SecureCredentialStore
) {
    private var tokenInMemory: String? = null
    private var userIdInMemory: Long? = null
    private var emailInMemory: String? = null
    private var roleInMemory: String? = null

    fun saveSession(response: AuthResponse) {
        tokenInMemory = response.token
        secureCredentialStore.write(KEY_ACCESS_TOKEN, response.token)
        response.refreshToken?.let { secureCredentialStore.write(KEY_REFRESH_TOKEN, it) }
            ?: secureCredentialStore.remove(KEY_REFRESH_TOKEN)
        userIdInMemory = response.userId
        emailInMemory = response.email
        roleInMemory = response.role
        settings.putLong(KEY_USER_ID, response.userId)
        settings.putString(KEY_EMAIL, response.email)
        settings.putString(KEY_ROLE, response.role)
        settings.putBoolean(KEY_REAUTHENTICATION_REQUIRED, false)
    }

    fun getToken(): String? {
        if (reauthenticationRequired()) return null
        tokenInMemory?.let { return it }
        secureCredentialStore.read(KEY_ACCESS_TOKEN)?.let {
            tokenInMemory = it
            return it
        }
        return settings.getStringOrNull(LEGACY_KEY_TOKEN)?.also {
            secureCredentialStore.write(KEY_ACCESS_TOKEN, it)
            settings.remove(LEGACY_KEY_TOKEN)
            tokenInMemory = it
        }
    }

    fun getRefreshToken(): String? {
        if (reauthenticationRequired()) return null
        secureCredentialStore.read(KEY_REFRESH_TOKEN)?.let { return it }
        return null
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        tokenInMemory = accessToken
        secureCredentialStore.write(KEY_ACCESS_TOKEN, accessToken)
        secureCredentialStore.write(KEY_REFRESH_TOKEN, refreshToken)
        settings.putBoolean(KEY_REAUTHENTICATION_REQUIRED, false)
    }

    fun requireReauthentication() {
        clear()
    }

    fun getUserId(): Long = userIdInMemory ?: settings.getLong(KEY_USER_ID, -1L)

    fun getRole(): String? = roleInMemory ?: settings.getStringOrNull(KEY_ROLE)

    fun getEmail(): String? = emailInMemory ?: settings.getStringOrNull(KEY_EMAIL)

    fun getDisplayName(): String {
        val email = settings.getStringOrNull(KEY_EMAIL)
        return email?.substringBefore("@")?.ifBlank { "Utilisateur" } ?: "Utilisateur"
    }

    fun hasStoredSession(): Boolean =
        !getToken().isNullOrBlank() || !getRefreshToken().isNullOrBlank()

    fun isLoggedIn(): Boolean = hasStoredSession()

    fun clear() {
        tokenInMemory = null
        userIdInMemory = null
        emailInMemory = null
        roleInMemory = null
        secureCredentialStore.remove(KEY_ACCESS_TOKEN)
        secureCredentialStore.remove(KEY_REFRESH_TOKEN)
        settings.remove(LEGACY_KEY_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_EMAIL)
        settings.remove(KEY_ROLE)
        settings.remove(KEY_REAUTHENTICATION_REQUIRED)
    }

    private fun reauthenticationRequired() =
        settings.getBoolean(KEY_REAUTHENTICATION_REQUIRED, false)

    companion object {
        const val SESSION_RETAINED_MESSAGE =
            "La session a expiré. Veuillez vous reconnecter."
        private const val KEY_ACCESS_TOKEN = "auth_access_token"
        private const val KEY_REFRESH_TOKEN = "auth_refresh_token"
        private const val LEGACY_KEY_TOKEN = "auth_token"
        private const val KEY_REAUTHENTICATION_REQUIRED = "auth_reauthentication_required"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_ROLE = "auth_role"
    }
}
