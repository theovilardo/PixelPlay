package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import java.net.URLEncoder
import timber.log.Timber
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import dev.shreyaspatil.capturable.capturable
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.media3.common.Player
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditSongSheet(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, lyrics: String, trackNumber: Int, coverArtUpdate: CoverArtUpdate?) -> Unit,
    generateAiMetadata: suspend (List<String>) -> Result<com.theveloper.pixelplay.data.ai.SongMetadata>
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var lyrics by remember { mutableStateOf(song.lyrics ?: "") }
    var trackNumber by remember { mutableStateOf(song.trackNumber.toString()) }
    var coverArtPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var editedCoverArt by remember { mutableStateOf<CoverArtUpdate?>(null) }
    var showCoverArtCropper by remember { mutableStateOf(false) }
    var pendingCoverArtUri by remember { mutableStateOf<Uri?>(null) }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickCoverArtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingCoverArtUri = uri
            showCoverArtCropper = true
        }
    }

    LaunchedEffect(song) {
        title = song.title
        artist = song.artist
        album = song.album
        genre = song.genre ?: ""
        lyrics = song.lyrics ?: ""
        trackNumber = song.trackNumber.toString()
        coverArtPreview = null
        editedCoverArt = null
    }

    if (isGenerating) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Generating Metadata") },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    if (showAiDialog) {
        AiMetadataDialog(
            song = song,
            onDismiss = { showAiDialog = false },
            onGenerate = { fields ->
                scope.launch {
                    isGenerating = true
                    val result = generateAiMetadata(fields)
                    result.onSuccess { metadata ->
                        Timber.d("AI metadata generated successfully: $metadata")
                        title = metadata.title ?: title
                        artist = metadata.artist ?: artist
                        album = metadata.album ?: album
                        genre = metadata.genre ?: genre
                    }.onFailure { error ->
                        Timber.e(error, "Failed to generate AI metadata")
                    }
                    isGenerating = false
                }
                showAiDialog = false
            }
        )
    }

    if (showCoverArtCropper && pendingCoverArtUri != null) {
        CoverArtCropperDialog(
            sourceUri = pendingCoverArtUri!!,
            onDismiss = {
                showCoverArtCropper = false
                pendingCoverArtUri = null
            },
            onConfirm = { result ->
                coverArtPreview = result.preview
                editedCoverArt = result.update
                showCoverArtCropper = false
                pendingCoverArtUri = null
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true } // Permite cerrar el BottomSheet
    )

    // Definición de colores para los TextFields
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    // Definición de la forma para los TextFields
    val textFieldShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 10.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusTR = 10.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = 10.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBR = 10.dp,
        smoothnessAsPercentTR = 60
    )

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    // --- Diálogo de Información ---
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = "Information Icon") },
            title = { Text("Editing Song Metadata") },
            text = { Text("Editing a song's metadata can affect how it's displayed and organized in your library. Changes are permanent and may not be reversible.") },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        //contentWindowInsets = { WindowInsets.ime.add(WindowInsets(bottom = 16.dp)) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(end = 16.dp, start = 16.dp, top = 8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Cabecera con Título y Botón de Información ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Edit Song",
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.displaySmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        ) {
                            IconButton(onClick = { showAiDialog = true }) {
                                Icon(
                                    modifier = Modifier
                                        .size(20.dp),
                                    painter = painterResource(id = R.drawable.gemini_ai),
                                    contentDescription = "Use Gemini AI",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { showInfoDialog = true },
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = "Show info dialog")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CoverArtEditorCard(
                    modifier = Modifier.fillMaxWidth(),
                    albumArtUri = song.albumArtUriString,
                    preview = coverArtPreview,
                    onPickNewArt = {
                        pickCoverArtLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onReset = {
                        coverArtPreview = null
                        editedCoverArt = null
                    }
                )

                // --- Campo de Título ---
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Title",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = title,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { title = it },
                        placeholder = { Text("Title") },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, tint = MaterialTheme.colorScheme.tertiary,contentDescription = "Title Icon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Track Number",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = trackNumber,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { trackNumber = it },
                        placeholder = { Text("Track Number") },
                        leadingIcon = { Icon(Icons.Rounded.FormatListNumbered, tint = MaterialTheme.colorScheme.secondary, contentDescription = "Track Number Icon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // --- Campo de Artista ---
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Artist",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = artist,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { artist = it },
                        placeholder = { Text("Artist") },
                        leadingIcon = { Icon(Icons.Rounded.Person, tint = MaterialTheme.colorScheme.primary, contentDescription = "Artist Icon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // --- Campo de Álbum ---
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Album",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = album,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { album = it },
                        placeholder = { Text("Album") },
                        leadingIcon = { Icon(Icons.Rounded.Album, tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Album Icon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // --- Campo de Género ---
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Genre",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = genre,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { genre = it },
                        placeholder = { Text("Genre") },
                        leadingIcon = { Icon(Icons.Rounded.Category, tint = MaterialTheme.colorScheme.secondary, contentDescription = "Genre Icon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // --- Campo de Letra ---
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Lyrics",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lyrics,
                            colors = textFieldColors,
                            shape = textFieldShape,
                            onValueChange = { lyrics = it },
                            placeholder = { Text("Lyrics") },
                            leadingIcon = { Icon(Icons.Rounded.Notes, tint = MaterialTheme.colorScheme.primary, contentDescription = "Lyrics Icon") },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = {
                                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                                val url = "https://lrclib.net/search/$encodedTitle%20$encodedArtist"
                                val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
                                context.startActivity(intent)
                            },
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_search_24),
                                contentDescription = "Search lyrics on lrclib.net"
                            )
                        }
                    }
                }

                // --- Botones de acción ---
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(top = 8.dp, end = 4.dp),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    FilledTonalButton(
//                        onClick = onDismiss,
//                        modifier = Modifier.height(48.dp),
//                        colors = ButtonDefaults.filledTonalButtonColors(
//                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
//                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
//                        )
//                    ) {
//                        Text("Cancel")
//                    }
//                    Button(
//                        onClick = {
//                            val resolvedTrackNumber = trackNumber.toIntOrNull() ?: song.trackNumber
//                            onSave(
//                                title.trim(),
//                                artist.trim(),
//                                album.trim(),
//                                genre.trim(),
//                                lyrics,
//                                resolvedTrackNumber,
//                                editedCoverArt
//                            )
//                        },
//                        modifier = Modifier.height(48.dp)
//                    ) {
//                        Text("Save")
//                    }
//                }

                Spacer(
                    modifier = Modifier.height(86.dp)
                )
            }

            HorizontalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                expandedShadowElevation = 0.dp,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                expanded = true,
                scrollBehavior = scrollBehavior,
                content = {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Cancel")
                    }
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Button(
                        onClick = {
                            val resolvedTrackNumber = trackNumber.toIntOrNull() ?: song.trackNumber
                            onSave(
                                title.trim(),
                                artist.trim(),
                                album.trim(),
                                genre.trim(),
                                lyrics,
                                resolvedTrackNumber,
                                editedCoverArt
                            )
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Save")
                    }
                }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    )
            ) {

            }
        }
    }
}

@Composable
private fun CoverArtEditorCard(
    modifier: Modifier = Modifier,
    albumArtUri: String?,
    preview: ImageBitmap?,
    onPickNewArt: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 12.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusTR = 12.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 12.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusBR = 12.dp,
            smoothnessAsPercentTR = 60,
        ),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Cover Art",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val cropSize = minOf(maxWidth, 220.dp)
                Box(
                    modifier = Modifier
                        .size(cropSize)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        preview != null -> {
                            Image(
                                bitmap = preview,
                                contentDescription = "Preview of the new cover art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        albumArtUri != null -> {
                            SmartImage(
                                model = albumArtUri,
                                contentDescription = "Current song cover art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholderResId = R.drawable.rounded_album_24,
                                errorResId = R.drawable.rounded_broken_image_24
                            )
                        }

                        else -> {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_album_24),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                    )
                                )
                            )
                    )
                }
            }

            Text(
                text = "Select a square image and fine-tune it so your cover art looks great across the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                FilledTonalButton(onClick = onPickNewArt) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change cover art")
                }

                if (preview != null) {
                    TextButton(onClick = onReset) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
        }
    }
}

