package com.kaloy.app.presentation.moi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.model.FollowArtisteInfo
import com.kaloy.app.data.repository.HistoriqueEcouteRepository
import com.kaloy.app.ui.theme.KaloyCyan
import com.kaloy.app.ui.theme.KaloyDarkBg
import com.kaloy.app.ui.theme.KaloyDarkCard
import com.kaloy.app.ui.theme.KaloyDarkElevated
import com.kaloy.app.ui.theme.KaloyGreen
import com.kaloy.app.ui.theme.KaloyPurple
import com.kaloy.app.ui.theme.KaloyPurpleLight
import com.kaloy.app.ui.theme.KaloyTextMuted
import com.kaloy.app.ui.theme.KaloyTextPrimary
import com.kaloy.app.ui.theme.KaloyTextSecondary

enum class ActivitySection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    RECENTLY_PLAYED("Récemment écouté", "Retrouvez les titres écoutés récemment.", Icons.Default.History),
    INTERACTIONS("Mes interactions", "Votre activité au sein de la communauté.", Icons.Default.Favorite),
    FOLLOWED_ARTISTS("Artistes suivis", "Les artistes que vous suivez.", Icons.Default.People),
    SHARES("Mes partages", "Les contenus musicaux que vous avez partagés.", Icons.Default.Share)
}

private data class ActivitySong(
    val title: String,
    val artist: String,
    val detail: String,
    val songId: Long = 0
)

private fun formatTrackDuration(seconds: Int): String =
    "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"

private fun formatListenedAt(listenedAt: String): String =
    listenedAt.substringAfter("T", "").take(5).ifBlank { "—" }

