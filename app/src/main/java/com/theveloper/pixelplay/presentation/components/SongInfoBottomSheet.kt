package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.AutoSizingTextToFill
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongInfoBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onEditSong: (title: String, artist: String, album: String, genre: String, lyrics: String) -> Unit,
    generateAiMetadata: suspend (List<String>) -> Result<com.theveloper.pixelplay.data.ai.SongMetadata>
) {
    val context = LocalContext.current
    var showEditSheet by remember { mutableStateOf(false) }

    val evenCornerRadiusElems = 26.dp

    // Formas personalizadas (asumiendo que AbsoluteSmoothCornerShape está definida en tu proyecto)
    // Si no lo está, reemplaza con RoundedCornerShape(valor)
    val listItemShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 20.dp,
        smoothnessAsPercentTL = 60, cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
    )
    val albumArtShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
        smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
        cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
    )
    val playButtonShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
        smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
        cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
    )

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true } // Permite cerrar el BottomSheet
    )

    // Animaciones para el botón de favorito
    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (isFavorite) evenCornerRadiusElems else 60.dp, // 28.dp para hacerlo circular en un alto de 56.dp
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )

    val favoriteButtonShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
        smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
        cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
    )


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.windowInsets } // Manejo de insets como el teclado
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // Permite scroll si el contenido es largo
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Fila para la carátula del álbum y el título
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = "Album Art",
                    shape = albumArtShape,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .weight(1f) // Ocupa el espacio restante
                        .fillMaxHeight(), // Ocupa toda la altura de la fila
                    contentAlignment = Alignment.CenterStart // Alinea el texto
                ) {
                    AutoSizingTextToFill(
                        modifier = Modifier.padding(end = 4.dp),
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Light,
                        text = song.title
                    )
                }
                FilledTonalIconButton(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 6.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { showEditSheet = true },
                ) {
                    Icon(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit song metadata"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fila de botones de acción con altura intrínseca
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min), // Asegura que todos los hijos puedan tener la misma altura
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MediumExtendedFloatingActionButton(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(), // Rellena a la altura de la Row
                    onClick = onPlaySong,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    shape = playButtonShape, // Usa tu forma personalizada
                    icon = {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play song")
                    },
                    text = {
                        Text(
                            modifier = Modifier.padding(end = 10.dp),
                            text = "Play"
                        )
                    }
                )

                // Botón de Favorito Modificado con animación y altura
                FilledIconButton(
                    modifier = Modifier
                        .weight(0.25f)
                        .fillMaxHeight(), // Rellena a la altura de la Row
                    onClick = onToggleFavorite,
                    shape = favoriteButtonShape, // Forma animada
                    colors = IconButtonDefaults.filledIconButtonColors( // Colores animados
                        containerColor = favoriteButtonContainerColor,
                        contentColor = favoriteButtonContentColor
                    )
                ) {
                    Icon(
                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                    )
                }

                // Botón de Compartir Modificado con altura
                FilledTonalIconButton(
                    modifier = Modifier
                        .weight(0.25f)
                        .fillMaxHeight(), // Rellena a la altura de la Row
                    onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*" // Tipo MIME para archivos de audio
                                putExtra(Intent.EXTRA_STREAM, song.contentUriString.toUri())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Necesario para URIs de contenido
                            }
                            // Inicia el chooser para que el usuario elija la app para compartir
                            context.startActivity(Intent.createChooser(shareIntent, "Share Song File Via"))
                        } catch (e: Exception) {
                            // Manejar el caso donde la URI es inválida o no hay app para compartir
                            Toast.makeText(context, "Could not share song: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = CircleShape // Mantenemos CircleShape para el botón de compartir
                ) {
                    Icon(
                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share song file"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón de Añadir a la Cola
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp), // Altura mínima recomendada para botones
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = CircleShape, // O considera RoundedCornerShape(16.dp)
                onClick = onAddToQueue
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to Queue icon")
                Spacer(Modifier.width(8.dp))
                Text("Add to Queue")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sección de Detalles
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ListItem(
                    modifier = Modifier.clip(shape = listItemShape),
                    headlineContent = { Text("Duration") },
                    supportingContent = { Text(formatDuration(song.duration)) },
                    leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = "Duration icon") }
                )

                if (!song.genre.isNullOrEmpty()) {
                    ListItem(
                        modifier = Modifier.clip(shape = listItemShape),
                        headlineContent = { Text("Genre") },
                        supportingContent = { Text(song.genre) }, // Safe call si es nullOrEmpty
                        leadingContent = { Icon(Icons.Rounded.MusicNote, contentDescription = "Genre icon") }
                    )
                }

                ListItem(
                    modifier = Modifier
                        .clip(shape = listItemShape)
                        .clickable(onClick = onNavigateToAlbum),
                    headlineContent = { Text("Album") },
                    supportingContent = { Text(song.album) },
                    leadingContent = { Icon(Icons.Rounded.Album, contentDescription = "Album icon") }
                )

                ListItem(
                    modifier = Modifier
                        .clip(shape = listItemShape)
                        .clickable(onClick = onNavigateToArtist),
                    headlineContent = { Text("Artist") },
                    supportingContent = { Text(song.artist) },
                    leadingContent = { Icon(Icons.Rounded.Person, contentDescription = "Artist icon") }
                )
            }
        }
    }

    if (showEditSheet) {
        EditSongSheet(
            song = song,
            onDismiss = { showEditSheet = false },
            onSave = { title, artist, album, genre, lyrics ->
                onEditSong(title, artist, album, genre, lyrics)
                showEditSheet = false
            },
            generateAiMetadata = generateAiMetadata
        )
    }
}