private data class CoverArtCropResult(
    val preview: ImageBitmap,
    val update: CoverArtUpdate,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun CoverArtCropperDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (CoverArtCropResult) -> Unit,
) {
    val context = LocalContext.current
    val dialogScope = rememberCoroutineScope()
    val captureController = rememberCaptureController()
    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(0f) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        loadError = null
        val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(context, sourceUri) }
        if (bitmap != null) {
            loadedBitmap = bitmap.asImageBitmap()
        } else {
            loadError = "Unable to load the selected image"
        }
        isLoading = false
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(containerSize, scale) {
        offset = clampOffset(offset, scale, containerSize)
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        offset = clampOffset(offset + panChange, newScale, containerSize)
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    BasicAlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Adjust your cover art",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    val cropSide = minOf(maxWidth, 320.dp)
                    val density = LocalDensity.current
                    val cropSidePx = remember(cropSide) { with(density) { cropSide.toPx() } }

                    LaunchedEffect(cropSidePx) {
                        containerSize = cropSidePx
                    }

                    Box(
                        modifier = Modifier
                            .size(cropSide)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceDim)
                                .clipToBounds()
                        ) {
                            when {
                                isLoading -> {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }

                                loadError != null -> {
                                    Text(
                                        text = loadError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                loadedBitmap != null -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clipToBounds()
                                            .capturable(controller = captureController)
                                            .transformable(transformableState)
                                    ) {
                                        Image(
                                            bitmap = loadedBitmap!!,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationX = offset.x
                                                    translationY = offset.y
                                                },
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val step = size.width / 3f
                                        for (index in 1 until 3) {
                                            val lineOffset = step * index
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(lineOffset, 0f),
                                                end = Offset(lineOffset, size.height),
                                                strokeWidth = 2f
                                            )
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(0f, lineOffset),
                                                end = Offset(size.width, lineOffset),
                                                strokeWidth = 2f
                                            )
                                        }
                                        drawRect(color = gridColor, style = Stroke(width = 3f))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Use pinch and drag gestures to find the perfect framing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = onDismiss
                    ) {
                        Text("Cancel")
                    }

                    val canConfirm = !isLoading && loadError == null && loadedBitmap != null
                    Button(
                        enabled = canConfirm && !isSaving,
                        onClick = {
                            if (!canConfirm) return@Button
                            dialogScope.launch {
                                isSaving = true
                                val captured = captureController.captureAsync().await()
                                if (captured != null) {
                                    val bytes = withContext(Dispatchers.IO) {
                                        imageBitmapToJpeg(captured)
                                    }
                                    if (bytes != null) {
                                        onConfirm(
                                            CoverArtCropResult(
                                                preview = captured,
                                                update = CoverArtUpdate(bytes, COVER_ART_MIME_TYPE)
                                            )
                                        )
                                    } else {
                                        Timber.w("Failed to convert captured cover art to JPEG")
                                    }
                                } else {
                                    Timber.w("CaptureController returned null bitmap")
                                }
                                isSaving = false
                            }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Apply cover art")
                    }
                }
            }
        }
    }
}

private const val COVER_ART_MIME_TYPE = "image/jpeg"

private fun clampOffset(offset: Offset, scale: Float, containerSize: Float): Offset {
    if (containerSize <= 0f) return Offset.Zero
    val maxTranslation = (containerSize * (scale - 1f)) / 2f
    if (maxTranslation <= 0f) return Offset.Zero
    return Offset(
        x = offset.x.coerceIn(-maxTranslation, maxTranslation),
        y = offset.y.coerceIn(-maxTranslation, maxTranslation)
    )
}

private fun imageBitmapToJpeg(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val bitmap = imageBitmap.asAndroidBitmap()
        ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.toByteArray()
        }
    } catch (error: Exception) {
        Timber.e(error, "Unable to compress image bitmap to JPEG")
        null
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxDimension = 2048
            val width = info.size.width
            val height = info.size.height
            val largerSide = maxOf(width, height)
            if (largerSide > maxDimension) {
                val scale = largerSide.toFloat() / maxDimension
                decoder.setTargetSize(
                    (width / scale).roundToInt(),
                    (height / scale).roundToInt()
                )
            }
            decoder.isMutableRequired = false
        }
    } catch (error: Exception) {
        Timber.e(error, "Failed to decode bitmap from $uri")
        null
    }
}
