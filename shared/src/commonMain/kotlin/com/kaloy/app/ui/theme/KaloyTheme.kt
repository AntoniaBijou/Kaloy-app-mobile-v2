package com.kaloy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================
// La palette, telle qu'elle a ete donnee
// ============================================================
//
// Ces cinq couleurs sont la source : tout le reste du fichier en derive.
// Aucune autre valeur ne doit etre inventee ailleurs dans l'application.

/** Bleu nuit tres fonce. Le fond de l'application. */
val KaloyNuit = Color(0xFF151633)

/** Creme tres clair. Le texte principal, et les surfaces claires. */
val KaloyCreme = Color(0xFFFCF4E8)

/** Bleu indigo. La couleur principale : boutons, selections, liens. */
val KaloyIndigo = Color(0xFF32317F)

/** Orange vif. L'accent, a doser : il attire l'oeil partout ou il est mis. */
val KaloyOrangeVif = Color(0xFFE85A04)

/** Beige sable. Les textes secondaires et les surfaces chaudes. */
val KaloySable = Color(0xFFEFE4CF)

// ============================================================
// Les roles, sous leurs anciens noms
// ============================================================
//
// Les noms ne changent pas, seules les valeurs changent. C'est voulu : ces
// 17 constantes sont utilisees 959 fois dans 40 fichiers, et une douzaine de
// branches sont ouvertes sur le depot. Renommer aurait produit un diff
// mecanique entrant en conflit avec chacune d'elles, pour un resultat visuel
// identique.
//
// La contrepartie est que les noms mentent desormais : KaloyPurple n'est plus
// violet, KaloyPink n'est plus rose, KaloyCyan n'est plus cyan. Le
// renommage reste a faire, une fois les branches en cours fusionnees.

// --- Surfaces, du fond vers l'avant -------------------------------------
//
// Les trois surfaces sont des melanges du bleu nuit vers l'indigo, de plus en
// plus clairs. Un degrade d'une seule famille plutot que des gris : sur un
// fond colore, un gris neutre parait sale.
val KaloyDarkBg = KaloyNuit
val KaloyDarkSurface = Color(0xFF191A3E)
val KaloyDarkCard = Color(0xFF1E1E4A)
val KaloyDarkElevated = Color(0xFF25255D)

// Les cartes posees SUR LA PHOTO de fond. Mesure sur les vrais pixels :
// a 60 pour cent, la photo se devine sans que le texte y perde -- a
// condition que les teintes faibles qu'elles portent soient remplacees
// par KaloyTextSecondary et KaloySable. KaloyTextMuted y tombe a 2,85,
// sous le seuil de 4,5, alors qu'il tient 4,59 sur une carte opaque.
//
// A n'employer que sur les ecrans qui portent la photo. Ailleurs le fond
// est un aplat : une carte voilee n'y laisserait rien voir.
//
// Les dialogues gardent la carte opaque : on verrait la page au travers.
val KaloyCarteVoilee = KaloyDarkCard.copy(alpha = 0.60f)
val KaloyCarteVoileeHaute = KaloyDarkElevated.copy(alpha = 0.60f)

// --- La couleur principale ----------------------------------------------
val KaloyPurple = KaloyIndigo
/**
 * Indigo eclairci, pour un texte ou une icone posee sur un fond sombre.
 *
 * La premiere valeur essayee, 0xFF7B7AD6, passait sur le fond nuit avec
 * 4,69 mais tombait a 4,18 sur une carte -- sous le seuil de 4,5. Or cette
 * couleur sert de libelle de champ sur l'ecran de connexion, c'est-a-dire
 * de texte, et ces champs sont poses sur une carte. Eclaircie, elle tient
 * les trois surfaces : 5,97 sur le fond, 5,32 sur une carte, 4,75 sur une
 * surface elevee.
 */
val KaloyPurpleLight = Color(0xFF908EE0)
/** Indigo assombri, pour les conteneurs : un bouton pose dessus ressort. */
val KaloyPurpleDark = Color(0xFF22215A)

