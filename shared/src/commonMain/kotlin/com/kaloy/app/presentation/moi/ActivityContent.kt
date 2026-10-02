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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kaloy.app.core.session.AuthSessionManager
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
    QUEUE("File d'attente", "Les prochains titres de votre file Up Next.", Icons.Default.QueueMusic),
    RADIO("Mode radio", "Votre dernière session radio.", Icons.Default.Radio),
    INTERACTIONS("Mes interactions", "Votre activité au sein de la communauté.", Icons.Default.Favorite),
    FOLLOWED_ARTISTS("Artistes suivis", "Les artistes que vous suivez.", Icons.Default.People),
    SHARES("Mes partages", "Les contenus musicaux que vous avez partagés.", Icons.Default.Share)
}

private data class ActivitySong(
    val title: String,
    val artist: String,
    val detail: String
)

private val recentSongs = listOf(
    ActivitySong("Eh sambatra sy tretrika", "Artiste Test Kaloy", "Il y a 5 min • 3:35"),
    ActivitySong("Gasy Tsara", "Reko Band", "Il y a 2 h • 4:12"),
    ActivitySong("Premier Pas", "Artiste Test Kaloy", "Hier • 3:45")
)

private val queuedSongs = listOf(
    ActivitySong("Misia", "Reko Band", "5:01"),
    ActivitySong("Eh sambatra sy tretrika", "Artiste Test Kaloy", "3:35"),
    ActivitySong("Premier Pas", "Artiste Test Kaloy", "3:45")
)

@Composable
fun ActivityOverview(
    modifier: Modifier = Modifier,
    onOpenSection: (ActivitySection) -> Unit
) {
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
                    value = "127",
                    label = "Chansons\nce mois",
                    icon = Icons.Default.MusicNote,
                    accent = KaloyPurpleLight
                )
                ActivityStatCard(
                    modifier = Modifier.weight(1f),
                    value = "8 h 32",
                    label = "Écoute\ntotale",
                    icon = Icons.Default.AccessTime,
                    accent = KaloyCyan
                )
                ActivityStatCard(
                    modifier = Modifier.weight(1f),
                    value = "23",
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
                recentSongs.forEach { song ->
                    ActivitySongRow(song = song)
                }
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.QUEUE,
                onOpenSection = onOpenSection
            ) {
                Text("3 chansons en attente", color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
                queuedSongs.take(2).forEach { song ->
                    ActivitySongRow(song = song, showMore = true)
                }
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.RADIO,
                onOpenSection = onOpenSection
            ) {
                RadioSessionCard()
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.INTERACTIONS,
                onOpenSection = onOpenSection
            ) {
                InteractionSummary()
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.FOLLOWED_ARTISTS,
                onOpenSection = onOpenSection
            ) {
                Text("8 artistes", color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
                ArtistActivityRow("Reko Band", "Nouvelle sortie il y a 2 j")
                ArtistActivityRow("Mamitiana", "Concert demain à 20 h")
            }
        }
        item {
            ActivityBlock(
                section = ActivitySection.SHARES,
                onOpenSection = onOpenSection
            ) {
                ShareSummary()
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
private fun ActivitySongRow(song: ActivitySong, showMore: Boolean = false) {
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
        IconButton(onClick = {}) {
            Icon(
                if (showMore) Icons.Default.MoreVert else Icons.Default.PlayArrow,
                contentDescription = if (showMore) "Autres options" else "Lire",
                tint = if (showMore) KaloyTextSecondary else KaloyPurpleLight
            )
        }
    }
}

@Composable
private fun RadioSessionCard() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(KaloyDarkElevated, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Radio, contentDescription = null, tint = KaloyCyan)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Dernière session : Gospel", color = KaloyTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Il y a 3 jours • 1 h 45", color = KaloyTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple.copy(alpha = 0.18f))
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = KaloyPurpleLight)
            Spacer(Modifier.width(8.dp))
            Text("Reprendre la session", color = KaloyPurpleLight)
        }
    }
}

@Composable
private fun InteractionSummary() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryLine(Icons.Default.ThumbUp, "12 likes", KaloyPurpleLight)
        SummaryLine(Icons.Default.ChatBubbleOutline, "5 commentaires", KaloyCyan)
        SummaryLine(Icons.Default.PhotoLibrary, "2 photos uploadées", KaloyGreen)
    }
}

@Composable
private fun ShareSummary() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryLine(Icons.Default.Share, "3 playlists partagées", KaloyPurpleLight)
        SummaryLine(Icons.Default.MusicNote, "12 chansons partagées", KaloyCyan)
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
private fun ArtistActivityRow(name: String, activity: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
    modifier: Modifier = Modifier
) {
    if (section == ActivitySection.RECENTLY_PLAYED) {
        RecentListeningHistory(sessionManager = sessionManager, modifier = modifier)
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
                Text("Écoutés récemment", color = KaloyTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                recentSongs.forEach { ActivitySongRow(it) }
                ActivitySongRow(ActivitySong("Misia", "Reko Band", "Avant-hier • 5:01"))
                ActivitySongRow(ActivitySong("Gasy Tsara", "Reko Band", "Avant-hier • 4:12"))
            }
            ActivitySection.QUEUE -> {
                Text("3 chansons en attente", color = KaloyTextSecondary, style = MaterialTheme.typography.bodyMedium)
                queuedSongs.forEach { ActivitySongRow(it, showMore = true) }
            }
            ActivitySection.RADIO -> RadioSessionCard()
            ActivitySection.INTERACTIONS -> InteractionSummary()
            ActivitySection.FOLLOWED_ARTISTS -> {
                Text("8 artistes suivis", color = KaloyTextSecondary, style = MaterialTheme.typography.bodyMedium)
                ArtistActivityRow("Reko Band", "Nouvelle sortie il y a 2 j")
                ArtistActivityRow("Mamitiana", "Concert demain à 20 h")
                ArtistActivityRow("Artiste Test Kaloy", "Vous suit également")
            }
            ActivitySection.SHARES -> ShareSummary()
        }
    }
}
