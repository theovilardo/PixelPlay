package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.screens.SongListItemFavsWrapper
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape


// 2) DailyMixSection y DailyMixCard quedan igual de ligeras...
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DailyMixSection(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    onClickOpen: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        DailyMixCard(songs = songs, playerViewModel = playerViewModel, onClickOpen =  onClickOpen)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DailyMixCard(
    songs: ImmutableList<Song>,
    onClickOpen: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val headerSongs = songs.take(3).toImmutableList()
    val songsToPlay = songs.take(4).toImmutableList()
    val cornerRadius = 30.dp
    Card(
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusBR = cornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = cornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = cornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusTL = cornerRadius,
            smoothnessAsPercentBR = 60
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DailyMixHeader(thumbnails = headerSongs)
            DailyMixSongList(songs = songsToPlay, playerViewModel)
            ViewAllDailyMixButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                onClickOpen = {
                    onClickOpen()
                },
            )
        }
    }
}

@Composable
fun DailyMixHeader(thumbnails: ImmutableList<Song>) {

    fun shapeConditionalModifier(index: Int): Modifier {
        if (index == 0){
            return Modifier.size(50.dp).padding(top = 4.dp)
        } else {
            if (index == 1) {
                return Modifier.size(44.dp).aspectRatio(1f).padding(bottom = 4.dp)
            }
            return Modifier.size(48.dp) //.padding( = 4.dp)
        }
    }

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
                .padding(start = 26.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DAILY MIX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "Based on History",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy((-16).dp)
            ) {
                thumbnails.forEachIndexed { index, song ->
                    val modifier = shapeConditionalModifier(index)
                    Box(
                        modifier = modifier
                            //.size(48.dp)
                            .clip(threeShapeSwitch(index))
                            .border(2.dp, MaterialTheme.colorScheme.surface, threeShapeSwitch(index))
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
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
fun threeShapeSwitch(index: Int, thirdShapeCornerRadius: Dp = 16.dp): Shape { // Ensure the function returns a Shape
    return when (index) { // Return the result of the when expression
        0 -> RoundedStarShape(
            sides = 6,
            rotation = 10f
        )
        1 -> CircleShape
        2 -> AbsoluteSmoothCornerShape(
            cornerRadiusBL = thirdShapeCornerRadius,
            cornerRadiusTR = thirdShapeCornerRadius,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentTR = 60,
            cornerRadiusTL = thirdShapeCornerRadius,
            cornerRadiusBR = thirdShapeCornerRadius,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentBR = 60
        )
        else -> CircleShape // It's good practice to have a default case
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DailyMixSongList(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
private fun ViewAllDailyMixButton(
    modifier: Modifier = Modifier,
    onClickOpen: () -> Unit
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = {
            onClickOpen()
        },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.Transparent
        ),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 10.dp,
            cornerRadiusTR = 10.dp,
            smoothnessAsPercentTL = 70,
            smoothnessAsPercentTR = 70,
            cornerRadiusBL = 60.dp,
            cornerRadiusBR = 60.dp,
            smoothnessAsPercentBL = 70,
            smoothnessAsPercentBR = 70

        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Check all of Daily Mix",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Icon(
                painter = painterResource(R.drawable.rounded_arrow_forward_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
