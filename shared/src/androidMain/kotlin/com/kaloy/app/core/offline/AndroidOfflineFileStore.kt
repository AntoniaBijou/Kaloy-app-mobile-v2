package com.kaloy.app.core.offline

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

private fun requireValidId(id: Long) {
    require(id > 0L) { "Offline audio identifiers must be positive" }
}

internal fun offlineAudioUrl(playlistId: Long, songId: Long): String {
    requireValidId(playlistId)
    requireValidId(songId)
    return "${AndroidOfflineFileStore.OFFLINE_SCHEME}://audio/$playlistId/$songId"
}

class AndroidOfflineFileStore(context: Context) : OfflineFileStore {
    private val appContext = context.applicationContext
    private val audioDirectory = File(appContext.filesDir, DIRECTORY_NAME)

    override suspend fun downloadAndEncrypt(
        playlistId: Long,
        songId: Long,
        downloadUrl: String,
        onProgress: (OfflineTransferProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        requireValidId(playlistId)
        requireValidId(songId)
        val initialUrl = validateDownloadUrl(downloadUrl)
        if (!audioDirectory.exists()) {
            check(audioDirectory.mkdirs()) { "Cannot create offline audio directory" }
        }
        validatedAudioDirectory()
        val destination = audioFile(playlistId, songId)
        val staging = File(audioDirectory, ".$playlistId-$songId-${SecureRandom().nextLong().toString(16)}.part")
        var connection: HttpURLConnection? = null
        var dataKeyBytes: ByteArray? = null
        try {
            val response = openResponse(initialUrl).also { connection = it }
            val totalBytes = response.contentLengthLong.takeIf { it >= 0L }
            response.inputStream.use { input ->
                val dataKey = ByteArray(DATA_KEY_BYTES).also {
                    SecureRandom().nextBytes(it)
                    dataKeyBytes = it
                }
                val (wrapNonce, wrappedKey) = wrapDataKey(dataKey)
                val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(SecureRandom()::nextBytes)
                RandomAccessFile(staging, "rw").use { fileOutput ->
                    fileOutput.setLength(0L)
                    val headerOffset = headerSize(wrappedKey.size)
                    fileOutput.setLength(headerOffset)
                    val buffer = ByteArray(CHUNK_SIZE_BYTES)
                    var transferred = 0L
                    var chunkIndex = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Offline audio transfer interrupted")
                        }
                        var count = 0
                        while (count < buffer.size) {
                            val read = runInterruptible { input.read(buffer, count, buffer.size - count) }
                            if (read < 0) break
                            if (read == 0) continue
                            count += read
                        }
                        if (count == 0) break
                        val cipher = chunkCipher(
                            Cipher.ENCRYPT_MODE,
                            javax.crypto.spec.SecretKeySpec(dataKey, "AES"),
                            noncePrefix,
                            chunkIndex
                        ).apply {
                            updateAAD(chunkAad(playlistId, songId, chunkIndex, count))
                        }
                        fileOutput.seek(fileOutput.length())
                        fileOutput.write(cipher.doFinal(buffer, 0, count))
                        transferred += count
                        onProgress(OfflineTransferProgress(songId, transferred, totalBytes))
                        chunkIndex++
                    }
                    require(transferred > 0L) { "Downloaded audio is empty" }
                    val header = encodeHeader(
                        playlistId,
                        songId,
                        wrapNonce,
                        wrappedKey,
                        noncePrefix,
                        transferred
                    )
                    val headerTag = chunkCipher(
                        Cipher.ENCRYPT_MODE,
                        javax.crypto.spec.SecretKeySpec(dataKey, "AES"),
                        noncePrefix,
                        HEADER_NONCE_INDEX
                    ).apply { updateAAD(header) }.doFinal()
                    fileOutput.seek(0L)
                    fileOutput.write(header)
                    fileOutput.write(headerTag)
                }
                currentCoroutineContext().ensureActive()
            }
            connection?.disconnect()
            connection = null
            Os.rename(staging.absolutePath, destination.absolutePath)
        } catch (failure: Throwable) {
            staging.delete()
            throw failure
        } finally {
            connection?.disconnect()
            dataKeyBytes?.fill(0)
        }
    }

    override suspend fun playableUrl(playlistId: Long, songId: Long): String? =
        withContext(Dispatchers.IO) {
            requireValidId(playlistId)
            requireValidId(songId)
            val file = audioFile(playlistId, songId)
            if (!file.isFile) return@withContext null
            RandomAccessFile(file, "r").use { input ->
                if (input.readInt() != FILE_MAGIC) return@withContext null
                val version = input.readUnsignedByte()
                if (version != FILE_VERSION) {
                    Log.w(
                        "KaloyAudio",
                        "Removing obsolete offline audio file for playlist=$playlistId song=$songId version=$version"
                    )
                    check(file.delete()) { "Impossible de supprimer un ancien fichier audio hors-ligne." }
                    return@withContext null
                }
                // Lecture séquentielle depuis l'offset 5 (après MAGIC+VERSION).
                if (input.readLong() != playlistId || input.readLong() != songId) {
                    Log.w(
                        "KaloyAudio",
                        "Removing mismatched offline audio file for playlist=$playlistId song=$songId"
                    )
                    check(file.delete()) {
                        "Impossible de supprimer un fichier audio hors-ligne incohérent."
                    }
                    return@withContext null
                }
            }
            offlineAudioUrl(playlistId, songId)
        }

    override suspend fun prewarm(playlistId: Long, songId: Long) {
        withContext(Dispatchers.IO) {
            val startedAtMs = android.os.SystemClock.elapsedRealtime()
            prewarmDataKey(appContext, playlistId, songId)
            Log.i(
                "KaloyAudio",
                "Offline prewarm completed in ${android.os.SystemClock.elapsedRealtime() - startedAtMs}ms"
            )
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        requireValidId(playlistId)
        val directory = validatedAudioDirectory()
        if (!directory.isDirectory) return@withContext
        val prefix = "$playlistId-"
        directory.listFiles()
            ?.filter { it.isFile && (it.name.startsWith(prefix) || it.name.startsWith(".$prefix")) }
            ?.forEach { file ->
                if (!file.delete()) throw IllegalStateException("Could not delete offline audio entry")
            }
    }

    internal fun audioFile(playlistId: Long, songId: Long): File {
        requireValidId(playlistId)
        requireValidId(songId)
        val parent = validatedAudioDirectory()
        val file = File(parent, "$playlistId-$songId.enc").canonicalFile
        check(file.parentFile == parent) { "Offline audio path escaped its private directory" }
        return file
    }

    private fun validatedAudioDirectory(): File {
        val privateRoot = appContext.filesDir.canonicalFile
        val directory = audioDirectory.canonicalFile
        check(directory.parentFile == privateRoot && directory.name == DIRECTORY_NAME) {
            "Offline audio directory must remain inside app-private storage"
        }
        return directory
    }

    private fun validateDownloadUrl(value: String): URL {
        require(value.isNotBlank()) { "Download URL must not be blank" }
        val url = URL(value)
        require(url.host.isNotBlank() && url.userInfo == null) { "Invalid audio download URL" }
        require(isSecureTransport(url)) {
            "Offline downloads require HTTPS or a private-network development server"
        }
        return url
    }

    private fun openResponse(initialUrl: URL): HttpURLConnection {
        var url = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
            }
            val status = connection.responseCode
            if (status in 200..299) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (status in REDIRECT_CODES && location != null && redirect < MAX_REDIRECTS) {
                url = URL(url, location)
                require(
                    isSecureTransport(url) &&
                        !(initialUrl.protocol.equals("https", ignoreCase = true) &&
                            url.protocol.equals("http", ignoreCase = true)) &&
                        url.userInfo == null
                ) {
                    "Redirected audio URL must use HTTPS or a private-network development server"
                }
            } else {
                throw IllegalStateException("Audio download failed with HTTP $status")
            }
        }
        error("Too many redirects for audio download")
    }

    private fun isSecureTransport(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) ||
            (url.protocol.equals("http", ignoreCase = true) && isPrivateHost(url.host))

    private fun isPrivateHost(host: String): Boolean {
        val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized == "::1") return true
        val octets = normalized.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 127)
    }

    private fun wrapDataKey(rawKey: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        }
        val nonce = requireNotNull(cipher.iv) { "Android Keystore did not generate an AES-GCM IV" }
        require(nonce.size == GCM_NONCE_BYTES) { "Android Keystore generated an invalid AES-GCM IV" }
        return nonce to cipher.doFinal(rawKey)
    }

    private fun unwrapDataKey(wrappedKey: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
        return cipher.doFinal(wrappedKey)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        cachedWrappingKey?.let { return it }
        return synchronized(wrappingKeyLock) {
            cachedWrappingKey ?: run {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                    val generator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    generator.init(
                        KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                    generator.generateKey()
                }
                cachedWrappingKey = key
                key
            }
        }
    }

    companion object {
        const val OFFLINE_SCHEME = "kaloy-offline"
        private const val DIRECTORY_NAME = "offline_audio"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "com.kaloy.offline.audio.wrap.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val NONCE_PREFIX_BYTES = 4
        private const val DATA_KEY_BYTES = 32
        private const val CHUNK_SIZE_BYTES = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val FILE_MAGIC = 0x4B414F46
        private const val FILE_VERSION = 2
        private const val HEADER_NONCE_INDEX = -1L
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        // Clé d'enveloppement : évite KeyStore.load() + KeyStore.getKey() (~200-500 ms TEE).
        @Volatile private var cachedWrappingKey: SecretKey? = null
        private val wrappingKeyLock = Any()

        // ExoPlayer peut rouvrir une piste pour inspecter le format ou effectuer un seek.
        // Garder la clé déchiffrée évite de solliciter à nouveau Android Keystore à chaque ouverture.
        private data class DataKeyEntry(
            val playlistId: Long,
            val songId: Long,
            val wrappedKey: ByteArray,
            val keySpec: javax.crypto.spec.SecretKeySpec
        )
        @Volatile private var cachedDataKey: DataKeyEntry? = null

        internal fun prewarmDataKey(context: Context, playlistId: Long, songId: Long) {
            try {
                val store = AndroidOfflineFileStore(context)
                val file = store.audioFile(playlistId, songId)
                if (!file.isFile) return
                RandomAccessFile(file, "r").use { readHeader(it, playlistId, songId, store) }
            } catch (_: Exception) { /* openDecryptedAudio gèrera l'erreur lors de l'ouverture réelle */ }
        }

        internal data class DecryptedAudio(val stream: InputStream, val length: Long)

        private data class AudioHeader(
            val noncePrefix: ByteArray,
            val plaintextLength: Long,
            val headerLength: Long,
            val key: javax.crypto.spec.SecretKeySpec
        )

        private fun readHeader(
            input: RandomAccessFile,
            playlistId: Long,
            songId: Long,
            store: AndroidOfflineFileStore
        ): AudioHeader {
            require(input.readInt() == FILE_MAGIC) { "Invalid offline audio file" }
            require(input.readUnsignedByte() == FILE_VERSION) {
                "Offline audio must be downloaded again to use the current format"
            }
            require(input.readLong() == playlistId && input.readLong() == songId) {
                "Offline audio identifiers do not match"
            }
            val wrapNonce = ByteArray(GCM_NONCE_BYTES).also(input::readFully)
            val wrappedKeyLength = input.readInt()
            require(wrappedKeyLength in (DATA_KEY_BYTES + GCM_TAG_BYTES)..256) {
                "Invalid offline audio key header"
            }
            val wrappedKey = ByteArray(wrappedKeyLength).also(input::readFully)
            val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(input::readFully)
            val chunkSize = input.readInt()
            require(chunkSize == CHUNK_SIZE_BYTES) { "Unsupported offline audio chunk size" }
            val plaintextLength = input.readLong()
            require(plaintextLength > 0L) { "Offline audio is empty" }
            val headerLength = headerSize(wrappedKeyLength)
            val chunkCount = (plaintextLength - 1L) / CHUNK_SIZE_BYTES + 1L
            val expectedFileLength = try {
                Math.addExact(
                    Math.addExact(headerLength, plaintextLength),
                    Math.multiplyExact(chunkCount, GCM_TAG_BYTES.toLong())
                )
            } catch (failure: ArithmeticException) {
                throw IllegalArgumentException("Invalid offline audio length", failure)
            }
            require(input.length() == expectedFileLength) { "Truncated or invalid offline audio file" }
            val header = encodeHeader(
                playlistId,
                songId,
                wrapNonce,
                wrappedKey,
                noncePrefix,
                plaintextLength
            )
            val headerTag = ByteArray(GCM_TAG_BYTES).also(input::readFully)

            val existing = cachedDataKey
            val key = if (existing != null &&
                existing.playlistId == playlistId &&
                existing.songId == songId &&
                existing.wrappedKey.contentEquals(wrappedKey)
            ) {
                existing.keySpec
            } else {
                val rawKey = store.unwrapDataKey(wrappedKey, wrapNonce)
                javax.crypto.spec.SecretKeySpec(rawKey, "AES").also { rawKey.fill(0) }
            }
            chunkCipher(Cipher.DECRYPT_MODE, key, noncePrefix, HEADER_NONCE_INDEX)
                .apply { updateAAD(header) }
                .doFinal(headerTag)
            cachedDataKey = DataKeyEntry(playlistId, songId, wrappedKey.copyOf(), key)
            input.seek(headerLength)
            return AudioHeader(noncePrefix, plaintextLength, headerLength, key)
        }

        private fun encodeHeader(
            playlistId: Long,
            songId: Long,
            wrapNonce: ByteArray,
            wrappedKey: ByteArray,
            noncePrefix: ByteArray,
            plaintextLength: Long
        ): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeByte(FILE_VERSION)
                output.writeLong(playlistId)
                output.writeLong(songId)
                output.write(wrapNonce)
                output.writeInt(wrappedKey.size)
                output.write(wrappedKey)
                output.write(noncePrefix)
                output.writeInt(CHUNK_SIZE_BYTES)
                output.writeLong(plaintextLength)
            }
            return bytes.toByteArray()
        }

        private fun headerSize(wrappedKeyLength: Int): Long =
            4L + 1L + 8L + 8L + GCM_NONCE_BYTES + 4L + wrappedKeyLength +
                NONCE_PREFIX_BYTES + 4L + 8L + GCM_TAG_BYTES

        private fun chunkCipher(
            mode: Int,
            key: javax.crypto.SecretKey,
            noncePrefix: ByteArray,
            chunkIndex: Long
        ): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, key, GCMParameterSpec(GCM_TAG_BITS, ByteBuffer.allocate(GCM_NONCE_BYTES)
                .put(noncePrefix)
                .putLong(chunkIndex)
                .array()))
        }

        private fun chunkAad(
            playlistId: Long,
            songId: Long,
            chunkIndex: Long,
            chunkLength: Int
        ): ByteArray = ByteBuffer.allocate(4 + 1 + 8 + 8 + 8 + 4)
            .putInt(FILE_MAGIC)
            .put(FILE_VERSION.toByte())
            .putLong(playlistId)
            .putLong(songId)
            .putLong(chunkIndex)
            .putInt(chunkLength)
            .array()

        private class ChunkedDecryptingInputStream(
            private val input: RandomAccessFile,
            private val header: AudioHeader,
            private val playlistId: Long,
            private val songId: Long,
            startPosition: Long
        ) : InputStream() {
            private var position = startPosition
            private var loadedChunkIndex = -1L
            private var decryptedChunk = ByteArray(0)
            private var chunkOffset = 0
            private var closed = false

            override fun read(): Int {
                val singleByte = ByteArray(1)
                val read = read(singleByte, 0, 1)
                return if (read < 0) -1 else singleByte[0].toInt() and 0xff
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (offset < 0 || length < 0 || length > buffer.size - offset) {
                    throw IndexOutOfBoundsException()
                }
                if (length == 0) return 0
                check(!closed) { "Offline audio stream is closed" }
                if (position >= header.plaintextLength) return -1

                val requestedChunkIndex = position / CHUNK_SIZE_BYTES
                if (requestedChunkIndex != loadedChunkIndex) loadChunk(requestedChunkIndex)
                val count = minOf(
                    length,
                    decryptedChunk.size - chunkOffset
                )
                decryptedChunk.copyInto(buffer, offset, chunkOffset, chunkOffset + count)
                chunkOffset += count
                position += count
                return count
            }

            private fun loadChunk(chunkIndex: Long) {
                decryptedChunk.fill(0)
                val plainStart = chunkIndex * CHUNK_SIZE_BYTES
                val plainLength = minOf(
                    CHUNK_SIZE_BYTES.toLong(),
                    header.plaintextLength - plainStart
                ).toInt()
                val encrypted = ByteArray(plainLength + GCM_TAG_BYTES)
                val recordSize = CHUNK_SIZE_BYTES.toLong() + GCM_TAG_BYTES
                input.seek(header.headerLength + chunkIndex * recordSize)
                input.readFully(encrypted)
                try {
                    decryptedChunk = chunkCipher(
                        Cipher.DECRYPT_MODE,
                        header.key,
                        header.noncePrefix,
                        chunkIndex
                    ).apply {
                        updateAAD(chunkAad(playlistId, songId, chunkIndex, plainLength))
                    }.doFinal(encrypted)
                } catch (failure: Exception) {
                    throw IOException("Offline audio block authentication failed", failure)
                } finally {
                    encrypted.fill(0)
                }
                loadedChunkIndex = chunkIndex
                chunkOffset = (position - plainStart).toInt()
            }

            override fun close() {
                if (closed) return
                closed = true
                decryptedChunk.fill(0)
                input.close()
            }
        }

        internal fun openDecryptedAudio(
            context: Context,
            playlistId: Long,
            songId: Long,
            position: Long = 0L
        ): DecryptedAudio {
            val store = AndroidOfflineFileStore(context)
            val file = store.audioFile(playlistId, songId)
            val input = RandomAccessFile(file, "r")
            try {
                val header = readHeader(input, playlistId, songId, store)
                require(position in 0L..header.plaintextLength) {
                    "Requested offline audio position is beyond end of file"
                }
                return DecryptedAudio(
                    ChunkedDecryptingInputStream(input, header, playlistId, songId, position),
                    header.plaintextLength
                )
            } catch (failure: Throwable) {
                input.close()
                throw failure
            }
        }

    }
}
