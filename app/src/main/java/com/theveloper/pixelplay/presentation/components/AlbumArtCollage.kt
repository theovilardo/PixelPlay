package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
data class Config(val size: Dp, val width: Dp, val height: Dp, val align: Alignment, val rot: Float, val shape: Shape, val offsetX: Dp, val offsetY: Dp)

/**
 * Muestra hasta 6 portadas en un layout de collage con formas simplificadas y redondeadas.
 * Las formas se dividen en dos grupos (superior e inferior) para evitar superposición.
 * Incluye una píldora central, círculo, squircle y estrella, con disposición ajustada.
 * Ajusta tamaños, rotaciones y posiciones para crear un look dinámico.
 * Utiliza BoxWithConstraints para adaptar las dimensiones al contenedor.
 */
@Composable
fun AlbumArtCollage(
    songs: ImmutableList<Song>,
    modifier: Modifier = Modifier,
    height: Dp = 400.dp,
    padding: Dp = 0.dp,
    onSongClick: (Song) -> Unit,
) {
    val context = LocalContext.current
    val songsToShow = remember(songs) {
        (songs.take(6) + List(6 - songs.size.coerceAtMost(6)) { null }).toImmutableList()
    }

    val requests = remember(songsToShow) {
        songsToShow.map { song ->
            song?.albumArtUriString?.let {
                ImageRequest.Builder(context)
                    .data(it)
                    .dispatcher(Dispatchers.IO)
                    .crossfade(true)
                    //.placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .build()
            }
        }.toImmutableList()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(padding)
    ) {
        val boxMaxHeight = maxHeight
        val shapeConfigs by produceState<List<Config>>(initialValue = emptyList(), songsToShow, boxMaxHeight) {
            value = withContext(Dispatchers.Default) {
                val min = minOf(300.dp, height)
                listOf(
                    Config(size = min * 0.8f, width = min * 0.48f, height = min * 0.8f, align = Alignment.Center, rot = 45f, shape = RoundedCornerShape(percent = 50), offsetX = 0.dp, offsetY = 0.dp),
                    Config(size = min * 0.4f, width = min * 0.24f, height = min * 0.24f, align = Alignment.TopStart, rot = 0f, shape = CircleShape, offsetX = (300.dp * 0.05f), offsetY = (boxMaxHeight * 0.05f)),
                    Config(size = min * 0.4f, width = min * 0.24f, height = min * 0.24f, align = Alignment.BottomEnd, rot = 0f, shape = CircleShape, offsetX = -(300.dp * 0.05f), offsetY = -(boxMaxHeight * 0.05f)),
                    Config(size = min * 0.6f, width = min * 0.35f, height = min * 0.35f, align = Alignment.TopStart, rot = -20f, shape = RoundedCornerShape(20.dp), offsetX = (300.dp * 0.1f), offsetY = (boxMaxHeight * 0.1f)),
                    Config(size = min * 0.9f, width = min * 0.9f, height = min * 0.9f, align = Alignment.BottomEnd, rot = 0f, shape = RoundedStarShape(sides = 6, curve = 0.09, rotation = 45f), offsetX = (42).dp, offsetY = 0.dp)
                )
            }
        }

        if (shapeConfigs.isNotEmpty()) {
            val (topConfigs, bottomConfigs) = shapeConfigs.take(3) to shapeConfigs.drop(3)

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(boxMaxHeight * 0.6f)) {
                    topConfigs.forEachIndexed { idx, cfg ->
                        songsToShow.getOrNull(idx)?.let { song ->
                            SmartImage(
                                model = requests[idx],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(cfg.width, cfg.height)
                                    .align(cfg.align)
                                    .offset(cfg.offsetX, cfg.offsetY)
                                    .graphicsLayer { rotationZ = cfg.rot }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSongClick(song) }
                                    .background(shape = cfg.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clip(cfg.shape)
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(boxMaxHeight * 0.4f)) {
                    bottomConfigs.forEachIndexed { j, cfg ->
                        songsToShow.getOrNull(j + 3)?.let { song ->
                            SmartImage(
                                model = requests[j + 3],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(cfg.width, cfg.height)
                                    .align(cfg.align)
                                    .offset(cfg.offsetX, cfg.offsetY)
                                    .graphicsLayer { rotationZ = cfg.rot }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSongClick(song) }
                                    .clip(cfg.shape)
                            )
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.rounded_music_note_24),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}
