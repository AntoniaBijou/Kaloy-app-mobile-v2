package com.kaloy.app.presentation.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.koin.mp.KoinPlatform
import java.io.RandomAccessFile

/**
 * Android : la camera ecrit dans un fichier expose par le FileProvider (voir le
 * manifeste), la galerie passe par le selecteur systeme. Dans les deux cas on ne
 * renvoie qu'une reference — l'URI, le type et la taille — jamais le contenu.
 *
 * Les captures video et photo vont directement dans le dossier d'envois, ce qui
 * evite une copie de 500 Mo : le fichier est deja la ou il doit etre.
 */


/**
 * Contexte Android hors composable.
 *
 * copierEnLocal, supprimerCopie et espaceLibre sont des fonctions de haut
 * niveau, sans LocalContext a disposition. Koin porte deja le contexte, pose
 * par androidContext() au demarrage dans MainActivity.
 */
private fun contexteApplication(): Context? =
    try {
        KoinPlatform.getKoin().get<Context>()
    } catch (_: Exception) {
        null
    }

/** Dossier des copies en cours d'envoi, dans le stockage prive. */
private fun dossierEnvois(contexte: Context): File =
    File(contexte.filesDir, "envois").apply { mkdirs() }

@Composable
actual fun rememberSelecteurMedia(
    onMediaSelectionne: (MediaSelectionne?) -> Unit
): (SourceMedia) -> Unit {
    val contexte = LocalContext.current

    // Destination de la capture, et son type. Conservees entre recompositions :
    // le resultat de la camera arrive apres coup et ne porte pas l'URI.
    val captureEnCours = remember { arrayOfNulls<Capture>(1) }

    val lanceurPhotoCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { reussi -> onMediaSelectionne(resultatCapture(reussi, captureEnCours[0])) }
    )

    val lanceurVideoCamera = rememberLauncherForActivityResult(
        // CaptureVideo et non TakePicture : sans quoi la camera rend une image.
        contract = ActivityResultContracts.CaptureVideo(),
        onResult = { reussi -> onMediaSelectionne(resultatCapture(reussi, captureEnCours[0])) }
    )

    val lanceurGalerie = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> onMediaSelectionne(uri?.let { decrire(contexte, it) }) }
    )

    return remember(lanceurPhotoCamera, lanceurVideoCamera, lanceurGalerie, contexte) {
        { source ->
            when (source) {
                // Un echec de preparation previent l'appelant par null, comme
                // une annulation : il affichera son message au lieu de rester
                // muet sur un bouton qui n'a rien fait.
                SourceMedia.PHOTO_CAMERA ->
                    if (!lancerCapture(contexte, captureEnCours, lanceurPhotoCamera::launch,
                            ".jpg", "image/jpeg")) {
                        onMediaSelectionne(null)
                    }

                SourceMedia.VIDEO_CAMERA ->
                    if (!lancerCapture(contexte, captureEnCours, lanceurVideoCamera::launch,
                            ".mp4", "video/mp4")) {
                        onMediaSelectionne(null)
                    }

                SourceMedia.PHOTO_GALERIE -> lanceurGalerie.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )

                SourceMedia.VIDEO_GALERIE -> lanceurGalerie.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        }
    }
}

/** Capture en attente : le fichier cree et le type qu'on en attend. */
private class Capture(val fichier: File, val typeMime: String)

/**
 * Prepare le fichier de capture et ouvre la camera.
 *
 * Le try/catch n'est pas decoratif : getUriForFile leve une
 * IllegalArgumentException si le dossier n'est pas declare dans file_paths.xml,
 * et une exception dans un gestionnaire de clic Compose ferme l'application
 * instantanement. C'est exactement ce qui s'est produit quand la capture est
 * passee de cacheDir a filesDir sans mettre file_paths.xml a jour.
 *
 * On echoue donc comme le reste du selecteur : en ne faisant rien.
 */
