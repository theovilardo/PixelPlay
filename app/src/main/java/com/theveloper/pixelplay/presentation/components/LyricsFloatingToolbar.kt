package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsFloatingToolbar(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    showSyncedLyrics: Boolean?,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    lyrics: Lyrics?,
    onSaveLyricsAsLrc: () -> Unit,
    onResetImportedLyrics: () -> Unit,
    isSyncControlsVisible: Boolean,
    onToggleSyncControls: () -> Unit,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    isImmersiveTemporarilyDisabled: Boolean = false,
    onSetImmersiveTemporarilyDisabled: ((Boolean) -> Unit)? = null
) {
    if (showSyncedLyrics == null) return

    Row(
        modifier = modifier
            .fillMaxWidth(),
            //.padding(start = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            onClick = onNavigateBack
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = onBackgroundColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleSegmentButton(
                modifier = Modifier.weight(1f).height(50.dp),
                active = showSyncedLyrics,
                activeColor = accentColor,
                inactiveColor = backgroundColor,
                activeContentColor = onAccentColor,
                inactiveContentColor = onBackgroundColor,
                activeCornerRadius = 50.dp,
                onClick = { onShowSyncedLyricsChange(true) },
                text = "Synced"
            )

            ToggleSegmentButton(
                modifier = Modifier.weight(1f).height(50.dp),
                active = !showSyncedLyrics,
                activeColor = accentColor,
                inactiveColor = backgroundColor,
                activeContentColor = onAccentColor,
                inactiveContentColor = onBackgroundColor,
                activeCornerRadius = 50.dp,
                onClick = { onShowSyncedLyricsChange(false) },
                text = "Static"
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        var expanded by remember { mutableStateOf(false) }
        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            onClick = { expanded = !expanded }
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Lyrics options",
                tint = onBackgroundColor
            )
            DropdownMenu(
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusBL = 20.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBR = 20.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusTL = 20.dp,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusTR = 20.dp,
                    smoothnessAsPercentBR = 60
                ),
                containerColor = backgroundColor,
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Save lyrics as .lrc option
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_save_24),
                            contentDescription = null
                        )
                    },
                    text = { Text(text = stringResource(R.string.save_lyrics_dialog_title).substringBefore("?")) }, // quick hack, verify strings
                    enabled = lyrics != null,
                    onClick = {
                        expanded = false
                        onSaveLyricsAsLrc()
                    }
                )
                // Reset imported lyrics option
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_restart_alt_24),
                            contentDescription = null
                        )
                    },
                    text = { Text(text = stringResource(R.string.reset_imported_lyrics)) },
                    onClick = {
                        expanded = false
                        onResetImportedLyrics()
                    }
                )

                // Sync Controls Toggle
                if (showSyncedLyrics) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null
                            )
                        },
                        text = { Text(text = if (isSyncControlsVisible) "Hide Sync Controls" else "Adjust Sync") },
                        onClick = {
                            expanded = false
                            onToggleSyncControls()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(CircleShape),
                        thickness = 2.dp,
                        color = onBackgroundColor.copy(alpha = 0.4f)
                    )


                    // Immersive Mode Temporary Disable
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Immersive Off (Once)", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                    ),
                                    checked = isImmersiveTemporarilyDisabled,
                                    onCheckedChange = null, // Handled by parent click
                                )
                            }
                        },
                        onClick = {
                            onSetImmersiveTemporarilyDisabled?.invoke(!isImmersiveTemporarilyDisabled)
                            // Keep menu open for toggling? Or close it? Usually better to keep open for switches.
                            // But for now let's just letting it handle the click.
                        }
                    )
                    //HorizontalDivider()
                }
            }
        }
    }
}
