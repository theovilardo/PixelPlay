package com.theveloper.pixelplay.presentation.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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
import coil.compose.AsyncImage
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
                    .placeholder(R.drawable.rounded_album_24)
                    .error(R.drawable.rounded_album_24)
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
                            AsyncImage(
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
                                    .clip(cfg.shape)
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(boxMaxHeight * 0.4f)) {
                    bottomConfigs.forEachIndexed { j, cfg ->
                        songsToShow.getOrNull(j + 3)?.let { song ->
                            AsyncImage(
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

//@Composable
//fun AlbumArtCollage3( // Mantengo el nombre Collage
//    albumArts: List<Uri?>,
//    modifier: Modifier = Modifier,
//    // Altura del contenedor del collage - ajustada para el nuevo diseño
//    height: Dp = 400.dp, // Aumentada la altura para dar más espacio a los dos grupos
//    padding: Dp = 0.dp // Padding dentro del BoxWithConstraints si lo necesitas
//) {
//    // Aseguramos tener al menos 6 elementos en la lista para evitar IndexOutOfBounds,
//    // llenando con null si es necesario.
//    val artsToShow = albumArts.take(6).toMutableList().apply {
//        while (size < 6) {
//            add(null) // Rellenamos con null si hay menos de 6
//        }
//    }
//
//    BoxWithConstraints(
//        modifier = modifier
//            .fillMaxWidth()
//            .height(height) // Usa el parámetro de altura
//            .padding(padding) // Usa el parámetro de padding
//    ) {
//        val boxWidth = maxWidth
//        val boxHeight = maxHeight
//        val minDimension = minOf(boxWidth, boxHeight)
//
//        // --- Grupo Superior (Píldora y 2 Círculos) ---
//        // Este BoxWithConstraints contendrá las formas superiores
//        BoxWithConstraints( // Cambiado a BoxWithConstraints
//            modifier = Modifier
//                .fillMaxWidth() // Ocupa todo el ancho del contenedor principal
//                .height(boxHeight * 0.6f) // Ocupa el 60% de la altura (ajustar según necesidad)
//                .align(Alignment.TopCenter) // Alineado en la parte superior central del contenedor principal
//            // .background(Color.Blue.copy(alpha = 0.1f)) // Opcional: visualizar límites
//        ) {
//            val topBoxWidth = maxWidth // Ahora correcto
//            val topBoxHeight = maxHeight // Ahora correcto
//            val topMinDimension = minOf(topBoxWidth, topBoxHeight)
//
//            // 0: Centro (Píldora Rotada) - La forma principal y única
//            val pillConfig = ImageConfig(
//                size = topMinDimension * 0.8f, // Tamaño base relativo al Top Box
//                width = topMinDimension * 0.6f, // Ancho específico para la píldora
//                height = topMinDimension * 0.9f, // Alto específico para la píldora
//                alignment = Alignment.Center, // Alineado al centro del Top Box
//                rotation = 45f,
//                shape = RoundedCornerShape(percent = 50),
//                offsetX = 0.dp, offsetY = 0.dp
//            )
//
//            // 1: TopStart (Círculo) - Posicionado arriba a la izquierda dentro del Top Box
//            val topLeftCircleConfig = ImageConfig(
//                size = topMinDimension * 0.3f, // Tamaño del círculo relativo al Top Box
//                alignment = Alignment.TopStart, // Alineado al TopStart del Top Box
//                rotation = 0f,
//                shape = CircleShape,
//                offsetX = topBoxWidth * 0.05f, offsetY = topBoxHeight * 0.05f // Offset dentro del Top Box
//            )
//
//            // 4: BottomEnd (Círculo) - Posicionado abajo a la derecha dentro del Top Box
//            val bottomRightCircleConfig = ImageConfig(
//                size = topMinDimension * 0.3f, // Tamaño relativo al Top Box
//                alignment = Alignment.BottomEnd, // Alineado al BottomEnd del Top Box
//                rotation = 0f,
//                shape = CircleShape,
//                offsetX = -topBoxWidth * 0.05f, offsetY = -topBoxHeight * 0.05f // Offset dentro del Top Box
//            )
//
//            // Dibujar las formas dentro del Grupo Superior
//            // Píldora (índice 0)
//            if (artsToShow.isNotEmpty() && artsToShow[0] != null) {
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(artsToShow[0])
//                        .dispatcher(Dispatchers.IO)
//                        .crossfade(true)
//                        .placeholder(R.drawable.rounded_album_24)
//                        .error(R.drawable.rounded_album_24)
//                        .build(),
//                    contentDescription = null,
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .size(width = pillConfig.width, height = pillConfig.height)
//                        .align(pillConfig.alignment)
//                        .offset(x = pillConfig.offsetX, y = pillConfig.offsetY)
//                        .graphicsLayer { rotationZ = pillConfig.rotation }
//                        .clip(pillConfig.shape)
//                )
//            }
//
//            // Círculo superior izquierdo (índice 1)
//            if (artsToShow.size > 1 && artsToShow[1] != null) {
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(artsToShow[1])
//                        .dispatcher(Dispatchers.IO)
//                        .crossfade(true)
//                        .placeholder(R.drawable.rounded_album_24)
//                        .error(R.drawable.rounded_album_24)
//                        .build(),
//                    contentDescription = null,
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .size(topLeftCircleConfig.size)
//                        .align(topLeftCircleConfig.alignment)
//                        .offset(x = topLeftCircleConfig.offsetX, y = topLeftCircleConfig.offsetY)
//                        .graphicsLayer { rotationZ = topLeftCircleConfig.rotation }
//                        .clip(topLeftCircleConfig.shape)
//                )
//            }
//
//            // Círculo inferior derecho (índice 4)
//            if (artsToShow.size > 4 && artsToShow[4] != null) {
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(artsToShow[4])
//                        .dispatcher(Dispatchers.IO)
//                        .crossfade(true)
//                        .placeholder(R.drawable.rounded_album_24)
//                        .error(R.drawable.rounded_album_24)
//                        .build(),
//                    contentDescription = null,
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .size(bottomRightCircleConfig.size)
//                        .align(bottomRightCircleConfig.alignment)
//                        .offset(x = bottomRightCircleConfig.offsetX, y = bottomRightCircleConfig.offsetY)
//                        .graphicsLayer { rotationZ = bottomRightCircleConfig.rotation }
//                        .clip(bottomRightCircleConfig.shape)
//                )
//            }
//        }
//
//
//        // --- Grupo Inferior (2 Squircle y 1 Estrella) ---
//        // Este BoxWithConstraints contendrá las formas inferiores, posicionado debajo del Grupo Superior
//        BoxWithConstraints( // Cambiado a BoxWithConstraints
//            modifier = Modifier
//                .fillMaxWidth() // Ocupa todo el ancho del contenedor principal
//                .height(boxHeight * 0.4f) // Ocupa el 40% de la altura restante (ajustar según necesidad)
//                .align(Alignment.BottomCenter) // Alineado en la parte inferior central del contenedor principal
//            // .background(Color.Green.copy(alpha = 0.1f)) // Opcional: visualizar límites
//        ) {
//            val bottomBoxWidth = maxWidth // Ahora correcto
//            val bottomBoxHeight = maxHeight // Ahora correcto
//            val bottomMinDimension = minOf(bottomBoxWidth, bottomBoxHeight)
//
//            // 2: TopStart (Squircle) dentro del Bottom Box
//            val bottomBoxSquircle1Config = ImageConfig(
//                size = bottomMinDimension * 0.6f, // Tamaño relativo al Bottom Box
//                alignment = Alignment.TopStart, // Alineación dentro del Bottom Box
//                rotation = -20f,
//                shape = RoundedCornerShape(20.dp),
//                offsetX = bottomBoxWidth * 0.1f, offsetY = bottomBoxHeight * 0.1f // Offset dentro del Bottom Box
//            )
//
//            // 3: Center (Estrella Redondeada) dentro del Bottom Box
//            val bottomBoxStarConfig = ImageConfig(
//                size = bottomMinDimension * 0.9f, // Tamaño relativo al Bottom Box
//                alignment = Alignment.BottomEnd, // Alineación dentro del Bottom Box
//                rotation = 0f, // Rotación especificada por el usuario
//                shape = RoundedStarShape(sides = 5, curve = 0.09, rotation = 0f), // Configuración especificada
//                offsetX = 0.dp, offsetY = 0.dp // Offset dentro del Bottom Box
//            )
//
//            // 5: BottomEnd (Squircle) dentro del Bottom Box
//            val bottomBoxSquircle2Config = ImageConfig(
//                size = bottomMinDimension * 0.5f, // Tamaño relativo al Bottom Box
//                alignment = Alignment.BottomEnd, // Alineación dentro del Bottom Box
//                rotation = 40f,
//                shape = RoundedCornerShape(16.dp),
//                offsetX = -bottomBoxWidth * 0.1f, offsetY = -bottomBoxHeight * 0.1f // Offset dentro del Bottom Box
//            )
//
//            // Dibujar las formas dentro del Grupo Inferior
//            // Squircle (índice 2)
//            if (artsToShow.size > 2 && artsToShow[2] != null) {
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(artsToShow[2])
//                        .dispatcher(Dispatchers.IO)
//                        .crossfade(true)
//                        .placeholder(R.drawable.rounded_album_24)
//                        .error(R.drawable.rounded_album_24)
//                        .build(),
//                    contentDescription = null,
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .size(bottomBoxSquircle1Config.size)
//                        .align(bottomBoxSquircle1Config.alignment)
//                        .offset(x = bottomBoxSquircle1Config.offsetX, y = bottomBoxSquircle1Config.offsetY)
//                        .graphicsLayer { rotationZ = bottomBoxSquircle1Config.rotation }
//                        .clip(bottomBoxSquircle1Config.shape)
//                )
//            }
//
//            // Estrella (índice 3)
//            if (artsToShow.size > 3 && artsToShow[3] != null) {
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(artsToShow[3])
//                        .dispatcher(Dispatchers.IO)
//                        .crossfade(true)
//                        .placeholder(R.drawable.rounded_album_24)
//                        .error(R.drawable.rounded_album_24)
//                        .build(),
//                    contentDescription = null,
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .size(bottomBoxStarConfig.size)
//                        .align(bottomBoxStarConfig.alignment)
//                        .offset(x = bottomBoxStarConfig.offsetX, y = bottomBoxStarConfig.offsetY)
//                        .graphicsLayer { rotationZ = bottomBoxStarConfig.rotation }
//                        .clip(bottomBoxStarConfig.shape)
//                )
//            }
//        }
//
//
//        // Considera añadir un placeholder o un mensaje si albumArts está vacío
//        if (albumArts.isEmpty()) {
//            Box(
//                modifier = Modifier.fillMaxSize(),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    painter = painterResource(R.drawable.rounded_music_note_24),
//                    contentDescription = null,
//                    modifier = Modifier.size(100.dp),
//                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
//                )
//            }
//        }
//    }
//}

// Clase de datos para almacenar la configuración de cada imagen en el collage
data class ImageConfig(
    val size: Dp, // Tamaño para formas cuadradas (la mayoría)
    val width: Dp = size, // Ancho específico (para píldora)
    val height: Dp = size, // Alto específico (para píldora)
    val alignment: Alignment,
    val rotation: Float,
    val shape: Shape,
    val offsetX: Dp,
    val offsetY: Dp
)