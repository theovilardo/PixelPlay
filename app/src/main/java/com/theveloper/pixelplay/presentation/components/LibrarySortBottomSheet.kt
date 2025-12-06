package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOf
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySortBottomSheet(
    title: String,
    options: List<SortOption>,
    selectedOption: SortOption?,
    onDismiss: () -> Unit,
    onOptionSelected: (SortOption) -> Unit,
    showViewToggle: Boolean = false,
    viewToggleChecked: Boolean = false,
    onViewToggleChange: (Boolean) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.surfaceContainerLow

    // Animate background color
    val boxBackgroundColor by animateColorAsState(
        targetValue = if (viewToggleChecked) MaterialTheme.colorScheme.tertiary else unselectedColor,
        label = "boxBackgroundColorAnimation"
    )

    // Animate corner radius
    val boxCornerRadius by animateDpAsState(
        targetValue = if (viewToggleChecked) 18.dp else 50.dp,
        label = "boxCornerRadiusAnimation"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 16.dp)
            )

            // Cast to nullable list to handle potential runtime nulls, then filter
            @Suppress("UNCHECKED_CAST")
            val safeOptions = (options as List<SortOption?>).filterNotNull()
            
            safeOptions.forEach { option ->
                // Defensive null-check for selectedOption in case it's null at runtime
                val isSelected = selectedOption?.storageKey == option.storageKey
                val containerColor = remember(isSelected) {
                    if (isSelected) selectedColor else unselectedColor
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(shape = CircleShape)
                        .selectable(
                            selected = isSelected,
                            onClick = { onOptionSelected(option) },
                            role = Role.RadioButton
                        )
                        .semantics { this.selected = isSelected }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    }
                }
            }

            if (showViewToggle) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "View",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                )

                val viewContainerColor = remember(viewToggleChecked) {
                    if (viewToggleChecked) selectedColor else unselectedColor
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .clip(
                            AbsoluteSmoothCornerShape(
                                cornerRadiusBL = boxCornerRadius,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusTR = boxCornerRadius,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusTL = boxCornerRadius,
                                smoothnessAsPercentBL = 60,
                                cornerRadiusBR = boxCornerRadius,
                                smoothnessAsPercentTR = 60
                            )
                        ) // Apply animated corner radius for clipping
                        .background(color = boxBackgroundColor)   // Apply animated background color
                        .clickable(
                            onClick = {
                                onViewToggleChange(!viewToggleChecked)
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playlist View",
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp, end = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (viewToggleChecked) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface // Adjust text color for contrast
                        )
                        Switch(
                            checked = viewToggleChecked,
                            onCheckedChange = {
                                onViewToggleChange(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            thumbContent = if (viewToggleChecked) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Switch is on",
                                        tint = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}