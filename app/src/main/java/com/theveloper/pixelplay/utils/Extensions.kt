package com.theveloper.pixelplay.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}