package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

@Composable
fun HomeOptionsBottomSheet(
    onNavigateToMashup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(bottom = 32.dp)) { // Padding for gesture bar
        ListItem(
            headlineContent = { Text("DJ Mashup") },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_instant_mix_24),
                    contentDescription = "DJ Mashup"
                )
            },
            modifier = Modifier.clickable(onClick = onNavigateToMashup)
        )
    }
}