// --- L'accent chaud ------------------------------------------------------
//
// Deux tons et non un seul : plusieurs ecrans superposent KaloyPink et
// KaloyOrange dans un meme degrade, et leur donner la meme valeur aurait
// aplati ces degrades sans que rien ne le signale.
val KaloyOrange = KaloyOrangeVif
val KaloyPink = Color(0xFFFF8A3D)
val KaloyPinkLight = Color(0xFFFFB37A)

// --- Le troisieme accent -------------------------------------------------
//
// Un indigo plus clair, et non le sable comme la palette le suggerait.
// Raison mesuree : KaloyCyan sert de FOND DE BOUTON a deux endroits, avec un
// libelle blanc pose dessus. En sable, ce libelle serait devenu invisible.
val KaloyCyan = Color(0xFF4F4EB0)
val KaloyCyanLight = Color(0xFF8584DE)

// --- Textes --------------------------------------------------------------
//
// Contrastes mesures sur le fond nuit : 16,1 pour le creme, 8,8 pour le
// sable assombri, 5,2 pour le texte attenue. Le minimum lisible est 4,5.
//
// Le texte attenue retombe a 4,59 sur une carte : il tient, mais de peu.
// Ne pas l'assombrir davantage sans remesurer.
val KaloyTextPrimary = KaloyCreme
val KaloyTextSecondary = Color(0xFFBFB6A6)
val KaloyTextMuted = Color(0xFF8B88A3)

// --- Etats ---------------------------------------------------------------
//
// Le vert et le rouge restent hors palette, et c'est delibere : ce sont des
// couleurs de SENS, pas d'identite. Un succes vert et une erreur rouge se
// lisent sans apprentissage ; les teindre en indigo ou en orange obligerait
// l'utilisateur a lire le texte pour savoir si l'operation a reussi.
//
// Le rouge est garde distinct de l'orange vif de la palette, sans quoi un
// message d'erreur ressemblerait a un bouton d'action.
val KaloyGreen = Color(0xFF10B981)
val KaloyRed = Color(0xFFEF4444)

// ============================================================
// Le theme Material 3
// ============================================================

private val SchemaSombre = darkColorScheme(
    primary = KaloyIndigo,
    onPrimary = KaloyCreme,
    primaryContainer = KaloyPurpleDark,
    onPrimaryContainer = KaloyPurpleLight,

    // onSecondary en bleu nuit et non en blanc : sur l'orange vif, le blanc
    // ne donne qu'un contraste de 3,6, sous le seuil lisible. Le bleu nuit
    // monte a 5,0.
    secondary = KaloyOrangeVif,
    onSecondary = KaloyNuit,
    secondaryContainer = Color(0xFF5A2608),
    onSecondaryContainer = KaloyPinkLight,

    tertiary = KaloyCyan,
    onTertiary = KaloyCreme,
    tertiaryContainer = Color(0xFF2A2A6E),
    onTertiaryContainer = KaloyCyanLight,

    background = KaloyDarkBg,
    onBackground = KaloyTextPrimary,
    surface = KaloyDarkSurface,
    onSurface = KaloyTextPrimary,
    surfaceVariant = KaloyDarkCard,
    onSurfaceVariant = KaloyTextSecondary,
    outline = KaloyTextMuted,

    error = KaloyRed,
    onError = KaloyCreme,
)

/**
 * Le theme de l'application. Sombre en toutes circonstances.
 *
 * Le parametre `darkTheme` a disparu, et le schema clair avec lui. La palette
 * est pensee sombre -- bleu nuit en fond, creme en texte -- et il n'existait
 * qu'un seul appel, dans App.kt, qui ne passait rien. Garder un schema clair
 * aurait voulu dire qu'un telephone regle en mode clair affichait encore
 * l'ancien violet sur blanc, puisque seul le schema sombre etait retouche :
 * deux identites visuelles pour la meme application, selon un reglage que
 * l'utilisateur ne relie pas a Kaloy.
 *
 * Un vrai theme clair reste possible -- le creme et le sable s'y preteraient
 * bien, ils ne servent presque a rien dans un theme sombre -- mais c'est un
 * travail a part, avec son propre voile sur la photo de fond.
 */
@Composable
fun KaloyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SchemaSombre,
        content = content
    )
}
