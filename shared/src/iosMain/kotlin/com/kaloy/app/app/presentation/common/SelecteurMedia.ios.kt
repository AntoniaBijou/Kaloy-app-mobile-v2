package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSMakeRange
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.subdataWithRange
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Selecteur de media iOS : photo ou video, appareil photo ou photothèque.
 *
 * Ce fichier ne faisait rien jusqu'au 29/10/2026 : toutes ses fonctions
 * renvoyaient null, et les boutons « Ajouter une photo ou une video » ne
 * produisaient aucun effet. C'etait assume faute de Mac pour verifier.
 *
 * Il suit le meme schema que [rememberSingleImagePicker], qui marche deja pour
 * la photo de profil : un UIImagePickerController, un delegue conserve entre
 * recompositions, et le fichier ecrit dans notre propre dossier.
 *
 * Le contrat commun ne transporte PAS les octets, seulement une reference.
 * C'est ce qui permet d'envoyer une video de 400 Mo par tranches sans jamais la
 * charger entiere en memoire.
 *
 * NON VERIFIE A L'EXECUTION : ecrit sur Windows, compile, jamais lance. Les
 * autorisations NSCameraUsageDescription et NSPhotoLibraryUsageDescription sont
 * en place dans Info.plist — sans elles iOS ferme l'application au lieu de
 * demander l'acces.
 */

/** Dossier des envois en cours, dans le stockage prive de l'application. */
@OptIn(ExperimentalForeignApi::class)
private fun dossierEnvois(): String? {
    val base = NSFileManager.defaultManager.URLsForDirectory(
        directory = platform.Foundation.NSDocumentDirectory,
        inDomains = platform.Foundation.NSUserDomainMask
    ).firstOrNull() as? NSURL ?: return null

    val dossier = base.URLByAppendingPathComponent("envois") ?: return null
    val chemin = dossier.path ?: return null

    if (!NSFileManager.defaultManager.fileExistsAtPath(chemin)) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = chemin,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }
    return chemin
}

