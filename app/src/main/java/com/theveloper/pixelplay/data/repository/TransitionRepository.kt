package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.TransitionResolution
import com.theveloper.pixelplay.data.model.TransitionRule
import com.theveloper.pixelplay.data.model.TransitionSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing transition rules and settings.
 * This is the single source of truth for transition-related data.
 */
interface TransitionRepository {

    /**
     * Resolves the effective transition settings for a given playback context.
     * It checks for rules in the following priority order:
     * 1. A specific rule for the given fromTrackId and toTrackId in the playlist.
     * 2. The default rule for the given playlistId.
     * 3. The global default settings from user preferences.
     */
    fun resolveTransitionSettings(
        playlistId: String,
        fromTrackId: String,
        toTrackId: String
    ): Flow<TransitionResolution>

    /**
     * Gets all rules defined for a specific playlist.
     */
    fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRule>>

    /**
     * Gets the default rule for a playlist. Does not fall back to global settings.
     * Returns a flow that may emit null if no specific default rule exists for the playlist.
     */
    fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRule?>

    /**
     * Saves a transition rule. If a rule for the given context
     * (playlistId, fromTrackId, toTrackId) already exists, it will be updated.
     * Otherwise, a new rule will be created.
     */
    suspend fun saveRule(rule: TransitionRule)

    /**
     * Deletes a specific rule by its unique ID.
     */
    suspend fun deleteRule(ruleId: Long)

    /**
     * Deletes the default transition rule for a given playlist.
     */
    suspend fun deletePlaylistDefaultRule(playlistId: String)

    /**
     * Gets the flow of global transition settings.
     */
    fun getGlobalSettings(): Flow<TransitionSettings>

    /**
     * Saves the global transition settings.
     */
    suspend fun saveGlobalSettings(settings: TransitionSettings)
}
