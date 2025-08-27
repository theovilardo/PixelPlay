package com.theveloper.pixelplay.presentation.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.theveloper.pixelplay.presentation.viewmodel.SetupViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalPagerApi::class, ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(
    setupViewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()

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
            count = pages.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { pageIndex ->
            val page = pages[pageIndex]
            val pageOffset = calculateCurrentOffsetForPage(pageIndex).absoluteValue

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - pageOffset
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
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Welcome to PixelPlay", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder for vector art
        Box(modifier = Modifier.size(200.dp))
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

@OptIn(ExperimentalPagerApi::class)
@Composable
fun SetupBottomBar(
    pagerState: PagerState,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier.weight(1f),
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        if (pagerState.currentPage < pagerState.pageCount - 1) {
            Button(onClick = onNextClicked) {
                Text(text = "Next")
            }
        } else {
            Button(onClick = onFinishClicked) {
                Text(text = "Finish")
            }
        }
    }
}
