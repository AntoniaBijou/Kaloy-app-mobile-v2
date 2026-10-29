package com.kaloy.app.core.offline

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream

internal class OfflineAudioDataSourceFactory(
    context: Context,
    httpDataSourceFactory: DataSource.Factory
) : DataSource.Factory {
    private val appContext = context.applicationContext
    private val defaultFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    override fun createDataSource(): DataSource = SwitchingDataSource(appContext, defaultFactory)
}

private class SwitchingDataSource(
    private val context: Context,
    private val defaultFactory: DataSource.Factory
) : DataSource {
    private var current: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == AndroidOfflineFileStore.OFFLINE_SCHEME) {
            DecryptingOfflineDataSource(context)
        } else {
            defaultFactory.createDataSource()
        }
        transferListeners.forEach(source::addTransferListener)
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: throw IOException("Data source is not open")

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        current?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = current
        current = null
        source?.close()
    }
}

private class DecryptingOfflineDataSource(private val context: Context) : DataSource {
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var remainingBytes = C.LENGTH_UNSET.toLong()
    private var bytesUntilFileEnd = 0L

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        val startedAtMs = SystemClock.elapsedRealtime()
        val requestedUri = dataSpec.uri
        if (requestedUri.scheme != AndroidOfflineFileStore.OFFLINE_SCHEME ||
            requestedUri.host != "audio" ||
            requestedUri.query != null ||
            requestedUri.fragment != null ||
            requestedUri.userInfo != null
        ) {
            throw IOException("Invalid offline audio URI")
        }
        val path = requestedUri.pathSegments
        if (path.size != 2) throw IOException("Invalid offline audio URI")
        val playlistId = path[0].toLongOrNull()?.takeIf { it > 0L }
            ?: throw IOException("Invalid offline playlist identifier")
        val songId = path[1].toLongOrNull()?.takeIf { it > 0L }
            ?: throw IOException("Invalid offline song identifier")

        val decrypted = try {
            AndroidOfflineFileStore.openDecryptedAudio(
                context,
                playlistId,
                songId,
                dataSpec.position
            )
        } catch (failure: Exception) {
            throw IOException(
                failure.message ?: "Could not open offline audio",
                failure
            )
        }
        val source = decrypted.stream
        try {
            uri = requestedUri
            stream = source
            bytesUntilFileEnd = decrypted.length - dataSpec.position
            remainingBytes = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                bytesUntilFileEnd
            } else {
                minOf(bytesUntilFileEnd, dataSpec.length)
            }
            if (bytesUntilFileEnd == 0L) verifyEndOfStream()
            Log.i(
                "KaloyAudio",
                "Offline data source opened in ${SystemClock.elapsedRealtime() - startedAtMs}ms"
            )
            return remainingBytes
        } catch (failure: Throwable) {
            source.close()
            throw failure
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remainingBytes == 0L) return C.RESULT_END_OF_INPUT
        val input = stream ?: throw IOException("Data source is not open")
        val requested = minOf(
            length.toLong(),
            remainingBytes,
            bytesUntilFileEnd
        ).toInt()
        val count = input.read(buffer, offset, requested)
        if (count < 0) throw IOException("Offline audio ended before its authenticated payload")
        remainingBytes -= count
        bytesUntilFileEnd -= count
        if (bytesUntilFileEnd == 0L) verifyEndOfStream()
        return count
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        try {
            stream?.close()
        } finally {
            stream = null
            uri = null
            remainingBytes = C.LENGTH_UNSET.toLong()
            bytesUntilFileEnd = 0L
        }
    }

    private fun verifyEndOfStream() {
        val input = stream ?: return
        if (input.read() != -1) throw IOException("Offline audio contains trailing plaintext")
    }
}
