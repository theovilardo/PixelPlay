package com.theveloper.pixelplay

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import android.os.Trace // Import Trace
import androidx.compose.ui.unit.Dp
// import androidx.compose.ui.platform.LocalView // No longer needed for this
import androidx.core.view.WindowCompat
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheet
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.components.AllFilesAccessDialog
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.SetupScreen
import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlayerInternalNavigationBar
import javax.annotation.concurrent.Immutable
import androidx.core.net.toUri
import com.theveloper.pixelplay.presentation.components.DismissUndoBar
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeightFullWidth
import kotlin.math.pow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.lerp
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Immutable
data class BottomNavItem(
    val label: String,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtils.d(this, "onCreate")
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val useDarkTheme = isSystemInDarkTheme()
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsState()
            var showSetupScreen by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(isSetupComplete) {
                if (showSetupScreen == null) {
                    showSetupScreen = !isSetupComplete
                }
            }

            PixelPlayTheme(
                darkTheme = useDarkTheme
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSetupScreen != null) {
                        AnimatedContent(
                            targetState = showSetupScreen,
                            transitionSpec = {
                                if (targetState == false) {
                                    // Transition from Setup to Main App
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) togetherWith
                                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                                } else {
                                    // Placeholder for other transitions, e.g., Main App to Setup
                                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                }
                            },
                            label = "SetupTransition"
                        ) { targetState ->
                            if (targetState == true) {
                                SetupScreen(onSetupComplete = { showSetupScreen = false })
                            } else {
                                HandlePermissions(mainViewModel)
                            }
                        }
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("ACTION_SHOW_PLAYER", false) == true) {
            playerViewModel.showPlayer()
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun HandlePermissions(mainViewModel: MainViewModel) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val permissionState = rememberMultiplePermissionsState(permissions = permissions)

        var showAllFilesAccessDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (!permissionState.allPermissionsGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }

        if (permissionState.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                LogUtils.i(this, "Permissions granted")
                Log.i("MainActivity", "Permissions granted. Calling mainViewModel.startSync()")
                mainViewModel.startSync()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                    showAllFilesAccessDialog = true
                }
            }
            MainAppContent(playerViewModel, mainViewModel)
        } else {
            PermissionsNotGrantedScreen {
                permissionState.launchMultiplePermissionRequest()
            }
        }

        if (showAllFilesAccessDialog) {
            AllFilesAccessDialog(
                onDismiss = { showAllFilesAccessDialog = false },
                onConfirm = {
                    showAllFilesAccessDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:$packageName".toUri()
                        requestAllFilesAccessLauncher.launch(intent)
                    }
                }
            )
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        Trace.beginSection("MainActivity.MainAppContent")
        val navController = rememberNavController()
        val isSyncing by mainViewModel.isSyncing.collectAsState()
        val isLibraryEmpty by mainViewModel.isLibraryEmpty.collectAsState()

        // Estado para controlar si el indicador de carga puede mostrarse después de un delay
        var canShowLoadingIndicator by remember { mutableStateOf(false) }

        val shouldPotentiallyShowLoading = isSyncing && isLibraryEmpty

        LaunchedEffect(shouldPotentiallyShowLoading) {
            if (shouldPotentiallyShowLoading) {
                // Espera un breve período antes de permitir que se muestre el indicador de carga
                // Ajusta este valor según sea necesario (por ejemplo, 300-500 ms)
                delay(300L)
                // Vuelve a verificar la condición después del delay,
                // ya que el estado podría haber cambiado.
                if (mainViewModel.isSyncing.value && mainViewModel.isLibraryEmpty.value) {
                    canShowLoadingIndicator = true
                }
            } else {
                // Si las condiciones ya no se cumplen, asegúrate de que no se muestre
                canShowLoadingIndicator = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(playerViewModel, navController)

            // Muestra el LoadingOverlay solo si las condiciones se cumplen Y el delay ha pasado
            if (shouldPotentiallyShowLoading && canShowLoadingIndicator) {
                LoadingOverlay()
            }
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        Trace.beginSection("MainActivity.MainUI")

        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
                BottomNavItem("Search", R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
                BottomNavItem("Library", R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, Screen.Library)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val routesWithHiddenNavigationBar = remember {
            setOf(
                Screen.Settings.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.GenreDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.DJSpace.route,
                Screen.NavBarCrRad.route,
                Screen.About.route
            )
        }
        val shouldHideNavigationBar by remember(currentRoute) {
            derivedStateOf {
                currentRoute?.let { route ->
                    routesWithHiddenNavigationBar.any { hiddenRoute ->
                        if (hiddenRoute.contains("{")) {
                            route.startsWith(hiddenRoute.substringBefore("{"))
                        } else {
                            route == hiddenRoute
                        }
                    }
                } ?: false
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsState()

        val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val horizontalPadding = if (navBarStyle == NavBarStyle.DEFAULT) {
            if (systemNavBarInset > 30.dp) 14.dp else systemNavBarInset
        } else {
            0.dp
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (!shouldHideNavigationBar) {
                    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction.value
                    val showPlayerContentArea = playerViewModel.stablePlayerState.collectAsState().value.currentSong != null
                    val navBarCornerRadius by playerViewModel.navBarCornerRadius.collectAsState()
                    val navBarElevation = 3.dp

                    val playerContentActualBottomRadiusTargetValue by remember(
                        navBarStyle,
                        showPlayerContentArea,
                        playerContentExpansionFraction,
                    ) {
                        derivedStateOf {
                            if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                                return@derivedStateOf lerp(32.dp, 26.dp, playerContentExpansionFraction)
                            }

                            if (showPlayerContentArea) {
                                if (playerContentExpansionFraction < 0.2f) {
                                    lerp(12.dp, 26.dp, (playerContentExpansionFraction / 0.2f).coerceIn(0f, 1f))
                                } else {
                                    26.dp
                                }
                            } else {
                                navBarCornerRadius.dp
                            }
                        }
                    }

                    val playerContentActualBottomRadius by animateDpAsState(
                        targetValue = playerContentActualBottomRadiusTargetValue,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ),
                        label = "PlayerContentBottomRadius"
                    )

                    val navBarHideFraction = if (showPlayerContentArea) playerContentExpansionFraction else 0f

                    val actualShape = remember(playerContentActualBottomRadius, showPlayerContentArea, navBarStyle, navBarCornerRadius) {
                        val bottomRadius = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else navBarCornerRadius.dp
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = playerContentActualBottomRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = playerContentActualBottomRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = bottomRadius,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBR = bottomRadius,
                            smoothnessAsPercentBL = 60
                        )
                    }

                    val conditionalShape = if (showPlayerContentArea) {
                        actualShape
                    } else {
                        if (navBarStyle == NavBarStyle.DEFAULT) {
                            RoundedCornerShape(60.dp)
                        } else { // FULL_WIDTH
                            RoundedCornerShape(0.dp)
                        }
                    }



                    var componentHeightPx by remember { mutableStateOf(0) }
                    val animatedTranslationY by remember(navBarHideFraction, componentHeightPx) { derivedStateOf { componentHeightPx * navBarHideFraction } }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { componentHeightPx = it.height }
                            .graphicsLayer { translationY = animatedTranslationY }
                    ) {
                        val navHeight: Dp
                        val bottomPadding: Dp

                        if (navBarStyle == NavBarStyle.DEFAULT) {
                            navHeight = NavBarContentHeight
                            bottomPadding = systemNavBarInset
                        } else { // FULL_WIDTH
                            navHeight = NavBarContentHeightFullWidth + systemNavBarInset
                            bottomPadding = 0.dp
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(navHeight)
                                .padding(horizontal = horizontalPadding)
                                .padding(bottom = bottomPadding),
                            color = NavigationBarDefaults.containerColor,
                            shape = conditionalShape,
                            shadowElevation = navBarElevation
                        ) {
                            PlayerInternalNavigationBar(
                                navController = navController,
                                navItems = commonNavItems,
                                currentRoute = currentRoute,
                                navBarStyle = navBarStyle,
                                navBarInset = systemNavBarInset,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val screenHeightPx = remember(configuration) { with(density) { configuration.containerSize.height } }
                val containerHeight = this.maxHeight

                val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
                val showPlayerContentInitially = stablePlayerState.currentSong != null

                val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route) }
                val shouldHideMiniPlayer by remember(currentRoute) {
                    derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                }

                val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
                val totalSheetHeightWhenContentCollapsedPx = if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                val bottomMargin = innerPadding.calculateBottomPadding()

                val spacerPx = with(density) { MiniPlayerBottomSpacer.toPx() }
                val sheetCollapsedTargetY = screenHeightPx - totalSheetHeightWhenContentCollapsedPx - with(density){ bottomMargin.toPx() } - spacerPx

                AppNavigation(
                    playerViewModel = playerViewModel,
                    navController = navController,
                    paddingValues = innerPadding
                )

                UnifiedPlayerSheet(
                    playerViewModel = playerViewModel,
                    sheetCollapsedTargetY = sheetCollapsedTargetY,
                    collapsedStateHorizontalPadding = horizontalPadding,
                    hideMiniPlayer = shouldHideMiniPlayer,
                    containerHeight = containerHeight,
                    isNavBarHidden = shouldHideNavigationBar
                )

                val playerUiState by playerViewModel.playerUiState.collectAsState()

                AnimatedVisibility(
                    visible = playerUiState.showDismissUndoBar,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + MiniPlayerBottomSpacer)
                        .padding(horizontal = horizontalPadding)
                ) {
                    DismissUndoBar(
                        modifier = Modifier
                            .fillMaxWidth()
//                            .background(
//                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
//                                shape = CircleShape
//                            )
                            .height(MiniPlayerHeight)
                            .padding(horizontal = 14.dp),
                        onUndo = { playerViewModel.undoDismissPlaylist() },
                        onClose = { playerViewModel.hideDismissUndoBar() },
                        durationMillis = playerUiState.undoBarVisibleDuration
                    )
                }
            }
        }
        Trace.endSection()
    }

    @Composable
    private fun LoadingOverlay() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing your library...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }

    @Composable
    fun PermissionsNotGrantedScreen(onRequestPermissions: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PixelPlay needs access to your audio files to scan and play your music. Please grant permission to continue.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permission")
            }
        }
    }


    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

}