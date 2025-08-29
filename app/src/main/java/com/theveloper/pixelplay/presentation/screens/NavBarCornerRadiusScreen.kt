package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBarCornerRadiusScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    var sliderValue by remember { mutableStateOf(uiState.navBarCornerRadius.toFloat()) }

    LaunchedEffect(uiState.navBarCornerRadius) {
        sliderValue = uiState.navBarCornerRadius.toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NavBar Corner Radius") },
                actions = {
                    TextButton(onClick = {
                        settingsViewModel.setNavBarCornerRadius(sliderValue.toInt())
                        navController.popBackStack()
                    }) {
                        Text("Done")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Text(
                text = "Match the black area's corners with your device's corners",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .padding(top = 32.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 16.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = paddingValues.calculateBottomPadding()),
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 10.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTR = 10.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = sliderValue.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusBL = sliderValue.dp,
                        smoothnessAsPercentTR = 60
                    )
                ) {

                }
            }
        }
    }
}
