# PixelPlay Beta 5.2 - Implementation Plan
## Features, Mejoras de Diseño y Funcionalidades Avanzadas

**Fecha**: Febrero 2026 (después de Beta 5.1)  
**Prerrequisito**: Completar satisfactoriamente Beta 5.1  
**Objetivo**: Implementar nuevas funcionalidades, mejoras visuales significativas, y optimizaciones avanzadas.

---

## 🎯 Resumen Ejecutivo

Beta 5.2 se enfoca en **mejoras de experiencia y nuevas funcionalidades**. Una vez que la estabilidad está asegurada con 5.1, podemos agregar:

- **Mejoras de diseño UI/UX significativas**
- **Nuevas opciones de configuración para usuarios avanzados**
- **Reimplementación de funcionalidades AI**
- **Optimización de estructuras de datos**
- **Correcciones de menor prioridad**

> [!IMPORTANT]
> **Principio Core**: Todas las implementaciones deben seguir Material 3 Expressive y mantener la eficiencia establecida en 5.1.

---

## 📋 Prerequisitos

Antes de comenzar:
- [x] Beta 5.1 completada y estable
- [ ] Baseline de benchmarks actualizado post-5.1
- [ ] Feedback de usuarios beta recopilado

---

## Fase 1: Mejoras de Diseño UI/UX

### 1.1 Rediseño de GenreDetailScreen

**Prioridad**: 🟠 Alta  
**Ubicación**: [GenreDetailScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/GenreDetailScreen.kt)

**Problema**:
GenreDetailScreen necesita mejoras mayores siguiendo Material 3 Expressive, con Collapsible top bar como SettingsScreen pero manteniendo el gradient de fondo.

