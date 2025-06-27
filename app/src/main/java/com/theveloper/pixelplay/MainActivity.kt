package com.theveloper.pixelplay

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.unit.Dp
// import androidx.compose.ui.platform.LocalView // No longer needed for this
// import androidx.core.view.ViewCompat // No longer needed for this
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.theveloper.pixelplay.presentation.components.NavBarPersistentHeight
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheet
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
//            statusBarStyle = SystemBarStyle.light(
//                android.graphics.Color.TRANSPARENT,
//                android.graphics.Color.TRANSPARENT
//            )
        )

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val useDarkTheme = isSystemInDarkTheme()
            val globalColorSchemePairForApp by playerViewModel.activeGlobalColorSchemePair.collectAsState()

            PixelPlayTheme(
                darkTheme = useDarkTheme,
                colorSchemePairOverride = globalColorSchemePairForApp
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

        LaunchedEffect(Unit) {
            if (!permissionState.allPermissionsGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }

        if (permissionState.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                startMusicServiceIfNeeded()
                Log.i("MainActivity", "Permissions granted. Calling mainViewModel.startSync()")
                mainViewModel.startSync()
            }
            MainAppContent(playerViewModel, mainViewModel)
        } else {
            PermissionsNotGrantedScreen {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    }

    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        val navController = rememberNavController()
        val isSyncing by mainViewModel.isSyncing.collectAsState()
        val isLibraryEmpty by mainViewModel.isLibraryEmpty.collectAsState()
        val showLoadingIndicator = isSyncing && isLibraryEmpty

        Box(modifier = Modifier.fillMaxSize()) {
            // Tu UI principal existente, intacta
            YourExistingUI(playerViewModel, navController)

            if (showLoadingIndicator) {
                LoadingOverlay()
            }
        }
    }

    @Composable
    private fun YourExistingUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        val useDarkTheme = isSystemInDarkTheme()
        val globalColorSchemePairForApp by playerViewModel.activeGlobalColorSchemePair.collectAsState()
        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.drawable.rounded_home_24, R.drawable.rounded_home_24, Screen.Home),
                BottomNavItem("Search", R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
                BottomNavItem("Library", R.drawable.rounded_library_music_24, R.drawable.rounded_library_music_24, Screen.Library)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val routesWithHiddenNavBar = remember {
            setOf(
                Screen.Settings.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.GenreDetail.route
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
            val navBarH = with(density) { MiniPlayerHeight.toPx() } // Using MiniPlayerHeight as NavBarPersistentHeight is not defined
            val collapsedMarginPx = with(density) { collapsedStateBottomMargin.toPx() }
            val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
            val showPlayerContentInitially = stablePlayerState.currentSong != null
            val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
            val spacerH = with(density) { 0.dp.toPx() } // CollapsedPlayerContentSpacerHeight is not defined
            val initialContentHeightPx = if (showPlayerContentInitially) miniPlayerH + spacerH else 0f
            val initialNavBarHeightPx = if (shouldHideNavBar) 0f else navBarH
            val initialTotalSheetHeightPx = initialContentHeightPx + initialNavBarHeightPx
            val initialY = screenHeightPx - initialTotalSheetHeightPx - collapsedMarginPx

            PixelPlayTheme(
                darkTheme = useDarkTheme,
                colorSchemePairOverride = globalColorSchemePairForApp
            ) {
                UnifiedPlayerSheet(
                    playerViewModel = playerViewModel, navController = navController,
                    navItems = commonNavItems,
                    initialTargetTranslationY = initialY,
                    collapsedStateHorizontalPadding = 22.dp,
                    collapsedStateBottomMargin = collapsedStateBottomMargin,
                    hideNavBar = shouldHideNavBar
                )
            }
        }
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

    private fun startMusicServiceIfNeeded() {
        val intent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            playerViewModel.connectToService()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
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

//@AndroidEntryPoint
//class MainActivity : ComponentActivity() {
//
//    private val playerViewModel: PlayerViewModel by viewModels()
//
//    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
//
//    private val requestPermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//            if (isGranted) {
//                startMusicServiceIfNeeded()
//            } else {
//                // Informar al usuario
//            }
//        }
//
//    @OptIn(ExperimentalPermissionsApi::class)
//    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen() // Instalar Splash Screen
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        checkAndRequestPermissions()
//        setContent {
//            val playerViewModel: PlayerViewModel = hiltViewModel()
//            val mainViewModel: MainViewModel = hiltViewModel()
//            val useDarkTheme = isSystemInDarkTheme()
//            val appGlobalTheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
//
//            // Define los permisos necesarios según la versión de Android
//            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                listOf(
//                    Manifest.permission.READ_MEDIA_AUDIO,
//                    Manifest.permission.POST_NOTIFICATIONS
//                )
//            } else {
//                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
//            }
//
//            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
//
//            // Este LaunchedEffect se ejecutará cuando el composable entre en la composición
//            LaunchedEffect(Unit) {
//                if (!permissionState.allPermissionsGranted) {
//                    permissionState.launchMultiplePermissionRequest()
//                }
//            }
//
//            val globalColorSchemePairForApp by playerViewModel.activeGlobalColorSchemePair.collectAsState()
//
//            PixelPlayTheme(
//                darkTheme = useDarkTheme,
//                colorSchemePairOverride = globalColorSchemePairForApp
//            ) {
//                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
//                    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState() // Para initialY
//                    val navController = rememberNavController()
//                    val commonNavItems = remember {
//                        persistentListOf(
//                            BottomNavItem("Home", R.drawable.rounded_home_24, R.drawable.rounded_home_24, Screen.Home),
//                            BottomNavItem("Search", R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
//                            BottomNavItem("Library", R.drawable.rounded_library_music_24, R.drawable.rounded_library_music_24, Screen.Library)
//                        )
//                    }
//
//                    // Keyboard visibility logic REMOVED from MainActivity
//
//                    // NUEVO: Observar la ruta actual para determinar si ocultar navbar
//                    val navBackStackEntry by navController.currentBackStackEntryAsState()
//                    val currentRoute = navBackStackEntry?.destination?.route
//
//                    // NUEVO: Lista de rutas donde la navbar debería estar oculta
//                    val routesWithHiddenNavBar = remember {
//                        setOf(
//                            Screen.Settings.route,
//                            Screen.PlaylistDetail.route,
//                            Screen.DailyMixScreen.route,
//                            Screen.GenreDetail.route
//                        )
//                    }
//
//                    // NUEVO: Determinar si ocultar navbar basado en la ruta actual
//                    val shouldHideNavBar by remember(currentRoute) {
//                        derivedStateOf {
//                            currentRoute?.let { route ->
//                                routesWithHiddenNavBar.any { hiddenRoute ->
//                                    // Para rutas con argumentos como playlist/{playlistId},
//                                    // comparamos solo la parte base de la ruta
//                                    if (hiddenRoute.contains("{")) {
//                                        route.startsWith(hiddenRoute.substringBefore("{"))
//                                    } else {
//                                        route == hiddenRoute
//                                    }
//                                }
//                            } ?: false
//                        }
//                    }
//
//                    Box(modifier = Modifier.fillMaxSize()) {
//                        AppNavigation(playerViewModel = playerViewModel, navController = navController)
//
//                        // UnifiedPlayerSheet AHORA SE COMPONE SIEMPRE
//                        val density = LocalDensity.current
//                        val configuration = LocalConfiguration.current
//                        val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
//                        val collapsedStateBottomMargin = getNavigationBarHeight()//22.dp // AJUSTA ESTE VALOR SEGÚN TU DISEÑO
//
//                        val navBarH = with(density) { NavBarPersistentHeight.toPx() }
//                        val collapsedMarginPx = with(density) { collapsedStateBottomMargin.toPx() }
//
//                        // Determinar si hay contenido de player al inicio (podría venir de un estado restaurado)
//                        val showPlayerContentInitially = stablePlayerState.currentSong != null
//                        val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
//                        val spacerH = with(density) { CollapsedPlayerContentSpacerHeight.toPx() }
//
//                        // MEJORADO: Calcular altura inicial considerando navbar oculta
//                        val initialContentHeightPx = if (showPlayerContentInitially) miniPlayerH + spacerH else 0f
//                        val initialNavBarHeightPx = if (shouldHideNavBar) 0f else navBarH
//                        val initialTotalSheetHeightPx = initialContentHeightPx + initialNavBarHeightPx
//
//                        val initialY = screenHeightPx - initialTotalSheetHeightPx - collapsedMarginPx
//
//                        PixelPlayTheme(
//                            darkTheme = useDarkTheme,
//                            colorSchemePairOverride = globalColorSchemePairForApp
//                        ) {
//                            UnifiedPlayerSheet(
//                                playerViewModel = playerViewModel, navController = navController,
//                                navItems = commonNavItems,
//                                initialTargetTranslationY = initialY,
//                                collapsedStateHorizontalPadding = 22.dp, // AJUSTA ESTE VALOR
//                                collapsedStateBottomMargin = collapsedStateBottomMargin,
//                                hideNavBar = shouldHideNavBar
//                                // isKeyboardVisible = isKeyboardVisible // REMOVE THIS ARGUMENT
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun startMusicServiceIfNeeded() {
//        // Aunque startForegroundService es idempotente, esta verificación puede ser útil
//        // para evitar lógica redundante si el servicio ya está completamente inicializado y activo.
//        // Sin embargo, para MediaSessionService, el sistema maneja mucho de esto.
//        // Lo más importante es que onStartCommand no haga trabajo pesado.
//        // if (!isMusicServiceRunning()) { // Esta comprobación puede no ser fiable al 100%
//        val intent = Intent(this, MusicService::class.java)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(intent)
//        } else {
//            startService(intent)
//        }
//        // }
//    }
//
//    private fun checkAndRequestPermissions() {
//        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            Manifest.permission.READ_MEDIA_AUDIO
//        } else {
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        }
//
//        when {
//            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
//                startMusicServiceIfNeeded()
//            }
//            shouldShowRequestPermissionRationale(permission) -> {
//                // Mostrar diálogo explicativo
//                requestPermissionLauncher.launch(permission) // Simplificado
//            }
//            else -> {
//                requestPermissionLauncher.launch(permission)
//            }
//        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
//            }
//        }
//    }
//
//    private fun startMusicService() {
//        val intent = Intent(this, MusicService::class.java)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(intent)
//        } else {
//            startService(intent)
//        }
//    }
//
//    override fun onStart() {
//        super.onStart()
//        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
//        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
//        // Puedes añadir un listener para cuando el controlador esté disponible
//        mediaControllerFuture?.addListener({
//            // MediaController está listo.
//            // val mediaController = mediaControllerFuture?.get()
//            // playerViewModel.setMediaController(mediaController) // Si el ViewModel lo necesita directamente
//        }, MoreExecutors.directExecutor())
//    }
//
//    override fun onStop() {
//        super.onStop()
//        mediaControllerFuture?.let {
//            MediaController.releaseFuture(it)
//        }
//    }
//}