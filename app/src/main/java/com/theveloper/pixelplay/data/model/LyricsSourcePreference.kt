package com.theveloper.pixelplay.data.model

/**
 * Preference for lyrics source priority order.
 * Determines which source to try first when fetching lyrics.
 */
enum class LyricsSourcePreference(val displayName: String) {
    /**
     * Try online API first, then embedded lyrics, then local .lrc files
     */
    API_FIRST("Online First"),
    
    /**
     * Try embedded lyrics in metadata first, then API, then local .lrc files
     */
    EMBEDDED_FIRST("Embedded First"),
    
    /**
     * Try local .lrc files first, then embedded lyrics, then API
     */
    LOCAL_FIRST("Local First");
    
    companion object {
        fun fromOrdinal(ordinal: Int): LyricsSourcePreference {
            return values().getOrElse(ordinal) { EMBEDDED_FIRST }
        }
        
        fun fromName(name: String?): LyricsSourcePreference {
            return values().find { it.name == name } ?: EMBEDDED_FIRST
        }
    }
}