**Implementación**:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String,
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    // Collapsible header setup (similar to SettingsScreen)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 220.dp // Más grande para mostrar arte del género
    
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }
    
    // Gradient dinámico basado en color del género
    val genreColor = viewModel.genreColor.collectAsState()
    val gradientBrush = remember(genreColor.value) {
        Brush.verticalGradient(
            colors = listOf(
                genreColor.value.copy(alpha = 0.8f),
                genreColor.value.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
    }
    
    // Nested scroll connection
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                
                if (!isScrollingDown && 
                    (lazyListState.firstVisibleItemIndex > 0 || 
                     lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }
                
                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight
                
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }
                
                return if (!(isScrollingDown && newHeight == minTopBarHeightPx)) {
                    Offset(0f, consumed)
                } else Offset.Zero
            }
        }
    }
    
    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
    ) {
        // Gradient background que persiste incluso colapsado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { topBarHeight.value.toDp() + 100.dp })
                .background(gradientBrush)
        )
        
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = with(density) { topBarHeight.value.toDp() },
                bottom = MiniPlayerHeight + 
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            // Song sections grouped by artist/album
            // ... existing content with improved styling
        }
        
        // Collapsible top bar with gradient
        GenreDetailTopBar(
            genreName = viewModel.genreName,
            collapseFraction = collapseFraction,
            headerHeight = with(density) { topBarHeight.value.toDp() },
            gradientBrush = gradientBrush,
            onBackPressed = { navController.popBackStack() }
        )
    }
}
```

**Verificación**:
- [ ] Comparar visualmente con SettingsScreen para consistencia
- [ ] Verificar que gradient se mantiene mientras scrollea
- [ ] Testear en light/dark mode

---

### 1.2 Fix: Texto cortado en StatsScreen

**Prioridad**: 🟡 Media  
**Ubicación**: [StatsScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/StatsScreen.kt)

**Problema**:
Algunos gráficos necesitan scroll horizontal para evitar texto truncado.

**Implementación**:

```kotlin
@Composable
fun CategoryVerticalBarChart(
    entries: List<CategoryMetricEntry>,
    modifier: Modifier = Modifier
) {
    // Calcular si necesita scroll
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val minBarWidth = 60.dp
    val totalRequiredWidth = entries.size * minBarWidth
    val needsHorizontalScroll = totalRequiredWidth > screenWidth - 32.dp
    
    val scrollState = rememberScrollState()
    
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(
                    if (needsHorizontalScroll) {
                        Modifier.horizontalScroll(scrollState)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = if (needsHorizontalScroll) {
                Arrangement.spacedBy(8.dp)
            } else {
                Arrangement.SpaceEvenly
            }
        ) {
            entries.forEach { entry ->
                BarChartColumn(
                    entry = entry,
                    modifier = Modifier.width(
                        if (needsHorizontalScroll) minBarWidth else Modifier.weight(1f)
                    )
                )
            }
        }
        
        // Indicador de scroll si es necesario
        if (needsHorizontalScroll) {
            HorizontalScrollIndicator(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }
    }
}
```

---

### 1.3 Fix: Glitch en PlayerUI al abrir QueueBottomSheet

**Prioridad**: 🟡 Media  
**Ubicación**: [UnifiedPlayerSheet.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt), [QueueBottomSheet.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/QueueBottomSheet.kt)

**Problema**:
Al abrir rápidamente el QueueBottomSheet solo un poco, hay un glitch visual momentáneo en el dim layer.

**Implementación**:

```kotlin
// Usar animación suavizada para el dim layer
@Composable
fun PlayerDimOverlay(
    queueSheetOffset: Float,
    maxOffset: Float
) {
    val dimAlpha by animateFloatAsState(
        targetValue = ((maxOffset - queueSheetOffset) / maxOffset * 0.5f).coerceIn(0f, 0.5f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dimAlpha"
    )
    
    // Solo renderizar si realmente hay dim
    if (dimAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dimAlpha))
                .pointerInput(Unit) {
                    detectTapGestures { /* Dismiss queue sheet */ }
                }
        )
    }
}
```

---

### 1.4 Mejoras de SplashScreen y HomeScreen

**Prioridad**: 🟡 Media  
**Ubicación**: [MainActivity.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/MainActivity.kt), [HomeScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/HomeScreen.kt)

**Problema**:
El primer inicio en HomeScreen se siente "glitchy". Necesita mejor animación cuando todos los elementos ya están cargados.

**Implementación**:

```kotlin
// En MainActivity - Mantener splash hasta que HomeScreen esté listo
class MainActivity {
    private var keepSplashScreen = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { keepSplashScreen }
            setOnExitAnimationListener { splashScreen ->
                // Animación de salida personalizada
                val fadeOut = ObjectAnimator.ofFloat(
                    splashScreen.view,
                    View.ALPHA,
                    1f, 0f
                ).apply {
                    duration = 300
                    interpolator = AccelerateInterpolator()
                    doOnEnd { splashScreen.remove() }
                }
                fadeOut.start()
            }
        }
        
        // ... setup ...
        
        // Esperar a que HomeScreen esté listo
        lifecycleScope.launch {
            playerViewModel.isHomeScreenReady.collect { ready ->
                if (ready) {
                    keepSplashScreen = false
                }
            }
        }
    }
}

// En PlayerViewModel
private val _isHomeScreenReady = MutableStateFlow(false)
val isHomeScreenReady: StateFlow<Boolean> = _isHomeScreenReady.asStateFlow()

fun markHomeScreenReady() {
    _isHomeScreenReady.value = true
}
```

---

### 1.5 Mejor comportamiento de Undo Bar

**Prioridad**: 🟡 Media  
**Ubicación**: [PlayerViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#L2276-L2319)

**Problema**:
El Undo Bar debe reaccionar al estado de reproducción. Si cancelas pero luego reproduces algo más, debería desaparecer.

**Implementación**:

```kotlin
// Monitorear cambios en la canción actual para ocultar undo bar
private fun setupUndoBarPlaybackObserver() {
    viewModelScope.launch {
        stablePlayerState
            .map { it.currentSong?.id }
            .distinctUntilChanged()
            .collect { newSongId ->
                val undoState = _playerUiState.value
                if (undoState.showDismissUndoBar && 
                    newSongId != null && 
                    newSongId != undoState.dismissedSong?.id) {
                    // Usuario reprodujo otra canción, ocultar undo bar
                    hideDismissUndoBar()
                }
            }
    }
}

