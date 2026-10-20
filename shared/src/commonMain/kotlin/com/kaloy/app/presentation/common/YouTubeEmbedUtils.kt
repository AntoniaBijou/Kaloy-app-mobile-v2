package com.kaloy.app.presentation.common

/**
 * Extraction de l'identifiant d'une URL YouTube.
 *
 * Il n'y a plus de HTML ici : le lecteur Android passe par la bibliotheque
 * androidyoutubeplayer, qui fabrique le sien. Une page d'integration ecrite
 * a la main a vecu ici jusqu'au 20/10/2026 sans jamais etre affichee, et un
 * correctif de l'erreur 152 y a ete applique par erreur le 7 octobre.
 */
object YouTubeEmbedUtils {
    private val videoIdRegex = Regex("""(?:youtube\.com/(?:watch\?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})""")

    fun extractVideoId(url: String): String? = videoIdRegex.find(url)?.groupValues?.get(1)
}
