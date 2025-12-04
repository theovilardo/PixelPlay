package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class
)
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
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsState()
    val trackVolume by playerViewModel.trackVolume.collectAsState()

    val activeRoute = selectedRoute?.takeUnless { it.isDefault }
    val isRemoteSession = isRemotePlaybackActive && activeRoute != null

    var sliderPosition by remember { mutableFloatStateOf(if (isRemoteSession) routeVolume.toFloat() else trackVolume) }

    LaunchedEffect(isRemoteSession, routeVolume, trackVolume) {
        sliderPosition = if (isRemoteSession) routeVolume.toFloat() else trackVolume
    }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(visible = isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    trackColor = colors.primary.copy(alpha = 0.1f)
                )
            }

            CastStatusHeader(
                isRemote = isRemoteSession,
                routeName = activeRoute?.name ?: "Este dispositivo",
                isPlaying = playerViewModel.stablePlayerState.collectAsState().value.isPlaying,
                onDisconnect = { playerViewModel.disconnect() },
                onRefresh = { playerViewModel.refreshCastRoutes() }
            )

            VolumeCard(
                isRemote = isRemoteSession,
                volume = sliderPosition,
                onVolumeChange = { newVolume ->
                    sliderPosition = newVolume
                    if (isRemoteSession) {
                        playerViewModel.setRouteVolume(newVolume.toInt())
                    } else {
                        playerViewModel.setTrackVolume(newVolume)
                    }
                },
                route = activeRoute,
                localVolumeRange = 0f..1f
            )

            ServiceStatusRow(
                isWifiEnabled = isWifiEnabled,
                isBluetoothEnabled = isBluetoothEnabled,
                onEnableWifi = { playerViewModel.refreshCastRoutes() },
                onEnableBluetooth = { playerViewModel.refreshCastRoutes() }
            )

            DeviceSection(
                routes = routes,
                selectedRoute = activeRoute,
                onRouteSelected = playerViewModel::selectRoute,
                onDisconnect = playerViewModel::disconnect,
                routeVolume = routeVolume,
                isRefreshing = isRefreshing,
                onRouteVolumeChange = playerViewModel::setRouteVolume
            )
        }
    }
}

@Composable
private fun CastStatusHeader(
    isRemote: Boolean,
    routeName: String,
    isPlaying: Boolean,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    val statusShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 28.dp,
        cornerRadiusTR = 12.dp,
        cornerRadiusBL = 12.dp,
        cornerRadiusBR = 28.dp,
        smoothnessAsPercentTL = 65,
        smoothnessAsPercentTR = 45,
        smoothnessAsPercentBL = 45,
        smoothnessAsPercentBR = 65
    )
    ElevatedCard(
        shape = statusShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRemote) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRemote) "Transmitiendo en" else "Reproduciendo en",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                AnimatedContent(
                    targetState = routeName,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(120)))
                    },
                    label = "routeName"
                ) { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(if (isPlaying) "En vivo" else "Pausado")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                        disabledContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(
                    onClick = onRefresh,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar dispositivos")
                }
                FilledTonalButton(
                    onClick = onDisconnect,
                    enabled = isRemote,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (isRemote) "Volver al teléfono" else "Listo")
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(
    isRemote: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    route: MediaRouter.RouteInfo?,
    localVolumeRange: ClosedFloatingPointRange<Float>
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 22.dp,
        cornerRadiusTR = 12.dp,
        cornerRadiusBL = 12.dp,
        cornerRadiusBR = 22.dp,
        smoothnessAsPercentTL = 55,
        smoothnessAsPercentTR = 40,
        smoothnessAsPercentBL = 40,
        smoothnessAsPercentBR = 55
    )
    val label = if (isRemote) "Volumen del dispositivo" else "Volumen local"
    val subtitle = route?.name ?: "Teléfono"
    val (rangeStart, rangeEnd) = if (isRemote && route != null) {
        0f to route.volumeMax.toFloat().coerceAtLeast(1f)
    } else {
        localVolumeRange.start to localVolumeRange.endInclusive
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isRemote) MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (isRemote) "Remoto" else "Local") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isRemote) Icons.Default.Cast else Icons.Rounded.Headphones,
                            contentDescription = null
                        )
                    }
                )
            }

            Slider(
                value = volume.coerceIn(rangeStart, rangeEnd),
                onValueChange = onVolumeChange,
                valueRange = rangeStart..rangeEnd,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun ServiceStatusRow(
    isWifiEnabled: Boolean,
    isBluetoothEnabled: Boolean,
    onEnableWifi: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceBadge(
            enabled = isWifiEnabled,
            label = "Wi‑Fi",
            icon = Icons.Default.Cast,
            onClick = onEnableWifi
        )
        ServiceBadge(
            enabled = isBluetoothEnabled,
            label = "Bluetooth",
            icon = Icons.Rounded.Bluetooth,
            onClick = onEnableBluetooth
        )
    }
}

