# PlayerViewModel - Informe Completo de Análisis Arquitectónico

## Resumen Ejecutivo

Este informe presenta un análisis exhaustivo del [PlayerViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt), el componente más crítico de PixelPlay con **5,604 líneas de código** (255KB). El objetivo es proporcionar toda la información relevante para diseñar un plan de migración hacia una arquitectura más modular que mejore:

- **Mantenibilidad**: Reducir acoplamiento y mejorar separación de responsabilidades
- **Escalabilidad**: Facilitar adición de nuevas funcionalidades sin afectar código existente
- **Eficiencia**: Optimizar uso de memoria RAM y procesamiento de estado

---

## Tabla de Contenidos

1. [Métricas Actuales](#1-métricas-actuales)
2. [Dependencias Inyectadas](#2-dependencias-inyectadas)
3. [Estado Actual de Refactorización](#3-estado-actual-de-refactorización)
4. [Inventario Completo de StateFlows](#4-inventario-completo-de-stateflows)
5. [Análisis Funcional por Responsabilidad](#5-análisis-funcional-por-responsabilidad)
6. [Acoplamiento con UI Components](#6-acoplamiento-con-ui-components)
7. [Problemas Arquitectónicos Identificados](#7-problemas-arquitectónicos-identificados)
8. [Propuesta de Descomposición Modular](#8-propuesta-de-descomposición-modular)
9. [Matriz de Impacto y Dependencias](#9-matriz-de-impacto-y-dependencias)
10. [Recomendaciones de Migración](#10-recomendaciones-de-migración)

---

## 1. Métricas Actuales

| Métrica | Valor | Observación |
|---------|-------|-------------|
| **Líneas de código** | 5,604 | ~7x mayor que el límite recomendado (800) |
| **Tamaño en bytes** | 255,604 | Archivo muy pesado |
| **Funciones públicas** | 227 | Excesiva API pública |
| **Dependencias inyectadas** | 22 | God Object pattern |
| **StateFlows definidos** | ~50+ | Memoria RAM significativa |
| **BroadcastReceivers** | 3 | WiFi, Bluetooth, Audio Devices |
| **Callbacks registrados** | 7+ | MediaController, Cast, MediaRouter, Network |
| **Nested Classes** | 6 | (QueueTransferData, RebuildArtifacts, TransferSnapshot, etc.) |

---

## 2. Dependencias Inyectadas

El constructor actual tiene **22 dependencias**, indicando violación del Single Responsibility Principle:

```kotlin
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager,
    private val songMetadataEditor: SongMetadataEditor,
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val aiMetadataGenerator: AiMetadataGenerator,
    private val artistImageRepository: ArtistImageRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: AppShortcutManager,
    private val listeningStatsTracker: ListeningStatsTracker,      // ✅ Extraído
    private val colorSchemeProcessor: ColorSchemeProcessor,        // ✅ Extraído
    private val dailyMixStateHolder: DailyMixStateHolder,          // ✅ Extraído
    private val lyricsStateHolder: LyricsStateHolder,              // ✅ Extraído
    private val castStateHolder: CastStateHolder,                  // ✅ Extraído
    private val queueStateHolder: QueueStateHolder                 // ✅ Extraído
) : ViewModel()
```

### Clasificación de Dependencias por Dominio

| Dominio | Dependencias | Estado |
|---------|--------------|--------|
| **Playback** | `dualPlayerEngine` | ❌ No extraído |
| **Library** | `musicRepository`, `aiPlaylistGenerator` | ❌ No extraído |
| **Preferences** | `userPreferencesRepository` | ❌ No extraído |
| **Theming** | `albumArtThemeDao`, `colorSchemeProcessor` | ⚠️ Parcialmente extraído |
| **Lyrics** | `lyricsStateHolder` | ✅ Extraído |
| **Cast** | `castStateHolder` | ⚠️ Parcialmente (lógica de transferencia permanece) |
| **Queue** | `queueStateHolder` | ✅ Extraído |
| **Stats** | `playbackStatsRepository`, `listeningStatsTracker` | ✅ Extraído |
| **Daily Mix** | `dailyMixManager`, `dailyMixStateHolder` | ✅ Extraído |
| **AI** | `aiPlaylistGenerator`, `aiMetadataGenerator` | ❌ No extraído |
| **Metadata** | `songMetadataEditor` | ❌ No extraído |
| **System** | `context`, `syncManager`, `appShortcutManager` | ❌ No extraído |

---

## 3. Estado Actual de Refactorización

### StateHolders Extraídos (6 componentes)

| Componente | Archivo | Líneas | Responsabilidad |
|------------|---------|--------|-----------------|
| [LyricsStateHolder](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/LyricsStateHolder.kt#35-134) | [LyricsStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/LyricsStateHolder.kt) | 134 | Carga/búsqueda de letras, offset de sincronización |
| [CastStateHolder](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/CastStateHolder.kt#28-170) | [CastStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/CastStateHolder.kt) | 170 | Estado de Cast session, remote playback flags |
| [QueueStateHolder](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/QueueStateHolder.kt#13-76) | [QueueStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/QueueStateHolder.kt) | 76 | Orden original de cola para shuffle/unshuffle |
| [DailyMixStateHolder](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/DailyMixStateHolder.kt#33-166) | [DailyMixStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/DailyMixStateHolder.kt) | 166 | Generación y persistencia de Daily/Your Mix |
| [ColorSchemeProcessor](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/ColorSchemeProcessor.kt#47-302) | [ColorSchemeProcessor.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/ColorSchemeProcessor.kt) | 302 | Extracción de colores, cache LRU |
| [ListeningStatsTracker](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/ListeningStatsTracker.kt#23-184) | [ListeningStatsTracker.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/ListeningStatsTracker.kt) | 199 | Tracking de sesiones de escucha, stats |

**Total extraído**: ~1,047 líneas

### Código Pendiente de Extracción

Estimado: **~4,500 líneas** aún en PlayerViewModel

---

## 4. Inventario Completo de StateFlows

### StateFlows Primarios de UI (Expuestos a Composables)

```kotlin
// Estados de reproducción principales
val playerUiState: StateFlow<PlayerUiState>          // Estado completo de UI
val stablePlayerState: StateFlow<StablePlayerState>  // Estado estable (canción, playing, lyrics)
val paginatedSongs: Flow<PagingData<Song>>           // Paginación de biblioteca

// Estado de sheet/player visibility
val sheetState: StateFlow<PlayerSheetState>          // COLLAPSED | EXPANDED
val isSheetVisible: StateFlow<Boolean>
val bottomBarHeight: StateFlow<Int>
val predictiveBackCollapseFraction: StateFlow<Float>
val playerContentExpansionFraction: Animatable<Float>

// Artistas de canción actual
val currentSongArtists: StateFlow<List<Artist>>

// AI Features
val showAiPlaylistSheet: StateFlow<Boolean>
val isGeneratingAiPlaylist: StateFlow<Boolean>
val aiError: StateFlow<String?>
val selectedSongForInfo: StateFlow<Song?>

// Theming
val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?>
val activePlayerColorSchemePair: StateFlow<ColorSchemePair?>
val playerThemePreference: StateFlow<String>

// Preferencias de UI
val navBarCornerRadius: StateFlow<Int>
val navBarStyle: StateFlow<String>
val libraryNavigationMode: StateFlow<String>
val carouselStyle: StateFlow<String>
val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks>

// Lyrics
val currentSongLyricsSyncOffset: StateFlow<Int>       // Delegado a LyricsStateHolder
val lyricsSourcePreference: StateFlow<LyricsSourcePreference>
val lyricsSearchUiState: StateFlow<LyricsSearchUiState>

// Sleep Timer
val sleepTimerEndTimeMillis: StateFlow<Long?>
val isEndOfTrackTimerActive: StateFlow<Boolean>
val activeTimerValueDisplay: StateFlow<String?>
val playCount: StateFlow<Float>

// Toast/Navigation Events
val toastEvents: SharedFlow<String>
val artistNavigationRequests: SharedFlow<Long>

// Cast/Remote Playback
val castRoutes: StateFlow<List<MediaRouter.RouteInfo>>
val selectedRoute: StateFlow<MediaRouter.RouteInfo?>
val routeVolume: StateFlow<Int>
val isRefreshingRoutes: StateFlow<Boolean>
val isRemotePlaybackActive: StateFlow<Boolean>        // Delegado a CastStateHolder
val isCastConnecting: StateFlow<Boolean>               // Delegado a CastStateHolder
val remotePosition: StateFlow<Long>                    // Delegado a CastStateHolder
val trackVolume: StateFlow<Float>

// Network/Connectivity
val isWifiEnabled: StateFlow<Boolean>
val isWifiRadioOn: StateFlow<Boolean>
val wifiName: StateFlow<String?>
val isBluetoothEnabled: StateFlow<Boolean>
val bluetoothName: StateFlow<String?>
val bluetoothAudioDevices: StateFlow<List<String>>

// Library
val lastLibraryTabIndexFlow: StateFlow<Int>
val libraryTabsFlow: StateFlow<List<String>>
val currentLibraryTabId: StateFlow<LibraryTabId>
val availableSortOptions: StateFlow<List<SortOption>>
val isSortingSheetVisible: StateFlow<Boolean>
val isSyncingStateFlow: StateFlow<Boolean>
val allSongsFlow: StateFlow<List<Song>>
val genres: StateFlow<ImmutableList<Genre>>
val isCurrentSongFavorite: StateFlow<Boolean>
val favoriteSongIds: StateFlow<Set<String>>
val favoriteSongs: StateFlow<ImmutableList<Song>>
val isInitialThemePreloadComplete: StateFlow<Boolean>
```

### StateFlows Internos (Privados)

```kotlin
private val _playerUiState: MutableStateFlow<PlayerUiState>
private val _masterAllSongs: MutableStateFlow<ImmutableList<Song>>
private val _stablePlayerState: MutableStateFlow<StablePlayerState>
private val _sheetState: MutableStateFlow<PlayerSheetState>
private val _isSheetVisible: MutableStateFlow<Boolean>
private val _bottomBarHeight: MutableStateFlow<Int>
private val _predictiveBackCollapseFraction: MutableStateFlow<Float>
private val _showAiPlaylistSheet: MutableStateFlow<Boolean>
private val _isGeneratingAiPlaylist: MutableStateFlow<Boolean>
private val _aiError: MutableStateFlow<String?>
private val _selectedSongForInfo: MutableStateFlow<Song?>
private val _currentAlbumArtColorSchemePair: MutableStateFlow<ColorSchemePair?>
private val _sleepTimerEndTimeMillis: MutableStateFlow<Long?>
private val _isEndOfTrackTimerActive: MutableStateFlow<Boolean>
private val _activeTimerValueDisplay: MutableStateFlow<String?>
private val _playCount: MutableStateFlow<Float>
private val _toastEvents: MutableSharedFlow<String>
private val _artistNavigationRequests: MutableSharedFlow<Long>
private val _castRoutes: MutableStateFlow<List<MediaRouter.RouteInfo>>
private val _selectedRoute: MutableStateFlow<MediaRouter.RouteInfo?>
private val _routeVolume: MutableStateFlow<Int>
private val _isRefreshingRoutes: MutableStateFlow<Boolean>
private val _isWifiEnabled: MutableStateFlow<Boolean>
private val _isWifiRadioOn: MutableStateFlow<Boolean>
private val _wifiName: MutableStateFlow<String?>
private val _isBluetoothEnabled: MutableStateFlow<Boolean>
private val _bluetoothName: MutableStateFlow<String?>
private val _bluetoothAudioDevices: MutableStateFlow<List<String>>
private val _loadedTabs: MutableStateFlow<Set<String>>
private val _currentLibraryTabId: MutableStateFlow<LibraryTabId>
private val _isSortingSheetVisible: MutableStateFlow<Boolean>
private val _isInitialDataLoaded: MutableStateFlow<Boolean>
private val _isInitialThemePreloadComplete: MutableStateFlow<Boolean>
private val _currentFavoriteSortOptionStateFlow: MutableStateFlow<SortOption>
private val _trackVolume: MutableStateFlow<Float>
private val isRemotelySeeking: MutableStateFlow<Boolean>
```

**Conteo aproximado: 50+ StateFlows activos**

---

## 5. Análisis Funcional por Responsabilidad

### 5.1 Control de Reproducción (~800 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [playPause()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4199-4256) | 4199-4255 | Control play/pause con lógica Cast |
| [seekTo()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4257-4283) | 4257-4282 | Seek con soporte Cast |
| [nextSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4284-4306) | 4284-4305 | Siguiente canción |
| [previousSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4307-4330) | 4307-4329 | Canción anterior |
| [startProgressUpdates()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4331-4365) | 4331-4364 | Actualización periódica de posición |
| [stopProgressUpdates()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4366-4370) | 4366-4369 | Detener actualizaciones |
| [toggleShuffle()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3685-3789) | 3685-3788 | Alternar shuffle (103 líneas!) |
| [cycleRepeatMode()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3790-3824) | 3790-3823 | Ciclar modos de repetición |
| [repeatSingle()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3825-3837) | 3825-3836 | Repetir una |
| [repeatOff()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3838-3850) | 3838-3849 | Desactivar repetición |
| [setupMediaControllerListeners()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2747-2935) | 2747-2934 | Configurar listeners (187 líneas!) |

### 5.2 Gestión de Cola (~600 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [playSongs()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3075-3105) | 3075-3104 | Reproducir lista de canciones |
| [playSongsShuffled()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3106-3135) | 3106-3134 | Reproducir shuffled |
| [showAndPlaySong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2486-2493) | 2412-2492 | Mostrar y reproducir canción (80 líneas) |
| [addSongToQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3865-3880) | 3865-3879 | Agregar a cola |
| [addSongNextToQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3881-3905) | 3881-3904 | Agregar como siguiente |
| [removeSongFromQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2559-2571) | 2559-2570 | Remover de cola |
| [reorderQueueItem()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2572-2584) | 2572-2583 | Reordenar item |
| [rebuildPlayerQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3010-3075) | 3010-3074 | Reconstruir cola (64 líneas) |
| [createShuffledQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/QueueStateHolder.kt#57-75) | 2936-2947 | Crear cola shuffled |
| [fisherYatesCopy()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2949-2962) | 2949-2961 | Algoritmo Fisher-Yates |
| [generateShuffleOrder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2963-2991) | 2963-2990 | Generar orden shuffle |
| [buildAnchoredShuffleQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3000-3009) | 3000-3008 | Cola shuffle anclada |
| [clearQueueExceptCurrent()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4763-4776) | 4763-4775 | Limpiar cola excepto actual |

### 5.3 Cast/Chromecast (~700 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [transferPlayback()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1579-1657) | 1579-1656 | Transferir a Cast (77 líneas) |
| [stopServerAndTransferBack()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1666-1951) | 1666-1950 | Transferir de vuelta (284 líneas!) |
| [onStatusUpdated()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1414-1576) | 1414-1575 | Callback de RemoteMediaClient (161 líneas!) |
| [selectRoute()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4777-4794) | 4777-4793 | Seleccionar ruta Cast |
| [disconnect()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4795-4818) | 4795-4817 | Desconectar Cast |
| [setRouteVolume()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4819-4823) | 4819-4822 | Volumen de ruta |
| [refreshCastRoutes()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4824-4844) | 4824-4843 | Refrescar rutas |
| [ensureHttpServerRunning()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4845-4904) | 4845-4903 | Servidor HTTP para Cast |
| `setupMediaRouterCallback()` | (init block) | Configurar MediaRouter |
| `onSession*` callbacks | 1658-1977 | Callbacks de Cast session |

### 5.4 Conectividad WiFi/Bluetooth (~300 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| `setupWifiStateObserver()` | (init block) | Observer de WiFi |
| `setupBluetoothStateObserver()` | (init block) | Observer de Bluetooth |
| [updateWifiInfo()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#995-1024) | 995-1023 | Actualizar info WiFi |
| [updateWifiRadioState()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1025-1033) | 1025-1032 | Estado de radio WiFi |
| [updateBluetoothName()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1034-1067) | 1034-1066 | Nombre Bluetooth |
| [updateBluetoothAudioDevices()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1068-1095) | 1068-1094 | Dispositivos audio BT |
| [refreshLocalConnectionInfo()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1100-1111) | 1100-1110 | Refrescar info conexión |
| Network/BT receivers | 1307-1382 | BroadcastReceivers |

### 5.5 Library/Data Loading (~500 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [loadInitialLibraryDataParallel()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2099-2194) | 2099-2193 | Carga inicial paralela (94 líneas) |
| [loadSongsFromRepository()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2208-2254) | 2208-2253 | Cargar canciones |
| [loadAlbumsFromRepository()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2255-2288) | 2255-2287 | Cargar álbumes |
| [loadArtistsFromRepository()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2320-2350) | 2320-2349 | Cargar artistas |
| [loadFoldersFromRepository()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2366-2394) | 2366-2393 | Cargar carpetas |
| [loadSongsIfNeeded()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2289-2304) | 2289-2303 | Carga lazy songs |
| [loadAlbumsIfNeeded()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2305-2319) | 2305-2318 | Carga lazy albums |
| [loadArtistsIfNeeded()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2351-2365) | 2351-2364 | Carga lazy artists |
| [resetAndLoadInitialData()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2195-2207) | 2195-2206 | Reset y recargar |
| [preloadThemesAndInitialData()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2074-2098) | 2074-2097 | Precarga de temas |

### 5.6 Search (~200 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [performSearch()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4606-4632) | 4606-4631 | Ejecutar búsqueda |
| [updateSearchFilter()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4582-4585) | 4582-4584 | Actualizar filtro |
| [loadSearchHistory()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4586-4594) | 4586-4593 | Cargar historial |
| [onSearchQuerySubmitted()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4595-4605) | 4595-4604 | Query enviado |
| [deleteSearchHistoryItem()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4633-4641) | 4633-4640 | Eliminar del historial |
| [clearSearchHistory()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4642-4650) | 4642-4649 | Limpiar historial |

### 5.7 Sorting (~200 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [sortSongs()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4408-4432) | 4408-4431 | Ordenar canciones |
| [sortAlbums()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4433-4456) | 4433-4455 | Ordenar álbumes |
| [sortArtists()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4457-4476) | 4457-4475 | Ordenar artistas |
| [sortFavoriteSongs()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4477-4487) | 4477-4486 | Ordenar favoritos |
| [sortFolders()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4488-4501) | 4488-4500 | Ordenar carpetas |
| [resolveSortOption()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#965-973) | 965-972 | Resolver opción |
| [showSortingSheet()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5220-5223) | 5220-5222 | Mostrar sheet |
| [hideSortingSheet()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5224-5227) | 5224-5226 | Ocultar sheet |

### 5.8 Sleep Timer (~250 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [setSleepTimer()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4917-5000) | 4917-4999 | Configurar timer (82 líneas) |
| [setEndOfTrackTimer()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5020-5066) | 5020-5065 | Timer fin de pista |
| [cancelSleepTimer()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5067-5102) | 5067-5101 | Cancelar timer |
| [playCounted()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5002-5010) | 5002-5009 | Reproducir N canciones |
| [cancelCountedPlay()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5011-5018) | 5011-5017 | Cancelar contador |

### 5.9 AI Features (~150 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [generateAiPlaylist()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4663-4717) | 4663-4716 | Generar playlist IA |
| [regenerateDailyMixWithPrompt()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4718-4762) | 4718-4761 | Regenerar mix con prompt |
| [generateAiMetadata()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5421-5424) | 5421-5423 | Generar metadata IA |
| [showAiPlaylistSheet()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4653-4656) | 4653-4655 | Mostrar sheet IA |
| [dismissAiPlaylistSheet()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4657-4662) | 4657-4661 | Cerrar sheet IA |

### 5.10 Lyrics (~200 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [loadLyricsForCurrentSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5279-5284) | 5279-5283 | Cargar letras |
| [fetchLyricsForCurrentSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5447-5509) | 5447-5508 | Buscar letras (61 líneas) |
| [searchLyricsManually()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5510-5536) | 5510-5535 | Búsqueda manual |
| [acceptLyricsSearchResultForCurrentSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5537-5551) | 5537-5550 | Aceptar resultado |
| [resetLyricsForCurrentSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5552-5560) | 5552-5559 | Resetear letras |
| [resetAllLyrics()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5561-5568) | 5561-5567 | Resetear todas |
| [importLyricsFromFile()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5569-5589) | 5569-5588 | Importar archivo |
| [resetLyricsSearchState()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5590-5596) | 5590-5595 | Reset estado búsqueda |
| [setLyricsSyncOffset()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#436-439) | 436-438 | Set offset sync |
| [observeCurrentSongLyricsOffset()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#440-449) | 440-448 | Observar offset |

### 5.11 Metadata Editing (~200 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [editSongMetadata()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5285-5387) | 5285-5386 | Editar metadata (101 líneas!) |
| [updateSongInStates()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5425-5446) | 5425-5445 | Actualizar estados |
| [selectSongForInfo()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5275-5278) | 5275-5277 | Seleccionar para info |
| [invalidateCoverArtCaches()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5388-5404) | 5388-5403 | Invalidar cache art |
| [purgeAlbumArtThemes()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5405-5420) | 5405-5419 | Purgar temas |

### 5.12 Color Scheme (~150 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [getAlbumColorSchemeFlow()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4029-4057) | 4029-4056 | Obtener flow esquema |
| [launchColorSchemeProcessor()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4058-4084) | 4058-4083 | Lanzar procesador |
| [getOrGenerateColorSchemeForUri()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4085-4107) | 4085-4106 | Obtener/generar esquema |
| [extractAndGenerateColorScheme()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4108-4137) | 4108-4136 | Extraer y generar |
| [mapColorSchemePairToEntity()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4138-4151) | 4138-4150 | Mapear a entidad |
| [mapEntityToColorSchemePair()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/ColorSchemeProcessor.kt#236-293) | 4152-4187 | Mapear desde entidad |
| [updateLavaLampColorsBasedOnActivePlayerScheme()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4189-4198) | 4189-4197 | Actualizar lava lamp |

### 5.13 External URI Playback (~350 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [playExternalUri()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3136-3179) | 3136-3178 | Reproducir URI externa |
| [buildExternalSongFromUri()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3294-3488) | 3294-3487 | Construir Song desde URI (193 líneas!) |
| [buildExternalQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3180-3198) | 3180-3197 | Construir cola externa |
| [loadAdditionalSongsFromFolder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3199-3287) | 3199-3286 | Cargar más de carpeta (87 líneas) |

### 5.14 Favorites (~50 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [toggleFavorite()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3851-3858) | 3851-3857 | Alternar favorito |
| [toggleFavoriteSpecificSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3859-3864) | 3859-3863 | Alternar favorito específico |

### 5.15 Navigation/UI State (~150 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [togglePlayerSheetState()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2585-2593) | 2585-2592 | Toggle sheet |
| [expandPlayerSheet()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2594-2598) | 2594-2597 | Expandir sheet |
| [collapsePlayerSheet()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2599-2603) | 2599-2602 | Colapsar sheet |
| [triggerArtistNavigationFromPlayer()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2604-2633) | 2604-2632 | Navegar a artista |
| [awaitSheetState()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2634-2637) | 2634-2636 | Esperar estado sheet |
| [awaitPlayerCollapse()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2638-2644) | 2638-2643 | Esperar colapso |
| [showPlayer()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3288-3293) | 3288-3292 | Mostrar player |
| [onLibraryTabSelected()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5228-5261) | 5228-5260 | Tab seleccionado |
| [saveLastLibraryTabIndex()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5214-5219) | 5214-5218 | Guardar índice |
| [saveLibraryTabsOrder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5262-5268) | 5262-5267 | Guardar orden tabs |
| [resetLibraryTabsOrder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5269-5274) | 5269-5273 | Reset orden |

### 5.16 Folder Navigation (~100 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [navigateToFolder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4509-4531) | 4509-4530 | Navegar a carpeta |
| [navigateBackFolder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4532-4547) | 4532-4546 | Navegar atrás |
| [findFolder()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4548-4562) | 4548-4561 | Buscar carpeta |
| [setFoldersPlaylistView()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4502-4508) | 4502-4507 | Set vista playlist |
| [setFoldersPlaylistViewState()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4563-4572) | 4563-4571 | Set estado vista |

### 5.17 Delete/Remove (~100 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [showMaterialDeleteConfirmation()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3905-3942) | 3905-3941 | Mostrar confirmación |
| [deleteFromDevice()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3943-3980) | 3943-3979 | Eliminar de dispositivo |
| [removeSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3981-3995) | 3981-3994 | Remover canción |
| [removeFromMediaControllerQueue()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3996-4027) | 3996-4026 | Remover de MediaController |

### 5.18 Dismiss/Undo (~100 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [dismissPlaylistAndShowUndo()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5103-5159) | 5103-5158 | Dismiss con undo |
| [hideDismissUndoBar()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5160-5171) | 5160-5170 | Ocultar barra |
| [undoDismissPlaylist()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5172-5207) | 5172-5206 | Deshacer dismiss |

### 5.19 Shortcuts/Quick Actions (~50 líneas)

| Función | Líneas | Descripción |
|---------|--------|-------------|
| [shuffleAllSongs()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#856-876) | 856-875 | Shuffle todas |
| [playRandomSong()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#877-883) | 877-882 | Reproducir random |
| [shuffleFavoriteSongs()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#884-904) | 884-903 | Shuffle favoritas |
| [shuffleRandomAlbum()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#905-915) | 905-914 | Shuffle álbum random |
| [shuffleRandomArtist()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#916-926) | 916-925 | Shuffle artista random |

---

## 6. Acoplamiento con UI Components

### Componentes que Dependen de PlayerViewModel

| Componente UI | Archivo | Dependencia |
|---------------|---------|-------------|
| `MainScreen` | MainScreen.kt | Acceso completo |
| `LibraryScreen` | LibraryScreen.kt | allSongs, albums, artists, sorting, search |
| `UnifiedPlayerSheet` | UnifiedPlayerSheet.kt | stablePlayerState, playerUiState, theming |
| `QueueBottomSheet` | QueueBottomSheet.kt | currentPlaybackQueue, reorder, remove |
| `CastBottomSheet` | CastBottomSheet.kt | castRoutes, selectedRoute, wifi/bt state |
| `SongInfoBottomSheet` | SongInfoBottomSheet.kt | selectedSongForInfo, metadata editing |
| `LyricsSheet` | LyricsSheet.kt | lyrics, syncOffset, search |
| `SleepTimerSheet` | SleepTimerSheet.kt | sleepTimer states |
| [AiPlaylistSheet](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#4653-4656) | AiPlaylistSheet.kt | aiPlaylist generation |
| [SortingSheet](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5220-5223) | SortingSheet.kt | sorting options |
| `MiniPlayer` | MiniPlayer.kt | stablePlayerState básico |

---

## 7. Problemas Arquitectónicos Identificados

### 7.1 🔴 Violación de Single Responsibility Principle

El ViewModel maneja **19+ responsabilidades distintas**:
1. Control de reproducción local
2. Control de reproducción remota (Cast)
3. Gestión de cola
4. Shuffle/Repeat
5. Estado de conectividad WiFi
6. Estado de Bluetooth
7. Búsqueda de música
8. Ordenamiento de biblioteca
9. Sleep Timer
10. Generación de playlists IA
11. Edición de metadata
12. Carga de letras
13. Generación de Color Schemes
14. Estadísticas de escucha
15. Daily/Your Mix
16. Favoritos
17. Navegación de carpetas
18. Navegación de UI (sheets)
19. Archivos externos

### 7.2 🔴 God Object Anti-Pattern

- **22 dependencias inyectadas**: Cada dependencia adicional aumenta complejidad
- **227 funciones públicas**: API surface extremadamente grande
- **50+ StateFlows**: Overhead de memoria significativo

### 7.3 🟠 Tight Coupling con System Services

Manejo directo de:
- `ConnectivityManager`
- `WifiManager`
- `BluetoothManager`
- `AudioManager`
- `AlarmManager`
- `MediaRouter`
- `CastContext`

### 7.4 🟠 Lifecycle Complexity

- 3 `BroadcastReceiver` registrados manualmente
- `AudioDeviceCallback` registrado
- `NetworkCallback` registrado
- `MediaRouter.Callback` registrado
- `SessionManagerListener` registrado
- `RemoteMediaClient.Callback` registrado
- `RemoteMediaClient.ProgressListener` registrado

### 7.5 🟠 State Explosion

[PlayerUiState](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#225-263) contiene demasiados campos no relacionados:
- `currentPosition`
- `currentPlaybackQueue`
- `albums`
- `artists`
- `searchResults`
- `musicFolders`
- `isSyncingLibrary`
- `showDismissUndoBar`
- `dismissedSong/Queue`
- etc.

### 7.6 🟡 Funciones Excesivamente Largas

| Función | Líneas | Problema |
|---------|--------|----------|
| [stopServerAndTransferBack()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1666-1951) | 284 | Lógica de transferencia Cast compleja |
| [buildExternalSongFromUri()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3294-3488) | 193 | Extracción de metadata externa |
| [setupMediaControllerListeners()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#2747-2935) | 187 | Configuración de listeners |
| [onStatusUpdated()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#1414-1576) | 161 | Callback RemoteMediaClient |
| [toggleShuffle()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#3685-3789) | 103 | Lógica shuffle con Edge cases |
| [editSongMetadata()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#5285-5387) | 101 | Edición completa de metadata |

---

## 8. Propuesta de Descomposición Modular

### Arquitectura Objetivo: Domain-Driven ViewModels

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │LibraryVM │  │ PlayerVM │  │ SheetsVM │  │SettingsVM│        │
│  │(reduced) │  │(reduced) │  │  (new)   │  │(existing)│        │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘        │
└───────┼─────────────┼─────────────┼─────────────┼───────────────┘
        │             │             │             │
┌───────┼─────────────┼─────────────┼─────────────┼───────────────┐
│       ▼             ▼             ▼             ▼               │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   State Holders Layer                    │    │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────┐ │    │
│  │  │Playback   │ │Library    │ │Cast       │ │Sleep     │ │    │
│  │  │StateHolder│ │StateHolder│ │StateHolder│ │TimerSH   │ │    │
│  │  └───────────┘ └───────────┘ └───────────┘ └──────────┘ │    │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────┐ │    │
│  │  │Lyrics     │ │Queue      │ │ColorScheme│ │AI        │ │    │
│  │  │StateHolder│ │StateHolder│ │Processor  │ │StateHold.│ │    │
│  │  └───────────┘ └───────────┘ └───────────┘ └──────────┘ │    │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐              │    │
│  │  │Connectivity│ │DailyMix  │ │Listening  │              │    │
│  │  │StateHolder │ │StateHold.│ │StatsTrack.│              │    │
│  │  └───────────┘ └───────────┘ └───────────┘              │    │
│  └─────────────────────────────────────────────────────────┘    │
│                          Domain Layer                            │
└──────────────────────────────────────────────────────────────────┘
```

### Nuevos StateHolders Propuestos

| StateHolder | Responsabilidad | Líneas Est. | Prioridad |
|-------------|-----------------|-------------|-----------|
| `PlaybackStateHolder` | Control reproducción, MediaController listeners, play/pause/seek/next/prev | ~400 | 🔴 Alta |
| `ConnectivityStateHolder` | WiFi, Bluetooth, Audio devices state | ~300 | 🟠 Media |
| `SleepTimerStateHolder` | Sleep timer, EOT timer, counted play | ~250 | 🟡 Baja |
| `LibraryStateHolder` | Songs/Albums/Artists loading, sorting | ~400 | 🟠 Media |
| `SearchStateHolder` | Search query, history, results | ~150 | 🟡 Baja |
| `ExternalMediaStateHolder` | URI externa, build song from file | ~350 | 🟡 Baja |
| `MetadataEditStateHolder` | Song metadata editing | ~200 | 🟡 Baja |
| `AiStateHolder` | AI playlist, AI metadata generation | ~150 | 🟡 Baja |
| `CastTransferStateHolder` | Lógica compleja de transferencia Cast | ~400 | 🟠 Media |

### Reducción Estimada de PlayerViewModel

| Estado | Líneas |
|--------|--------|
| **Actual** | 5,604 |
| **Después de extraer StateHolders adicionales** | ~1,200 |
| **Reducción** | ~78% |

---

## 9. Matriz de Impacto y Dependencias

### Dependencias entre Funcionalidades

```
                    ┌──────────────────┐
                    │ stablePlayerState│
                    │   currentSong    │
                    └────────┬─────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Lyrics Loading│    │ Color Scheme  │    │ Stats Tracker │
└───────────────┘    └───────────────┘    └───────────────┘
        
┌──────────────────┐         ┌──────────────────┐
│playerUiState.    │◄───────►│  LibraryScreen   │
│currentPlaybackQue│         │  QueueBottomSheet│
└──────────────────┘         └──────────────────┘

┌──────────────────┐         ┌──────────────────┐
│  Cast Features   │◄───────►│  HTTP Server     │
│  (CastStateHold.)│         │  (MediaFile)     │
└──────────────────┘         └──────────────────┘
```

### Riesgo de Breaking Changes

| Extracción | Riesgo | Reason |
|------------|--------|--------|
| PlaybackStateHolder | 🔴 Alto | Touch punto central |
| ConnectivityStateHolder | 🟢 Bajo | Aislado |
| SleepTimerStateHolder | 🟡 Medio | Interactúa con playback |
| LibraryStateHolder | 🟠 Medio-Alto | Muchos UI consumers |
| SearchStateHolder | 🟢 Bajo | Aislado |
| ExternalMediaStateHolder | 🟢 Bajo | Caso edge |
| MetadataEditStateHolder | 🟢 Bajo | Aislado |
| AiStateHolder | 🟢 Bajo | Aislado |
| CastTransferStateHolder | 🔴 Alto | Lógica compleja |

---

## 10. Recomendaciones de Migración

### Fase 1: Extracciones de Bajo Riesgo (Quick Wins)

1. **`ConnectivityStateHolder`** - WiFi/Bluetooth state managers
2. **`SleepTimerStateHolder`** - Timer completo
3. **`SearchStateHolder`** - Search y history
4. **`AiStateHolder`** - AI features

**Impacto**: Reducir ~900 líneas, bajo riesgo

### Fase 2: Extracciones de Medio Riesgo

1. **`LibraryStateHolder`** - Loading/sorting de canciones/albums/artists
2. **`MetadataEditStateHolder`** - Edición de metadata
3. **`ExternalMediaStateHolder`** - Archivos externos

**Impacto**: Reducir ~950 líneas, requiere testing extensivo

### Fase 3: Extracciones de Alto Riesgo

1. **`PlaybackStateHolder`** - MediaController, play/pause, progress
2. **`CastTransferStateHolder`** - Toda la lógica de transferencia Cast

**Impacto**: Reducir ~800 líneas, requiere refactoring cuidadoso

### Estrategia de Migración

1. **Crear interfaces**: Definir contratos antes de extraer
2. **Tests primero**: Escribir tests para funcionalidad existente
3. **Extracción incremental**: Un StateHolder a la vez
4. **Feature flags**: Poder revertir si hay issues
5. **Companion objects**: Mantener constantes relacionadas juntas

### Consideraciones Técnicas

1. **Inyección de dependencias**: Todos los StateHolders deben ser `@Singleton` inyectados por Hilt
2. **Scope management**: Usar `viewModelScope` pasado via [initialize()](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/DailyMixStateHolder.kt#47-53)
3. **State propagation**: Usar callbacks o SharedFlow para comunicación
4. **Testing**: Cada StateHolder debe ser unit-testeable independientemente

---

## Conclusión

El [PlayerViewModel](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt#282-5604) requiere una refactorización significativa para ser mantenible y escalable. La arquitectura propuesta de StateHolders modulares permitirá:

- **Reducir** el tamaño a ~1,200 líneas (78% reducción)
- **Mejorar** testabilidad con componentes aislados
- **Facilitar** adición de nuevas features sin afectar código existente
- **Optimizar** memoria al poder garbage-collect StateHolders no usados

La migración debe ser incremental, comenzando con componentes de bajo riesgo para validar el patrón antes de abordar las extracciones más complejas.
