package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SearchHistoryItem(
    val id: Long? = null,
    val query: String,
    val timestamp: Long
)
