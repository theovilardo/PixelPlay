@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.ImageCropView
import com.theveloper.pixelplay.data.model.PlaylistShapeType
// import com.theveloper.pixelplay.presentation.screens.ShapeType // Removed local enum
import com.theveloper.pixelplay.presentation.components.SongPickerList
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Matrix
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SliderDefaults


import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CreatePlaylistDialog(
    visible: Boolean,
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit // ... d4
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "create_playlist_dialog"
            ) {
                CreatePlaylistContent(
                    allSongs = allSongs,
                    onDismiss = onDismiss,
                    onCreate = onCreate
                )
            }
        }
    }
}

// Enum removed, using com.theveloper.pixelplay.data.model.PlaylistShapeType

@Composable
private fun CreatePlaylistContent(
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    val context = LocalContext.current

    // Shared State
    var playlistName by remember { mutableStateOf("") }
    
    // Step 1: Info State
    var currentStep by remember { mutableStateOf(0) } // 0: Info, 1: Songs
    var selectedTab by remember { mutableStateOf(0) } // 0: Default, 1: Image, 2: Icon
    
    // Songs State
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    
    // Image State
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropUi by remember { mutableStateOf(false) }
    // Lifted Bitmap State for Preview Consistency
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }

    // Crop State
    var cropScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Custom Color/Icon State
    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf<Int?>(defaultColor) }
    var selectedIconName by remember { mutableStateOf<String?>("MusicNote") }

    // Shape State
    var selectedShapeType by remember { mutableStateOf(PlaylistShapeType.Circle) }
    
    // SmoothRect Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(20f) } // 0-50
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(60f) } // 0-100
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(0.15) } // 0.0 - 0.5
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(0f) } // 0 - 360
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) } // 0.5 - 1.5
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(5) } // 3 - 20

    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = drawable.bitmap.asImageBitmap()
             }
         } else {
             imageBitmap = null
             cropScale = 1f
             cropOffset = androidx.compose.ui.geometry.Offset.Zero
         }
    }
    var searchQuery by remember { mutableStateOf("") }


    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showCropUi = true
        }
    }
    
    // Back Handler for Step 2
    BackHandler(enabled = currentStep == 1) {
        currentStep = 0
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(targetState = currentStep, label = "Title Animation") { step ->
                        Text(
                            if (step == 0) "New playlist" else "Add Songs",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 24.sp,
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                            ),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            if (currentStep == 1) currentStep = 0 else onDismiss()
                        }
                    ) {
                        Icon(
                            if (currentStep == 1) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            contentDescription = "Back or Cancel"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (!showCropUi) {
                MediumExtendedFloatingActionButton(
                    text = { Text(if (currentStep == 0) "Next" else "Create") },
                    icon = { 
                        Icon(
                            if (currentStep == 0) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check, 
                            contentDescription = null
                        ) 
                    },
                    onClick = {
                        if (currentStep == 0) {
                            if (playlistName.isNotBlank()) {
                                currentStep = 1
                            }
                        } else {
                            val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                            val color = if(selectedTab == 2) selectedColor else null
                            val icon = if(selectedTab == 2) selectedIconName else null
                            
                            val scale = if(selectedTab == 1) cropScale else 1f
                            val panX = if(selectedTab == 1) cropOffset.x else 0f
                            val panY = if(selectedTab == 1) cropOffset.y else 0f
                            
                            val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                            val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                when (selectedShapeType) {
                                    PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                    PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                    else -> Quadruple(0f, 0f, 0f, 0f)
                                }
                            } else Quadruple(null, null, null, null)

                            onCreate(
                                playlistName, 
                                imageUriString, 
                                color, 
                                icon, 
                                selectedSongIds.filterValues { it }.keys.toList(),
                                scale,
                                panX,
                                panY,
                                shapeTypeForSave,
                                d1, d2, d3, d4
                            )
                        }
                    },
                    expanded = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 8.dp)
                        .height(56.dp), // Standard height, feels substantial
                    containerColor = if (currentStep == 0 && playlistName.isBlank()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (currentStep == 0 && playlistName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
            label = "Step Transition"
        ) { step ->
            if (step == 0) {
                // Step 1: Info (Name, Cover) - Split Layout
                Column(modifier = Modifier
                    .fillMaxSize()
                    .imePadding()) {
                    
                     // FIXED PREVIEW SECTION
                    AnimatedContent(
                         targetState = selectedTab,
                         transitionSpec = {
                             fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                         },
                         label = "preview_transition",
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(top = 8.dp, bottom = 4.dp)
                    ) { targetTab ->
                         Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp), // Reduced height
                            contentAlignment = Alignment.Center
                        ) {
                            when (targetTab) {
                                0 -> { // Default
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(180.dp)
                                                .clip(RoundedCornerShape(32.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                            contentAlignment = Alignment.Center
                                        ) {
                                             Icon(
                                                Icons.Rounded.GridView,
                                                contentDescription = null,
                                                modifier = Modifier.size(80.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "Auto-generated collage of first 4 songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                1 -> { // Image
                                     if (showCropUi && imageBitmap != null) {
                                         // Crop UI
                                         Box(modifier = Modifier
                                             .size(240.dp)
                                             .clip(RoundedCornerShape(24.dp))) {
                                             ImageCropView(
                                                 imageBitmap = imageBitmap!!,
                                                 modifier = Modifier.fillMaxSize(),
                                                 scale = cropScale,
                                                 pan = cropOffset,
                                                 enabled = true,
                                                 onCrop = { scale, pan -> 
                                                     cropScale = scale
                                                     cropOffset = pan
                                                 }
                                             )
                                             FilledIconButton(
                                                onClick = { showCropUi = false },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(16.dp)
                                            ) {
                                                Icon(Icons.Rounded.Check, contentDescription = "Confirm Crop")
                                            }
                                         }
                                     } else {
                                         // Image Preview (Read Only)
                                         if (imageBitmap != null) {
                                             Box(
                                                modifier = Modifier
                                                    .size(180.dp)
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .clickable { imagePickerLauncher.launch("image/*") }
                                             ) {
                                                 // Reuse ImageCropView in read-only mode for perfect visual match
                                                 // Check if user has actually cropped. If not, default to standard CenterCrop to avoid distortion.
                                                 if (cropScale == 1f && cropOffset == androidx.compose.ui.geometry.Offset.Zero) {
                                                     AsyncImage(
                                                         model = selectedImageUri,
                                                         contentDescription = null,
                                                         modifier = Modifier.fillMaxSize(),
                                                         contentScale = ContentScale.Crop
                                                     )
                                                 } else {
                                                     ImageCropView(
                                                         imageBitmap = imageBitmap!!,
                                                         modifier = Modifier.fillMaxSize(),
                                                         scale = cropScale,
                                                         pan = cropOffset, // Normalized
                                                         enabled = false,
                                                         onCrop = { _, _ -> }
                                                     )
                                                 }
                                             }
                                         } else {
                                             Box(
                                                modifier = Modifier
                                                    .size(180.dp)
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                    .clickable { imagePickerLauncher.launch("image/*") },
                                                contentAlignment = Alignment.Center
                                             ) {
                                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                     Icon(
                                                         Icons.Rounded.AddPhotoAlternate,
                                                         contentDescription = "Add Photo",
                                                         modifier = Modifier.size(56.dp),
                                                         tint = MaterialTheme.colorScheme.primary
                                                     )
                                                     Spacer(Modifier.height(12.dp))
                                                     Text("Pick Image", style = MaterialTheme.typography.titleSmall)
                                                 }
                                             }
                                         }
                                     }
                                }
                                2 -> { // Icon / Custom Shape
                                     AnimatedContent(
                                         targetState = selectedShapeType,
                                         transitionSpec = {
                                             fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f) togetherWith 
                                             fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f)
                                         },
                                         label = "shape_transition"
                                     ) { currentShapeType ->
                                         val currentShape: Shape = when(currentShapeType) {
                                             PlaylistShapeType.Circle -> CircleShape
                                             PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(
                                                 cornerRadiusTL = smoothRectCornerRadius.dp,
                                                 smoothnessAsPercentTR = smoothRectSmoothness.toInt(),
                                                 cornerRadiusTR = smoothRectCornerRadius.dp,
                                                 smoothnessAsPercentTL = smoothRectSmoothness.toInt(),
                                                 cornerRadiusBR = smoothRectCornerRadius.dp,
                                                 smoothnessAsPercentBR = smoothRectSmoothness.toInt(),
                                                 cornerRadiusBL = smoothRectCornerRadius.dp,
                                                 smoothnessAsPercentBL = smoothRectSmoothness.toInt(),
                                             )
                                             PlaylistShapeType.RotatedPill -> {
                                                 // Vertical Pill Shape (Narrower than container)
                                                 androidx.compose.foundation.shape.GenericShape { size, _ ->
                                                     val w = size.width
                                                     val h = size.height
                                                     val pillW = w * 0.75f // 75% width (Fat Pill)
                                                     val offset = (w - pillW) / 2
                                                     addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                                 }
                                             }
                                             PlaylistShapeType.Star -> RoundedStarShape(
                                                 sides = starSides,
                                                 curve = starCurve,
                                                 rotation = starRotation
                                             )
                                         }
                                         
                                         val shapeMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                                         val iconMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
                                         val scaleMod = if(currentShapeType == PlaylistShapeType.Star) Modifier.graphicsLayer(scaleX = starScale, scaleY = starScale) else Modifier
    
                                         Box(
                                             modifier = Modifier
                                                 .size(180.dp)
                                                 .then(scaleMod)
                                                 .then(shapeMod)
                                                 .clip(currentShape)
                                                 .background(selectedColor?.let { Color(it) }
                                                     ?: MaterialTheme.colorScheme.primaryContainer),
                                             contentAlignment = Alignment.Center
                                         ) {
                                             if (selectedIconName != null) {
                                                 val icon = getIconByName(selectedIconName!!) ?: Icons.Rounded.MusicNote
                                                 Icon(
                                                     imageVector = icon,
                                                     contentDescription = null,
                                                     modifier = Modifier
                                                         .size(80.dp)
                                                         .then(iconMod),
                                                     tint = selectedColor?.let { getThemeContentColor(it, MaterialTheme.colorScheme) } 
                                                           ?: MaterialTheme.colorScheme.onPrimaryContainer
                                                 )
                                             }
                                         }
                                     }
                                }
                            }
                        }
                    }

                    // SCROLLABLE DETIALS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // Take remaining space
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        
                        // Name Input (Polished)
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Playlist Name") },
                            placeholder = { Text("My awesome mix") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
    
                        // Expressive Button Group
                        val tabs = listOf("Default", "Image", "Icon")
                        ExpressiveButtonGroup(
                            items = tabs,
                            selectedIndex = selectedTab,
                            onItemClick = { selectedTab = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                        )
                    
                    
                    // Tab Content controls (Colors/Icons)
                    AnimatedVisibility(
                         visible = selectedTab == 2,
//                         enter = fadeIn() + slideInVertically(),
//                         exit = fadeOut() + slideOutVertically()
                    ) {
                         Column(
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(
                                 modifier = Modifier.padding(start = 22.dp),
                                 text = "Background Color",
                                 style = MaterialTheme.typography.titleSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                             
                             val colors = listOf( 
                                 MaterialTheme.colorScheme.primary.toArgb(),
                                 MaterialTheme.colorScheme.primaryContainer.toArgb(),
                                 MaterialTheme.colorScheme.secondary.toArgb(),
                                 MaterialTheme.colorScheme.secondaryContainer.toArgb(),
                                 MaterialTheme.colorScheme.tertiary.toArgb(),
                                 MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
                                 MaterialTheme.colorScheme.error.toArgb(),
                                 MaterialTheme.colorScheme.errorContainer.toArgb(),
                                 MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
                                 MaterialTheme.colorScheme.inverseSurface.toArgb()
                             )
                             
                             FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                             ) {
                                 colors.forEach { color ->
                                     val isSelected = selectedColor == color
                                     val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")
                                     
                                     // Nested Box for separate border
                                     Box(
                                         modifier = Modifier
                                             .size(52.dp) // Slightly larger container
                                            .clip(RoundedCornerShape(cornerRadius))
                                             .background(if (isSelected) Color(color) else Color.Transparent) // Use same color for border effect or transparent
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) Color(color) else Color.Transparent,
                                                shape = RoundedCornerShape(cornerRadius)
                                            ),
                                         contentAlignment = Alignment.Center
                                     ) {
                                          // Inner Solid
                                          Box(
                                             modifier = Modifier
                                                 .size(if (isSelected) 42.dp else 48.dp) // Shrink when selected to create gap
                                                .clip(RoundedCornerShape(if (isSelected) 8.dp else cornerRadius)) // Adjust inner radius
                                                .background(Color(color))
                                                 .clickable { selectedColor = color }
                                          )
                                     }
                                 }
                             }
                             
                             Spacer(Modifier.height(8.dp))

                             Text(
                                 modifier = Modifier.padding(start = 22.dp),
                                 text = "Icon Symbol",
                                 style = MaterialTheme.typography.titleSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
        
                             val icons = listOf(
                                "MusicNote", "Headphones", "Album", "Mic", "Speaker", "Favorite", "Piano", "Queue"
                             )
                             
                             FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                             ) {
                                 icons.forEach { iconName ->
                                     val isSelected = selectedIconName == iconName
                                     val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                                     Box(
                                         modifier = Modifier
                                             .size(52.dp)
                                             .clip(RoundedCornerShape(cornerRadius))
                                             .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                             .clickable { selectedIconName = iconName },
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(
                                             imageVector = getIconByName(iconName) ?: Icons.Rounded.MusicNote,
                                             contentDescription = null,
                                             tint = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                         )
                                     }
                                 }
                             }

                             Spacer(Modifier.height(16.dp))

                             Text(
                                 modifier = Modifier.padding(start = 22.dp),
                                 text = "Shape Style",
                                 style = MaterialTheme.typography.titleSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )

                             LazyRow(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(vertical = 8.dp),
                                 horizontalArrangement = Arrangement.spacedBy(2.dp),
                                 contentPadding = PaddingValues(horizontal = 16.dp)
                             ) {
                                 item {
                                     PlaylistShapeType.entries.forEach { shapeType ->
                                         val isSelected = selectedShapeType == shapeType
                                         // Preview Shape for Selector
                                         val previewShape = when(shapeType) {
                                             PlaylistShapeType.Circle -> CircleShape
                                             PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(12.dp, 60, 12.dp, 60, 12.dp, 60, 12.dp, 60)
                                             PlaylistShapeType.RotatedPill -> androidx.compose.foundation.shape.GenericShape { size, _ ->
                                                 val w = size.width
                                                 val h = size.height
                                                 val pillW = w * 0.75f
                                                 val offset = (w - pillW) / 2
                                                 addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                             }
                                             PlaylistShapeType.Star -> RoundedStarShape(5, 0.15, 0f)
                                         }

                                         val rotationM = if(shapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                                         val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                                         Row(
                                             modifier = Modifier.padding(2.dp)
                                         ) {
                                             Spacer(
                                                 Modifier.width(2.dp)
                                             )
                                             Column(
                                                 horizontalAlignment = Alignment.CenterHorizontally,
                                                 modifier = Modifier
                                                     .clip(RoundedCornerShape(cornerRadius))
                                                     .width(100.dp)
                                                     .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                                     .clickable { selectedShapeType = shapeType }
                                                     .padding(12.dp) // Internal padding
                                             ) {
                                                 Box(
                                                     modifier = Modifier
                                                         .size(40.dp) // Slightly smaller inside
                                                         .then(rotationM)
                                                         .clip(previewShape)
                                                         // Shape color: onSurface (dark) or Primary.
                                                         .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                 }
                                                 Spacer(Modifier.height(8.dp))
                                                 Text(
                                                     text = shapeType.name,
                                                     overflow = TextOverflow.Ellipsis,
                                                     style = MaterialTheme.typography.labelSmall,
                                                     color = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                 )
                                             }
                                             Spacer(
                                                 Modifier.width(2.dp)
                                             )
                                         }
                                     }
                                 }
                             }
                             
                              // Shape Sliders
                             AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.SmoothRect) {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .padding(horizontal = 22.dp)
                                         .padding(top = 8.dp),
                                     verticalArrangement = Arrangement.spacedBy(16.dp)
                                 ) {
                                     Text("Shape parameters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                     
                                     ShapeParameterCard(
                                         title = "Corner Radius",
                                         value = smoothRectCornerRadius,
                                         valueRange = 0f..50f,
                                         onValueChange = { smoothRectCornerRadius = it },
                                         valueDisplay = { it.toInt().toString() }
                                     )
                                     
                                     ShapeParameterCard(
                                         title = "Smoothness",
                                         value = smoothRectSmoothness,
                                         valueRange = 0f..100f,
                                         onValueChange = { smoothRectSmoothness = it },
                                         valueDisplay = { "${it.toInt()}%" }
                                     )
                                 }
                             }
                             
                             AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.Star) {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .padding(horizontal = 22.dp)
                                         .padding(top = 8.dp),
                                     verticalArrangement = Arrangement.spacedBy(16.dp)
                                 ) {
                                     Text("Shape parameters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                     ShapeParameterCard(
                                         title = "Sides",
                                         value = starSides.toFloat(),
                                         valueRange = 3f..20f,
                                         onValueChange = { starSides = it.toInt() },
                                         valueDisplay = { it.toInt().toString() },
                                         steps = 17
                                     )

                                     ShapeParameterCard(
                                         title = "Curve",
                                         value = starCurve.toFloat(),
                                         valueRange = 0f..0.5f,
                                         onValueChange = { starCurve = it.toDouble() },
                                         valueDisplay = { String.format("%.2f", it) }
                                     )
                                     
                                     ShapeParameterCard(
                                         title = "Rotation",
                                         value = starRotation,
                                         valueRange = 0f..360f,
                                         onValueChange = { starRotation = it },
                                         valueDisplay = { "${it.toInt()}°" }
                                     )
                                     
                                     ShapeParameterCard(
                                         title = "Scale",
                                         value = starScale,
                                         valueRange = 0.5f..1.5f,
                                         onValueChange = { starScale = it },
                                         valueDisplay = { String.format("%.1fx", it) }
                                     )
                                 }
                             }
                     
                        }
                    }
                    
                    Spacer(Modifier.height(100.dp)) 
                } // End Scrollable Column
            }
            } else {
                    // Step 2: Songs
                    val filteredSongs = remember(searchQuery, allSongs) {
                        if (searchQuery.isBlank()) allSongs
                        else allSongs.filter {
                            it.title.contains(searchQuery, true) || it.artist.contains(
                                searchQuery,
                                true
                            )
                        }
                    }

                    val animatedAlbumCornerRadius = 60.dp
                    val albumShape = remember(animatedAlbumCornerRadius) {
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = animatedAlbumCornerRadius,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTR = animatedAlbumCornerRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBL = animatedAlbumCornerRadius,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = animatedAlbumCornerRadius,
                            smoothnessAsPercentTL = 60
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column {
                            // Search Bar (Polished)
                            OutlinedTextField(
                                value = searchQuery,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                ),
                                onValueChange = { searchQuery = it },
                                label = { Text("Search for songs...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = CircleShape,
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) IconButton(onClick = {
                                        searchQuery = ""
                                    }) { Icon(Icons.Rounded.Close, null) }
                                }
                            )

                            // Song List
                            SongPickerList(
                                filteredSongs = filteredSongs,
                                isLoading = false,
                                selectedSongIds = selectedSongIds,
                                albumShape = albumShape,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    bottom = 120.dp,
                                    top = 8.dp
                                ) // More padding for FAB
                            )
                        }
                    }
                }
        }
    }
}



