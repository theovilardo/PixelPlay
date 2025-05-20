package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.protobuf.ByteString
import androidx.glance.action.actionParametersOf
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.colorProviders
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.PlayerInfoProto
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.LightColorScheme

class PixelPlayGlanceWidget : GlanceAppWidget() {

    companion object {
        // Tamaños definidos para diferentes configuraciones del widget
        private val THIN_LAYOUT_SIZE = DpSize(width = 220.dp, height = 70.dp)  // Aprox 4x1
        private val SMALL_LAYOUT_SIZE = DpSize(width = 120.dp, height = 120.dp) // Aprox 2x2
        private val MEDIUM_LAYOUT_SIZE = DpSize(width = 220.dp, height = 120.dp)// Aprox 3x2 / 4x1.5
        private val LARGE_LAYOUT_SIZE = DpSize(width = 280.dp, height = 180.dp) // Aprox 4x2 / 5x2
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(THIN_LAYOUT_SIZE, SMALL_LAYOUT_SIZE, MEDIUM_LAYOUT_SIZE, LARGE_LAYOUT_SIZE)
    )

//    companion object {
//        private val THIN_RECTANGLE_SIZE = DpSize(width = 220.dp, height = 70.dp) // Ajustado para 4x1
//        private val SMALL_SQUARE_SIZE = DpSize(width = 120.dp, height = 120.dp)
//        private val MEDIUM_RECTANGLE_SIZE = DpSize(width = 200.dp, height = 120.dp)
//        private val LARGE_RECTANGLE_SIZE = DpSize(width = 280.dp, height = 120.dp)
//    }
//
//    override val sizeMode = SizeMode.Responsive(
//        setOf(THIN_RECTANGLE_SIZE, SMALL_SQUARE_SIZE, MEDIUM_RECTANGLE_SIZE, LARGE_RECTANGLE_SIZE)
//    )

    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val playerInfo = currentState<PlayerInfoProto>() ?: PlayerInfoProto.getDefaultInstance()
            val currentSize = LocalSize.current

            Log.d("PixelPlayGlanceWidget", "Providing Glance. PlayerInfo: title='${playerInfo.songTitle}', artist='${playerInfo.artistName}', isPlaying=${playerInfo.isPlaying}, hasBitmap=${playerInfo.albumArtBitmapData != ByteString.EMPTY}, progress=${playerInfo.currentPositionMs}/${playerInfo.totalDurationMs}")

            GlanceTheme { // Intenta usar Material You del sistema
                WidgetUi(playerInfo = playerInfo, size = currentSize)
            }
        }
    }