init {
    // ... existing init ...
    setupUndoBarPlaybackObserver()
}
```

---

### 1.6 Mejores placeholders para Your Mix y Daily Mix

**Prioridad**: 🟢 Baja  
**Ubicación**: [DailyMixSection.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/DailyMixSection.kt)

**Problema**:
Cuando Daily Mix o Your Mix no están creados o están cargando, los placeholders necesitan mejor UX.

**Implementación**:

```kotlin
@Composable
fun DailyMixPlaceholder(
    state: DailyMixState,
    onGenerateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        when (state) {
            is DailyMixState.Loading -> {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Creating your Daily Mix...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            is DailyMixState.Empty -> {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your Daily Mix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Listen to some music and we'll create a personalized mix for you",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onGenerateClick) {
                        Text("Generate Now")
                    }
                }
            }
            
            else -> { /* Show actual content */ }
        }
    }
}
```

---

## Fase 2: Developer Options y Configuración

### 2.1 Opción de escalado de Album Art (Developer Options)

**Prioridad**: 🟠 Alta  
**Ubicación**: [SettingsCategoryScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt), [UserPreferencesRepository.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt)

**Problema**:
Los usuarios avanzados necesitan poder elegir cuánto se reduce el Album Art en diferentes lugares. Thumbnails pequeños siempre se reducirán para ahorrar memoria.

**Implementación**:

```kotlin
// En UserPreferencesRepository
enum class AlbumArtQuality(val maxSize: Int, val label: String) {
    LOW(256, "Low (256px) - Better performance"),
    MEDIUM(512, "Medium (512px) - Balanced"),
    HIGH(800, "High (800px) - Best quality"),
    ORIGINAL(0, "Original - Maximum quality")
}

object PreferencesKeys {
    // ... existing keys ...
    val ALBUM_ART_QUALITY = stringPreferencesKey("album_art_quality")
}

val albumArtQualityFlow: Flow<AlbumArtQuality> =
    dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ALBUM_ART_QUALITY]
            ?.let { AlbumArtQuality.valueOf(it) }
            ?: AlbumArtQuality.MEDIUM
    }

suspend fun setAlbumArtQuality(quality: AlbumArtQuality) {
    dataStore.edit { preferences ->
        preferences[PreferencesKeys.ALBUM_ART_QUALITY] = quality.name
    }
}
```

```kotlin
// En Developer Options section de SettingsCategoryScreen
@Composable
fun DeveloperOptionsSection(
    settingsViewModel: SettingsViewModel
) {
    val albumArtQuality by settingsViewModel.albumArtQuality.collectAsState()
    
    SettingsSection(title = "Developer Options") {
        // ... existing options ...
        
        SettingsDropdownItem(
            title = "Album Art Quality",
            subtitle = "Controls maximum resolution for album artwork in player view. Thumbnails always use low resolution.",
            selectedValue = albumArtQuality,
            options = AlbumArtQuality.entries,
            displayText = { it.label },
            onValueSelected = { settingsViewModel.setAlbumArtQuality(it) },
            icon = Icons.Rounded.Image
        )
    }
}
```

```kotlin
// En OptimizedAlbumArt.kt o SmartImage.kt
@Composable
fun PlayerAlbumArt(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val quality by LocalAlbumArtQuality.current.collectAsState()
    
    val targetSize = remember(quality) {
        if (quality.maxSize > 0) {
            Size(quality.maxSize, quality.maxSize)
        } else {
            Size.ORIGINAL
        }
    }
    
    SmartImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(uri)
            .size(targetSize)
            .build(),
        // ...
    )
}
```

---

### 2.2 Centralizar control de volumen (Sistema vs App)

**Prioridad**: 🟡 Media  
**Ubicación**: [UserPreferencesRepository.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt), sliders de volumen en la app

**Problema**:
Algunos sliders de volumen controlan media, otros controlan app volume. Necesita ser centralizado para que usuarios elijan.

**Implementación**:

```kotlin
// En UserPreferencesRepository
enum class VolumeControlMode {
    SYSTEM_MEDIA,  // Controla volumen de media del sistema
    APP_ONLY       // Solo controla volumen interno de la app
}