@Composable
actual fun rememberSelecteurMedia(
    onMediaSelectionne: (MediaSelectionne?) -> Unit
): (SourceMedia) -> Unit {

    // Le delegue doit survivre a la presentation du controleur : sans reference
    // conservee il serait libere avant que l'utilisateur ait choisi, et le
    // rappel n'arriverait jamais.
    val delegue = remember { DelegueSelecteurMedia() }

    return remember(delegue) {
        { source ->
            delegue.onResultat = onMediaSelectionne

            val racine = UIApplication.sharedApplication.keyWindow?.rootViewController
            val appareil = source == SourceMedia.PHOTO_CAMERA || source == SourceMedia.VIDEO_CAMERA
            val typeSource = if (appareil) {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            } else {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }

            when {
                racine == null -> onMediaSelectionne(null)

                // Le simulateur n'a pas d'appareil photo : sans ce test, la
                // presentation echoue et l'ecran reste muet.
                !UIImagePickerController.isSourceTypeAvailable(typeSource) ->
                    onMediaSelectionne(null)

                else -> {
                    val controleur = UIImagePickerController()
                    controleur.sourceType = typeSource
                    // Sans mediaTypes, le selecteur ne propose que des images,
                    // meme quand on lui demande une video.
                    controleur.mediaTypes = listOf(
                        if (source == SourceMedia.VIDEO_CAMERA || source == SourceMedia.VIDEO_GALERIE) {
                            "public.movie"
                        } else {
                            "public.image"
                        }
                    )
                    controleur.delegate = delegue
                    racine.presentViewController(controleur, animated = true, completion = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DelegueSelecteurMedia :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onResultat: ((MediaSelectionne?) -> Unit)? = null

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        // Une video arrive sous forme d'URL de fichier, une photo sous forme
        // d'UIImage en memoire : deux chemins distincts.
        val urlVideo = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage

        val media = when {
            urlVideo != null -> deplacerVideo(urlVideo)
            image != null -> enregistrerPhoto(image)
            else -> null
        }

        picker.dismissViewControllerAnimated(true) {
            onResultat?.invoke(media)
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            onResultat?.invoke(null)
        }
    }

    /**
     * Deplace la video hors du dossier temporaire d'iOS.
     *
     * iOS y depose la capture et peut la purger sans prevenir. Une video de
     * 400 Mo en cours d'envoi par morceaux doit survivre a cette purge, et meme
     * a la fermeture de l'application : la reprise relit le fichier.
     */
    private fun deplacerVideo(source: NSURL): MediaSelectionne? {
        val dossier = dossierEnvois() ?: return null
        val nom = "video_${NSUUID().UUIDString()}.mov"
        val destination = "$dossier/$nom"

        val deplace = NSFileManager.defaultManager.moveItemAtPath(
            srcPath = source.path ?: return null,
            toPath = destination,
            error = null
        )
        if (!deplace) return null

        return MediaSelectionne(
            uri = destination,
            nomFichier = nom,
            typeMime = "video/quicktime",
            taille = tailleFichier(destination)
        )
    }

    /** 0.85 : meme compromis poids / qualite que pour la photo de profil. */
    private fun enregistrerPhoto(image: UIImage): MediaSelectionne? {
        val donnees = UIImageJPEGRepresentation(image, 0.85) ?: return null
        val dossier = dossierEnvois() ?: return null
        val nom = "photo_${NSUUID().UUIDString()}.jpg"
        val destination = "$dossier/$nom"

        if (!donnees.writeToFile(destination, atomically = true)) return null

        return MediaSelectionne(
            uri = destination,
            nomFichier = nom,
            typeMime = "image/jpeg",
            taille = tailleFichier(destination)
        )
    }
}

/** Taille en octets, ou 0 si le fichier est illisible. */
@OptIn(ExperimentalForeignApi::class)
private fun tailleFichier(chemin: String): Long {
    val attributs = NSFileManager.defaultManager.attributesOfItemAtPath(chemin, null)
    val taille = attributs?.get(platform.Foundation.NSFileSize) as? NSNumber
    return taille?.longLongValue ?: 0L
}

/**
 * Sur iOS le fichier est deja dans notre dossier : rien a copier.
 *
 * Le pendant Android doit recopier depuis un content:// ; ici le selecteur a
 * ecrit directement ou il fallait.
 */
actual suspend fun copierEnLocal(media: MediaSelectionne): String? = withContext(Dispatchers.Default) {
    if (NSFileManager.defaultManager.fileExistsAtPath(media.uri)) media.uri else null
}

/**
 * Lit une tranche du fichier, sans charger le reste.
 *
 * NSFileHandle et son seek plutot qu'un NSData complet : sur une video de
 * 400 Mo, lire le fichier entier pour en extraire 2 Mo ferait tomber
 * l'application pour depassement memoire.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun lireMorceau(chemin: String, decalage: Long, longueur: Int): ByteArray? =
    withContext(Dispatchers.Default) {
        // NSDataReadingMappedIfSafe : le fichier est projete en memoire
        // virtuelle, pas copie. Lire 2 Mo dans une video de 400 Mo ne coute
        // donc que ces 2 Mo, et non 400.
        val donnees = NSData.dataWithContentsOfFile(
            path = chemin,
            options = NSDataReadingMappedIfSafe,
            error = null
        ) ?: return@withContext null

        val total = donnees.length.toLong()
        if (decalage >= total) return@withContext null

        // Le dernier morceau est plus court que les autres : on ne demande
        // jamais au-dela de la fin, sinon subdataWithRange leve une exception.
        val aLire = minOf(longueur.toLong(), total - decalage).toInt()
        if (aLire <= 0) return@withContext null

        val tranche = donnees.subdataWithRange(
            NSMakeRange(decalage.toULong(), aLire.toULong())
        )
        tranche.versOctets(aLire)
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.versOctets(taille: Int): ByteArray {
    val octets = ByteArray(taille)
    octets.usePinned { epingle ->
        memcpy(epingle.addressOf(0), this.bytes, taille.toULong())
    }
    return octets
}

@OptIn(ExperimentalForeignApi::class)
actual fun supprimerCopie(chemin: String) {
    NSFileManager.defaultManager.removeItemAtPath(chemin, null)
}

/**
 * Espace libre, pour refuser un envoi qui ne tiendrait pas.
 *
 * Renvoyer 0 ferait echouer toute verification d'espace : en cas de doute on
 * renvoie une valeur tres grande, et c'est l'ecriture qui echouera le cas
 * echeant — un refus a tort serait plus genant qu'un echec tardif.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun espaceLibre(): Long {
    val dossier = dossierEnvois() ?: return Long.MAX_VALUE
    val attributs = NSFileManager.defaultManager.attributesOfFileSystemForPath(dossier, null)
    val libre = attributs?.get(NSFileSystemFreeSize) as? NSNumber
    return libre?.longLongValue ?: Long.MAX_VALUE
}
