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
// import androidx.core.view.ViewCompat // No longer needed for this
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
import com.theveloper.pixelplay.presentation.components.CollapsedPlayerContentSpacerHeight
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheet
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.components.AllFilesAccessDialog
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem
import com.theveloper.pixelplay.presentation.navigation.Screen
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
import androidx.compose.runtime.SideEffect

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
        )

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val useDarkTheme = isSystemInDarkTheme()

            PixelPlayTheme(
                darkTheme = useDarkTheme
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HandlePermissions(mainViewModel)
                }
            }
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
                        intent.data = android.net.Uri.parse("package:$packageName")
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
        val useDarkTheme = isSystemInDarkTheme()
        // val globalColorSchemePairForApp by playerViewModel.activeGlobalColorSchemePair.collectAsState() // Removed
        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
                BottomNavItem("Search", R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
                BottomNavItem("Library", R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, Screen.Library)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val routesWithHiddenNavBar = remember {
            setOf(
                Screen.Settings.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.GenreDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.DJSpace.route,
                Screen.NavBarCrRad.route
            )
        }
        val shouldHideNavBar by remember(currentRoute) {
            derivedStateOf {
                currentRoute?.let { route ->
                    routesWithHiddenNavBar.any { hiddenRoute ->
                        if (hiddenRoute.contains("{")) {
                            route.startsWith(hiddenRoute.substringBefore("{"))
                        } else {
                            route == hiddenRoute
                        }
                    }
                } ?: false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AppNavigation(playerViewModel = playerViewModel, navController = navController)

            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
            val collapsedStateBottomMargin = getNavigationBarHeight()
            val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val navBarH = with(density) { (NavBarContentHeight + collapsedStateBottomMargin).toPx() }
            val collapsedMarginPx = with(density) { collapsedStateBottomMargin.toPx() }
            val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
            val showPlayerContentInitially = stablePlayerState.currentSong != null
            val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
            val spacerH = with(density) { 0.dp.toPx() } // CollapsedPlayerContentSpacerHeight is not defined
            val initialContentHeightPx = if (showPlayerContentInitially) miniPlayerH + spacerH else 0f
            val initialNavBarHeightPx = if (shouldHideNavBar) 0f else navBarH
            val initialTotalSheetHeightPx = initialContentHeightPx + initialNavBarHeightPx
            val initialY = screenHeightPx - initialTotalSheetHeightPx - collapsedMarginPx
            val horizontalMargin = systemNavBarInset

            // The PixelPlayTheme wrapping UnifiedPlayerSheet is removed as UnifiedPlayerSheet
            // now handles its own theming internally based on playerViewModel's activePlayerColorSchemePair
            // and the system MaterialTheme.colorScheme.
            // The outer PixelPlayTheme in setContent > HandlePermissions > MainAppContent
            // provides the overall app theme.
            UnifiedPlayerSheet(
                playerViewModel = playerViewModel, navController = navController,
                    navItems = commonNavItems,
                    initialTargetTranslationY = initialY,
                    collapsedStateHorizontalPadding = collapsedStateBottomMargin,
                    collapsedStateBottomMargin = collapsedStateBottomMargin,
                    hideNavBar = shouldHideNavBar
            )
        }
        Trace.endSection() // End MainActivity.MainUI
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
                    text = "Preparando tu biblioteca...",
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
                text = "Permiso Requerido",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PixelPlay necesita acceso a tus archivos de audio para poder escanear y reproducir tu música. Por favor, concede el permiso para continuar.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermissions) {
                Text("Conceder Permiso")
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
            playerViewModel.connectToService()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    // Helper composable para la altura de la barra de navegación, si lo necesitas
    @Composable
    fun getNavigationBarHeight(): Dp {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return if (bottomPadding > 0.dp) bottomPadding else 22.dp // Fallback
    }

}