package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Curve
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.presentation.viewmodel.TransitionViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransitionScreen(
    navController: NavController,
    viewModel: TransitionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val displayedSettings = if (uiState.useGlobalDefaults) {
        uiState.globalSettings
    } else {
        uiState.rule?.settings ?: uiState.globalSettings
    }
    val isPlaylistScope = uiState.playlistId != null
    val hasCustomRule = uiState.rule != null && !uiState.useGlobalDefaults
    val isCrossfadeEnabled = displayedSettings.mode != TransitionMode.NONE

    // Configuración para el comportamiento de la TopBar colapsable (Material 3)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar(
                message = if (isPlaylistScope && uiState.useGlobalDefaults) {
                    "Using global defaults"
                } else {
                    "Changes saved successfully"
                }
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = if (isPlaylistScope) "Playlist rules" else "Global transitions",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier
                            .padding(start = 10.dp),
                        onClick = { navController.navigateUp() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        onClick = { viewModel.saveSettings() },
                        enabled = !uiState.isLoading,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                    ){
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
//            if (!uiState.isLoading) {
//                ExtendedFloatingActionButton(
//                    modifier = Modifier.padding(),
//                    onClick = { viewModel.saveSettings() },
//                    icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
//                    text = { Text("Save changes") },
//                    containerColor = MaterialTheme.colorScheme.primaryContainer,
//                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
//                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp) // Flat style for consistency
//                )
//            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = if (isPlaylistScope) "Configure default behavior for this specific playlist." else "This configuration applies to all playback sources unless overridden.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                item {
                    TransitionSummaryCard(
                        isPlaylistScope = isPlaylistScope,
                        hasCustomRule = hasCustomRule,
                        followingGlobal = uiState.useGlobalDefaults,
                        onResetToGlobal = { viewModel.useGlobalDefaults() },
                        onEnableOverride = { viewModel.enablePlaylistOverride() },
                        enabled = isPlaylistScope
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                item {
                    TransitionModeSection(
                        selected = displayedSettings.mode,
                        onModeSelected = viewModel::updateMode
                    )
                }

                // Animación de visibilidad: Oculta controles complejos si no hay transición
                item {
                    AnimatedVisibility(
                        visible = isCrossfadeEnabled,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            TransitionDurationSection(
                                settings = displayedSettings,
                                onDurationChange = viewModel::updateDuration
                            )

                            TransitionCurvesSection(
                                settings = displayedSettings,
                                onCurveInSelected = viewModel::updateCurveIn,
                                onCurveOutSelected = viewModel::updateCurveOut
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionSummaryCard(
    isPlaylistScope: Boolean,
    hasCustomRule: Boolean,
    followingGlobal: Boolean,
    onResetToGlobal: () -> Unit,
    onEnableOverride: () -> Unit,
    enabled: Boolean
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active Status",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when {
                            !isPlaylistScope -> "Global Default"
                            followingGlobal -> "Following Global"
                            hasCustomRule -> "Custom Override"
                            else -> "Playlist Default"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            AnimatedVisibility(visible = isPlaylistScope) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)) {
                            Text("Custom Override", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Enable to set specific rules for this playlist.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = !followingGlobal,
                            onCheckedChange = { isEnabled -> if (isEnabled) onEnableOverride() else onResetToGlobal() },
                            enabled = enabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionModeSection(
    selected: TransitionMode,
    onModeSelected: (TransitionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Transition Style", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "How tracks blend together",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Componente Toggle rediseñado: Plano, simétrico, sin sombras raras
        ExpressiveMorphingToggle(
            options = listOf(TransitionMode.NONE, TransitionMode.OVERLAP),
            selectedOption = selected,
            onOptionSelected = onModeSelected
        )
    }
}

@Composable
private fun ExpressiveMorphingToggle(
    options: List<TransitionMode>,
    selectedOption: TransitionMode,
    onOptionSelected: (TransitionMode) -> Unit
) {
    val selectedIndex = if (selectedOption == TransitionMode.OVERLAP) 1 else 0
    val shape = CircleShape //RoundedCornerShape(16.dp) // Menos redondeado para más estructura, o 50 para capsula

    // Contenedor Plano con borde sutil
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            //.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp)
    ) {
        val maxWidth = maxWidth
        val indicatorWidth = maxWidth / 2

        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedIndex == 1) indicatorWidth else 0.dp,
            animationSpec = tween(durationMillis = 300),
            label = "offset"
        )

        // El indicador se mueve detrás del texto
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .fillMaxSize()
                .offset(x = indicatorOffset)
                .clip(CircleShape) // Un poco más pequeño que el contenedor
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { mode ->
                val isSelected = selectedOption == mode
                val title = if (mode == TransitionMode.OVERLAP) "Crossfade" else "None"

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { onOptionSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionDurationSection(
    settings: TransitionSettings,
    onDurationChange: (Int) -> Unit
) {
    val durationInSeconds = TimeUnit.MILLISECONDS.toSeconds(settings.durationMs.toLong())

    // Tarjeta limpia, sin bordes innecesarios, usando el fondo para agrupar
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("Transition Duration", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${durationInSeconds}s total overlap",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilledIconButton(
                onClick = { onDurationChange(TransitionSettings().durationMs) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Reset")
            }
        }

        // Visualizador contextual de canciones
        CrossfadeVisualizer(durationMs = settings.durationMs)

        Slider(
            value = settings.durationMs.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = 0f..12000f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(height = 36.dp, width = 8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        //.border(4.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                )
            }
        )
    }
}

@Composable
private fun CrossfadeVisualizer(durationMs: Int) {
    val maxDuration = 12000f
    val normalized = durationMs.coerceIn(0, 12000)
    // Porcentaje de la superposición respecto al máximo
    val overlapFactor by animateFloatAsState(targetValue = normalized / maxDuration, label = "width")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Labels de canciones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Current Song",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                "Next Song",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Barra Canción 1 (Izquierda -> Derecha)
                // Se extiende hasta la mitad + la mitad del overlap
                Box(
                    modifier = Modifier
                        .weight(1f) // Base width
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        )
                ) {
                    // Extensión visual de la barra superior (Song 1 Ending)
                    // Esta lógica es visual para representar el "overlap"
                }

                // Barra Canción 2 (Derecha -> Izquierda)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                        )
                )
            }

            // Área de superposición dinámica (El "Crossfade")
            // Representa el tiempo compartido
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.1f + (overlapFactor * 0.4f)) // Mínimo visual + factor
                    .background(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow//.copy(alpha = 0.8f) // Masking effect
                    )
                    .height(32.dp)
//                    .border(
//                        1.dp,
//                        MaterialTheme.colorScheme.outlineVariant,
//                        RoundedCornerShape(8.dp)
//                    )
            ) {
                // Representación interna del cruce
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 0.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 50.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBR = 0.dp,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBL = 50.dp,
                                    smoothnessAsPercentBR = 60
                                )
                            )
                    )
                    //Spacer(modifier = Modifier.width(1.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 50.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 0.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBR = 50.dp,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBL = 0.dp,
                                    smoothnessAsPercentBR = 60
                                )
                            )
                    )
                }
                // Icono central
                Icon(
                    Icons.Rounded.AutoAwesomeMotion,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Explicación textual dinámica
        Text(
            text = "Tracks will overlap for ${TimeUnit.MILLISECONDS.toSeconds(durationMs.toLong())}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TransitionCurvesSection(
    settings: TransitionSettings,
    onCurveInSelected: (Curve) -> Unit,
    onCurveOutSelected: (Curve) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Volume Curves", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Fine tune the audio slope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            // Usamos colores terciarios para la salida (Fade Out)
            CurveSelectionColumn(
                modifier = Modifier.weight(1f),
                title = "Fade Out",
                selected = settings.curveOut,
                onCurveSelected = onCurveOutSelected,
                activeColor = MaterialTheme.colorScheme.tertiaryContainer,
                onActiveColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            // Usamos colores secundarios para la entrada (Fade In)
            CurveSelectionColumn(
                modifier = Modifier.weight(1f),
                title = "Fade In",
                selected = settings.curveIn,
                onCurveSelected = onCurveInSelected,
                activeColor = MaterialTheme.colorScheme.secondaryContainer,
                onActiveColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun CurveSelectionColumn(
    modifier: Modifier,
    title: String,
    selected: Curve,
    onCurveSelected: (Curve) -> Unit,
    activeColor: Color,
    onActiveColor: Color
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp, top = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Curve.entries.forEach { curve ->
                    val isSelected = selected == curve

                    // Diseño Expressive: La selección es una forma, no solo un check
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) activeColor else Color.Transparent)
                            .clickable { onCurveSelected(curve) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = curve.name.lowercase().replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = onActiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}