private fun lancerCapture(
    contexte: Context,
    emplacement: Array<Capture?>,
    lancer: (Uri) -> Unit,
    extension: String,
    typeMime: String
): Boolean = try {
    val fichier = File(dossierEnvois(contexte), "capture_${System.currentTimeMillis()}$extension")
    emplacement[0] = Capture(fichier, typeMime)
    lancer(
        FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
    )
    true
} catch (_: Exception) {
    emplacement[0] = null
    false
}

private fun resultatCapture(reussi: Boolean, capture: Capture?): MediaSelectionne? {
    if (!reussi || capture == null || !capture.fichier.exists()) {
        capture?.fichier?.delete()
        return null
    }
    return MediaSelectionne(
        // Un chemin direct, pas une URI content:// : le fichier est deja dans
        // notre stockage prive, donc copierEnLocal n'aura rien a faire.
        uri = capture.fichier.absolutePath,
        nomFichier = capture.fichier.name,
        typeMime = capture.typeMime,
        taille = capture.fichier.length()
    )
}

/**
 * Lit le type, le nom et la taille sans ouvrir le contenu.
 *
 * Le type declare par le systeme est conserve tel quel. L'ancienne version
 * forcait tout type inconnu en image/jpeg, ce qui aurait transforme une video en
 * photo et fait refuser l'envoi par le serveur.
 */
private fun decrire(contexte: Context, uri: Uri): MediaSelectionne? = try {
    val type = contexte.contentResolver.getType(uri) ?: return null
    var nom = if (type.startsWith("video/")) "video" else "photo"
    var taille = 0L

    contexte.contentResolver.query(uri, null, null, null, null)?.use { curseur ->
        if (curseur.moveToFirst()) {
            val colonneNom = curseur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (colonneNom >= 0 && !curseur.isNull(colonneNom)) {
                nom = curseur.getString(colonneNom)
            }
            val colonneTaille = curseur.getColumnIndex(OpenableColumns.SIZE)
            if (colonneTaille >= 0 && !curseur.isNull(colonneTaille)) {
                taille = curseur.getLong(colonneTaille)
            }
        }
    }

    if (taille <= 0L) null else MediaSelectionne(uri.toString(), nom, type, taille)
} catch (_: Exception) {
    null
}

actual suspend fun copierEnLocal(media: MediaSelectionne): String? = withContext(Dispatchers.IO) {
    // Une capture est deja dans notre dossier : rien a copier. C'est ce qui
    // evite de dupliquer 500 Mo juste apres les avoir filmes.
    if (!media.uri.startsWith("content://")) {
        return@withContext media.uri.takeIf { File(it).exists() }
    }

    val contexte = contexteApplication() ?: return@withContext null
    val destination = File(dossierEnvois(contexte), "envoi_${System.currentTimeMillis()}_${media.nomFichier}")

    try {
        contexte.contentResolver.openInputStream(Uri.parse(media.uri))?.use { entree ->
            destination.outputStream().use { sortie -> entree.copyTo(sortie, 1 shl 16) }
        } ?: return@withContext null
        destination.absolutePath
    } catch (_: Exception) {
        destination.delete()
        null
    }
}

actual suspend fun lireMorceau(chemin: String, decalage: Long, longueur: Int): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            // RandomAccessFile plutot qu'un flux reouvert et saute a chaque appel :
            // sur 250 morceaux, sauter depuis le debut chaque fois serait quadratique.
            RandomAccessFile(File(chemin), "r").use { fichier ->
                fichier.seek(decalage)
                val tampon = ByteArray(longueur)
                val lus = fichier.read(tampon)
                when {
                    lus <= 0 -> null
                    lus == longueur -> tampon
                    // Dernier morceau : plus court que les autres.
                    else -> tampon.copyOf(lus)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

actual fun supprimerCopie(chemin: String) {
    try {
        File(chemin).delete()
    } catch (_: Exception) {
        // Sans consequence : la purge du dossier d'envois repassera.
    }
}

actual fun espaceLibre(): Long {
    val contexte = contexteApplication() ?: return 0L
    return try {
        contexte.filesDir.usableSpace
    } catch (_: Exception) {
        0L
    }
}