@Composable
private fun ServiceBadge(
    enabled: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val badgeColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val strokeColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = badgeColor),
        border = CardDefaults.outlinedCardBorder(strokeColor = strokeColor),
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = strokeColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = strokeColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (enabled) "Activado" else "Necesario para buscar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedContent(targetState = enabled, label = "serviceState") { state ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (state) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(
    routes: List<MediaRouter.RouteInfo>,
    selectedRoute: MediaRouter.RouteInfo?,
    onRouteSelected: (MediaRouter.RouteInfo) -> Unit,
    onDisconnect: () -> Unit,
    routeVolume: Int,
    isRefreshing: Boolean,
    onRouteVolumeChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dispositivos cercanos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            AssistChip(
                onClick = onDisconnect,
                label = { Text("Volver aquí") },
                leadingIcon = {
                    Icon(Icons.Default.Cast, contentDescription = null)
                }
            )
        }

        val availableRoutes = routes.filterNot { it.isDefault }
        if (availableRoutes.isEmpty() && !isRefreshing) {
            EmptyDeviceState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                availableRoutes.forEach { route ->
                    CastDeviceCard(
                        route = route,
                        isSelected = selectedRoute?.id == route.id,
                        onSelect = { onRouteSelected(route) },
                        onDisconnect = onDisconnect,
                        routeVolume = routeVolume,
                        onVolumeChange = onRouteVolumeChange
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDeviceState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Buscando dispositivos...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Asegúrate de que tu TV o parlante esté encendido y en la misma red.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CastDeviceCard(
    route: MediaRouter.RouteInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit,
    routeVolume: Int,
    onVolumeChange: (Int) -> Unit
) {
    val expressiveShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 18.dp,
        cornerRadiusTR = 30.dp,
        cornerRadiusBL = 30.dp,
        cornerRadiusBR = 18.dp,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentTR = 75,
        smoothnessAsPercentBL = 75,
        smoothnessAsPercentBR = 60
    )
    val deviceIcon = when (route.deviceType) {
        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
        MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH -> Icons.Rounded.Bluetooth
        else -> Icons.Default.Cast
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (isSelected) onDisconnect else onSelect),
        shape = expressiveShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(deviceIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (isSelected) "Conectado" else "Disponible") },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = if (route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE) R.drawable.rounded_wifi_24 else R.drawable.rounded_bluetooth_24),
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledLabelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                            )
                        )
                        if (route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Sesión activa") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
            if (route.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && isSelected) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${routeVolume.coerceIn(0, route.volumeMax)} / ${route.volumeMax}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Slider(
                        value = routeVolume.coerceIn(0, route.volumeMax).toFloat(),
                        onValueChange = { onVolumeChange(it.toInt()) },
                        valueRange = 0f..route.volumeMax.toFloat(),
                        modifier = Modifier.width(150.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}
