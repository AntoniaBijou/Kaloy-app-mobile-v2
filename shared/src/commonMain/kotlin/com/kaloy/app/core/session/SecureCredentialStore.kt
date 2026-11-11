package com.kaloy.app.core.session

interface SecureCredentialStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}
