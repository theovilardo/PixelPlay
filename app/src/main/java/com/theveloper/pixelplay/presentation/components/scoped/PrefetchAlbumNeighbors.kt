package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PrefetchAlbumNeighbors(
    currentSong: com.theveloper.pixelplay.data.model.Song,
    queue: ImmutableList<com.theveloper.pixelplay.data.model.Song>,
    trigger: Boolean,
    radius: Int = 2
) {
    if (!trigger) return
    val context = LocalContext.current
    val doneKey = remember(currentSong.id) { mutableStateOf(false) }
    LaunchedEffect(trigger, currentSong.id) {
        if (doneKey.value) return@LaunchedEffect
        val idx = queue.indexOfFirst { it.id == currentSong.id }
        if (idx >= 0) {
            val neighbors = (maxOf(0, idx - radius)..minOf(queue.lastIndex, idx + radius))
                .filter { it != idx }
                .mapNotNull { queue[it].albumArtUriString }
            neighbors.forEach { uriStr ->
                val request = ImageRequest.Builder(context)
                    .data(uriStr)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
                Coil.imageLoader(context).enqueue(request)
            }
        }
        doneKey.value = true
    }
}