val volumeControlModeFlow: Flow<VolumeControlMode> =
    dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VOLUME_CONTROL_MODE]
            ?.let { VolumeControlMode.valueOf(it) }
            ?: VolumeControlMode.SYSTEM_MEDIA
    }

// Helper composable para usar en todos los sliders
@Composable
fun VolumeSlider(
    modifier: Modifier = Modifier
) {
    val volumeMode by LocalVolumeControlMode.current.collectAsState()
    val audioManager = LocalContext.current.getSystemService<AudioManager>()
    
    val (volume, setVolume) = when (volumeMode) {
        VolumeControlMode.SYSTEM_MEDIA -> {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            current.toFloat() / max to { v: Float ->
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (v * max).roundToInt(),
                    0
                )
            }
        }
        VolumeControlMode.APP_ONLY -> {
            val playerVolume by playerViewModel.trackVolume.collectAsState()
            playerVolume to { v: Float -> playerViewModel.setTrackVolume(v) }
        }
    }
    
    Slider(
        value = volume,
        onValueChange = setVolume,
        modifier = modifier
    )
}
```

---

### 2.3 Opción para desactivar "tap on background closes player"

**Prioridad**: 🟢 Baja  
**Ubicación**: [UnifiedPlayerSheet.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt)

**Problema**:
La funcionalidad de cerrar el player al tocar el fondo puede ser molesta para algunos usuarios.

**Implementación**:

```kotlin
// En UserPreferencesRepository
val tapBackgroundClosesPlayerFlow: Flow<Boolean> =
    dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] ?: true
    }

// En UnifiedPlayerSheet
@Composable
fun UnifiedPlayerSheet(...) {
    val tapClosesPlayer by settingsViewModel.tapBackgroundClosesPlayer.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (tapClosesPlayer) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            onDismiss()
                        }
                    }
                } else Modifier
            )
    ) {
        // ... player content
    }
}
```

---

## Fase 3: Datos y Almacenamiento

### 3.1 Definir estructura final de Stats

**Prioridad**: 🟠 Alta  
**Ubicación**: [PlaybackStatsRepository.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/stats/PlaybackStatsRepository.kt)

**Problema**:
La estructura de datos de Stats necesita ser optimizada para memoria/tiempo y preparada para Export/Import.

**Implementación**:

```kotlin
// Nueva estructura optimizada usando Room en lugar de JSON
@Entity(tableName = "playback_events")
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val songId: String,
    @ColumnInfo(index = true) val timestamp: Long,
    val durationMs: Long
)

