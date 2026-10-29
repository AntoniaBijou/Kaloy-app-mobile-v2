package com.kaloy.app.presentation.common

/**
 * Identifiant d'une URL YouTube, et page d'integration pour le lecteur iOS.
 *
 * Android n'utilise rien d'ici a part extractVideoId : il passe par la
 * bibliotheque androidyoutubeplayer, qui fabrique sa propre page. C'est iOS qui
 * a besoin de ce HTML, faute d'equivalent natif.
 *
 * Une version de cette page a vecu ici jusqu'au 20/10/2026 sans jamais etre
 * affichee par personne, et un correctif de l'erreur 152 y a ete applique par
 * erreur le 7 octobre. Elle revient parce qu'elle a enfin un appelant.
 */
object YouTubeEmbedUtils {
    private val videoIdRegex =
        Regex("""(?:youtube\.com/(?:watch\?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})""")

    /** Nom du canal par lequel la page remonte ses erreurs a l'application. */
    const val CANAL_JOURNAL = "kaloyVideo"

    /**
     * Origine annoncee a YouTube.
     *
     * YouTube verifie que la page qui integre son lecteur annonce une origine
     * coherente avec celle sous laquelle elle est reellement servie. Une page
     * qui pretend etre youtube.com sans l'etre est refusee — c'est tout le
     * sujet de l'erreur 152, et c'est ce que la bibliotheque Android a corrige
     * dans sa version 13.0.0 en passant au nom de paquet de l'application.
     *
     * La meme valeur doit donc etre donnee deux fois : ici, dans playerVars, et
     * comme baseURL du loadHTMLString cote iOS. Si les deux divergent, YouTube
     * refuse.
     */
    const val ORIGINE = "https://com.kaloy.app"

    fun extractVideoId(url: String): String? = videoIdRegex.find(url)?.groupValues?.get(1)

    /**
     * Page minimale qui integre le lecteur YouTube.
     *
     * onError ne se contente pas d'exister : il renvoie le code vers
     * l'application. Sans ce canal, un refus de YouTube resterait des pixels
     * dans une WebView, invisible dans les journaux — exactement ce qui a rendu
     * l'erreur 152 si longue a cerner cote Android.
     */
    fun getIframeHtml(videoId: String): String = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
html, body { width:100%; height:100%; background:#000; overflow:hidden; }
#yt { width:100%; height:100%; }
</style>
</head>
<body>
<div id="yt"></div>
<script>
function journal(texte) {
  try { window.webkit.messageHandlers.$CANAL_JOURNAL.postMessage(texte); } catch (e) {}
}
var s = document.createElement('script');
s.src = 'https://www.youtube.com/iframe_api';
s.onerror = function() { journal('script iframe_api injoignable'); };
document.head.appendChild(s);
function onYouTubeIframeAPIReady() {
  new YT.Player('yt', {
    videoId: '$videoId',
    width: '100%', height: '100%',
    playerVars: { playsinline: 1, rel: 0, autoplay: 1, origin: '$ORIGINE' },
    events: {
      onReady: function(e) { journal('pret'); e.target.playVideo(); },
      onError: function(e) { journal('erreur ' + e.data); }
    }
  });
}
</script>
</body>
</html>"""
}
