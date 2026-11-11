package com.kaloy.app.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRetain
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosSecureCredentialStore : SecureCredentialStore {
    override fun read(key: String): String? =
        withQuery(key) { query ->
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != 0) return@memScoped null
                val value = result.value ?: return@memScoped null
                try {
                    val data = value as? CFDataRef ?: error("Keychain returned invalid credential data.")
                    val size = CFDataGetLength(data).toInt()
                    require(size > 0) { "Keychain returned empty credential data." }
                    ByteArray(size).also { bytes ->
                        bytes.usePinned {
                            memcpy(it.addressOf(0), CFDataGetBytePtr(data), size.toULong())
                        }
                    }.decodeToString()
                } finally {
                    CFRelease(value)
                }
            }
        }

    override fun write(key: String, value: String) {
        withQuery(key) { query ->
            SecItemDelete(query)
            val bytes = value.encodeToByteArray()
            val data = bytes.usePinned {
                CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong())
            } ?: error("Could not encode authentication credential for Keychain.")
            try {
                CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                CFDictionarySetValue(query, kSecValueData, data)
                check(SecItemAdd(query, null) == 0) { "Could not save authentication credential to Keychain." }
            } finally {
                CFRelease(data)
            }
        }
    }

    override fun remove(key: String) {
        withQuery(key) { SecItemDelete(it) }
    }

    private inline fun <T> withQuery(key: String, block: (CFMutableDictionaryRef) -> T): T {
        val query = CFDictionaryCreateMutable(null, 0, null, null)
            ?: error("Could not create Keychain query.")
        val service = CFBridgingRetain(KEYCHAIN_SERVICE)
        val account = CFBridgingRetain(key)
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, account)
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(account)
        }
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "com.kaloy.auth.credentials.v1"
    }
}
