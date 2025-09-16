package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

const val DEFAULT_NAV_BAR_CORNER_RADIUS = 32f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBarCornerRadiusScreen(
    navController: NavController, settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    var sliderValue by remember { mutableFloatStateOf(uiState.navBarCornerRadius.toFloat()) }
    var hasBeenAdjusted by remember { mutableStateOf(sliderValue != DEFAULT_NAV_BAR_CORNER_RADIUS) }

    LaunchedEffect(uiState.navBarCornerRadius) {
        sliderValue = uiState.navBarCornerRadius.toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("NavBar Corner Radius") }, actions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Button(
                        onClick = {
                            settingsViewModel.setNavBarCornerRadius(sliderValue.toInt())
                            navController.popBackStack()
                        },
                    ) {
                        Text("Done")
                    }
                }
            })
        }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Text(
                text = "Match the navbar shape's corners with your device's corners",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .padding(top = 32.dp)
            )


            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hasBeenAdjusted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 16.dp), horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                sliderValue = DEFAULT_NAV_BAR_CORNER_RADIUS
                                hasBeenAdjusted = false
                            }, colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = "Reset",
                                )
                                Text("Reset")
                            }
                        }
                    }
                }

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        if (!hasBeenAdjusted) {
                            hasBeenAdjusted = true
                        }
                    },
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
