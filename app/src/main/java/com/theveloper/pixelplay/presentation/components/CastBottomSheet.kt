package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.SliderDefaults
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CastBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val routes by playerViewModel.castRoutes.collectAsState()
    val selectedRoute by playerViewModel.selectedRoute.collectAsState()
    val routeVolume by playerViewModel.routeVolume.collectAsState()
    val isRefreshing by playerViewModel.isRefreshingRoutes.collectAsState()
    val isWifiEnabled by playerViewModel.isWifiEnabled.collectAsState()
    val isBluetoothEnabled by playerViewModel.isBluetoothEnabled.collectAsState()
    val context = LocalContext.current

    val wifiPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES
        )
    )

    val bluetoothPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainer,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colors.primary
            )
        }
    ) {
        Scaffold(
            containerColor = colors.surfaceContainer,
            floatingActionButton = {
                MediumFloatingActionButton(onClick = { playerViewModel.refreshCastRoutes() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh devices")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedVisibility(visible = isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = "Connect to a device",
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )

                if (!isWifiEnabled) {
                    EnableServiceCard(
                        serviceName = "Wi-Fi",
                        iconRes = R.drawable.rounded_wifi_24,
                        onEnableClick = {
                            if (wifiPermissions.allPermissionsGranted) {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            } else {
                                wifiPermissions.launchMultiplePermissionRequest()
                            }
                        }
                    )
                }

                if (!isBluetoothEnabled) {
                    EnableServiceCard(
                        serviceName = "Bluetooth",
                        iconRes = R.drawable.rounded_bluetooth_24,
                        onEnableClick = {
                            if (bluetoothPermissions.allPermissionsGranted) {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } else {
                                bluetoothPermissions.launchMultiplePermissionRequest()
                            }
                        }
                    )
                }

                if (routes.isEmpty() && !isRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Searching for devices...", color = colors.onSurface)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = routes.toList(),
                            key = { it.id }
                        ) { route ->
                            if (!route.isDefault) {
                                DeviceItem(
                                    route = route,
                                    isSelected = route.id == selectedRoute?.id,
                                    currentVolume = routeVolume,
                                    onConnect = {
                                        playerViewModel.selectRoute(route)
                                    },
                                    onDisconnect = {
                                        playerViewModel.disconnect()
                                    },
                                    onVolumeChange = { newVolume ->
                                        playerViewModel.setRouteVolume(newVolume)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnableServiceCard(
    serviceName: String,
    iconRes: Int,
    onEnableClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "$serviceName disabled",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "$serviceName is disabled",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(onClick = onEnableClick) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun DeviceItem(
    route: MediaRouter.RouteInfo,
    isSelected: Boolean,
    currentVolume: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 18.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusTR = 18.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = 18.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBR = 18.dp,
        smoothnessAsPercentTR = 60
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Green, CircleShape)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }

                    val deviceIconRes = when (route.deviceType) {
                        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> R.drawable.rounded_tv_24
                        MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> R.drawable.rounded_speaker_24
                        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER -> R.drawable.rounded_speaker_24
                        else -> R.drawable.rounded_cast_24
                    }

                    Icon(
                        painter = painterResource(id = deviceIconRes),
                        contentDescription = "Device Type",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val connectionTypeRes = when {
                            route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> R.drawable.rounded_bluetooth_24
                            route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE -> R.drawable.rounded_wifi_24
                            else -> null
                        }

                        if (connectionTypeRes != null) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .height(22.dp)
                                    .width(28.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    painter = painterResource(id = connectionTypeRes),
                                    contentDescription = "Connection Type",
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(18.dp)
                                        .padding(2.dp),
                                    tint = (if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                if (isSelected) {
                    FilledTonalButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Button(onClick = onConnect) {
                        Text("Connect")
                    }
                }
            }
            if (isSelected && route.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) {
                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..route.volumeMax.toFloat(),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    )
                )
            }
        }
    }
}