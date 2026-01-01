package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.ui.res.vectorResource

@Composable
fun SettingsSection(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        onClick: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClick)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Box(
                        modifier = Modifier.padding(end = 4.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Switch(
                    checked = checked,
                    onCheckedChange = { if (enabled) onCheckedChange(it) },
                    enabled = enabled,
                    colors =
                            SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        showSheet = true
                    }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp, top = 2.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Selected Value Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                             text = selectedOption,
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.primary,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall, // Larger header
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    fontWeight = FontWeight.Bold
                )
                
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        
                        Surface(
                            onClick = {
                                onSelectionChanged(key)
                                showSheet = false
                            },
                            shape = RoundedCornerShape(24.dp), // Expressive full round
                            color = containerColor,
                            modifier = Modifier.fillMaxWidth().height(72.dp) // Thick/Tall item
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)) // Large corners for the group
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        content()
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
        valueText: (Float) -> String
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = valueText(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                )
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
        }
    }
}

@Composable
fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        onRefresh: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = "Refresh Library",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = "Tap the sync button to rescan and update your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                        onClick = onRefresh,
                        enabled = !isSyncing,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Refresh library",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text =
                                    "Scanned ${syncProgress.currentCount} of ${syncProgress.totalCount} songs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = "Refreshing library…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RefreshLyricsItem(
        isRefreshing: Boolean,
        progress: LyricsRefreshProgress,
        onRefresh: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            painter = painterResource(id = R.drawable.rounded_lyrics_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = "Refresh Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = "Automatically fetch lyrics for all songs using lrclib.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Refresh lyrics",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isRefreshing && progress.hasProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text =
                                "Processing ${progress.currentCount} of ${progress.totalSongs} songs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GeminiApiKeyItem(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    title: String,
    subtitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter API Key") },
                singleLine = true
            )
        }
    }
}
