package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.screens.SectionHeader
import com.theveloper.pixelplay.presentation.screens.SongListItemFavsWrapper
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList


// 2) DailyMixSection y DailyMixCard quedan igual de ligeras...
@Composable
fun DailyMixSection(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionHeader(
            title = "Your Daily Mix",
            showViewAll = true,
            onViewAllClick = { /* navegar */ }
        )
        Spacer(Modifier.height(16.dp))
        DailyMixCard(songs, playerViewModel, navController)
    }
}

@Composable
private fun DailyMixCard(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    val headerSongs = songs.take(3).toImmutableList()
    val songsToPlay = songs.take(4).toImmutableList()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DailyMixHeader(thumbnails = headerSongs)
            DailyMixSongList(songs = songsToPlay, playerViewModel, navController)
            ViewAllDailyMixButton()
        }
    }
}

@Composable
private fun DailyMixHeader(thumbnails: ImmutableList<Song>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    )
                )
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DAILY MIX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "Basado en tu historial",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            Row(modifier = Modifier.padding(end = 16.dp)) {
                thumbnails.forEachIndexed { index, song ->
                    Box(
                        modifier = Modifier
                            .offset(x = (-16 * index).dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        SmartImage(
                            model = song.albumArtUri ?: R.drawable.rounded_album_24,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyMixSongList(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        songs.forEach { song ->
            SongListItemFavsWrapper(
                song = song,
                playerViewModel = playerViewModel,
                onClick = {
                    playerViewModel.playSongs(
                        songsToPlay = songs,
                        startSong = song,
                        queueName = "DailyMix"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ViewAllDailyMixButton() {
    TextButton(
        onClick = { /* TODO: Navegar a pantalla con todo el Daily Mix */ },
        //modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Text(
            text = "Ver todo el Daily Mix",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.rounded_keyboard_arrow_right_24),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}
