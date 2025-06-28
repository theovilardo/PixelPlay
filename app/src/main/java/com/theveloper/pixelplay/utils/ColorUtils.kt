package com.theveloper.pixelplay.utils

import androidx.compose.ui.graphics.Color

/**
 * Converts a hex color string to a Compose Color object.
 *
 * @param hex The hex color string (e.g., "#FF0000" or "FF0000").
 * @param defaultColor The color to return if the hex string is null or invalid.
 * @return The Compose Color object.
 */
fun hexToColor(hex: String?, defaultColor: Color = Color.Gray): Color {
    if (hex == null) return defaultColor
    val colorString = if (hex.startsWith("#")) hex.substring(1) else hex
    return try {
        Color(android.graphics.Color.parseColor("#$colorString"))
    } catch (e: IllegalArgumentException) {
        defaultColor
    }
}