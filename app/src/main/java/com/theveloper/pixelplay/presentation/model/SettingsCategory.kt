package com.theveloper.pixelplay.presentation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.R

enum class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    LIBRARY(
        id = "library",
        title = "Music Management",
        subtitle = "Manage folders, refresh library, parsing options",
        icon = Icons.Rounded.LibraryMusic
    ),
    APPEARANCE(
        id = "appearance",
        title = "Appearance",
        subtitle = "Themes, layout, and visual styles",
        icon = Icons.Rounded.Palette
    ),
    PLAYBACK(
        id = "playback",
        title = "Playback",
        subtitle = "Audio behavior, crossfade, and background play",
        icon = Icons.Rounded.MusicNote // Using MusicNote again or maybe PlayCircle if available
    ),
    AI_INTEGRATION(
        id = "ai",
        title = "AI Integration (Beta)",
        subtitle = "Gemini API key and AI features",
        iconRes = R.drawable.gemini_ai
    ),
    DEVELOPER(
        id = "developer",
        title = "Developer Options",
        subtitle = "Experimental features and debugging",
        icon = Icons.Rounded.DeveloperMode
    ),
    EQUALIZER(
        id = "equalizer",
        title = "Equalizer",
        subtitle = "Adjust audio frequencies and presets",
        icon = Icons.Rounded.GraphicEq
    ),
    ABOUT(
        id = "about",
        title = "About",
        subtitle = "App info, version, and credits",
        icon = Icons.Rounded.Info
    );

    companion object {
        fun fromId(id: String): SettingsCategory? = entries.find { it.id == id }
    }
}
