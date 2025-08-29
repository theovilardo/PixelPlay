package com.theveloper.pixelplay.presentation.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.PermissionIconCollage
import com.theveloper.pixelplay.presentation.components.subcomps.MaterialYouVectorDrawable
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.presentation.viewmodel.SetupUiState
import com.theveloper.pixelplay.presentation.viewmodel.SetupViewModel
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SetupScreen(
    setupViewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by setupViewModel.uiState.collectAsState()

    // Re-check permissions when the screen is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setupViewModel.checkPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pages = remember {
        val list = mutableListOf<SetupPage>(
            SetupPage.Welcome,
        )
        // Add media permissions page for all versions
        list.add(SetupPage.MediaPermission)
        // Add notifications permission page for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(SetupPage.NotificationsPermission)
        }
        // Add all files access page for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(SetupPage.AllFilesPermission)
        }
        list.add(SetupPage.Finish)
        list
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            SetupBottomBar(
                pagerState = pagerState,
                animated = (pagerState.currentPage != 0),
                onNextClicked = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onFinishClicked = {
                    // Re-check permissions before finishing
                    setupViewModel.checkPermissions(context)
                    if (uiState.allPermissionsGranted) {
                        setupViewModel.setSetupComplete()
                        onSetupComplete()
                    } else {
                        Toast.makeText(context, "Please grant all required permissions.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { pageIndex ->
            val page = pages[pageIndex]
            val pageOffset = pagerState.currentPageOffsetFraction

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - pageOffset.coerceIn(0f, 1f)
                        translationX = size.width * pageOffset
                    },
                contentAlignment = Alignment.Center
            ) {
                when (page) {
                    SetupPage.Welcome -> WelcomePage()
                    SetupPage.MediaPermission -> MediaPermissionPage(uiState)
                    SetupPage.NotificationsPermission -> NotificationsPermissionPage(uiState)
                    SetupPage.AllFilesPermission -> AllFilesPermissionPage(uiState)
                    SetupPage.Finish -> FinishPage()
                }
            }
        }
    }
}

sealed class SetupPage {
    object Welcome : SetupPage()
    object MediaPermission : SetupPage()
    object NotificationsPermission : SetupPage()
    object AllFilesPermission : SetupPage()
    object Finish : SetupPage()
}

@Composable
fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(top = 12.dp),
            text = "Welcome to PixelPlay",
            style = ExpTitleTypography.displayLarge.copy(
                fontSize = 42.sp,
                lineHeight = 1.1.em
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder for vector art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                //.background(color = Color.Red)
                .clip(RoundedCornerShape(20.dp))
        ){
            MaterialYouVectorDrawable(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(R.drawable.welcome_art)
            )
            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.surface,
                alpha = 0.95f,
                strokeWidth = 16.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(22.dp)
                    .background(color = MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp)
            ){

            }
            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary, //Container.copy(alpha = 0.9f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Let's get everything set up for you.", style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionPage(uiState: SetupUiState) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)
    val mediaIcons = persistentListOf(
        R.drawable.rounded_music_note_24,
        R.drawable.rounded_album_24,
        R.drawable.rounded_library_music_24,
        R.drawable.rounded_artist_24,
        R.drawable.rounded_playlist_play_24
    )

    // Sync the granted state with the ViewModel
    val isGranted = uiState.mediaPermissionGranted || permissionState.allPermissionsGranted

    PermissionPageLayout(
        title = "Media Permission",
        granted = isGranted,
        description = "PixelPlay needs access to your audio files to build your music library.",
        buttonText = if (isGranted) "Permission Granted" else "Grant Media Permission",
        icons = mediaIcons,
        onGrantClicked = {
            if (!isGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationsPermissionPage(uiState: SetupUiState) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permissionState = rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.POST_NOTIFICATIONS))
    val notificationIcons = persistentListOf(
        R.drawable.rounded_circle_notifications_24,
        R.drawable.rounded_skip_next_24,
        R.drawable.rounded_play_arrow_24,
        R.drawable.rounded_pause_24,
        R.drawable.rounded_skip_previous_24
    )

    // Sync the granted state with the ViewModel
    val isGranted = uiState.notificationsPermissionGranted || permissionState.allPermissionsGranted

    PermissionPageLayout(
        title = "Notifications",
        granted = isGranted,
        description = "Enable notifications to control your music from the lock screen and notification shade.",
        buttonText = if (isGranted) "Permission Granted" else "Enable Notifications",
        icons = notificationIcons,
        onGrantClicked = {
            if (!isGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    )
}

@Composable
fun AllFilesPermissionPage(uiState: SetupUiState) {
    val context = LocalContext.current
    val fileIcons = persistentListOf(
        R.drawable.rounded_question_mark_24,
        R.drawable.rounded_attach_file_24,
        R.drawable.rounded_imagesmode_24,
        R.drawable.rounded_broken_image_24,
        R.drawable.rounded_folder_24
    )

    val isGranted = uiState.allFilesAccessGranted

    PermissionPageLayout(
        title = "All Files Access",
        granted = isGranted,
        description = "For some Android versions, PixelPlay needs broader file access to find all your music.",
        buttonText = if(isGranted) "Permission Granted" else "Go to Settings",
        icons = fileIcons,
        onGrantClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isGranted) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
            }
        }
    )
}

