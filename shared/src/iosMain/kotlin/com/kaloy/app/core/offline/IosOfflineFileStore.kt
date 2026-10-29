package com.kaloy.app.core.offline

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.kSecRandomDefault
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rewind
import platform.posix.SEEK_END
import kotlinx.cinterop.ptr
import kotlinx.coroutines.currentCoroutineContext

/**
 * iOS offline storage. The file format is a sequence of independently
 * authenticated AES-256-GCM blocks, allowing bounded-memory transfer/playback.
 * The only copy of each random song key is a device-only Keychain item.
 */
@OptIn(ExperimentalForeignApi::class)
class IosOfflineFileStore : OfflineFileStore {
    private val http = HttpClient(Darwin) {
        expectSuccess = true
        followRedirects = false
    }
    private val files = NSFileManager.defaultManager
    private val aesGcm = CryptographyProvider.Default.get(AES.GCM)

    override suspend fun downloadAndEncrypt(
        playlistId: Long,
        songId: Long,
        downloadUrl: String,
        onProgress: (OfflineTransferProgress) -> Unit
    ) {
        require(playlistId > 0L && songId > 0L) { "Offline audio identifiers must be positive." }
        val targetUrl = Url(downloadUrl)
        require(
            targetUrl.protocol.name.equals("https", ignoreCase = true) ||
                (targetUrl.protocol.name.equals("http", ignoreCase = true) &&
                    isPrivateHost(targetUrl.host))
        ) {
            "Offline downloads require HTTPS or a private-network development server."
        }
        require(targetUrl.user == null && targetUrl.password == null) {
            "Invalid offline download URL."
        }
        val directory = offlineDirectory()
        val destination = encryptedPath(directory, playlistId, songId)
        val partial = "$destination.part"
        removeIfPresent(partial)
        removeIfPresent(destination)
        deleteKey(playlistId, songId)

        val key = randomBytes(KEY_SIZE)
        val baseNonce = randomBytes(NONCE_SIZE)
        var output: CPointer<FILE>? = null
        try {
            addSongToManifest(directory, playlistId, songId)
            storeKey(playlistId, songId, key)
            val cryptoKey = decodeKey(key)
            check(files.createFileAtPath(partial, null, protectedAttributes())) {
                "Could not create protected offline file."
            }
            val writer = fopen(partial, "wb") ?: error("Could not open offline file for writing.")
            output = writer
            writeBytes(writer, FILE_MAGIC + baseNonce)

            http.prepareGet(downloadUrl).execute { response ->
                val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(CHUNK_SIZE)
                var transferred = 0L
                var counter = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    check(counter <= MAX_CHUNK_COUNTER) { "Offline file is too large." }
                    val plain = buffer.copyOf(count)
                    val nonce = nonceFor(baseNonce, counter)
                    val encrypted = encryptChunk(
                        cryptoKey,
                        nonce,
                        associatedData(playlistId, songId, counter),
                        plain
                    )
                    writeBytes(writer, int32Bytes(count))
                    writeBytes(writer, encrypted.first)
                    writeBytes(writer, encrypted.second)
                    transferred += count
                    counter++
                    onProgress(OfflineTransferProgress(songId, transferred, total))
                }
                writeBytes(writer, int32Bytes(0))
            }
            val closeStatus = fclose(writer)
            output = null
            check(closeStatus == 0) { "Could not flush offline file." }
            applyProtection(partial)
            check(files.moveItemAtPath(partial, destination, null)) {
                "Could not finalize offline file."
            }
            applyProtection(destination)
        } catch (failure: Throwable) {
            output?.let(::fclose)
            removeIfPresent(partial)
            removeIfPresent(destination)
            deleteKey(playlistId, songId)
            removeSongFromManifest(directory, playlistId, songId)
            if (failure is CancellationException) throw failure
            throw failure
        }
    }

    override suspend fun playableUrl(playlistId: Long, songId: Long): String? {
        require(playlistId > 0L && songId > 0L) { "Offline audio identifiers must be positive." }
        val directory = offlineDirectory()
        removeStalePlaybackFiles(directory)
        val encrypted = encryptedPath(directory, playlistId, songId)
        if (!files.fileExistsAtPath(encrypted)) return null
        val rawKey = loadKey(playlistId, songId) ?: return null
        val key = decodeKey(rawKey)
        val input = fopen(encrypted, "rb") ?: return null
        var inputOpen = true
        val plainPath = "$directory/.playback-$playlistId-$songId-${randomHex(8)}.tmp"
        var output: CPointer<FILE>? = null
        try {
            check(files.createFileAtPath(plainPath, null, protectedAttributes())) {
                "Could not create protected playback file."
            }
            val writer = fopen(plainPath, "wb") ?: error("Could not open playback file for writing.")
            output = writer
            val header = readBytes(input, FILE_MAGIC.size + NONCE_SIZE)
            check(header.size == FILE_MAGIC.size + NONCE_SIZE &&
                header.copyOfRange(0, FILE_MAGIC.size).contentEquals(FILE_MAGIC)
            ) { "Invalid offline file." }
            val baseNonce = header.copyOfRange(FILE_MAGIC.size, header.size)
            var counter = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val length = readInt32(input)
                if (length == 0) break
                check(length in 1..CHUNK_SIZE && counter <= MAX_CHUNK_COUNTER) {
                    "Invalid offline block."
                }
                val ciphertext = readBytes(input, length)
                val tag = readBytes(input, TAG_SIZE)
                check(ciphertext.size == length && tag.size == TAG_SIZE) { "Truncated offline file." }
                val plain = decryptChunk(
                    key,
                    nonceFor(baseNonce, counter),
                    associatedData(playlistId, songId, counter),
                    ciphertext,
                    tag
                )
                writeBytes(writer, plain)
                counter++
            }
            val closeStatus = fclose(writer)
            output = null
            val inputCloseStatus = fclose(input)
            inputOpen = false
            check(closeStatus == 0 && inputCloseStatus == 0) {
                "Could not close playback files."
            }
            applyProtection(plainPath)
            return NSURL.fileURLWithPath(plainPath).absoluteString
        } catch (failure: Throwable) {
            output?.let(::fclose)
            if (inputOpen) fclose(input)
            removeIfPresent(plainPath)
            if (failure is CancellationException) throw failure
            throw failure
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        require(playlistId > 0L) { "Offline playlist identifier must be positive." }
        val directory = offlineDirectory()
        val songIds = playlistSongIds(directory, playlistId).toMutableSet()
        val names = files.contentsOfDirectoryAtPath(directory, null).orEmpty()
        for (name in names) {
            val fileName = name as? String ?: continue
            val prefix = "song-$playlistId-"
            if (!fileName.startsWith(prefix)) continue
            fileName.removePrefix(prefix)
                .substringBefore(".")
                .toLongOrNull()
                ?.let(songIds::add)
            removeIfPresent("$directory/$fileName")
        }
        songIds.forEach { deleteKey(playlistId, it) }
        removeIfPresent(manifestPath(directory, playlistId))
        removePlaybackFiles(directory, playlistId)
    }

    private fun offlineDirectory(): String {
        val applicationSupport = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String ?: error("Application Support directory is unavailable.")
        val path = "$applicationSupport/offline_audio"
        check(files.createDirectoryAtPath(path, true, null, null)) {
            "Could not create offline directory."
        }
        applyProtection(path)
        return path
    }

    private fun encryptedPath(directory: String, playlistId: Long, songId: Long) =
        "$directory/song-$playlistId-$songId.aesgcm"

    private fun manifestPath(directory: String, playlistId: Long) =
        "$directory/.playlist-$playlistId-song-ids"

    private fun playlistSongIds(directory: String, playlistId: Long): Set<Long> {
        val path = manifestPath(directory, playlistId)
        if (!files.fileExistsAtPath(path)) return emptySet()
        val input = fopen(path, "rb") ?: return emptySet()
        val content = try {
            fseek(input, 0, SEEK_END)
            val size = ftell(input).toInt()
            rewind(input)
            if (size < 0) ByteArray(0) else readBytes(input, size)
        } finally {
            fclose(input)
        }
        return content.decodeToString()
            .lineSequence()
            .mapNotNull(String::toLongOrNull)
            ?.toSet()
            .orEmpty()
    }

    private fun addSongToManifest(directory: String, playlistId: Long, songId: Long) {
        val ids = playlistSongIds(directory, playlistId) + songId
        writeManifest(directory, playlistId, ids)
    }

    private fun removeSongFromManifest(directory: String, playlistId: Long, songId: Long) {
        val ids = playlistSongIds(directory, playlistId) - songId
        if (ids.isEmpty()) removeIfPresent(manifestPath(directory, playlistId))
        else writeManifest(directory, playlistId, ids)
    }

    private fun writeManifest(directory: String, playlistId: Long, songIds: Set<Long>) {
        val path = manifestPath(directory, playlistId)
        val data = songIds.sorted().joinToString("\n").encodeToByteArray()
        val output = fopen(path, "wb") ?: error("Could not update offline index.")
        try {
            writeBytes(output, data)
            val closeStatus = fclose(output)
            check(closeStatus == 0) { "Could not flush offline index." }
        } catch (failure: Throwable) {
            fclose(output)
            throw failure
        }
        applyProtection(path)
    }

    private fun keyAccount(playlistId: Long, songId: Long) = "playlist-$playlistId-song-$songId"

    private inline fun <T> withKeyQuery(
        playlistId: Long,
        songId: Long,
        block: (CFMutableDictionaryRef) -> T
    ): T {
        val query = CFDictionaryCreateMutable(null, 0, null, null)
            ?: error("Could not create Keychain query.")
        val service = CFBridgingRetain(KEYCHAIN_SERVICE)
        val account = CFBridgingRetain(keyAccount(playlistId, songId))
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

    private fun storeKey(playlistId: Long, songId: Long, key: ByteArray) {
        withKeyQuery(playlistId, songId) { query ->
            val data = key.usePinned {
                CFDataCreate(null, it.addressOf(0).reinterpret<UByteVar>(), key.size.toLong())
            } ?: error("Could not create Keychain key data.")
            try {
                CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                CFDictionarySetValue(query, kSecValueData, data)
                check(SecItemAdd(query, null) == 0) { "Could not store offline key in Keychain." }
            } finally {
                CFRelease(data)
            }
        }
    }

    private fun loadKey(playlistId: Long, songId: Long): ByteArray? =
        withKeyQuery(playlistId, songId) { query ->
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != 0) return@memScoped null
                val value = result.value ?: return@memScoped null
                try {
                    val data = value as? CFDataRef ?: return@memScoped null
                    if (CFDataGetLength(data).toInt() != KEY_SIZE) return@memScoped null
                    val key = ByteArray(KEY_SIZE)
                    key.usePinned {
                        platform.posix.memcpy(
                            it.addressOf(0),
                            CFDataGetBytePtr(data),
                            KEY_SIZE.toULong()
                        )
                    }
                    key
                } finally {
                    CFRelease(value)
                }
            }
        }

    private fun deleteKey(playlistId: Long, songId: Long) {
        withKeyQuery(playlistId, songId) { SecItemDelete(it) }
    }

    private fun removeIfPresent(path: String) {
        if (files.fileExistsAtPath(path)) files.removeItemAtPath(path, null)
    }

    private fun isPrivateHost(host: String): Boolean {
        val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized == "::1") return true
        val octets = normalized.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 169 && octets[1] == 254) ||
            octets[0] == 127
    }

    private fun protectedAttributes(): Map<Any?, *> =
        mapOf(NSFileProtectionKey to NSFileProtectionComplete)

    private fun applyProtection(path: String) {
        check(files.setAttributes(protectedAttributes(), ofItemAtPath = path, error = null)) {
            "Could not enable iOS file protection."
        }
    }

    private fun removeStalePlaybackFiles(directory: String, playlistId: Long? = null) {
        val cutoff = NSDate().timeIntervalSince1970 - PLAYBACK_TTL_SECONDS
        val names = files.contentsOfDirectoryAtPath(directory, null).orEmpty()
        for (name in names) {
            val fileName = name as? String ?: continue
            if (!fileName.startsWith(".playback-") || !fileName.endsWith(".tmp")) continue
            if (playlistId != null && !fileName.startsWith(".playback-$playlistId-")) continue
            val path = "$directory/$fileName"
            val modified = files.attributesOfItemAtPath(path, null)?.get("NSFileModificationDate") as? NSDate
            if (modified == null || modified.timeIntervalSince1970 < cutoff) removeIfPresent(path)
        }
    }

    private fun removePlaybackFiles(directory: String, playlistId: Long) {
        val names = files.contentsOfDirectoryAtPath(directory, null).orEmpty()
        val prefix = ".playback-$playlistId-"
        for (name in names) {
            val fileName = name as? String ?: continue
            if (fileName.startsWith(prefix) && fileName.endsWith(".tmp")) {
                val path = "$directory/$fileName"
                removeIfPresent(path)
            }
        }
    }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        if (size == 0) return bytes
        val status = bytes.usePinned {
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.addressOf(0))
        }
        check(status == 0) { "Secure random generation failed." }
        return bytes
    }

    private fun randomHex(size: Int): String =
        randomBytes(size).joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }

    private suspend fun decodeKey(rawKey: ByteArray): AES.GCM.Key {
        require(rawKey.size == KEY_SIZE)
        return aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, rawKey)
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun encryptChunk(
        key: AES.GCM.Key,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray
    ): Pair<ByteArray, ByteArray> {
        val combined = key.cipher().encryptWithIv(
            iv = nonce,
            plaintext = plaintext,
            associatedData = aad
        )
        check(combined.size >= TAG_SIZE)
        return combined.copyOfRange(0, combined.size - TAG_SIZE) to
            combined.copyOfRange(combined.size - TAG_SIZE, combined.size)
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun decryptChunk(
        key: AES.GCM.Key,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray
    ): ByteArray {
        return key.cipher().decryptWithIv(
            iv = nonce,
            ciphertext = ciphertext + tag,
            associatedData = aad
        )
    }

    /** Combines one random per-song IV with a unique monotonic block counter. */
    private fun nonceFor(base: ByteArray, counter: Long): ByteArray {
        val result = base.copyOf()
        for (i in 0 until 8) {
            val offset = result.lastIndex - i
            result[offset] = (result[offset].toInt() xor ((counter ushr (i * 8)) and 0xff).toInt()).toByte()
        }
        return result
    }

    private fun associatedData(playlistId: Long, songId: Long, counter: Long): ByteArray =
        longBytes(playlistId) + longBytes(songId) + longBytes(counter)

    private fun longBytes(value: Long): ByteArray =
        ByteArray(8) { index -> (value ushr (56 - index * 8)).toByte() }

    private fun int32Bytes(value: Int) = byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )

    private fun readInt32(input: CPointer<FILE>): Int {
        val bytes = readBytes(input, 4)
        return ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
    }

    private fun writeBytes(file: CPointer<FILE>, data: ByteArray) {
        if (data.isEmpty()) return
        val written = data.usePinned {
            fwrite(it.addressOf(0), 1uL, data.size.toULong(), file).toInt()
        }
        check(written == data.size) { "Could not write offline file." }
    }

    private fun readBytes(file: CPointer<FILE>, size: Int): ByteArray {
        val result = ByteArray(size)
        if (size == 0) return result
        val read = result.usePinned {
            fread(it.addressOf(0), 1uL, size.toULong(), file).toInt()
        }
        check(read == size) { "Truncated offline file." }
        return result
    }

    private companion object {
        val FILE_MAGIC = "KALOFFL1".encodeToByteArray()
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 12
        const val TAG_SIZE = 16
        const val CHUNK_SIZE = 64 * 1024
        const val MAX_CHUNK_COUNTER = 0xffff_ffffL
        const val KEYCHAIN_SERVICE = "com.kaloy.offline-audio"
        const val PLAYBACK_TTL_SECONDS = 24 * 60 * 60.0
    }
}
