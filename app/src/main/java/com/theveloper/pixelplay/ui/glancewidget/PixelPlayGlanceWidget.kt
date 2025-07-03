package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.layout.fillMaxHeight
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
import com.theveloper.pixelplay.ui.glancewidget.subcomponents.WavyLinearProgressIndicator

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
            val playerInfo = currentState<PlayerInfo>() ?: PlayerInfo()
            val currentSize = LocalSize.current

            Log.d("PixelPlayGlanceWidget", "Providing Glance. PlayerInfo: title='${playerInfo.songTitle}', artist='${playerInfo.artistName}', isPlaying=${playerInfo.isPlaying}, hasBitmap=${playerInfo.albumArtBitmapData != null}, progress=${playerInfo.currentPositionMs}/${playerInfo.totalDurationMs}")

            GlanceTheme {
                WidgetUi(playerInfo = playerInfo, size = currentSize, context = context)
            }
        }
    }

    @Composable
    private fun WidgetUi(playerInfo: PlayerInfo, size: DpSize, context: Context) {
        val title = playerInfo.songTitle.ifEmpty { "PixelPlay" }
        val artist = playerInfo.artistName.ifEmpty { "Toca para abrir" }
        val isPlaying = playerInfo.isPlaying
        val isFavorite = playerInfo.isFavorite
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val currentProgress = playerInfo.currentPositionMs
        val totalDuration = playerInfo.totalDurationMs

        Log.d("PixelPlayGlanceWidget", "WidgetUi: PlayerInfo received. Title: $title, Artist: $artist, HasBitmapData: ${albumArtBitmapData != null}, BitmapDataSize: ${albumArtBitmapData?.size ?: "N/A"}")

        // Cambio de color de fondo solicitado por el usuario
        // Intentaremos usar surfaceContainer. Si no existe, surfaceContainerLow, luego surface.
        // Por ahora, asumimos que GlanceTheme.colors.surfaceContainer está disponible o es un buen proxy.
        // Si se necesita un color específico no en GlanceTheme, se requeriría una solución más compleja.
        val backgroundColor = GlanceTheme.colors.surface // Usar surface como base más oscura que surfaceVariant
                                                        // O si tuvieras acceso a los tokens exactos:
                                                        // val backgroundColor = ColorProviders.MadetoolsSurface // Asumiendo que tienes acceso a tus colores Material 3
                                                        // Para este ejemplo, usaré surface, que es generalmente más oscuro que surfaceVariant.
                                                        // Si GlanceTheme expone surfaceContainerLow o similar, úsalo.
                                                        // Como no tengo la definición de GlanceTheme.colors aquí, me quedaré con surface por ahora.
                                                        // En un caso real, verificaría los colores disponibles en el tema de Glance de la app.
                                                        // Vamos a asumir que GlanceTheme.colors.surfaceContainer es lo que queremos.
                                                        // Si no existe, esto fallará en tiempo de compilación. Asumamos que existe para el ejemplo.
                                                        // Como no puedo verificar, usaré surface y lo comentaré.
                                                        // GlanceTheme.colors.surfaceContainer // Este sería el ideal si existe
        val actualBackgroundColor = GlanceTheme.colors.surface // Fallback si surfaceContainer no está
        val onBackgroundColor = GlanceTheme.colors.onSurface
        val progressBgColor = ColorProvider(GlanceTheme.colors.onSurface.getColor(context).copy(alpha = 0.2f))
        val primaryColor = GlanceTheme.colors.primary

        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(actualBackgroundColor) // Usar el nuevo color de fondo
            .cornerRadius(28.dp)
            .clickable(actionStartActivity<MainActivity>())

        when {
            size.height < THIN_LAYOUT_SIZE.height && size.width >= THIN_LAYOUT_SIZE.width -> {
                VeryThinWidgetLayout(baseModifier.padding(horizontal = 12.dp, vertical = 8.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor, context)
            }
            size.height < SMALL_LAYOUT_SIZE.height && size.width >= MEDIUM_LAYOUT_SIZE.width -> {
                ThinWidgetLayout(baseModifier.padding(horizontal = 16.dp, vertical = 10.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor, context)
            }
            size.width < MEDIUM_LAYOUT_SIZE.width || size.height < MEDIUM_LAYOUT_SIZE.height -> {
                SmallWidgetLayout(baseModifier.padding(16.dp), title, albumArtBitmapData, isPlaying, onBackgroundColor, context)
            }
            size.width < LARGE_LAYOUT_SIZE.width || size.height < LARGE_LAYOUT_SIZE.height -> {
                MediumWidgetLayout(baseModifier.padding(12.dp), title, artist, albumArtBitmapData, isPlaying, onBackgroundColor, context)
            }
            size.width < EXTRA_LARGE_LAYOUT_SIZE.width || size.height < EXTRA_LARGE_LAYOUT_SIZE.height -> { // Condición para el layout grande
                LargeWidgetLayout(
                    baseModifier.padding(16.dp),
                    title,
                    artist,
                    albumArtBitmapData,
                    isPlaying,
                    isFavorite,
                    currentProgress,
                    totalDuration,
                    textColor = onBackgroundColor,
                    progressBgColor = progressBgColor,
                    accentColor = primaryColor,
                    context = context
                )
            }
            else -> { // Layout extra grande
                ExtraLargeWidgetLayout(
                    baseModifier.padding(16.dp),
                    title,
                    artist,
                    albumArtBitmapData,
                    isPlaying,
                    currentProgress,
                    totalDuration,
                    onBackgroundColor,
                    primaryColor,
                    context = context,
                    progressBgColor = progressBgColor
                )
            }
        }
    }

    @Composable
    fun VeryThinWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider, context: Context) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 36.dp, context = context, cornerRadius = 18.dp) // Circular
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != "Toca para abrir") {
                    Text(text = artist, style = TextStyle(fontSize = 10.sp, color = textColor), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(6.dp))
            PlayPauseButtonGlance(modifier = GlanceModifier.size(30.dp), isPlaying = isPlaying, iconColor = textColor, backgroundColor = GlanceTheme.colors.secondaryContainer, iconSize = 20.dp, cornerRadius = 15.dp)
            NextButtonGlance(modifier = GlanceModifier.size(30.dp), iconColor = textColor, backgroundColor = GlanceTheme.colors.secondaryContainer, iconSize = 20.dp, cornerRadius = 15.dp)
        }
    }


    @Composable
    fun ThinWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider, context: Context) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 48.dp, context = context, cornerRadius = 24.dp) // Circular
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != "Toca para abrir") {
                    Text(text = artist, style = TextStyle(fontSize = 12.sp, color = textColor), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, iconSize = 36.dp, iconColor = textColor)
            NextButtonGlance(iconSize = 36.dp, iconColor = textColor)
        }
    }

    @Composable
    fun SmallWidgetLayout(modifier: GlanceModifier, title: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider, context: Context) {
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 60.dp, context = context, cornerRadius = 30.dp, modifier = GlanceModifier.padding(bottom = 8.dp)) // Circular
            Text(text = title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            PlayPauseButtonGlance(isPlaying = isPlaying, iconSize = 40.dp, iconColor = textColor)
        }
    }

    @Composable
    fun MediumWidgetLayout(modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?, isPlaying: Boolean, textColor: ColorProvider, context: Context) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 64.dp, context = context, cornerRadius = 16.dp) // Rectángulo redondeado
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                Text(text = artist, style = TextStyle(fontSize = 13.sp, color = textColor), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            PreviousButtonGlance(iconSize = 32.dp, iconColor = textColor)
            PlayPauseButtonGlance(isPlaying = isPlaying, iconSize = 38.dp, iconColor = textColor)
            NextButtonGlance(iconSize = 32.dp, iconColor = textColor)
            val controlButtonModifier = GlanceModifier.size(36.dp) // Tamaño unificado para esta fila
            val controlIconSize = 22.dp
            val controlButtonCornerRadius = 18.dp
            val transparentBg = GlanceTheme.colors.surface

            PreviousButtonGlance(modifier = controlButtonModifier, iconColor = textColor, backgroundColor = transparentBg, iconSize = controlIconSize, cornerRadius = controlButtonCornerRadius)
            PlayPauseButtonGlance(modifier = controlButtonModifier, isPlaying = isPlaying, iconColor = textColor, backgroundColor = transparentBg, iconSize = controlIconSize, cornerRadius = controlButtonCornerRadius)
            NextButtonGlance(modifier = controlButtonModifier, iconColor = textColor, backgroundColor = transparentBg, iconSize = controlIconSize, cornerRadius = controlButtonCornerRadius)
        }
    }

    @Composable
    fun LargeWidgetLayout(
        modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?,
        isPlaying: Boolean, isFavorite: Boolean, currentProgressMs: Long, totalDurationMs: Long,
        textColor: ColorProvider, accentColor: ColorProvider,
        progressBgColor: ColorProvider,
        context: Context // Añadir contexto para el Intent
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 64.dp, context = context, cornerRadius = 18.dp)
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                    Text(text = artist, style = TextStyle(fontSize = 13.sp, color = textColor), maxLines = 1)
                }
                Spacer(GlanceModifier.width(4.dp))
                Image(
                    provider = ImageProvider(if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24),
                    contentDescription = "favorite",
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(actionRunCallback<PlayerControlActionCallback>(actionParametersOf(PlayerActions.key to PlayerActions.FAVORITE)))
                        .padding(2.dp),
                    colorFilter = ColorFilter.tint(textColor)
                )
                Spacer(GlanceModifier.width(8.dp))
            }
            Spacer(GlanceModifier.height(4.dp))
            if (totalDurationMs > 0L) {
                WavyLinearProgressIndicator(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .cornerRadius(2.dp),
                    progress = currentProgressMs.toFloat() / totalDurationMs.toFloat(),
                    isPlaying = isPlaying,
                    trackHeight = 4.dp,
                    waveAmplitude = 2.dp,
                    thumbRadius = 6.dp,
                    trackBackgroundColor = progressBgColor.getColor(context),
                    activeTrackColor = accentColor.getColor(context),
                    thumbColor = accentColor.getColor(context)
                )
//                LinearProgressIndicator(
//                    progress = currentProgressMs.toFloat() / totalDurationMs.toFloat(),
//                    modifier = GlanceModifier
//                        .fillMaxWidth()
//                        .height(6.dp)
//                        .cornerRadius(2.dp),
//                    color = accentColor,
//                    backgroundColor = progressBgColor // Un poco más tenue para el fondo
//                )
                Spacer(GlanceModifier.height(2.dp))
                Row(
                    GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, end = 8.dp, start = 8.dp)
                    // horizontalAlignment = Alignment.SpaceBetween  <- Esto no funciona en Glance Row directamente
                ) {
                    Text(formatDurationGlance(currentProgressMs), style = TextStyle(fontSize = 12.sp, color = textColor))
                    Spacer(GlanceModifier.defaultWeight()) // Spacer con weight para empujar el siguiente texto al final
                    Text(formatDurationGlance(totalDurationMs), style = TextStyle(fontSize = 12.sp, color = textColor))
                }
            } else {
                Spacer(GlanceModifier.height(4.dp + 10.sp.value.dp + 4.dp)) // Mantener espacio similar
            }
            Spacer(GlanceModifier.height(10.dp))

            // Fila de Controles Rediseñada
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                    //.height(48.dp), // Altura fija para los botones
                verticalAlignment = Alignment.CenterVertically
            ) {
                val secondaryColor = GlanceTheme.colors.secondaryContainer
                val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
                val primaryContainerColor = GlanceTheme.colors.primaryContainer
                val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer
                val tertiaryColor = GlanceTheme.colors.tertiaryContainer
                val onTertiaryColor = GlanceTheme.colors.onTertiaryContainer
                val buttonCornerRadius = 60.dp // Redondeo para cada botón
                val playButtonCornerRadius = if (isPlaying) 20.dp else 60.dp

                PreviousButtonGlance(
                    modifier = GlanceModifier.defaultWeight(), // Aplicar weight aquí
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    cornerRadius = buttonCornerRadius
                )
                Spacer(GlanceModifier.width(8.dp))
                PlayPauseButtonGlance(
                    modifier = GlanceModifier.defaultWeight(), // Aplicar weight aquí
                    isPlaying = isPlaying,
                    iconColor = onPrimaryContainerColor,
                    backgroundColor = primaryContainerColor,
                    cornerRadius = playButtonCornerRadius
                )
                Spacer(GlanceModifier.width(8.dp))
                NextButtonGlance(
                    modifier = GlanceModifier.defaultWeight(), // Aplicar weight aquí
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    cornerRadius = buttonCornerRadius
                )
            }
        }
    }

    @Composable
    fun ExtraLargeWidgetLayout( // Nuevo layout para el tamaño más grande
        modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?,
        isPlaying: Boolean, currentProgressMs: Long, totalDurationMs: Long,
        textColor: ColorProvider, accentColor: ColorProvider,
        progressBgColor: ColorProvider,
        context: Context // Añadir contexto para el Intent
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            // Fila superior: Carátula e Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                AlbumArtImageGlance(bitmapData = albumArtBitmapData, size = 80.dp, context = context, cornerRadius = 22.dp)
                Spacer(GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor),
                        maxLines = 2
                    ) // Permitir 2 líneas
                    Text(
                        text = artist,
                        style = TextStyle(fontSize = 14.sp, color = textColor),
                        maxLines = 1
                    )
                }
                // Podríamos añadir un botón de "Abrir App" aquí también si se desea, similar a LargeWidgetLayout
            }

            Spacer(GlanceModifier.height(8.dp))

            // Fila media: Barra de progreso y tiempos
            Column(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 12.dp)
            ) { // Más padding vertical
                if (totalDurationMs > 0L) {
                    LinearProgressIndicator(
                        progress = currentProgressMs.toFloat() / totalDurationMs.toFloat(),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .cornerRadius(30.dp), // Barra más gruesa
                        color = accentColor,
                        backgroundColor = progressBgColor // Un poco más tenue para el fondo
                    )
                    Row(
                        GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 6.dp) // Más padding superior
                        // horizontalAlignment = Alignment.SpaceBetween // <- Esto no funciona en Glance Row directamente
                    ) {
                        Text(formatDurationGlance(currentProgressMs), style = TextStyle(fontSize = 12.sp, color = textColor))
                        Spacer(GlanceModifier.defaultWeight()) // Spacer con weight para empujar el siguiente texto al final
                        Text(formatDurationGlance(totalDurationMs), style = TextStyle(fontSize = 12.sp, color = textColor))
                    }
                } else {
                    Spacer(GlanceModifier.height(6.dp + 12.sp.value.dp + 6.dp)) // Mantener espacio
                }
            }

            // Fila inferior: Controles Rediseñada
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(56.dp), // Altura mayor para botones más grandes
                verticalAlignment = Alignment.CenterVertically
            ) {
                val secondaryColor = GlanceTheme.colors.secondaryContainer
                val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
                val primaryContainerColor = GlanceTheme.colors.primaryContainer
                val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer
                val tertiaryColor = GlanceTheme.colors.tertiaryContainer
                val onTertiaryColor = GlanceTheme.colors.onTertiaryContainer
                val buttonCornerRadius = 28.dp // Redondeo para cada botón

                PreviousButtonGlance(
                    modifier = GlanceModifier.defaultWeight(), // Aplicar weight aquí
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    iconSize = 28.dp,
                    cornerRadius = buttonCornerRadius
                )
                Spacer(GlanceModifier.width(10.dp))
                PlayPauseButtonGlance(
                    modifier = GlanceModifier.defaultWeight(), // Aplicar weight aquí
                    isPlaying = isPlaying,
                    iconColor = onPrimaryContainerColor,
                    backgroundColor = primaryContainerColor,
                    iconSize = 30.dp,
                    cornerRadius = buttonCornerRadius
                )
                Spacer(GlanceModifier.width(10.dp))
                NextButtonGlance(
                    modifier = GlanceModifier.defaultWeight(), // Aplicar weight aquí
                    iconColor = onTertiaryColor,
                    backgroundColor = tertiaryColor,
                    iconSize = 28.dp,
                    cornerRadius = buttonCornerRadius
                )
            }
        }
    }

    @Composable
    fun AlbumArtImageGlance(bitmapData: ByteArray?, size: Dp, context: Context, modifier: GlanceModifier = GlanceModifier, cornerRadius: Dp = 16.dp) {
        val TAG_AAIG = "AlbumArtImageGlance"
        Log.d(TAG_AAIG, "Init. bitmapData is null: ${bitmapData == null}. Requested Dp size: $size")
        if (bitmapData != null) Log.d(TAG_AAIG, "bitmapData size: ${bitmapData.size} bytes")

        val imageProvider = bitmapData?.let { data ->
            val cacheKey = AlbumArtBitmapCache.getKey(data)
            var bitmap = AlbumArtBitmapCache.getBitmap(cacheKey)

            if (bitmap != null) {
                Log.d(TAG_AAIG, "Bitmap cache HIT for key: $cacheKey. Using cached bitmap.")
            } else {
                Log.d(TAG_AAIG, "Bitmap cache MISS for key: $cacheKey. Decoding new bitmap.")
                try {
                    // 1. Decode bounds
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, options)
                    Log.d(TAG_AAIG, "Initial bounds: ${options.outWidth}x${options.outHeight}")

                    // Calculate inSampleSize
                    val imageHeight = options.outHeight
                    val imageWidth = options.outWidth
                    var inSampleSize = 1
                    val targetSizePx = with(context.resources.displayMetrics) { size.value * density }.toInt()
                    Log.d(TAG_AAIG, "Target Px size for Dp $size : $targetSizePx")

                    if (imageHeight > targetSizePx || imageWidth > targetSizePx) {
                        val halfHeight: Int = imageHeight / 2
                        val halfWidth: Int = imageWidth / 2
                        while (halfHeight / inSampleSize >= targetSizePx && halfWidth / inSampleSize >= targetSizePx) {
                            inSampleSize *= 2
                        }
                    }
                    Log.d(TAG_AAIG, "Calculated inSampleSize: $inSampleSize")

                    // 2. Decode with inSampleSize
                    options.inSampleSize = inSampleSize
                    options.inJustDecodeBounds = false
                    val sampledBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)

                    if (sampledBitmap == null) {
                        Log.e(TAG_AAIG, "BitmapFactory.decodeByteArray returned null after sampling.")
                        return@let null // Fallback to placeholder
                    }
                    Log.d(TAG_AAIG, "Sampled bitmap size: ${sampledBitmap.width}x${sampledBitmap.height}")

                    // 3. Scale to exact target size if necessary
                    if (sampledBitmap.width != targetSizePx || sampledBitmap.height != targetSizePx) {
                        Log.d(TAG_AAIG, "Scaling sampled bitmap from ${sampledBitmap.width}x${sampledBitmap.height} to ${targetSizePx}x${targetSizePx}")
                        val scaledBitmap = Bitmap.createScaledBitmap(sampledBitmap, targetSizePx, targetSizePx, true)
                        if (scaledBitmap != sampledBitmap) { // If scaling created a new bitmap instance
                            sampledBitmap.recycle() // Recycle the larger/intermediate sampled bitmap
                            Log.d(TAG_AAIG, "Recycled intermediate sampledBitmap.")
                        }
                        bitmap = scaledBitmap
                    } else {
                        bitmap = sampledBitmap
                        Log.d(TAG_AAIG, "No final scaling needed. Using sampled bitmap directly.")
                    }

                    Log.d(TAG_AAIG, "Final bitmap size: ${bitmap?.width}x${bitmap?.height}. Putting into cache with key: $cacheKey")
                    bitmap?.let { AlbumArtBitmapCache.putBitmap(cacheKey, it) }

                } catch (e: Exception) {
                    Log.e(TAG_AAIG, "Error decoding or scaling bitmap: ${e.message}", e)
                    bitmap = null // Ensure fallback to placeholder on error
                }
            }
            bitmap?.let { ImageProvider(it) }
        } ?: run {
            Log.d(TAG_AAIG, "Using placeholder image because bitmapData was null or processing failed.")
            ImageProvider(R.drawable.rounded_album_24)
        }


        Image(
            provider = imageProvider,
            contentDescription = "Album Art",
            modifier = modifier.size(size).background(GlanceTheme.colors.surface).cornerRadius(cornerRadius),
            contentScale = ContentScale.Crop
        )
    }

    @Composable
    fun PlayPauseButtonGlance(
        modifier: GlanceModifier = GlanceModifier,
        isPlaying: Boolean,
        iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
        backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
        iconSize: Dp = 24.dp,
        cornerRadius: Dp = 0.dp
    ) {
        val params = actionParametersOf(PlayerActions.key to PlayerActions.PLAY_PAUSE)
        Box(
            modifier = modifier
                //.fillMaxSize() // Llenar el espacio asignado por el peso
                .fillMaxHeight()
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<PlayerControlActionCallback>(params)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }

    @Composable
    fun NextButtonGlance(
        modifier: GlanceModifier = GlanceModifier,
        iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
        backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
        iconSize: Dp = 24.dp,
        cornerRadius: Dp = 8.dp
    ) {
        val params = actionParametersOf(PlayerActions.key to PlayerActions.NEXT)
        Box(
            modifier = modifier
                //.fillMaxSize() // Llenar el espacio asignado por el peso
                .fillMaxHeight()
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<PlayerControlActionCallback>(params)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.rounded_skip_next_24),
                contentDescription = "Next",
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }

    @Composable
    fun PreviousButtonGlance(
        modifier: GlanceModifier = GlanceModifier,
        iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
        backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
        iconSize: Dp = 24.dp,
        cornerRadius: Dp = 8.dp
    ) {
        val params = actionParametersOf(PlayerActions.key to PlayerActions.PREVIOUS)
        Box(
            modifier = modifier
                //.fillMaxSize() // Llenar el espacio asignado por el peso
                .fillMaxHeight()
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<PlayerControlActionCallback>(params)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.rounded_skip_previous_24),
                contentDescription = "Previous",
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
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