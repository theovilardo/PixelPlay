package com.theveloper.pixelplay.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// import android.util.Log // Uncomment if logging is needed

/**
 * Singleton object to hold and share the state of the "End of Track" (EOT) timer,
 * specifically which song ID is targeted by an active EOT.
 * This allows communication between PlayerViewModel and MusicService regarding EOT state.
 */
object EotStateHolder {
    private val _eotTargetSongId = MutableStateFlow<String?>(null)
    val eotTargetSongId: StateFlow<String?> = _eotTargetSongId.asStateFlow()

    /**
     * Sets the song ID for which the "End of Track" timer is active.
     * Call with null to indicate EOT is not active or has been cleared.
     *
     * @param songId The ID of the song targeted by EOT, or null if EOT is inactive.
     */
    fun setEotTargetSong(songId: String?) {
        _eotTargetSongId.value = songId
        // Log.d("EotStateHolder", "EOT Target Song ID set to: $songId") // Optional: for debugging
    }
}
