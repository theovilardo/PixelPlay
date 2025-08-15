package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

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
        },
        containerColor = Color.LightGray.copy(alpha = 0.9f)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = "Match the black area's corners with your device's corners",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .padding(top = 32.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..50f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 16.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 12.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(
                        bottomStart = sliderValue.dp,
                        bottomEnd = sliderValue.dp
                    )
                ) {}
            }
        }
    }
}