@Composable
fun FinishPage() {
    val finishIcons = persistentListOf(
        R.drawable.rounded_check_circle_24,
        R.drawable.round_favorite_24,
        R.drawable.rounded_celebration_24,
        R.drawable.round_favorite_24,
        R.drawable.rounded_explosion_24
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "All Set!", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        PermissionIconCollage(
            modifier = Modifier.height(230.dp),
            icons = finishIcons
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "You're ready to enjoy your music.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PermissionPageLayout(
    title: String,
    granted: Boolean = false,
    description: String,
    buttonText: String,
    icons: ImmutableList<Int>,
    onGrantClicked: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        PermissionIconCollage(icons = icons)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantClicked,
            enabled = !granted,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            AnimatedVisibility(visible = granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = "Granted")
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Text(text = buttonText)
        }
    }
}

/**
 * Una Bottom Bar flotante con un diseño expresivo inspirado en Material 3,
 * que incluye una onda sinusoidal animada en la parte superior.
 *
 * @param modifier Modificador para el Composable.
 * @param pagerState El estado del Pager para mostrar el indicador de página.
 * @param onNextClicked Lambda que se invoca al pulsar el botón "Siguiente".
 * @param onFinishClicked Lambda que se invoca al pulsar el botón "Finalizar".
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SetupBottomBar(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    pagerState: PagerState,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit
) {
    // --- Animaciones para el Morphing y Rotación ---
    val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
    // Animación más lenta y sutil para la rotación
    val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

    // 1. Determina los porcentajes de las esquinas para la forma objetivo
    val targetShapeValues = when (pagerState.currentPage % 3) {
        0 -> listOf(50f, 50f, 50f, 50f) // Círculo (50% en todas las esquinas)
        1 -> listOf(16f, 16f, 16f, 16f) // Cuadrado Redondeado
        else -> listOf(18f, 50f, 18f, 50f) // Forma de "Hoja"
    }

    // 2. Anima cada esquina individualmente hacia el valor objetivo
    val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
    val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
    val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
    val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")

    // 3. Anima la rotación del botón para que gire 360 grados en cada cambio de página.
    val animatedRotation by animateFloatAsState(
        targetValue = pagerState.currentPage * 360f,
        animationSpec = rotationAnimationSpec,
        label = "Rotation"
    )

    Surface(
        modifier = modifier
            //.padding(horizontal = 24.dp, vertical = 16.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), clip = true),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
//            SineWaveLine(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(16.dp)
//                    .padding(top = 16.dp, bottom = 6.dp),
//                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
//                strokeWidth = 2.dp,
//                amplitude = 6.dp,
//                waves = 1.5f,
//                //animate = true,
//                animationDurationMillis = 3000
//            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- CAMBIO CLAVE: Texto animado ---
                AnimatedContent(
                    targetState = pagerState.currentPage,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                        } else {
                            (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "StepTextAnimation"
                ) { targetPage ->
                    if (targetPage == 0) {
                        Text(
                            text = "Let's Go!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = "Step ${targetPage} of ${pagerState.pageCount - 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 4. Aplica la forma y rotación animadas al botón
                MediumFloatingActionButton(
                    onClick = if (pagerState.currentPage < pagerState.pageCount - 1) onNextClicked else onFinishClicked,
                    shape = RoundedCornerShape(
                        topStartPercent = animatedTopStart.toInt(),
                        topEndPercent = animatedTopEnd.toInt(),
                        bottomStartPercent = animatedBottomStart.toInt(),
                        bottomEndPercent = animatedBottomEnd.toInt()
                    ),
                    modifier = Modifier
                        .rotate(animatedRotation)
                        .padding(end = 0.dp)
                ) {
                    // 5. Aplica una contra-rotación al contenido del botón (el icono)
                    AnimatedContent(
                        modifier = Modifier.rotate(-animatedRotation),
                        targetState = pagerState.currentPage < pagerState.pageCount - 1,
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220, delayMillis = 90)),
                                initialContentExit = fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.9f, animationSpec = tween(90))
                            ).using(SizeTransform(clip = false))
                        },
                        label = "AnimatedFabIcon"
                    ) { isNextPage ->
                        if (isNextPage) {
                            Icon(Icons.Rounded.ArrowForward, contentDescription = "Siguiente")
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = "Finalizar")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomPagerIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until pagerState.pageCount) {
            val color by animateColorAsState(
                targetValue = if (i == pagerState.currentPage) activeColor else inactiveColor,
                animationSpec = tween(durationMillis = 300)
            )
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