private fun getIconByName(name: String?): ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn 
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.Rounded.QueueMusic
        else -> null
    }
}

@Composable
fun ExpressiveButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Transparent, CircleShape), // Optional container background
            //.padding(1.dp), // Check if padding is needed
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Gap between buttons
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val shape = if (isSelected) CircleShape else RoundedCornerShape(10.dp) // Pill vs RoundedRect
            val containerColor by androidx.compose.animation.animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "ButtonColor"
            )
            val contentColor by androidx.compose.animation.animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "ContentColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp) // Taller button
                    .clip(shape)
                    .background(containerColor)
                    .clickable { onItemClick(index) },
                contentAlignment = Alignment.Center
            ) {
                 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                     androidx.compose.animation.AnimatedVisibility(visible = isSelected) {
                         Icon(
                             Icons.Rounded.Check, 
                             contentDescription = null, 
                             modifier = Modifier.padding(end = 8.dp).size(18.dp),
                             tint = contentColor
                         )
                     }
                     Text(
                         text = title,
                         style = MaterialTheme.typography.labelLarge,
                         color = contentColor,
                         fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Medium
                     )
                 }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeParameterCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String,
    steps: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                     text = title,
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                     text = valueDisplay(value),
                     style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.onSurface,
                     fontWeight = FontWeight.Bold
                )
            }
            
            // Custom Thick Slider
            ThickSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun ThickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
             thumbColor = MaterialTheme.colorScheme.primary,
             activeTrackColor = MaterialTheme.colorScheme.primary,
             inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        thumb = {
             Box(
                 modifier = Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
             )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 4.dp,
                drawStopIndicator = null
            )
        }
    )
}


private fun getThemeContentColor(colorArgb: Int, scheme: androidx.compose.material3.ColorScheme): Color {
    return when (colorArgb) {
        scheme.primary.toArgb() -> scheme.onPrimary
        scheme.primaryContainer.toArgb() -> scheme.onPrimaryContainer
        scheme.secondary.toArgb() -> scheme.onSecondary
        scheme.secondaryContainer.toArgb() -> scheme.onSecondaryContainer
        scheme.tertiary.toArgb() -> scheme.onTertiary
        scheme.tertiaryContainer.toArgb() -> scheme.onTertiaryContainer
        scheme.error.toArgb() -> scheme.onError
        scheme.errorContainer.toArgb() -> scheme.onErrorContainer
        scheme.surfaceContainerHigh.toArgb() -> scheme.onSurface
        scheme.inverseSurface.toArgb() -> scheme.inverseOnSurface
        else -> scheme.onPrimaryContainer
    }
}

// End of file
