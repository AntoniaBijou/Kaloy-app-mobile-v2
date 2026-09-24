package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS : UIImagePickerController couvre a la fois l'appareil photo et la
 * photothèque. L'image est convertie en JPEG, format accepte par le backend.
 *
 * Le fichier Info.plist doit declarer NSCameraUsageDescription et
 * NSPhotoLibraryUsageDescription, sans quoi iOS ferme l'application a
 * l'ouverture du selecteur.
 *
 * Non verifiable depuis Windows : ce code n'a pas ete compile (la compilation
 * iOS exige Xcode sur macOS).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberSelecteurMedia(
    onMediaChoisi: (MediaChoisi?) -> Unit
): (SourceMedia) -> Unit {

    // Le delegue doit survivre a la presentation du controleur : sans reference
    // conservee, il serait libere avant que l'utilisateur ait choisi sa photo.
    val delegue = remember { DelegueSelecteurPhoto() }

    return remember(delegue) {
        { source ->
            delegue.onResultat = onMediaChoisi

            val controleur = UIImagePickerController()
            controleur.sourceType = when (source) {
                SourceMedia.CAMERA -> {
                    // Le simulateur n'a pas d'appareil photo : on retombe sur
                    // la photothèque plutot que d'afficher un ecran noir.
                    if (UIImagePickerController.isSourceTypeAvailable(
                            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                        )
                    ) {
                        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    } else {
                        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    }
                }

                SourceMedia.GALERIE ->
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }
            controleur.delegate = delegue

            val racine = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (racine == null) {
                onMediaChoisi(null)
            } else {
                racine.presentViewController(controleur, animated = true, completion = null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DelegueSelecteurPhoto :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onResultat: ((MediaChoisi?) -> Unit)? = null

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        // 0.85 : compromis habituel entre poids du fichier et qualite visible.
        val donnees = image?.let { UIImageJPEGRepresentation(it, 0.85) }

        picker.dismissViewControllerAnimated(true) {
            onResultat?.invoke(
                donnees?.let {
                    MediaChoisi(
                        octets = it.versOctets(),
                        nomFichier = "photo.jpg",
                        typeMime = "image/jpeg"
                    )
                }
            )
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            onResultat?.invoke(null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.versOctets(): ByteArray {
    val taille = length.toInt()
    if (taille == 0) return ByteArray(0)
    val octets = ByteArray(taille)
    octets.usePinned { epingle ->
        memcpy(epingle.addressOf(0), this.bytes, this.length)
    }
    return octets
}
