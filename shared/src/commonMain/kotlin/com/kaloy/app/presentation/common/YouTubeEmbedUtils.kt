package com.kaloy.app.presentation.common

object YouTubeEmbedUtils {
    private val videoIdRegex = Regex("""(?:youtube\.com/(?:watch\?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})""")

    fun extractVideoId(url: String): String? = videoIdRegex.find(url)?.groupValues?.get(1)

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
var s = document.createElement('script');
s.src = 'https://www.youtube.com/iframe_api';
document.head.appendChild(s);
function onYouTubeIframeAPIReady() {
  new YT.Player('yt', {
    videoId: '$videoId',
    width: '100%', height: '100%',
    playerVars: { playsinline: 1, rel: 0, autoplay: 1 },
    events: { onReady: function(e) { e.target.playVideo(); } }
  });
}
</script>
</body>
</html>"""
}
