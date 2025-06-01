package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Genre(
    val id: String,
    val name: String,
    val imageUrl: String? = null // Optional image URL for the genre category
)
