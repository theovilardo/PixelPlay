package com.theveloper.pixelplay.presentation.telegram.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramDashboardScreen(
    onAddChannel: () -> Unit,
    onBack: () -> Unit,
    viewModel: TelegramDashboardViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val isRefreshingId by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    // Show status message in snackbar
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synced Channels") },
                navigationIcon = {
                     // Back button handled by activity usually, generic here
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChannel) {
                Icon(Icons.Default.Add, contentDescription = "Add Channel")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (channels.isEmpty()) {
                EmptyState(
                     modifier = Modifier.align(Alignment.Center),
                     onAdd = onAddChannel
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels, key = { it.chatId }) { channel ->
                        ChannelItem(
                            channel = channel,
                            isSyncing = isRefreshingId == channel.chatId,
                            onSync = { viewModel.refreshChannel(channel) },
                            onDelete = { viewModel.removeChannel(channel.chatId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItem(
    channel: TelegramChannelEntity,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Photo
            if (channel.photoPath != null) {
                AsyncImage(
                    model = File(channel.photoPath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${channel.songCount} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                 IconButton(onClick = onSync) {
                     Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                 }
                 IconButton(onClick = onDelete) {
                     Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                 }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Add, // Placeholder icon
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Channels Synced",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a public Telegram channel to sync its music library.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Text("Add Channel")
        }
    }
}