//    override suspend fun provideGlance(context: Context, id: GlanceId) {
//        provideContent {
//            val playerInfo = currentState<PlayerInfoProto>() ?: PlayerInfoProto.getDefaultInstance()
//            val currentSize = LocalSize.current
//
//            Log.d("PixelPlayGlanceWidget", "Providing Glance. PlayerInfo: title='${playerInfo.songTitle}', artist='${playerInfo.artistName}', isPlaying=${playerInfo.isPlaying}, hasBitmap=${playerInfo.albumArtBitmapData != ByteString.EMPTY}")
//
//            // GlanceTheme sin ColorProviders explícitos para usar Material You del sistema
//            GlanceTheme {
//                WidgetUi(playerInfo = playerInfo, size = currentSize)
//            }
//        }
//    }

    @Composable
    private fun WidgetUi(playerInfo: PlayerInfoProto, size: DpSize) {
        val title = playerInfo.songTitle.ifEmpty { "PixelPlay" }
        val artist = playerInfo.artistName.ifEmpty { "Toca para abrir" }
        val isPlaying = playerInfo.isPlaying
        val albumArtBitmapData = if (playerInfo.albumArtBitmapData != ByteString.EMPTY) {
            playerInfo.albumArtBitmapData.toByteArray()
        } else {
            null
        }
        val currentProgress = playerInfo.currentPositionMs
        val totalDuration = playerInfo.totalDurationMs

        val backgroundColor = GlanceTheme.colors.surfaceVariant
        val onBackgroundColor = GlanceTheme.colors.onSurfaceVariant
        val primaryColor = GlanceTheme.colors.primary // Para la barra de progreso y acentos

        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(28.dp)
            .clickable(actionStartActivity<MainActivity>())


        // Lógica de selección de layout basada en el tamaño
        when {
            size.height < SMALL_LAYOUT_SIZE.height && size.width >= THIN_LAYOUT_SIZE.width -> {
                ThinWidgetLayout(baseModifier.padding(horizontal = 12.dp, vertical = 8.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            size.width < MEDIUM_LAYOUT_SIZE.width || size.height < MEDIUM_LAYOUT_SIZE.height -> {
                SmallWidgetLayout(baseModifier.padding(16.dp), title, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            size.width < LARGE_LAYOUT_SIZE.width || size.height < LARGE_LAYOUT_SIZE.height -> {
                MediumWidgetLayout(baseModifier.padding(12.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
            }
            else -> {
                LargeWidgetLayout(baseModifier.padding(12.dp), title, artist, albumArtBitmapData, isPlaying, currentProgress, totalDuration, onBackgroundColor, primaryColor)
            }
        }
    }

//    @Composable
//    private fun WidgetUi(playerInfo: PlayerInfoProto, size: DpSize) {
//        val title = playerInfo.songTitle.ifEmpty { "PixelPlay" }
//        val artist = playerInfo.artistName.ifEmpty { "Toca para abrir" }
//        val isPlaying = playerInfo.isPlaying
//        val albumArtBitmapData = if (playerInfo.albumArtBitmapData != ByteString.EMPTY) {
//            playerInfo.albumArtBitmapData.toByteArray()
//        } else {
//            null
//        }
//
//        val backgroundColor = GlanceTheme.colors.surfaceVariant
//        val onBackgroundColor = GlanceTheme.colors.onSurfaceVariant
//
//        val baseModifier = GlanceModifier
//            .fillMaxSize()
//            .background(backgroundColor)
//            .cornerRadius(28.dp) // Esquinas redondeadas expresivas
//            .clickable(actionStartActivity<MainActivity>())
//
//
//        when {
//            // Evaluar primero el tamaño más restrictivo (THIN_RECTANGLE_SIZE)
//            // Una forma común es verificar si el widget es más ancho que alto y su altura es pequeña.
//            size.height < SMALL_SQUARE_SIZE.height && size.width >= THIN_RECTANGLE_SIZE.width -> {
//                Log.d("WidgetUi", "Using ThinWidgetLayout: ${size.width}x${size.height}")
//                ThinWidgetLayout(modifier = baseModifier.padding(horizontal = 12.dp, vertical = 8.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
//            }
//            size.width < MEDIUM_RECTANGLE_SIZE.width || size.height < MEDIUM_RECTANGLE_SIZE.height -> {
//                Log.d("WidgetUi", "Using SmallWidgetLayout: ${size.width}x${size.height}")
//                SmallWidgetLayout(modifier = baseModifier.padding(16.dp), title, albumArtBitmapData, isPlaying, onBackgroundColor)
//            }
//            size.width < LARGE_RECTANGLE_SIZE.width || size.height < LARGE_RECTANGLE_SIZE.height -> {
//                Log.d("WidgetUi", "Using MediumWidgetLayout: ${size.width}x${size.height}")
//                MediumWidgetLayout(modifier = baseModifier.padding(12.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
//            }
//            else -> {
//                Log.d("WidgetUi", "Using LargeWidgetLayout: ${size.width}x${size.height}")
//                LargeWidgetLayout(modifier = baseModifier.padding(12.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor)
//            }
//        }
//    }

    @Composable
    fun ThinWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 40.dp, cornerRadius = 12.dp)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != "Toca para abrir") {
                    Text(text = artist, style = TextStyle(fontSize = 11.sp, color = textColor), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(4.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 32.dp, iconColor = textColor)
            NextButtonGlance(size = 32.dp, iconColor = textColor)
        }
    }

//    @Composable
//    fun ThinWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
//        Row(
//            modifier = modifier,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 40.dp)
//            Spacer(GlanceModifier.width(8.dp))
//            Column(modifier = GlanceModifier.defaultWeight()) {
//                Text(text = title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
//                if (artist.isNotEmpty() && artist != "Toca para abrir") {
//                    Text(text = artist, style = TextStyle(fontSize = 11.sp, color = textColor), maxLines = 1)
//                }
//            }
//            // Spacer(GlanceModifier.width(4.dp)) // Menos espacio si es necesario
//            PreviousButtonGlance(size = 28.dp, iconColor = textColor)
//            PlayPauseButtonGlance(isPlaying = isPlaying, size = 30.dp, iconColor = textColor)
//            NextButtonGlance(size = 28.dp, iconColor = textColor)
//        }
//    }

    @Composable
    fun SmallWidgetLayout(modifier: GlanceModifier, title: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 64.dp, modifier = GlanceModifier.padding(bottom = 8.dp), cornerRadius = 16.dp)
            Text(text = title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 40.dp, iconColor = textColor)
        }
    }

//    @Composable
//    fun SmallWidgetLayout(modifier: GlanceModifier, title: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
//        Column(
//            modifier = modifier,
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalAlignment = Alignment.CenterHorizontally,
//        ) {
//            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 56.dp, modifier = GlanceModifier.padding(bottom = 6.dp))
//            Text(
//                text = title,
//                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor),
//                maxLines = 1
//            )
//            Spacer(GlanceModifier.height(4.dp))
//            PlayPauseButtonGlance(isPlaying = isPlaying, size = 36.dp, iconColor = textColor)
//        }
//    }

    @Composable
    fun MediumWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 72.dp, cornerRadius = 20.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                Text(text = artist, style = TextStyle(fontSize = 13.sp, color = textColor), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            PreviousButtonGlance(size = 30.dp, iconColor = textColor)
            PlayPauseButtonGlance(isPlaying = isPlaying, size = 36.dp, iconColor = textColor)
            NextButtonGlance(size = 30.dp, iconColor = textColor)
        }
    }

//    @Composable
//    fun MediumWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
//        Row(
//            modifier = modifier,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 60.dp)
//            Spacer(GlanceModifier.width(10.dp))
//            Column(modifier = GlanceModifier.defaultWeight()) {
//                Text(text = title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
//                Text(text = artist, style = TextStyle(fontSize = 12.sp, color = textColor), maxLines = 1)
//            }
//            Spacer(GlanceModifier.width(6.dp))
//            PlayPauseButtonGlance(isPlaying = isPlaying, size = 32.dp, iconColor = textColor)
//            NextButtonGlance(size = 32.dp, iconColor = textColor)
//        }
//    }

    @Composable
    fun LargeWidgetLayout(
        modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?,
        isPlaying: Boolean, currentProgressMs: Long, totalDurationMs: Long,
        textColor: ColorProvider, progressColor: ColorProvider
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 80.dp, cornerRadius = 22.dp)
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = title, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                    Text(text = artist, style = TextStyle(fontSize = 14.sp, color = textColor), maxLines = 1)
                }
                // Botón de "Like/Favorite" - Abre la app
                Image(
                    provider = ImageProvider(R.drawable.rounded_favorite_24), // Necesitas ic_widget_favorite y ic_widget_favorite_border
                    contentDescription = "Abrir en app / Favorito",
                    modifier = GlanceModifier.size(30.dp).clickable(actionStartActivity<MainActivity>()).padding(4.dp),
                    colorFilter = ColorFilter.tint(textColor)
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            // Barra de Progreso
            if (totalDurationMs > 0) {
                LinearProgressIndicator(
                    progress = currentProgressMs.toFloat() / totalDurationMs.toFloat(),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                    color = progressColor,
                    backgroundColor = textColor
                )
            } else {
                Spacer(GlanceModifier.height(4.dp)) // Mantener el espacio si no hay duración
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                //horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviousButtonGlance(size = 36.dp, iconColor = textColor)
                PlayPauseButtonGlance(isPlaying = isPlaying, size = 48.dp, iconColor = textColor)
                NextButtonGlance(size = 36.dp, iconColor = textColor)
            }
        }
    }

