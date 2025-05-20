package com.theveloper.pixelplay

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.presentation.components.LoadingScreen
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheet
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startMusicServiceIfNeeded()
            } else {
                // Informar al usuario
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // Instalar Splash Screen
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()
        setContent {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val isThemePreloadComplete by playerViewModel.isInitialThemePreloadComplete.collectAsState()
            var isReadyThemePreload by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                playerViewModel.isInitialThemePreloadComplete
                    .first { it }
                isReadyThemePreload = true
            }
            val globalColorSchemePairForApp by playerViewModel.activeGlobalColorSchemePair.collectAsState()
            val playerSpecificColorSchemePairForSheet by playerViewModel.activePlayerColorSchemePair.collectAsState()
            val useDarkTheme = isSystemInDarkTheme()

            val appGlobalTheme = globalColorSchemePairForApp?.let {
                if (useDarkTheme) it.dark else it.light
            } ?: if (useDarkTheme) DarkColorScheme else LightColorScheme

            PixelPlayTheme(
                darkTheme = useDarkTheme,
                colorSchemePairOverride = globalColorSchemePairForApp
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isReadyThemePreload) {
                        LoadingScreen("Preparando tu biblioteca...")
                    } else {
                        val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
                        val isSheetVisible by playerViewModel.isSheetVisible.collectAsState()
                        val currentSheetState by playerViewModel.sheetState.collectAsState()
                        val bottomBarHeightPx by playerViewModel.bottomBarHeight.collectAsState()
                        val navController = rememberNavController()

                        val playerBlackListDestinations = listOf(
                            Screen.Settings.route
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavigation(playerViewModel = playerViewModel, navController = navController)

                            if ((isSheetVisible && stablePlayerState.currentSong != null) || stablePlayerState.isPlaying) {
                                val density = LocalDensity.current
                                val configuration = LocalConfiguration.current
                                val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
                                val miniPlayerHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }
                                val restingTargetTranslationY = if (currentSheetState == PlayerSheetState.EXPANDED) 0f else (screenHeightPx - miniPlayerHeightPx - bottomBarHeightPx)
                                //val restingTargetTranslationY = if (currentSheetState == PlayerSheetState.EXPANDED) { 0f } else { screenHeightPx - miniPlayerHeightPx - bottomBarHeightPx }

                                // Envolver UnifiedPlayerSheet en su propio MaterialTheme para aplicar su tema específico
                                //val playerThemeToApply = if (useDarkTheme) playerSpecificColorSchemePair.dark else playerSpecificColorSchemePair.light
                                val collapsedBottomMarginPx = 30.dp //with(density) { 36.dp.toPx() }
                                // Determinar el tema específico para el reproductor
                                val playerThemeToApply = playerSpecificColorSchemePairForSheet?.let {
                                    if (useDarkTheme) it.dark else it.light
                                } ?: appGlobalTheme // Fallback al tema global de la app si el específico del player es null
                                MaterialTheme(
                                    colorScheme = playerThemeToApply
                                ){
                                    if (!(playerBlackListDestinations.contains(navController.currentDestination?.route))) {
                                        UnifiedPlayerSheet(
                                            playerViewModel = playerViewModel,
                                            initialTargetTranslationY = restingTargetTranslationY,
                                            collapsedStateHorizontalPadding = 22.dp,
                                            collapsedStateBottomMargin = collapsedBottomMarginPx
                                        )
                                    }
                                }
//                                PixelPlayTheme(
//                                    darkTheme = useDarkTheme,
//                                    colorSchemePairOverride = playerSpecificColorSchemePairForSheet // Puede ser null
//                                ){
//                                    if (!(playerBlackListDestinations.contains(navController.currentDestination?.route))) {
//                                        UnifiedPlayerSheet(
//                                            playerViewModel = playerViewModel,
//                                            initialTargetTranslationY = restingTargetTranslationY,
//                                            collapsedStateHorizontalPadding = 22.dp,
//                                            collapsedStateBottomMargin = collapsedBottomMarginPx
//                                        )
//                                    }
//                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isMusicServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MusicService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startMusicServiceIfNeeded() {
        // Aunque startForegroundService es idempotente, esta verificación puede ser útil
        // para evitar lógica redundante si el servicio ya está completamente inicializado y activo.
        // Sin embargo, para MediaSessionService, el sistema maneja mucho de esto.
        // Lo más importante es que onStartCommand no haga trabajo pesado.
        // if (!isMusicServiceRunning()) { // Esta comprobación puede no ser fiable al 100%
        val intent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                startMusicServiceIfNeeded()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // Mostrar diálogo explicativo
                requestPermissionLauncher.launch(permission) // Simplificado
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startMusicService() {
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
        // Puedes añadir un listener para cuando el controlador esté disponible
        mediaControllerFuture?.addListener({
            // MediaController está listo.
            // val mediaController = mediaControllerFuture?.get()
            // playerViewModel.setMediaController(mediaController) // Si el ViewModel lo necesita directamente
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}