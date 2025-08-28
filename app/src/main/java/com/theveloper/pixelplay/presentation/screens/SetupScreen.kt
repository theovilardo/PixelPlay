package com.theveloper.pixelplay.presentation.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.subcomps.MaterialYouVectorDrawable
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.presentation.viewmodel.SetupViewModel
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SetupScreen(
    setupViewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val pages = remember {
        val list = mutableListOf<SetupPage>(
            SetupPage.Welcome,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(SetupPage.MediaPermission)
            list.add(SetupPage.NotificationsPermission)
        } else {
            list.add(SetupPage.MediaPermission)
        }
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
                onNextClicked = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onFinishClicked = {
                    setupViewModel.setSetupComplete()
                    onSetupComplete()
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
                    SetupPage.MediaPermission -> MediaPermissionPage()
                    SetupPage.NotificationsPermission -> NotificationsPermissionPage()
                    SetupPage.AllFilesPermission -> AllFilesPermissionPage()
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
            text = "Welcome to PixelPlay",
            style = ExpTitleTypography.displayLarge.copy(
                fontSize = 42.sp,
                lineHeight = 1.1.em
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        SineWaveLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp),
            animate = true,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            alpha = 0.95f,
            strokeWidth = 3.dp,
            amplitude = 4.dp,
            waves = 7.6f,
            phase = 0f
        )
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
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Let's get everything set up for you.", style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionPage() {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)

    PermissionPageLayout(
        title = "Media Permission",
        description = "PixelPlay needs access to your audio files to build your music library.",
        buttonText = if (permissionState.allPermissionsGranted) "Permission Granted" else "Grant Media Permission",
        onGrantClicked = {
            if (!permissionState.allPermissionsGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationsPermissionPage() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permissionState = rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.POST_NOTIFICATIONS))

    PermissionPageLayout(
        title = "Notifications",
        description = "Enable notifications to control your music from the lock screen and notification shade.",
        buttonText = if (permissionState.allPermissionsGranted) "Permission Granted" else "Enable Notifications",
        onGrantClicked = {
            if (!permissionState.allPermissionsGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    )
}

@Composable
fun AllFilesPermissionPage() {
    val context = LocalContext.current
    PermissionPageLayout(
        title = "All Files Access",
        description = "For some Android versions, PixelPlay needs broader file access to find all your music.",
        buttonText = "Go to Settings",
        onGrantClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            }
        }
    )
}

@Composable
fun FinishPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "All Set!", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder for vector art
        Box(modifier = Modifier.size(200.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "You're ready to enjoy your music.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PermissionPageLayout(
    title: String,
    description: String,
    buttonText: String,
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
        // Placeholder for vector art
        Box(modifier = Modifier.size(200.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGrantClicked) {
            Text(text = buttonText)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupBottomBar(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp) // Padding para efecto flotante
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp), // Sombra con la misma forma
                clip = true
            ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp) // Bordes redondeados expresivos
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // La onda sinusoidal animada como decoración superior
            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                strokeWidth = 2.5.dp,
                amplitude = 6.dp,
                waves = 1.5f,
                animate = true,
                animationDurationMillis = 3000
            )

            // Contenido de la barra inferior (indicador y botones)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aquí iría tu CustomPagerIndicator. Como no tengo el código,
                // lo represento con un Text. Reemplázalo por tu Composable.
                Text(
                    text = "Step ${pagerState.currentPage + 1} of ${pagerState.pageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                // Botón dinámico que cambia según la página actual
                ElevatedButton(
                    onClick = if (pagerState.currentPage < pagerState.pageCount - 1) onNextClicked else onFinishClicked,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                        Icon(Icons.Rounded.ArrowForward, contentDescription = "Siguiente")
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = "Finalizar")
                    }
                }
            }
        }
    }
}
//@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
//@Composable
//fun SetupBottomBar(
//    pagerState: PagerState,
//    onNextClicked: () -> Unit,
//    onFinishClicked: () -> Unit
//) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(16.dp),
//        horizontalArrangement = Arrangement.SpaceBetween,
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        CustomPagerIndicator(
//            pagerState = pagerState,
//            modifier = Modifier.weight(1f)
//        )
//        if (pagerState.currentPage < pagerState.pageCount - 1) {
//            Button(onClick = onNextClicked) {
//                Text(text = "Next")
//            }
//        } else {
//            Button(onClick = onFinishClicked) {
//                Text(text = "Finish")
//            }
//        }
//    }
//}

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
