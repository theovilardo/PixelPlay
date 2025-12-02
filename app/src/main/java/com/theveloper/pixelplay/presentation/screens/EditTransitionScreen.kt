package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.model.Curve
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.presentation.viewmodel.TransitionViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar(
                message = if (isPlaylistScope && uiState.useGlobalDefaults) {
                    "Using global defaults for this playlist"
                } else {
                    "Transition settings saved"
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = if (isPlaylistScope) "Playlist transitions" else "Global transitions")
                        Text(
                            text = if (isPlaylistScope) "Default transition for all songs" else "Applies everywhere",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveSettings() }, enabled = !uiState.isLoading) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    TransitionModeSection(
                        selected = displayedSettings.mode,
                        onModeSelected = viewModel::updateMode
                    )
                }

                item {
                    TransitionDurationSection(
                        settings = displayedSettings,
                        onDurationChange = viewModel::updateDuration
                    )
                }

                item { Divider() }

                item {
                    TransitionCurvesSection(
                        settings = displayedSettings,
                        onCurveInSelected = viewModel::updateCurveIn,
                        onCurveOutSelected = viewModel::updateCurveOut
                    )
                }
                item { Spacer(modifier = Modifier.padding(bottom = 8.dp)) }
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
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = {},
                    enabled = false,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Filled.AutoAwesomeMotion, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPlaylistScope) "Playlist default transition" else "Global transition",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            !isPlaylistScope -> "Affects every playback source"
                            followingGlobal -> "Following global defaults"
                            hasCustomRule -> "Custom transition applied to all song pairs"
                            else -> "Using playlist defaults"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = isPlaylistScope) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Override for this playlist", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (followingGlobal) "Using the global transition until you customize it." else "Saved locally for every gap in this playlist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !followingGlobal,
                        onCheckedChange = { isEnabled -> if (isEnabled) onEnableOverride() else onResetToGlobal() },
                        enabled = enabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransitionModeSection(
    selected: TransitionMode,
    onModeSelected: (TransitionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Transition type", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Choose how songs blend together", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransitionMode.entries.forEach { mode ->
                AssistChip(
                    onClick = { onModeSelected(mode) },
                    leadingIcon = {
                        val icon = when (mode) {
                            TransitionMode.NONE -> Icons.Outlined.Info
                            TransitionMode.FADE_IN_OUT -> Icons.Filled.Tune
                            TransitionMode.OVERLAP -> Icons.Filled.CheckCircle
                            TransitionMode.SMOOTH -> Icons.Filled.GraphicEq
                        }
                        Icon(icon, contentDescription = null)
                    },
                    label = { Text(mode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }
        }
    }
}

@Composable
private fun TransitionDurationSection(
    settings: TransitionSettings,
    onDurationChange: (Int) -> Unit
) {
    val durationInSeconds = TimeUnit.MILLISECONDS.toSeconds(settings.durationMs.toLong())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Duration", style = MaterialTheme.typography.titleMedium)
                    Text("${durationInSeconds}s blend", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledIconButton(onClick = { onDurationChange(TransitionSettings().durationMs) }, colors = IconButtonDefaults.filledIconButtonColors()) {
                    Icon(Icons.Filled.AutoAwesomeMotion, contentDescription = "Reset")
                }
            }
            Slider(
                value = settings.durationMs.toFloat(),
                onValueChange = { onDurationChange(it.toInt()) },
                valueRange = 0f..12000f,
                steps = 11,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )
            TransitionPreview(durationMs = settings.durationMs)
        }
    }
}

@Composable
private fun TransitionPreview(durationMs: Int) {
    val normalized = durationMs.coerceIn(0, 12000)
    val widthFraction = normalized / 12000f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Preview", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f - widthFraction.coerceAtMost(0.9f))
                    .height(6.dp)
                    .padding(end = 4.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(50))
            )
            Box(
                modifier = Modifier
                    .weight(widthFraction.coerceAtLeast(0.1f))
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
        }
        Text(
            text = "Starts ${TimeUnit.MILLISECONDS.toSeconds((12000 - normalized).toLong())}s before track end",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransitionCurvesSection(
    settings: TransitionSettings,
    onCurveInSelected: (Curve) -> Unit,
    onCurveOutSelected: (Curve) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Volume curves", style = MaterialTheme.typography.titleMedium)
                Text("Control how fast each track fades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            CurveCard(title = "Fade in", selected = settings.curveIn, onCurveSelected = onCurveInSelected)
            CurveCard(title = "Fade out", selected = settings.curveOut, onCurveSelected = onCurveOutSelected)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurveCard(
    title: String,
    selected: Curve,
    onCurveSelected: (Curve) -> Unit
) {
    ElevatedCard(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Curve.entries.forEach { curve ->
                    AssistChip(
                        onClick = { onCurveSelected(curve) },
                        label = { Text(curve.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected == curve) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }
        }
    }
}
