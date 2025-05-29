package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
            .padding(top = 12.dp, bottom = 8.dp, start = 16.dp, end = 16.dp), // Adjusted bottom padding
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Action Button (replaces FAB)
        FilledTonalButton(
            onClick = onMainActionClick,
            shape = defaultShape, // Using fallback shape, replace with your AbsoluteSmoothCornerShape if available
            // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 26.dp, ...), // Your custom shape
//            colors = ButtonDefaults.buttonColors(
//                containerColor = MaterialTheme.colorScheme.primaryContainer,
//                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
//            ),
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
                        .then(if (currentPage != 3) Modifier.rotate(iconRotation) else Modifier) // Only rotate shuffle
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
                IconButton(onClick = onSortIconClick) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant // Good contrast
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = onDismissSortMenu,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant) // Custom background for dropdown
                ) {
                    currentSortOptionsForTab.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                onSortOptionSelected(option)
                                // onDismissSortMenu() // Already called in LibraryScreen's onSortOptionSelected lambda
                            },
                            leadingIcon = if (option::class == selectedSortOption::class) { // Check if it's the selected one
                                {
                                    Icon(
                                        Icons.Filled.Check,
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