//    @Composable
//    fun LargeWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider) {
//        Row(
//            modifier = modifier,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 72.dp)
//            Spacer(GlanceModifier.width(12.dp))
//            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
//                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
//                Text(text = artist, style = TextStyle(fontSize = 13.sp, color = textColor), maxLines = 1)
//            }
//            Spacer(GlanceModifier.width(10.dp))
//            Row(verticalAlignment = Alignment.CenterVertically) {
//                PreviousButtonGlance(size = 30.dp, iconColor = textColor)
//                Spacer(GlanceModifier.width(4.dp))
//                PlayPauseButtonGlance(isPlaying = isPlaying, size = 40.dp, iconColor = textColor)
//                Spacer(GlanceModifier.width(4.dp))
//                NextButtonGlance(size = 30.dp, iconColor = textColor)
//            }
//        }
//    }

    @Composable
    fun AlbumArtImageGlance(bitmapData: ByteArray?, size: Dp, modifier: GlanceModifier = GlanceModifier, cornerRadius: Dp = 16.dp) {
        val imageProvider = bitmapData?.let {
            try {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.let { bmp -> ImageProvider(bmp) }
            } catch (e: Exception) {
                Log.e("AlbumArtImageGlance", "Error decoding bitmap: ${e.message}")
                null
            }
        } ?: ImageProvider(R.drawable.rounded_album_24)

        Image(
            provider = imageProvider,
            contentDescription = "Album Art",
            modifier = modifier.size(size).background(GlanceTheme.colors.surface).cornerRadius(cornerRadius),
            contentScale = ContentScale.Crop
        )
    }

