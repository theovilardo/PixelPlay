package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Genre(
    val id: String,
    val name: String,
    val iconResId: Int? = null, // Optional: For a Material symbol or drawable
    val colorHex: String? = null // Optional: For custom genre color
)
