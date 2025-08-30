package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Playlist(
    val id: String,
    var name: String,
    var songIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    var lastModified: Long = System.currentTimeMillis(),
    val isAiGenerated: Boolean = false
)