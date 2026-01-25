package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Trace
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.AlbumArtThemeEntity
import com.theveloper.pixelplay.data.database.StoredColorSchemeValues
import com.theveloper.pixelplay.data.database.toComposeColor
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.extractSeedColor
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Efficient color scheme processor for album art.
 * 
 * Optimizations:
 * - In-memory LRU cache to avoid disk reads for recently accessed schemes
 * - Mutex-protected processing to avoid duplicate work
 * - Batched bitmap operations on IO dispatcher
 * - Reduced bitmap size (128x128) for faster processing
 * 
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class ColorSchemeProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumArtThemeDao: AlbumArtThemeDao
) {
    // In-memory LRU cache for faster access (avoids DB reads for hot paths)
    private val memoryCache = LruCache<String, ColorSchemePair>(20)
    private val processingMutex = Mutex()
    private val inProgressUris = mutableSetOf<String>()

    /**
     * Channel for queuing color scheme requests.
     * Used by PlayerViewModel for background processing.
     */
    val requestChannel = Channel<String>(Channel.UNLIMITED)

    /**
     * Gets or generates a color scheme for the given album art URI.
     * Checks memory cache first, then database, then generates new.
     * All heavy operations are performed on appropriate dispatchers.
     */
    /**
     * Gets or generates a color scheme for the given album art URI.
     * Checks memory cache first, then database, then generates new.
     * @param forceRefresh If true, bypasses caches and forces regeneration from source image.
     */
    suspend fun getOrGenerateColorScheme(albumArtUri: String, forceRefresh: Boolean = false): ColorSchemePair? {
        Trace.beginSection("ColorSchemeProcessor.getOrGenerate")
        try {
            if (!forceRefresh) {
                // 1. Check memory cache first (fastest)
                memoryCache.get(albumArtUri)?.let {
                    Trace.endSection()
                    return it
                }

                // 2. Check database cache
                val cachedEntity = withContext(Dispatchers.IO) {
                    albumArtThemeDao.getThemeByUri(albumArtUri)
                }
                if (cachedEntity != null) {
                    val schemePair = mapEntityToColorSchemePair(cachedEntity)
                    memoryCache.put(albumArtUri, schemePair)
                    Trace.endSection()
                    return schemePair
                }
            }

            // 3. Generate new color scheme
            return generateAndCacheColorScheme(albumArtUri, forceRefresh)
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Generates a color scheme from the album art bitmap.
     * All processing done on Default dispatcher for CPU-bound work.
     */
    private suspend fun generateAndCacheColorScheme(albumArtUri: String, forceRefresh: Boolean = false): ColorSchemePair? {
        Trace.beginSection("ColorSchemeProcessor.generate")
        try {
            // Load bitmap on IO dispatcher
            val bitmap = withContext(Dispatchers.IO) {
                loadBitmapForColorExtraction(albumArtUri, forceRefresh)
            } ?: return null

            // Extract colors on Default dispatcher (CPU-bound)
            val schemePair = withContext(Dispatchers.Default) {
                val seed = extractSeedColor(bitmap)
                generateColorSchemeFromSeed(seed)
            }

            // Cache to memory
            memoryCache.put(albumArtUri, schemePair)

            // Persist to database (fire and forget on IO)
            withContext(Dispatchers.IO) {
                albumArtThemeDao.insertTheme(
                    mapColorSchemePairToEntity(albumArtUri, schemePair)
                )
            }

            return schemePair
        } catch (e: Exception) {
            return null
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Loads a small bitmap optimized for color extraction.
     */
    private suspend fun loadBitmapForColorExtraction(uri: String, skipCache: Boolean): Bitmap? {
        return try {
            val cachePolicy = if (skipCache) CachePolicy.DISABLED else CachePolicy.ENABLED
            
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false) // Required for pixel access
                .size(Size(128, 128)) // Small size for fast processing
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .memoryCachePolicy(cachePolicy)
                .diskCachePolicy(cachePolicy)
                .build()
            
            val drawable = context.imageLoader.execute(request).drawable ?: return null
            
            createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1)
            ).also { bmp ->
                Canvas(bmp).let { canvas ->
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a URI is currently being processed.
     * Used to avoid duplicate work.
     */
    suspend fun markProcessing(uri: String): Boolean {
        return processingMutex.withLock {
            if (inProgressUris.contains(uri)) {
                false
            } else {
                inProgressUris.add(uri)
                true
            }
        }
    }

    /**
     * Marks a URI as finished processing.
     */
    suspend fun markComplete(uri: String) {
        processingMutex.withLock {
            inProgressUris.remove(uri)
        }
    }

    /**
     * Clears the in-memory cache.
     * Call when memory is low or on configuration changes.
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    /**
     * Removes a specific URI from the cache.
     */
    fun evictFromCache(uri: String) {
        memoryCache.remove(uri)
    }

    /**
     * Invalidates the color scheme for a URI in both memory and database.
     */
    suspend fun invalidateScheme(uri: String) {
        memoryCache.remove(uri)
        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(listOf(uri))
        }
    }

    // Mapping functions
    private fun mapColorSchemePairToEntity(uri: String, pair: ColorSchemePair): AlbumArtThemeEntity {
        fun mapScheme(cs: ColorScheme) = StoredColorSchemeValues(
            primary = cs.primary.toHexString(),
            onPrimary = cs.onPrimary.toHexString(),
            primaryContainer = cs.primaryContainer.toHexString(),
            onPrimaryContainer = cs.onPrimaryContainer.toHexString(),
            secondary = cs.secondary.toHexString(),
            onSecondary = cs.onSecondary.toHexString(),
            secondaryContainer = cs.secondaryContainer.toHexString(),
            onSecondaryContainer = cs.onSecondaryContainer.toHexString(),
            tertiary = cs.tertiary.toHexString(),
            onTertiary = cs.onTertiary.toHexString(),
            tertiaryContainer = cs.tertiaryContainer.toHexString(),
            onTertiaryContainer = cs.onTertiaryContainer.toHexString(),
            background = cs.background.toHexString(),
            onBackground = cs.onBackground.toHexString(),
            surface = cs.surface.toHexString(),
            onSurface = cs.onSurface.toHexString(),
            surfaceVariant = cs.surfaceVariant.toHexString(),
            onSurfaceVariant = cs.onSurfaceVariant.toHexString(),
            error = cs.error.toHexString(),
            onError = cs.onError.toHexString(),
            outline = cs.outline.toHexString(),
            errorContainer = cs.errorContainer.toHexString(),
            onErrorContainer = cs.onErrorContainer.toHexString(),
            inversePrimary = cs.inversePrimary.toHexString(),
            inverseSurface = cs.inverseSurface.toHexString(),
            inverseOnSurface = cs.inverseOnSurface.toHexString(),
            surfaceTint = cs.surfaceTint.toHexString(),
            outlineVariant = cs.outlineVariant.toHexString(),
            scrim = cs.scrim.toHexString()
        )
        return AlbumArtThemeEntity(uri, mapScheme(pair.light), mapScheme(pair.dark))
    }

    private fun mapEntityToColorSchemePair(entity: AlbumArtThemeEntity): ColorSchemePair {
        val placeholder = Color.Magenta
        fun mapStored(sv: StoredColorSchemeValues, isDark: Boolean) = ColorScheme(
            primary = sv.primary.toComposeColor(),
            onPrimary = sv.onPrimary.toComposeColor(),
            primaryContainer = sv.primaryContainer.toComposeColor(),
            onPrimaryContainer = sv.onPrimaryContainer.toComposeColor(),
            secondary = sv.secondary.toComposeColor(),
            onSecondary = sv.onSecondary.toComposeColor(),
            secondaryContainer = sv.secondaryContainer.toComposeColor(),
            onSecondaryContainer = sv.onSecondaryContainer.toComposeColor(),
            tertiary = sv.tertiary.toComposeColor(),
            onTertiary = sv.onTertiary.toComposeColor(),
            tertiaryContainer = sv.tertiaryContainer.toComposeColor(),
            onTertiaryContainer = sv.onTertiaryContainer.toComposeColor(),
            background = sv.background.toComposeColor(),
            onBackground = sv.onBackground.toComposeColor(),
            surface = sv.surface.toComposeColor(),
            onSurface = sv.onSurface.toComposeColor(),
            surfaceVariant = sv.surfaceVariant.toComposeColor(),
            onSurfaceVariant = sv.onSurfaceVariant.toComposeColor(),
            error = sv.error.toComposeColor(),
            onError = sv.onError.toComposeColor(),
            outline = sv.outline.toComposeColor(),
            errorContainer = sv.errorContainer.toComposeColor(),
            onErrorContainer = sv.onErrorContainer.toComposeColor(),
            inversePrimary = sv.inversePrimary.toComposeColor(),
            inverseSurface = sv.inverseSurface.toComposeColor(),
            inverseOnSurface = sv.inverseOnSurface.toComposeColor(),
            surfaceTint = sv.surfaceTint.toComposeColor(),
            outlineVariant = sv.outlineVariant.toComposeColor(),
            scrim = sv.scrim.toComposeColor(),
            surfaceBright = placeholder,
            surfaceDim = placeholder,
            surfaceContainer = placeholder,
            surfaceContainerHigh = placeholder,
            surfaceContainerHighest = placeholder,
            surfaceContainerLow = placeholder,
            surfaceContainerLowest = placeholder,
            primaryFixed = placeholder,
            primaryFixedDim = placeholder,
            onPrimaryFixed = placeholder,
            onPrimaryFixedVariant = placeholder,
            secondaryFixed = placeholder,
            secondaryFixedDim = placeholder,
            onSecondaryFixed = placeholder,
            onSecondaryFixedVariant = placeholder,
            tertiaryFixed = placeholder,
            tertiaryFixedDim = placeholder,
            onTertiaryFixed = placeholder,
            onTertiaryFixedVariant = placeholder
        )
        return ColorSchemePair(
            light = mapStored(entity.lightThemeValues, false),
            dark = mapStored(entity.darkThemeValues, true)
        )
    }

    private fun Color.toHexString(): String {
        return String.format("#%08X", toArgb())
    }

    companion object {
        private const val TAG = "ColorSchemeProcessor"
    }
}
