package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * Choix d'une image dans la photothèque, cote iOS.
 *
 * Cette implementation renvoyait auparavant null sans rien afficher : changer
 * sa photo de profil ne faisait rien sur iPhone, en silence.
 *
 * Le contrat commun attend une CHAINE, pas des octets — contrairement a
 * [rememberSelecteurMedia], qui sert aux photos d'evenement envoyees au
 * serveur. On ecrit donc l'image dans le dossier temporaire et on renvoie son
 * chemin, ce qui reproduit le comportement Android, lequel renvoie une URI
 * locale. Les deux plateformes partagent du coup la meme limite, deja
 * consignee : une photo de profil choisie ainsi n'est visible que sur
 * l'appareil qui l'a choisie, puisque rien n'est televerse.
 *
 * NON VERIFIE A L'EXECUTION : compile, mais jamais lance faute de macOS.
 * Info.plist doit declarer NSPhotoLibraryUsageDescription, sans quoi iOS ferme
 * l'application a l'ouverture du selecteur.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberSingleImagePicker(
    onImageSelected: (String?) -> Unit
): () -> Unit {

    // Le delegue doit survivre a la presentation du controleur : sans reference
    // conservee, il serait libere avant que l'utilisateur ait choisi.
    val delegue = remember { DelegueImageUnique() }

    return remember(delegue) {
        {
            delegue.onResultat = onImageSelected

            val controleur = UIImagePickerController()
            controleur.sourceType =
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            controleur.delegate = delegue

            val racine = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (racine == null) {
                onImageSelected(null)
            } else {
                racine.presentViewController(controleur, animated = true, completion = null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DelegueImageUnique :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onResultat: ((String?) -> Unit)? = null

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val chemin = image?.let { enregistrerEnTemporaire(it) }

        picker.dismissViewControllerAnimated(true) {
            onResultat?.invoke(chemin)
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            onResultat?.invoke(null)
        }
    }

    /**
     * Ecrit l'image choisie dans le dossier temporaire et renvoie son URL.
     *
     * Le nom tire d'un UUID evite qu'une seconde photo ecrase la premiere avant
     * que l'interface ait eu le temps de l'afficher.
     */
    private fun enregistrerEnTemporaire(image: UIImage): String? {
        // 0.85 : meme compromis poids / qualite que pour les photos d'evenement.
        val donnees = UIImageJPEGRepresentation(image, 0.85) ?: return null
        val nom = "profil_${NSUUID().UUIDString()}.jpg"
        val chemin = NSTemporaryDirectory() + nom
        val ecrit = donnees.writeToFile(chemin, atomically = true)
        return if (ecrit) NSURL.fileURLWithPath(chemin).absoluteString else null
    }
}

@Composable
actual fun rememberSingleImagePickerWithBytes(
    onImageSelected: (uri: String?, bytes: ByteArray?) -> Unit
): () -> Unit = { }
