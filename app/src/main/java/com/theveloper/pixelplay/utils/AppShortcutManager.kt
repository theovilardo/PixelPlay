package com.theveloper.pixelplay.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dynamic app shortcuts for the launcher.
 */
@Singleton
class AppShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SHORTCUT_ID_LAST_PLAYLIST = "last_playlist"
    }

    /**
     * Updates the dynamic shortcut for the last played playlist.
     * @param playlistId The ID of the playlist
     * @param playlistName The display name of the playlist
     */
    fun updateLastPlaylistShortcut(playlistId: String, playlistName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return // Shortcuts not supported before API 25
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYLIST
            putExtra(MainActivity.EXTRA_PLAYLIST_ID, playlistId)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_LAST_PLAYLIST)
            .setShortLabel(playlistName)
            .setLongLabel(playlistName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_playlist_purple))
            .setIntent(intent)
            .build()

        // Remove old shortcut first to force icon refresh
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_LAST_PLAYLIST))
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Removes the last playlist shortcut if it exists.
     */
    fun removeLastPlaylistShortcut() {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_LAST_PLAYLIST))
    }
}