@Composable
fun ActivityOverview(
    modifier: Modifier = Modifier,
    sessionManager: AuthSessionManager,
    onOpenSection: (ActivitySection) -> Unit,
    onPlay: ((songId: Long) -> Unit)? = null,
    onNavigateToSong: ((songId: Long) -> Unit)? = null
) {
    var songsCeMois by remember { mutableStateOf("—") }
    var ecouteTotale by remember { mutableStateOf("—") }
    var artistesSuivis by remember { mutableStateOf("—") }
    var recentSongs by remember { mutableStateOf<List<ActivitySong>>(emptyList()) }
    var previewArtistes by remember { mutableStateOf<List<FollowArtisteInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val repo = HistoriqueEcouteRepository(sessionManager)
        runCatching {
            val (monthCount, allTimeCount) = repo.getStats()
            songsCeMois  = monthCount.toString()
            ecouteTotale = allTimeCount.toString()
        }
        runCatching {
            artistesSuivis = repo.getFollowsCount().toString()
        }
        runCatching {
            val history = repo.getHistory(period = "ALL", search = "", page = 0, size = 3)
            recentSongs = history.entries.map { item ->
                ActivitySong(
                    title = item.songTitle.ifBlank { "Titre inconnu" },
                    artist = item.artistName.ifBlank { "Artiste inconnu" },
                    detail = "${formatListenedAt(item.listenedAt)} • ${formatTrackDuration(item.songDurationSeconds ?: 0)}",
                    songId = item.songId
                )
            }
        }
        runCatching {
            previewArtistes = repo.getArtistesEnSuivi().take(2)
        }
    }

    LazyColumn(
        modifier = modifier.background(KaloyDarkBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Résumé de votre activité",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KaloyTextPrimary
                )
                Text(
                    text = "Votre musique, vos découvertes et vos partages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KaloyTextSecondary
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityStatCard(
                    modifier = Modifier.weight(1f),
                    value = songsCeMois,
                    label = "Chansons\nce mois",
                    icon = Icons.Default.MusicNote,
                    accent = KaloyPurpleLight
                )
                ActivityStatCard(
                    modifier = Modifier.weight(1f),
                    value = ecouteTotale,
                    label = "Écoute\ntotale",
                    icon = Icons.Default.AccessTime,
                    accent = KaloyCyan
                )
                ActivityStatCard(
                    modifier = Modifier.weight(1f),
                    value = artistesSuivis,
                    label = "Artistes\nsuivis",
                    icon = Icons.Default.People,
                    accent = KaloyGreen
                )
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.RECENTLY_PLAYED,
                onOpenSection = onOpenSection
            ) {
                if (recentSongs.isEmpty()) {
                    Text(
                        "Aucune chanson récente",
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    recentSongs.forEach { song ->
                        ActivitySongRow(
                            song = song,
                            onPlay = { onPlay?.invoke(song.songId) }
                        )
                    }
                }
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.INTERACTIONS,
                onOpenSection = onOpenSection
            ) {
                InteractionSummary(sessionManager)
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.FOLLOWED_ARTISTS,
                onOpenSection = onOpenSection
            ) {
                if (previewArtistes.isEmpty()) {
                    Text(
                        "Aucun artiste suivi",
                        color = KaloyTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    previewArtistes.forEach { a ->
                        ArtistActivityRow(name = a.stageName, activity = "Artiste suivi")
                    }
                }
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.SHARES,
                onOpenSection = onOpenSection
            ) {
                ShareSummary(sessionManager)
            }
        }
    }
}

@Composable
private fun ActivityStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = KaloyDarkCard)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            Text(
                text = value,
                color = KaloyTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ActivityBlock(
    section: ActivitySection,
    onOpenSection: (ActivitySection) -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(section.icon, contentDescription = null, tint = KaloyPurpleLight, modifier = Modifier.size(20.dp))
                Text(
                    text = section.title,
                    color = KaloyTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = { onOpenSection(section) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Voir", color = KaloyPurpleLight, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Voir ${section.title}",
                    tint = KaloyPurpleLight,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = KaloyDarkCard
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ActivitySongRow(song: ActivitySong, showMore: Boolean = false, onPlay: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(50.dp).background(KaloyDarkElevated, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = KaloyPurpleLight)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                song.title,
                color = KaloyTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = KaloyTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(song.detail, color = KaloyTextMuted, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = if (showMore) ({}) else onPlay) {
            Icon(
                if (showMore) Icons.Default.MoreVert else Icons.Default.PlayArrow,
                contentDescription = if (showMore) "Autres options" else "Lire",
                tint = if (showMore) KaloyTextSecondary else KaloyPurpleLight
            )
        }
    }
}

@Composable
private fun InteractionSummary(sessionManager: AuthSessionManager) {
    var favorisTotaux by remember { mutableStateOf("—") }
    LaunchedEffect(Unit) {
        runCatching {
            favorisTotaux = HistoriqueEcouteRepository(sessionManager).getFavorisCount().toString()
        }
    }
    SummaryLine(Icons.Default.Favorite, "$favorisTotaux favoris", KaloyPurpleLight)
}

@Composable
private fun InteractionDetail(sessionManager: AuthSessionManager) {
    var chansons by remember { mutableStateOf("—") }
    var albums   by remember { mutableStateOf("—") }
    var artistes by remember { mutableStateOf("—") }
    LaunchedEffect(Unit) {
        runCatching {
            val stats = HistoriqueEcouteRepository(sessionManager).getFavorisStats()
            chansons = stats.chansons.toString()
            albums   = stats.albums.toString()
            artistes = stats.artistes.toString()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryLine(Icons.Default.MusicNote, "$chansons chansons",  KaloyCyan)
        SummaryLine(Icons.Default.Album,     "$albums albums",       KaloyGreen)
        SummaryLine(Icons.Default.Mic,       "$artistes artistes",   KaloyPurpleLight)
    }
}

@Composable
private fun ShareSummary(sessionManager: AuthSessionManager) {
    var nbPlaylists by remember { mutableStateOf("—") }
    var nbChansons by remember { mutableStateOf("—") }
    LaunchedEffect(Unit) {
        runCatching {
            val (playlists, chansons) = HistoriqueEcouteRepository(sessionManager).getPartagesStats()
            nbPlaylists = playlists.toString()
            nbChansons = chansons.toString()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryLine(Icons.Default.Share, "$nbPlaylists playlists partagées", KaloyPurpleLight)
        SummaryLine(Icons.Default.MusicNote, "$nbChansons chansons partagées", KaloyCyan)
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Text(text, color = KaloyTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArtistActivityRow(name: String, activity: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(KaloyDarkElevated, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = KaloyCyan)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, color = KaloyTextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(activity, color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ActivityDetailScreen(
    section: ActivitySection,
    sessionManager: AuthSessionManager,
    modifier: Modifier = Modifier,
    onPlay: ((songId: Long) -> Unit)? = null,
    onNavigateToSong: ((songId: Long) -> Unit)? = null,
    onNavigateToArtist: ((artistId: Long) -> Unit)? = null
) {
    if (section == ActivitySection.RECENTLY_PLAYED) {
        RecentListeningHistory(
            sessionManager = sessionManager,
            modifier = modifier,
            onPlay = onPlay,
            onNavigateToSong = onNavigateToSong
        )
        return
    }

    Column(
        modifier = modifier
            .background(KaloyDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = KaloyDarkCard) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(52.dp).background(KaloyDarkElevated, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(section.icon, contentDescription = null, tint = KaloyPurpleLight, modifier = Modifier.size(25.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section.title, color = KaloyTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(section.subtitle, color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        when (section) {
            ActivitySection.RECENTLY_PLAYED -> {
                // handled above by early return to RecentListeningHistory
            }
            ActivitySection.INTERACTIONS -> InteractionDetail(sessionManager)
            ActivitySection.FOLLOWED_ARTISTS -> {
                var artistes by remember { mutableStateOf<List<FollowArtisteInfo>>(emptyList()) }
                var chargement by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    runCatching {
                        artistes = HistoriqueEcouteRepository(sessionManager).getArtistesEnSuivi()
                    }
                    chargement = false
                }
                if (chargement) {
                    Text("Chargement…", color = KaloyTextMuted, style = MaterialTheme.typography.bodySmall)
                } else if (artistes.isEmpty()) {
                    Text("Vous ne suivez aucun artiste.", color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    artistes.forEach { a ->
                        ArtistActivityRow(
                            name = a.stageName,
                            activity = "Artiste suivi",
                            onClick = { onNavigateToArtist?.invoke(a.artisteId) }
                        )
                    }
                }
            }
            ActivitySection.SHARES -> ShareSummary(sessionManager)
        }
    }
}
