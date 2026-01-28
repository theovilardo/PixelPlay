package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import android.os.Trace
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeStateHolder @Inject constructor(
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private var scope: CoroutineScope? = null

    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()

    private val _lavaLampColors = MutableStateFlow<ImmutableList<Color>>(persistentListOf())
    val lavaLampColors: StateFlow<ImmutableList<Color>> = _lavaLampColors.asStateFlow()

    private val playerThemePreference = userPreferencesRepository.playerThemePreferenceFlow

    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = combine(
        playerThemePreference, _currentAlbumArtColorSchemePair
    ) { playerPref, albumScheme ->
        when (playerPref) {
            com.theveloper.pixelplay.data.preferences.ThemePreference.ALBUM_ART -> albumScheme
            // DYNAMIC and DEFAULT fall back to null (system theme)
            else -> null
        }
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default), // Placeholder, will be replaced in initialize
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        // Re-scope the activePlayerColorSchemePair if possible, or just rely on the injected scope for processing
        
        scope.launch {
            activePlayerColorSchemePair.collect { schemePair ->
                 updateLavaLampColors(schemePair)
            }
        }
    }

    private var currentAlbumArtUri: String? = null

    suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, currentSongUriString: String?, isPreload: Boolean = false) {
        Trace.beginSection("ThemeStateHolder.extractAndGenerateColorScheme")
        try {
            if (albumArtUriAsUri == null) {
                if (!isPreload && currentSongUriString == null) {
                    _currentAlbumArtColorSchemePair.value = null
                    currentAlbumArtUri = null
                }
                return
            }

            val uriString = albumArtUriAsUri.toString()
            // Use the optimized ColorSchemeProcessor with LRU cache
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(uriString)

            if (!isPreload && currentSongUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                currentAlbumArtUri = uriString
            }
        } catch (e: Exception) {
            if (!isPreload && albumArtUriAsUri != null && currentSongUriString == albumArtUriAsUri.toString()) {
                _currentAlbumArtColorSchemePair.value = null
                currentAlbumArtUri = null
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun updateLavaLampColors(schemePair: ColorSchemePair?) {
        val schemeForLava = schemePair?.dark ?: DarkColorScheme
        _lavaLampColors.update {
            listOf(schemeForLava.primary, schemeForLava.secondary, schemeForLava.tertiary).distinct().toImmutableList()
        }
    }

    // LRU Cache for individual album schemes
    private val individualAlbumColorSchemes = object : LinkedHashMap<String, MutableStateFlow<ColorSchemePair?>>(
        32, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<ColorSchemePair?>>?): Boolean {
            return size > 30
        }
    }

    fun getAlbumColorSchemeFlow(uriString: String): StateFlow<ColorSchemePair?> {
        val existingFlow = individualAlbumColorSchemes[uriString]
        if (existingFlow != null) return existingFlow.asStateFlow()

        val newFlow = MutableStateFlow<ColorSchemePair?>(null)
        individualAlbumColorSchemes[uriString] = newFlow

        // Trigger generation asynchronously
        scope?.launch(Dispatchers.IO) {
            try {
                val scheme = colorSchemeProcessor.getOrGenerateColorScheme(uriString)
                newFlow.value = scheme
            } catch (e: Exception) {
                // Ignore or log
            }
        }

        return newFlow.asStateFlow()
    }
    
    suspend fun getOrGenerateColorScheme(uriString: String): ColorSchemePair? {
         return colorSchemeProcessor.getOrGenerateColorScheme(uriString)
    }

    suspend fun forceRegenerateColorScheme(uriString: String) {
         android.util.Log.d("ThemeStateHolder", "forceRegenerateColorScheme called for: $uriString")
         android.util.Log.d("ThemeStateHolder", "Current tracked global URI: $currentAlbumArtUri")
         
         colorSchemeProcessor.invalidateScheme(uriString)
         
         val newScheme = colorSchemeProcessor.getOrGenerateColorScheme(uriString, forceRefresh = true)

         // Iterate if there is an active flow for this URI and update it
         val activeFlow = individualAlbumColorSchemes[uriString]
         if (activeFlow != null) {
             activeFlow.value = newScheme
         }
         
         // Also update the main current album art scheme if it matches the one we are tracking
         // We use equality check. If they are the same string object or equal content.
         if (currentAlbumArtUri == uriString) {
             android.util.Log.d("ThemeStateHolder", "Updating global color scheme flow directly.")
             _currentAlbumArtColorSchemePair.value = newScheme
         } else {
             android.util.Log.d("ThemeStateHolder", "Global URI did not match. Skipping global update.")
         }
    }

}