@Dao
interface PlaybackStatsDao {
    @Query("SELECT * FROM playback_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getEventsAfter(startTime: Long): Flow<List<PlaybackEventEntity>>
    
    @Query("""
        SELECT songId, COUNT(*) as playCount, SUM(durationMs) as totalDuration
        FROM playback_events 
        WHERE timestamp >= :startTime
        GROUP BY songId
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun getTopSongs(startTime: Long, limit: Int): List<SongPlaybackSummary>
    
    @Insert
    suspend fun insert(event: PlaybackEventEntity)
    
    // Para Export/Import
    @Query("SELECT * FROM playback_events")
    suspend fun getAllForExport(): List<PlaybackEventEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importAll(events: List<PlaybackEventEntity>)
}

// Export format compatible con JSON
@Serializable
data class StatsExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val events: List<PlaybackEventExport>
)

@Serializable
data class PlaybackEventExport(
    val songId: String,
    val timestamp: Long,
    val durationMs: Long
)
```

**Migración**:
```kotlin
// Migrar de JSON a Room
class StatsMigrationWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        val legacyFile = File(context.filesDir, "playback_stats.json")
        if (!legacyFile.exists()) return Result.success()
        
        return try {
            val legacyEvents = gson.fromJson<List<LegacyEvent>>(legacyFile.readText())
            val entities = legacyEvents.map { it.toEntity() }
            
            playbackStatsDao.importAll(entities)
            legacyFile.delete()
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate stats")
            Result.retry()
        }
    }
}
```

---

### 3.2 Fix: Folders Tab sin opción SDCard

**Prioridad**: 🟡 Media  
**Ubicación**: [LibraryScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt), [StorageUtils.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/utils/StorageUtils.kt)

**Problema**:
El tab de Folders no muestra la opción de SDCard aunque el dispositivo tenga una.

**Implementación**:

```kotlin
// En StorageUtils.kt - mejorar detección
fun getAvailableStorages(context: Context): List<StorageInfo> {
    val storages = mutableListOf<StorageInfo>()
    
    // Internal storage
    val internalPath = Environment.getExternalStorageDirectory()
    storages.add(StorageInfo(
        path = internalPath,
        name = "Internal Storage",
        type = StorageType.INTERNAL,
        isRemovable = false
    ))
    
    // External storages (SD Cards, USB)
    context.getExternalFilesDirs(null).drop(1).forEach { file ->
        if (file != null) {
            // Navigate up to the root of the external storage
            var root = file
            while (root.parentFile?.parentFile?.parentFile?.parentFile != null) {
                root = root.parentFile!!
            }
            
            val volumeName = getVolumeLabel(context, root) ?: "SD Card"
            storages.add(StorageInfo(
                path = root,
                name = volumeName,
                type = StorageType.SD_CARD,
                isRemovable = true
            ))
        }
    }
    
    return storages.distinctBy { it.path.absolutePath }
}

private fun getVolumeLabel(context: Context, path: File): String? {
    val storageManager = context.getSystemService<StorageManager>() ?: return null
    return try {
        val volumes = storageManager.storageVolumes
        volumes.find { volume ->
            val volumePath = volume.directory?.absolutePath
            volumePath != null && path.absolutePath.startsWith(volumePath)
        }?.getDescription(context)
    } catch (e: Exception) {
        null
    }
}
```

---

### 3.3 Priorización de carátulas de álbum

**Prioridad**: 🟡 Media  
**Ubicación**: [AlbumArtUtils.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/utils/AlbumArtUtils.kt)

**Problema**:
Dentro de álbumes, las covers independientes no se respetan. Orden de prioridad: File (metadata) → User edited → Album match.

**Implementación**:

```kotlin
// Definir fuentes de album art con prioridad
enum class AlbumArtSource(val priority: Int) {
    FILE_METADATA(0),    // Embebido en el archivo
    USER_EDITED(1),      // Editado por usuario
    ALBUM_MATCH(2),      // Compartido del álbum
    FOLDER_IMAGE(3),     // imagen en carpeta (cover.jpg, etc.)
    CACHED_REMOTE(4)     // Descargado de API
}

suspend fun resolveAlbumArt(song: Song): AlbumArtResult {
    // 1. Verificar si el archivo tiene arte embebido
    val embeddedArt = extractEmbeddedArt(song.path)
    if (embeddedArt != null) {
        return AlbumArtResult(embeddedArt, AlbumArtSource.FILE_METADATA)
    }
    
    // 2. Verificar si usuario editó el arte
    val userEditedArt = getUserEditedArt(song.id)
    if (userEditedArt != null) {
        return AlbumArtResult(userEditedArt, AlbumArtSource.USER_EDITED)
    }
    
    // 3. Verificar arte del álbum
    val albumArt = getAlbumArt(song.albumId)
    if (albumArt != null) {
        return AlbumArtResult(albumArt, AlbumArtSource.ALBUM_MATCH)
    }
    
    // 4. Buscar imagen en carpeta
    val folderImage = findFolderImage(song.path)
    if (folderImage != null) {
        return AlbumArtResult(folderImage, AlbumArtSource.FOLDER_IMAGE)
    }
    
    // 5. Fallback a placeholder
    return AlbumArtResult(null, null)
}
```

---

## Fase 4: AI Features

### 4.1 Reimplementar generación de Playlists con IA

**Prioridad**: 🟠 Alta  
**Ubicación**: [AiPlaylistGenerator.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/ai/AiPlaylistGenerator.kt), [AiStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/AiStateHolder.kt)

**Problema**:
La generación de playlists con IA necesita ser más inteligente, mejor integrada e intuitiva, aprovechando los datos que la app ya maneja.

**Implementación**:

```kotlin
class SmartAiPlaylistGenerator @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val dailyMixManager: DailyMixManager,
    private val geminiClient: GeminiClient
) {
    
    suspend fun generateSmartPlaylist(
        prompt: String,
        allSongs: List<Song>,
        maxSongs: Int = 30
    ): List<Song> {
        // 1. Recopilar contexto del usuario
        val userContext = buildUserContext()
        
        // 2. Crear prompt enriquecido
        val enrichedPrompt = """
            User request: $prompt
            
            User listening context:
            - Top genres (last 30 days): ${userContext.topGenres.joinToString()}
            - Favorite artists: ${userContext.topArtists.take(5).joinToString { it.name }}
            - Average listening time: ${userContext.avgListeningMinutes} minutes/session
            - Preferred mood (based on listening patterns): ${userContext.inferredMood}
            
            Available songs: ${allSongs.size} tracks
            
            Please select song IDs that best match the user's request, 
            considering their listening history and preferences.
            Return only the song IDs, one per line.
        """.trimIndent()
        
        // 3. Enviar a Gemini
        val response = geminiClient.generate(enrichedPrompt)
        
        // 4. Parsear respuesta y mapear a canciones
        val selectedIds = parseAiResponse(response)
        
        // 5. Ordenar por relevancia (no aleatorio)
        return selectedIds
            .mapNotNull { id -> allSongs.find { it.id == id } }
            .take(maxSongs)
    }
    
    private suspend fun buildUserContext(): UserListeningContext {
        val stats = playbackStatsRepository.loadSummary(
            range = StatsTimeRange.LAST_30_DAYS,
            songs = emptyList()
        )
        
        return UserListeningContext(
            topGenres = stats.topGenres.map { it.genre },
            topArtists = stats.topArtists,
            avgListeningMinutes = stats.averageSessionLength,
            inferredMood = inferMoodFromStats(stats)
        )
    }
}
```

---

### 4.2 Mejorar función IA para Daily Mix

**Prioridad**: 🟠 Alta  
**Ubicación**: [DailyMixManager.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/DailyMixManager.kt)

**Problema**:
Daily Mix debe ser más inteligente usando datos de la app y optimizar el uso de API.

**Implementación**:

```kotlin
class EnhancedDailyMixManager @Inject constructor(
    private val engagementDao: EngagementDao,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val geminiClient: GeminiClient?
) {
    
    suspend fun generateDailyMix(
        allSongs: List<Song>,
        favoriteSongIds: Set<String>,
        useAi: Boolean = true,
        limit: Int = 30
    ): List<Song> {
        // 1. Calcular scores base (sin API)
        val rankedSongs = computeRankedSongs(allSongs, favoriteSongIds)
        
        // 2. Si AI está disponible y habilitada, enriquecer selección
        val finalSelection = if (useAi && geminiClient != null) {
            enhanceWithAi(rankedSongs, limit)
        } else {
            pickWithDiversity(rankedSongs, favoriteSongIds, limit)
        }
        
        return finalSelection
    }
    
    private suspend fun enhanceWithAi(
        rankedSongs: List<RankedSong>,
        limit: Int
    ): List<Song> {
        // Crear pool de candidatos (top 100)
        val candidatePool = rankedSongs.take(100)
        
        // Pedir a AI que ordene por "vibe" del día
        val prompt = """
            Today is ${LocalDate.now().dayOfWeek.name}.
            Time is ${LocalTime.now().hour}:00.
            
            From these ${candidatePool.size} songs, select $limit that would be 
            perfect for a "${getCurrentMoodContext()}" mood playlist.
            
            Songs: ${candidatePool.map { "${it.song.id}: ${it.song.title} by ${it.song.artist}" }}
        """.trimIndent()
        
        return try {
            val response = geminiClient!!.generate(prompt)
            val selectedIds = parseAiResponse(response)
            selectedIds.mapNotNull { id -> 
                candidatePool.find { it.song.id == id }?.song 
            }
        } catch (e: Exception) {
            // Fallback a selección sin AI
            Timber.w(e, "AI enhancement failed, using fallback")
            candidatePool.take(limit).map { it.song }
        }
    }
    
    private fun getCurrentMoodContext(): String {
        val hour = LocalTime.now().hour
        return when {
            hour in 6..9 -> "energizing morning"
            hour in 10..12 -> "focused work"
            hour in 13..17 -> "afternoon chill"
            hour in 18..21 -> "evening relaxation"
            else -> "late night vibes"
        }
    }
}
```

---

## Fase 5: Correcciones Menores

### 5.1 Añadir Credits para Rhythm Guy (@chroma)

**Prioridad**: 🟢 Baja  
**Ubicación**: [AboutScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/AboutScreen.kt)

**Problema**:
Necesita nueva sección de Credits en AboutScreen.

**Implementación**:

```kotlin
// Nueva sección después de Contributors
item(key = "special_thanks_header") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Special Thanks",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

val specialThanks = listOf(
    Contributor(
        name = "@chroma",
        role = "Rhythm Guy - Audio visualizer inspiration",
        telegramUrl = "https://t.me/..." // Si aplica
    ),
    // Otros agradecimientos
)

items(
    items = specialThanks,
    key = { "thanks_${it.name}" }
) { contributor ->
    ContributorCard(contributor)
}
```

---

### 5.2 Fix: Artist Tab contando mal canciones

**Prioridad**: 🟢 Baja  
**Ubicación**: [LibraryScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt)

**Problema**:
A veces el conteo de canciones por artista es incorrecto.

**Implementación**:

```kotlin
// Verificar que el conteo usa cross-references correctamente
fun countSongsForArtist(
    artistId: String,
    songArtistCrossRefs: List<SongArtistCrossRef>,
    allSongs: List<Song>
): Int {
    val songIdsForArtist = songArtistCrossRefs
        .filter { it.artistId == artistId }
        .map { it.songId }
        .toSet()
    
    return allSongs.count { it.id in songIdsForArtist }
}

// O mejor, usar Flow desde el DAO
@Query("""
    SELECT COUNT(DISTINCT s.id) 
    FROM songs s 
    INNER JOIN song_artist_cross_ref ref ON s.id = ref.songId 
    WHERE ref.artistId = :artistId
""")
fun getSongCountForArtist(artistId: String): Flow<Int>
```

---

### 5.3 Fix: Easter Egg reinicia niveles

**Prioridad**: 🟢 Baja  
**Ubicación**: [BrickBreakerOverlay.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/brickbreaker/BrickBreakerOverlay.kt)

**Problema**:
El Easter Egg (Brick Breaker) reinicia niveles cuando se reproduce música aleatoria.

**Implementación**:

```kotlin
@Composable
fun BrickBreakerOverlay(
    modifier: Modifier = Modifier,
    isMiniPlayerVisible: Boolean = false,
    onPlayRandom: () -> Unit,
    onClose: () -> Unit
) {
    // Preservar estado del juego entre reproducciones
    val savedLevel = rememberSaveable { mutableIntStateOf(1) }
    val savedScore = rememberSaveable { mutableIntStateOf(0) }
    val savedLives = rememberSaveable { mutableIntStateOf(3) }
    
    var level by savedLevel
    var score by savedScore
    var lives by savedLives
    
    // ... resto del juego usa estos estados
    
    // Cuando onPlayRandom se llama, NO resetear el juego
    val handlePlayRandom = remember {
        {
            // Solo reproducir música, no tocar el estado del juego
            onPlayRandom()
        }
    }
}
```

---

### 5.4 Fix: Setup debug version visible

**Prioridad**: 🟢 Baja  
**Ubicación**: [SetupScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/SetupScreen.kt), [strings.xml](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/res/values/strings.xml)

**Problema**:
A veces aparece el setup en español "Permiso Especial" en lugar de inglés.

**Implementación**:

```xml
<!-- En strings.xml - usar inglés por defecto -->
<string name="all_files_access_title">Special Permission Required</string>
<string name="all_files_access_description">To edit song metadata (MP3 files), PixelPlayer needs special access to all files. This allows us to modify track tags directly. Please grant this permission on the next screen to enable metadata editing.</string>

<!-- Mover traducciones a values-es/strings.xml si se desea soporte español -->
```

```kotlin
// En SetupScreen, verificar que nunca se muestra sin contexto
@Composable
fun AllFilesPermissionPage(uiState: SetupUiState) {
    // Solo mostrar si realmente necesita el permiso Y está en flujo de setup
    if (!uiState.isInSetupFlow) {
        // Si no está en setup, mostrar diálogo simple en lugar de página completa
        PermissionRequestDialog(
            title = stringResource(R.string.permission_needed),
            message = stringResource(R.string.all_files_access_description),
            onConfirm = { /* Request permission */ },
            onDismiss = { /* Continue without */ }
        )
        return
    }
    
    // ... página normal de setup
}
```

---

## Fase 6: Investigaciones

### 6.1 Investigar problema con "Big Me" de Foo Fighters LRC

**Prioridad**: 🟢 Baja  
**Ubicación**: [LyricsRepositoryImpl.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/repository/LyricsRepositoryImpl.kt)

**Problema**:
La búsqueda de LRC para "Big Me" de Foo Fighters devuelve una canción incorrecta.

**Investigación requerida**:
1. Verificar qué API de letras se está usando
2. Probar búsqueda manual en las APIs
3. Si es problema de API, documentar y agregar fallback
4. Considerar agregar opción de "Search manually" para letras

---

## Verificación Plan

### Automated Tests
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

### Manual Verification Checklist

- [ ] **Diseño**
  - [ ] GenreDetailScreen se ve consistente con SettingsScreen
  - [ ] Gráficos de Stats no tienen texto cortado
  - [ ] Placeholders de Daily Mix se ven profesionales

- [ ] **Developer Options**
  - [ ] Cambiar calidad de album art afecta PlayerUI
  - [ ] SDCard aparece en Folders Tab

- [ ] **AI Features**
  - [ ] Generación de playlist con AI usa contexto de usuario
  - [ ] Daily Mix se siente más personalizado

---

## Métricas de Éxito

| Métrica | Objetivo |
|---------|----------|
| Memory usage (AI ops) | +0% vs no-AI baseline |
| UI frame rate | Mantener 60 FPS |
| User satisfaction | Mejora en feedback cualitativo |
| Feature adoption | >50% usuarios usan nuevas opciones |

---

## Notas Finales

> [!TIP]
> **Orden recomendado**: Comenzar con Fase 1 (UI) ya que son cambios visibles que mejoran percepción. AI Features pueden desarrollarse en paralelo.

> [!WARNING]
> **Testing de AI**: Las funcionalidades de AI requieren testing extensivo con diferentes bibliotecas de música para asegurar resultados de calidad.
