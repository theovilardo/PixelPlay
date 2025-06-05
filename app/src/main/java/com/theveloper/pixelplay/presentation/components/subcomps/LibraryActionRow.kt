package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SortOption

val defaultShape = RoundedCornerShape(26.dp) // Fallback shape

@Composable
fun LibraryActionRow(
    currentPage: Int,
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    showSortButton: Boolean,
    onSortIconClick: () -> Unit,
    showSortMenu: Boolean,
    onDismissSortMenu: () -> Unit,
    currentSortOptionsForTab: List<SortOption>,
    selectedSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp), // Adjusted bottom padding
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Action Button (replaces FAB)
        FilledTonalButton(
            onClick = onMainActionClick,
            shape = defaultShape, // Using fallback shape, replace with your AbsoluteSmoothCornerShape if available
            // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 26.dp, ...), // Your custom shape
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp, // Slightly reduced elevation for a normal button
                pressedElevation = 6.dp
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp) // Standard button padding
        ) {
            val icon = if (currentPage == 3) Icons.Filled.PlaylistAdd else Icons.Filled.Shuffle
            val text = if (currentPage == 3) "New Playlist" else "Shuffle"
            val contentDesc = if (currentPage == 3) "Create New Playlist" else "Shuffle Play"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    modifier = Modifier
                        .size(20.dp)
                        .then(Modifier.rotate(iconRotation)) // Only rotate shuffle
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Sort Button and Dropdown Menu
        if (showSortButton) {
            Box { // Box is needed for DropdownMenu positioning
                FilledTonalIconButton(onClick = onSortIconClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort Options",
                        //tint = MaterialTheme.colorScheme.onSurfaceVariant // Good contrast
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = onDismissSortMenu,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Color.Transparent,
                    shadowElevation = 0.dp,
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) // Custom background for dropdown
                ) {
                    currentSortOptionsForTab.forEach { option: SortOption ->
                        val enabled = option::class == selectedSortOption::class
                        DropdownMenuItem(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow, //if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = if (enabled) CircleShape else RoundedCornerShape(12.dp)
                                )
                                .clip(if (enabled) CircleShape else RoundedCornerShape(12.dp)),
                            text = { Text(option.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                onSortOptionSelected(option)
                                // onDismissSortMenu() // Already called in LibraryScreen's onSortOptionSelected lambda
                            },
                            leadingIcon = if (enabled) { // Check if it's the selected one
                                {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        } else {
            // If no sort button, add a Spacer to maintain balance if the main button is not centered
            // Or ensure the main button is centered if it's the only element.
            // For SpaceBetween arrangement, this side will just be empty.
        }
    }
}