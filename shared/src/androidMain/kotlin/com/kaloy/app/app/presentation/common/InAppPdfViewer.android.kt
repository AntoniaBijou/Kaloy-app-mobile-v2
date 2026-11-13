package com.kaloy.app.presentation.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
actual fun InAppPdfViewer(url: String, modifier: Modifier) {
    var state by remember(url) { mutableStateOf<PdfViewerState>(PdfViewerState.Loading) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        state = PdfViewerState.Loading
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "solfa_temp.pdf")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.connect()
                cacheFile.outputStream().use { out -> conn.inputStream.copyTo(out) }
                conn.disconnect()

                val fd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pages = mutableListOf<ImageBitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    try {
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        Canvas(bitmap).drawColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bitmap.asImageBitmap())
                    } finally {
                        page.close()
                    }
                }
                renderer.close()
                fd.close()
                state = PdfViewerState.Success(pages)
            } catch (e: Exception) {
                state = PdfViewerState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    when (val s = state) {
        PdfViewerState.Loading -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = KaloyPurple)
        }
        is PdfViewerState.Error -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Impossible d'afficher la partition",
                color = KaloyTextPrimary,
                textAlign = TextAlign.Center
            )
        }
        is PdfViewerState.Success -> LazyColumn(modifier = modifier) {
            items(s.pages) { page ->
                Image(
                    bitmap = page,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private sealed class PdfViewerState {
    object Loading : PdfViewerState()
    data class Error(val message: String) : PdfViewerState()
    data class Success(val pages: List<ImageBitmap>) : PdfViewerState()
}
