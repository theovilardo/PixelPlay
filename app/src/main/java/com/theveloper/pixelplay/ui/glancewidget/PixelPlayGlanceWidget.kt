package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import androidx.datastore.preferences.protobuf.ByteString // No longer needed
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.PlayerInfo // Changed import
import com.theveloper.pixelplay.R

class PixelPlayGlanceWidget : GlanceAppWidget() {

    companion object {
        // Tamaños definidos para diferentes configuraciones del widget
        private val VERY_THIN_LAYOUT_SIZE = DpSize(width = 200.dp, height = 60.dp)
        private val THIN_LAYOUT_SIZE = DpSize(width = 250.dp, height = 80.dp)
        private val SMALL_LAYOUT_SIZE = DpSize(width = 150.dp, height = 150.dp)
        private val MEDIUM_LAYOUT_SIZE = DpSize(width = 250.dp, height = 150.dp)
        private val LARGE_LAYOUT_SIZE = DpSize(width = 300.dp, height = 180.dp)
        private val EXTRA_LARGE_LAYOUT_SIZE = DpSize(width = 300.dp, height = 220.dp) // Nuevo tamaño

        // LruCache for Bitmaps
        private object AlbumArtBitmapCache {
            private const val CACHE_SIZE_BYTES = 4 * 1024 * 1024 // 4 MiB
            private val lruCache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
                override fun sizeOf(key: String, value: Bitmap): Int {
                    return value.byteCount
                }
            }

            fun getBitmap(key: String): Bitmap? = lruCache.get(key)

            fun putBitmap(key: String, bitmap: Bitmap) {
                if (getBitmap(key) == null) { // Avoid re-putting if already cached by another thread/call
                    lruCache.put(key, bitmap)
                }
            }

            fun getKey(byteArray: ByteArray): String {
                // Using contentHashCode for simplicity; consider a more robust hash if collisions are a concern
                // or if a stable media ID is available and preferable.
                return byteArray.contentHashCode().toString()
            }
        }
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(VERY_THIN_LAYOUT_SIZE, THIN_LAYOUT_SIZE, SMALL_LAYOUT_SIZE, MEDIUM_LAYOUT_SIZE, LARGE_LAYOUT_SIZE, EXTRA_LARGE_LAYOUT_SIZE)
    )
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val playerInfo = currentState<PlayerInfo>() ?: PlayerInfo() // Changed to PlayerInfo
            val currentSize = LocalSize.current

            Log.d("PixelPlayGlanceWidget", "Providing Glance. PlayerInfo: title='${playerInfo.songTitle}', artist='${playerInfo.artistName}', isPlaying=${playerInfo.isPlaying}, hasBitmap=${playerInfo.albumArtBitmapData != null}, progress=${playerInfo.currentPositionMs}/${playerInfo.totalDurationMs}")

            GlanceTheme {
                WidgetUi(playerInfo = playerInfo, size = currentSize)
            }
        }
    }

    @Composable
    private fun WidgetUi(playerInfo: PlayerInfo, size: DpSize) { // Changed to PlayerInfo
        val title = playerInfo.songTitle.ifEmpty { "PixelPlay" }
        val artist = playerInfo.artistName.ifEmpty { "Toca para abrir" }
        val isPlaying = playerInfo.isPlaying
        // playerInfo.albumArtBitmapData is already ByteArray?
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val currentProgress = playerInfo.currentPositionMs
        val totalDuration = playerInfo.totalDurationMs

        val backgroundColor = GlanceTheme.colors.surfaceVariant
        val onBackgroundColor = GlanceTheme.colors.onSurfaceVariant
        val primaryColor = GlanceTheme.colors.primary

        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(28.dp)
            .clickable(actionStartActivity<MainActivity>())

        when {
            size.height < THIN_LAYOUT_SIZE.height && size.width >= THIN_LAYOUT_SIZE.width -> {
                VeryThinWidgetLayout(baseModifier.padding(horizontal = 12.dp, vertical = 8.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            size.height < SMALL_LAYOUT_SIZE.height && size.width >= MEDIUM_LAYOUT_SIZE.width -> {
                ThinWidgetLayout(baseModifier.padding(horizontal = 16.dp, vertical = 10.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            size.width < MEDIUM_LAYOUT_SIZE.width || size.height < MEDIUM_LAYOUT_SIZE.height -> {
                SmallWidgetLayout(baseModifier.padding(16.dp), title, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            size.width < LARGE_LAYOUT_SIZE.width || size.height < LARGE_LAYOUT_SIZE.height -> {
                MediumWidgetLayout(baseModifier.padding(12.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            size.width < EXTRA_LARGE_LAYOUT_SIZE.width || size.height < EXTRA_LARGE_LAYOUT_SIZE.height -> { // Condición para el layout grande
                LargeWidgetLayout(baseModifier.padding(16.dp), title, artist, albumArtBitmapData, isPlaying, currentProgress, totalDuration, onBackgroundColor, primaryColor)
            }
            else -> { // Layout extra grande
                ExtraLargeWidgetLayout(baseModifier.padding(16.dp), title, artist, albumArtBitmapData, isPlaying, currentProgress, totalDuration, onBackgroundColor, primaryColor)
            }
        }
    }

    @Composable
    fun VeryThinWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 36.dp, cornerRadius = 18.dp) // Circular
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != "Toca para abrir") {
                    Text(text = artist, style = TextStyle(fontSize = 10.sp, color = textColor), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(6.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 30.dp, iconColor = textColor)
            NextButtonGlance(size = 30.dp, iconColor = textColor)
        }
    }


    @Composable
    fun ThinWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 48.dp, cornerRadius = 24.dp) // Circular
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != "Toca para abrir") {
                    Text(text = artist, style = TextStyle(fontSize = 12.sp, color = textColor), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 36.dp, iconColor = textColor)
            NextButtonGlance(size = 36.dp, iconColor = textColor)
        }
    }

    @Composable
    fun SmallWidgetLayout(modifier: GlanceModifier, title: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 60.dp, cornerRadius = 30.dp, modifier = GlanceModifier.padding(bottom = 8.dp)) // Circular
            Text(text = title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 40.dp, iconColor = textColor)
        }
    }

    @Composable
    fun MediumWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 64.dp, cornerRadius = 16.dp) // Rectángulo redondeado
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                Text(text = artist, style = TextStyle(fontSize = 13.sp, color = textColor), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            PreviousButtonGlance(size = 32.dp, iconColor = textColor)
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 38.dp, iconColor = textColor)
            NextButtonGlance(size = 32.dp, iconColor = textColor)
        }
    }

    @Composable
    fun LargeWidgetLayout(
        modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?,
        isPlaying: Boolean, currentProgressMs: Long, totalDurationMs: Long,
        textColor: ColorProvider, accentColor: ColorProvider
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 64.dp, cornerRadius = 18.dp)
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                    Text(text = artist, style = TextStyle(fontSize = 13.sp, color = textColor), maxLines = 1)
                }
                Image(
                    provider = ImageProvider(R.drawable.rounded_favorite_24),
                    contentDescription = "Abrir en app",
                    modifier = GlanceModifier.size(28.dp).clickable(actionStartActivity<MainActivity>()).padding(2.dp),
                    colorFilter = ColorFilter.tint(textColor)
                )
            }
            if (totalDurationMs > 0L) {
                LinearProgressIndicator(
                    progress = currentProgressMs.toFloat() / totalDurationMs.toFloat(),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                    color = accentColor,
                    backgroundColor = textColor
                )
                Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(formatDurationGlance(currentProgressMs), style = TextStyle(fontSize = 10.sp, color = textColor))
                    Text(formatDurationGlance(totalDurationMs), style = TextStyle(fontSize = 10.sp, color = textColor))
                }
            } else {
                Spacer(GlanceModifier.height(4.dp + 10.sp.value.dp)) // Mantener espacio similar
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviousButtonGlance(size = 36.dp, iconColor = textColor)
                PlayPauseButtonGlance(isPlaying = isPlaying, size = 44.dp, iconColor = textColor)
                NextButtonGlance(size = 36.dp, iconColor = textColor)
            }
        }
    }

    @Composable
    fun ExtraLargeWidgetLayout( // Nuevo layout para el tamaño más grande
        modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?,
        isPlaying: Boolean, currentProgressMs: Long, totalDurationMs: Long,
        textColor: ColorProvider, accentColor: ColorProvider
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            // Fila superior: Carátula e Info
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 80.dp, cornerRadius = 22.dp)
                Spacer(GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = title, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 2) // Permitir 2 líneas
                    Text(text = artist, style = TextStyle(fontSize = 14.sp, color = textColor), maxLines = 1)
                }
            }

            // Fila media: Barra de progreso y tiempos
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 8.dp)) {
                if (totalDurationMs > 0L) {
                    LinearProgressIndicator(
                        progress = currentProgressMs.toFloat() / totalDurationMs.toFloat(),
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp), // Barra más gruesa
                        color = accentColor,
                        backgroundColor = textColor
                    )
                    Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(formatDurationGlance(currentProgressMs), style = TextStyle(fontSize = 12.sp, color = textColor))
                        Text(formatDurationGlance(totalDurationMs), style = TextStyle(fontSize = 12.sp, color = textColor))
                    }
                } else {
                    Spacer(GlanceModifier.height(6.dp + 12.sp.value.dp)) // Mantener espacio
                }
            }

            // Fila inferior: Controles
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Podríamos añadir Shuffle y Repeat aquí si el espacio lo permite
                // Por ahora, mantenemos los controles básicos más grandes
                PreviousButtonGlance(size = 40.dp, iconColor = textColor)
                PlayPauseButtonGlance(isPlaying = isPlaying, size = 52.dp, iconColor = textColor) // Botón de play más grande
                NextButtonGlance(size = 40.dp, iconColor = textColor)
            }
        }
    }

    @Composable
    fun AlbumArtImageGlance(bitmapData: ByteArray?, size: Dp, modifier: GlanceModifier = GlanceModifier, cornerRadius: Dp = 16.dp) {
        val imageProvider = bitmapData?.let { data ->
            val cacheKey = AlbumArtBitmapCache.getKey(data)
            var bitmap = AlbumArtBitmapCache.getBitmap(cacheKey)

            if (bitmap == null) {
                try {
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    bitmap?.let { bmp ->
                        AlbumArtBitmapCache.putBitmap(cacheKey, bmp)
                    }
                } catch (e: Exception) {
                    Log.e("PixelPlayGlanceWidget", "Error decoding bitmap for cache key: $cacheKey", e)
                    // bitmap remains null
                }
            }
            bitmap?.let { ImageProvider(it) }
        } ?: ImageProvider(R.drawable.rounded_album_24)

        Image(
            provider = imageProvider,
            contentDescription = "Album Art",
            modifier = modifier.size(size).background(GlanceTheme.colors.surface).cornerRadius(cornerRadius),
            contentScale = ContentScale.Crop
        )
    }

    @Composable
    fun PlayPauseButtonGlance(isPlaying: Boolean, size: Dp = 36.dp, iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant) {
        val params = actionParametersOf(PlayerActions.key to PlayerActions.PLAY_PAUSE)
        Box(
            modifier = GlanceModifier.size(size).clickable(actionRunCallback<PlayerControlActionCallback>(params)).padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = GlanceModifier.size(size - 8.dp),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }

    @Composable
    fun NextButtonGlance(size: Dp = 36.dp, iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant) {
        val params = actionParametersOf(PlayerActions.key to PlayerActions.NEXT)
        Box(
            modifier = GlanceModifier.size(size).clickable(actionRunCallback<PlayerControlActionCallback>(params)).padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = ImageProvider(R.drawable.rounded_skip_next_24), contentDescription = "Next", modifier = GlanceModifier.size(size - 8.dp), colorFilter = ColorFilter.tint(iconColor))
        }
    }

    @Composable
    fun PreviousButtonGlance(size: Dp = 36.dp, iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant) {
        val params = actionParametersOf(PlayerActions.key to PlayerActions.PREVIOUS)
        Box(
            modifier = GlanceModifier.size(size).clickable(actionRunCallback<PlayerControlActionCallback>(params)).padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = ImageProvider(R.drawable.rounded_skip_previous_24), contentDescription = "Previous", modifier = GlanceModifier.size(size - 8.dp), colorFilter = ColorFilter.tint(iconColor))
        }
    }
}

// Helper para formatear duración en Glance (no puede usar TimeUnit directamente)
private fun formatDurationGlance(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}