//    @Composable
//    fun AlbumArtImageGlance(bitmapData: ByteArray?, size: Dp, modifier: GlanceModifier = GlanceModifier) {
//        val imageProvider = bitmapData?.let {
//            try {
//                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
//                if (bitmap != null) {
//                    ImageProvider(bitmap)
//                } else {
//                    Log.w("AlbumArtImageGlance", "BitmapFactory.decodeByteArray returned null for widget album art.")
//                    ImageProvider(R.drawable.rounded_album_24) // Asegúrate que este drawable exista
//                }
//            } catch (e: Exception) {
//                Log.e("AlbumArtImageGlance", "Error decoding bitmap byte array for widget: ${e.message}")
//                ImageProvider(R.drawable.rounded_album_24)
//            }
//        } ?: ImageProvider(R.drawable.rounded_album_24)
//
//        Image(
//            provider = imageProvider,
//            contentDescription = "Album Art",
//            modifier = modifier.size(size).background(GlanceTheme.colors.surface).cornerRadius(16.dp),
//            contentScale = ContentScale.Crop
//        )
//    }

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

//    @Composable
//    fun PlayPauseButtonGlance(isPlaying: Boolean, size: Dp = 36.dp, iconColor: ColorProvider = GlanceTheme.colors.onSurface) {
//        val params = actionParametersOf(PlayerActions.key to PlayerActions.PLAY_PAUSE)
//        Box(
//            modifier = GlanceModifier.size(size)
//                .clickable(actionRunCallback<PlayerControlActionCallback>(params))
//                .padding(4.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Image(
//                provider = ImageProvider(if (isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24),
//                contentDescription = if (isPlaying) "Pause" else "Play",
//                modifier = GlanceModifier.size(size - 8.dp),
//                colorFilter = ColorFilter.tint(iconColor)
//            )
//        }
//    }

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

//    @Composable
//    fun NextButtonGlance(size: Dp = 36.dp, iconColor: ColorProvider = GlanceTheme.colors.onSurface) {
//        val params = actionParametersOf(PlayerActions.key to PlayerActions.NEXT)
//        Box(
//            modifier = GlanceModifier.size(size)
//                .clickable(actionRunCallback<PlayerControlActionCallback>(params))
//                .padding(4.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Image(
//                provider = ImageProvider(R.drawable.rounded_skip_next_24),
//                contentDescription = "Next",
//                modifier = GlanceModifier.size(size - 8.dp),
//                colorFilter = ColorFilter.tint(iconColor)
//            )
//        }
//    }

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

//    @Composable
//    fun PreviousButtonGlance(size: Dp = 36.dp, iconColor: ColorProvider = GlanceTheme.colors.onSurface) {
//        val params = actionParametersOf(PlayerActions.key to PlayerActions.PREVIOUS)
//        Box(
//            modifier = GlanceModifier.size(size)
//                .clickable(actionRunCallback<PlayerControlActionCallback>(params))
//                .padding(4.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Image(
//                provider = ImageProvider(R.drawable.rounded_skip_previous_24),
//                contentDescription = "Previous",
//                modifier = GlanceModifier.size(size - 8.dp),
//                colorFilter = ColorFilter.tint(iconColor)
//            )
//        }
//    }
}