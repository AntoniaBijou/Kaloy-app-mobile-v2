package com.kaloy.app.presentation.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android : la camera ecrit dans un fichier temporaire expose par un
 * FileProvider (voir le manifeste), la galerie passe par le selecteur systeme.
 * Dans les deux cas on relit ensuite les octets via le ContentResolver.
 */
@Composable
actual fun rememberSelecteurMedia(
    onMediaChoisi: (MediaChoisi?) -> Unit
): (SourceMedia) -> Unit {
    val contexte = LocalContext.current

    // Destination de la prise de vue. Conservee entre les recompositions : le
    // resultat de la camera arrive apres coup et ne porte pas l'URI.
    val uriPhoto = remember { arrayOfNulls<Uri>(1) }

    val lanceurCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { reussi ->
            val uri = uriPhoto[0]
            if (reussi && uri != null) {
                onMediaChoisi(lireMedia(contexte, uri, "photo.jpg", "image/jpeg"))
            } else {
                onMediaChoisi(null)
            }
        }
    )

    val lanceurGalerie = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri == null) {
                onMediaChoisi(null)
            } else {
                val type = contexte.contentResolver.getType(uri) ?: "image/jpeg"
                onMediaChoisi(lireMedia(contexte, uri, "galerie.jpg", type))
            }
        }
    )

    return remember(lanceurCamera, lanceurGalerie, contexte) {
        { source ->
            when (source) {
                SourceMedia.CAMERA -> {
                    val fichier = File(contexte.cacheDir, "prise_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        contexte,
                        "${contexte.packageName}.fileprovider",
                        fichier
                    )
                    uriPhoto[0] = uri
                    lanceurCamera.launch(uri)
                }

                SourceMedia.GALERIE -> lanceurGalerie.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }
}

/**
 * Lit le contenu pointe par [uri]. Renvoie null plutot que de propager
 * l'exception : l'appelant affiche un message, il n'y a rien a rattraper.
 */
private fun lireMedia(
    contexte: Context,
    uri: Uri,
    nomParDefaut: String,
    typeMime: String
): MediaChoisi? = try {
    contexte.contentResolver.openInputStream(uri)?.use { flux ->
        MediaChoisi(
            octets = flux.readBytes(),
            nomFichier = nomParDefaut,
            // Le backend n'accepte que ces trois formats : tout autre type
            // (HEIC de certains telephones, par exemple) serait refuse en 400.
            typeMime = when (typeMime) {
                "image/png", "image/webp", "image/jpeg" -> typeMime
                else -> "image/jpeg"
            }
        )
    }
} catch (_: Exception) {
    null
}
