package com.kaloy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.paint
import kaloy.shared.generated.resources.Res
import kaloy.shared.generated.resources.fond_accueil
import org.jetbrains.compose.resources.painterResource

/**
 * Le fond photographique des ecrans d'entree.
 *
 * S'emploie a la place d'un `.background(...)`, sur le conteneur racine :
 *
 *     Scaffold(
 *         modifier = Modifier.fondKaloy(),
 *         containerColor = Color.Transparent,
 *
 * Un Modifier et non un composable enveloppant, et c'est delibere : envelopper
 * aurait demande de deplacer la fermeture des accolades de chaque ecran, ce
 * qui est le genre de modification ou l'on casse une mise en page sans s'en
 * apercevoir. Ici chaque ecran change d'une ligne.
 *
 * Reserve a l'accueil, a la recherche et aux deux ecrans d'authentification.
 * Pas aux listes ni aux formulaires : une photo derriere un contenu qui defile
 * fait varier le contraste du texte selon ce qui passe dessous, et l'ecran
 * devient illisible par endroits sans qu'on puisse le prevoir. Le reste de
 * l'application garde l'aplat bleu nuit.
 *
 * @param voile opacite du voile a mi-hauteur. Au-dela de 0,9 la photo
 *   disparait ; en dessous de 0,7 le texte commence a lutter contre elle.
 */
@Composable
fun Modifier.fondKaloy(voile: Float = 0.82f): Modifier = this
    // L'aplat bleu nuit vient AVANT la photo, et non a sa place : si la
    // ressource manque ou met du temps a se decoder, l'ecran reste du bon
    // bleu au lieu de clignoter en blanc.
    .background(KaloyNuit)
    .paint(
        painter = painterResource(Res.drawable.fond_accueil),
        contentScale = ContentScale.Crop,
        // Cadrage par le haut : la photo est en portrait et son sujet occupe
        // la moitie superieure. Centree, un ecran large lui couperait la tete.
        alignment = Alignment.TopCenter
    )
    // Le voile s'alourdit vers le bas. En haut la photo respire, en bas le
    // contenu -- champs, boutons, listes -- se lit sur un fond presque uni.
    // Un voile uniforme obligeait a choisir entre les deux.
    .background(
        Brush.verticalGradient(
            listOf(
                KaloyNuit.copy(alpha = voile * 0.70f),
                KaloyNuit.copy(alpha = voile),
                KaloyNuit.copy(alpha = 0.97f)
            )
        )
    )
