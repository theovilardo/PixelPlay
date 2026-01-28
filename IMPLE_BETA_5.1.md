# PixelPlay Beta 5.1 - Implementation Plan
## Estabilidad, Rendimiento y Correcciones Críticas

**Fecha**: Enero 2026  
**Objetivo**: Resolver los problemas más críticos que afectan la estabilidad, rendimiento y experiencia básica del usuario.

---

## 🎯 Resumen Ejecutivo

Beta 5.1 se enfoca en **estabilidad ante todo**. Antes de agregar nuevas características, debemos asegurar que la app funcione de manera confiable. Este release aborda:

- **Crashes y problemas de estabilidad**
- **Issues de playback y audio**
- **Conectividad (Cast, Bluetooth)**
- **Optimizaciones de performance fundamentales**
- **Correcciones de UI prioritarias**

> [!IMPORTANT]
> **Principio Core**: Todas las implementaciones deben ser más eficientes que el código actual. Usar profiling antes/después de cada cambio significativo.

---

## 📋 Prerequisitos

Antes de comenzar, asegurar:
- [ ] Baseline de benchmarks establecido (ver `pixelplayer-benchmark-guide.md`)
- [ ] Build de release funcionando sin errores
- [ ] Dispositivos de testing disponibles (gama baja, media, alta)

---

## Fase 1: Estabilidad Crítica

### 1.1 Fix: Crash al editar metadata de canciones

**Prioridad**: 🔴 Crítica  
**Ubicación**: [SongMetadataEditor.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/media/SongMetadataEditor.kt)

**Problema**:
Editar metadata de canciones puede causar crashes bajo condiciones indeterminadas.

**Análisis requerido**:
1. Revisar `editSongMetadata()` para identificar operaciones sin manejo de errores
2. Verificar acceso a archivos con permisos insuficientes
3. Identificar casos de archivos FLAC problemáticos (relacionado con 1.2)

**Implementación**:
```kotlin
// Envolver cada operación crítica en try-catch específicos
suspend fun editSongMetadata(...): SongMetadataEditResult {
    return try {
        // Validar inputs primero
        validateInputs(newTitle, newArtist, newAlbum)
        
        // Verificar permisos de archivo
        val file = File(filePath)
        if (!file.canWrite()) {
            return SongMetadataEditResult.Error("No write permission")
        }
        
        // Operación de edición con timeout
        withTimeoutOrNull(30_000L) {
            updateFileMetadataWithTagLib(...)
        } ?: return SongMetadataEditResult.Error("Timeout editing file")
        
        SongMetadataEditResult.Success
    } catch (e: TaglibCrashException) {
        Timber.e(e, "TagLib crash for file: $filePath")
        SongMetadataEditResult.Error("Unsupported file format")
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error editing metadata")
        SongMetadataEditResult.Error(e.localizedMessage ?: "Unknown error")
    }
}
```

**Verificación**:
- [ ] Testear con archivos MP3, FLAC, OGG, OPUS
- [ ] Testear con archivos en SDCard vs almacenamiento interno
- [ ] Testear con caracteres especiales en metadata
- [ ] Verificar que no hay crashes en Crashlytics después del fix

---

### 1.2 Fix: Problemas con archivos FLAC

**Prioridad**: 🔴 Crítica  
**Ubicación**: [SongMetadataEditor.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/media/SongMetadataEditor.kt), [SyncWorker.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/sync/SyncWorker.kt)

**Problema**:
Algunos tipos de archivos FLAC causan problemas en la app.

**Investigación**:
1. Identificar tipos específicos de FLAC problemáticos:
   - Bitdepth (16, 24, 32 bit)
   - Sample rate (44.1kHz, 48kHz, 96kHz, etc.)
   - FLAC con cover art embebido de gran tamaño

**Implementación**:
```kotlin
// Agregar detección y manejo especial para FLAC
private fun isProblematicFlac(file: File): Boolean {
    // Check for known problematic patterns
    return try {
        val tag = AudioFileIO.read(file)
        val audioHeader = tag.audioHeader
        // Check for high sample rates or unusual configurations
        audioHeader.sampleRate > 96000 || audioHeader.bitsPerSample > 24
    } catch (e: Exception) {
        Timber.w("Could not analyze FLAC: ${file.name}")
        true // Treat as potentially problematic
    }
}

// En editSongMetadata, usar fallback para FLAC problemáticos
if (isFlacFile(filePath) && isProblematicFlac(File(filePath))) {
    return updateMetadataWithFallbackMethod(...)
}
```

**Verificación**:
- [ ] Colección de archivos FLAC de prueba con diferentes configuraciones
- [ ] Testear escaneo, reproducción y edición de cada tipo

---

### 1.3 Fix: Fallo al cargar música en primer setup

**Prioridad**: 🔴 Crítica  
**Ubicación**: [SetupScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/SetupScreen.kt), [SyncWorker.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/sync/SyncWorker.kt)

**Problema**:
A veces el escaneo inicial de música falla silenciosamente.

**Implementación**:
```kotlin
// Mejorar robustez del proceso de inicialización
class SetupViewModel {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    
    fun startInitialSync() {
        viewModelScope.launch {
            _syncState.value = SyncState.Starting
            
            var retryCount = 0
            val maxRetries = 3
            
            while (retryCount < maxRetries) {
                try {
                    val result = syncManager.runInitialSync()
                    if (result.success && result.songsFound > 0) {
                        _syncState.value = SyncState.Success(result.songsFound)
                        return@launch
                    } else if (result.songsFound == 0) {
                        _syncState.value = SyncState.Empty
                        return@launch
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Initial sync failed, attempt ${retryCount + 1}")
                    retryCount++
                    delay(1000L * retryCount) // Exponential backoff
                }
            }
            
            _syncState.value = SyncState.Failed("Could not scan music library")
        }
    }
}
```

**UI Feedback**:
- Mostrar indicador de progreso claro durante escaneo
- Si falla, mostrar mensaje con opción de reintentar
- Agregar logs detallados para debugging

---

## Fase 2: Playback y Audio

### 2.1 Fix: Reproducción se traba en Daily Mix

**Prioridad**: 🟠 Alta  
**Ubicación**: [PlayerViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt), [DailyMixStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/DailyMixStateHolder.kt)

**Problema**:
El playback se queda trabado al reproducir desde Daily Mix.

**Causas potenciales a investigar**:
1. Songs inválidas en la cola (archivos movidos/eliminados)
2. Condición de carrera al actualizar la cola
3. Issue con el motor de reproducción dual (crossfade)

**Implementación**:
```kotlin
// En playDailyMix o cuando se carga desde Daily Mix
fun playFromDailyMix(songs: List<Song>, startIndex: Int = 0) {
    viewModelScope.launch {
        // 1. Validar que los archivos existen
        val validSongs = songs.filter { song ->
            val file = File(song.path)
            if (!file.exists()) {
                Timber.w("Song file missing: ${song.path}")
                false
            } else true
        }
        
        if (validSongs.isEmpty()) {
            sendToast("No valid songs in Daily Mix")
            return@launch
        }
        
        // 2. Ajustar startIndex si hubo filtrado
        val adjustedIndex = songs.take(startIndex + 1)
            .count { validSongs.contains(it) } - 1
        
        // 3. Reproducir con queue validada
        playQueue(
            songs = validSongs,
            startIndex = adjustedIndex.coerceAtLeast(0),
            queueName = "DailyMix"
        )
    }
}
```

**Verificación**:
- [ ] Testear con Daily Mix que contiene canciones eliminadas
- [ ] Testear transición entre canciones con crossfade activado
- [ ] Monitorear por 30+ minutos de reproducción continua

---

### 2.2 Fix: Issues del Ecualizador (Virtualizer, Crossfade)

**Prioridad**: 🟠 Alta  
**Ubicación**: [EqualizerScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/EqualizerScreen.kt), [DualPlayerEngine.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt)

**Problema**:
- Virtualizer a veces pausa la reproducción
- Crossfade puede causar conflictos con efectos de audio

**Análisis**:
```kotlin
// Revisar orden de aplicación de efectos
// El virtualizer debe aplicarse DESPUÉS de que el buffer esté listo

// Implementar mutex para cambios de efectos durante crossfade
private val effectChangeLock = Mutex()

suspend fun applyVirtualizer(strength: Int) {
    effectChangeLock.withLock {
        if (isCrossfadeInProgress) {
            // Defer effect change until crossfade completes
            pendingEffectChanges.add { setVirtualizerStrength(strength) }
            return
        }
        setVirtualizerStrength(strength)
    }
}
```

**Verificación**:
- [ ] Testear cambios de EQ durante crossfade
- [ ] Testear virtualizer con diferentes dispositivos de audio
- [ ] Verificar que no hay pausas inesperadas

---

### 2.3 Fix: Glitch al cambiar shuffle

**Prioridad**: 🟡 Media  
**Ubicación**: [PlayerViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt)

**Problema**:
Al cambiar el modo shuffle durante la reproducción, hay un pequeño glitch audible.

**Implementación**:
```kotlin
// Cambiar shuffle sin interrumpir playback
fun toggleShuffle() {
    val currentMediaItem = player.currentMediaItem ?: return
    val currentPosition = player.currentPosition
    
    // 1. Actualizar estado de shuffle
    _shuffleEnabled.update { !it }
    
    // 2. Regenerar cola sin tocar el player
    viewModelScope.launch(Dispatchers.Default) {
        val newQueue = if (_shuffleEnabled.value) {
            currentQueue.shuffled().toMutableList().apply {
                // Mover canción actual al inicio
                remove(currentMediaItem)
                add(0, currentMediaItem)
            }
        } else {
            originalQueue.toMutableList()
        }
        
        // 3. Actualizar cola internamente sin rebuild del MediaSource
        withContext(Dispatchers.Main) {
            updateQueueInternally(newQueue, preservePosition = true)
        }
    }
}
```

---

## Fase 3: Conectividad

### 3.1 Fix: Native Cast no funciona

**Prioridad**: 🟠 Alta  
**Ubicación**: [CastPlayer.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/service/player/CastPlayer.kt), [CastBottomSheet.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/CastBottomSheet.kt)

**Problema**:
Google Cast funciona parcialmente, pero Native Cast (DLNA/UPnP) tiene issues.

**Implementación**:
1. Verificar que `MediaRouteProvider` está correctamente configurado
2. Revisar compatibilidad con protocolo DLNA
3. Agregar logging detallado para diagnosticar

```kotlin
// Mejorar detección y filtrado de rutas
fun getAvailableRoutes(): List<MediaRouterRoute> {
    return mediaRouter.routes.mapNotNull { route ->
        when {
            route.isDefaultOrBluetooth -> null // Skip default routes
            route.isBluetooth -> RouteInfo(route, RouteType.BLUETOOTH)
            route.supportsControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO) ->
                RouteInfo(route, RouteType.NATIVE_CAST)
            route.supportsControlCategory(CastMediaControlIntent.categoryForCast()) ->
                RouteInfo(route, RouteType.GOOGLE_CAST)
            else -> null
        }
    }
}
```

---

### 3.2 Fix: Notificación de Cast innecesaria

**Prioridad**: 🟡 Media  
**Ubicación**: [MusicNotificationProvider.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/service/MusicNotificationProvider.kt)

**Problema**:
La notificación de Cast aparece cuando no está conectado o en estados similares incorrectos.

**Implementación**:
```kotlin
// Solo mostrar notificación de Cast cuando realmente está conectado y reprodiciendo
private fun shouldShowCastNotification(): Boolean {
    val castState = castStateHolder.castState.value
    return castState is CastState.Connected && 
           castState.isPlaying &&
           castState.currentRoute != null
}

// En updateNotification()
fun updateNotification() {
    val showCastBadge = shouldShowCastNotification()
    // ... usar showCastBadge para agregar/remover indicador de Cast
}
```

---

### 3.3 Fix: Desconexiones aleatorias de Bluetooth

**Prioridad**: 🟡 Media  
**Ubicación**: [PlayerViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt) (BroadcastReceivers)

**Problema**:
Bluetooth se desconecta aleatoriamente por unos segundos durante la reproducción.

**Implementación**:
```kotlin
// Mejorar manejo de eventos Bluetooth
private val bluetoothStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                Timber.d("Bluetooth disconnected: ${device?.name}")
                
                // Intentar reconexión automática después de un delay
                viewModelScope.launch {
                    delay(2000) // Wait for potential reconnection
                    if (!isBluetoothDeviceConnected(device)) {
                        // Pause playback only if still disconnected
                        pausePlayback()
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // Resume if was paused due to disconnect
                if (wasAutoPaused) {
                    resumePlayback()
                }
            }
        }
    }
}
```

---

## Fase 4: Optimización de Rendimiento

### 4.1 Fix: Deshabilitar Queue History por defecto

**Prioridad**: 🟢 Baja  
**Ubicación**: [UserPreferencesRepository.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt)

**Problema**:
Queue history está habilitado por defecto, consumiendo recursos innecesarios.

**Implementación**:
```kotlin
// Cambiar valor por defecto
val showQueueHistoryFlow: Flow<Boolean> =
    dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_QUEUE_HISTORY] ?: false // Era true
    }
```

---

### 4.2 Fix: Daily Mix no se actualiza al forzar desde Settings

**Prioridad**: 🟠 Alta  
**Ubicación**: [DailyMixStateHolder.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/DailyMixStateHolder.kt), [SettingsViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/SettingsViewModel.kt)

**Problema**:
La actualización forzada del Daily Mix desde Settings no funciona.

**Implementación**:
```kotlin
// En DailyMixStateHolder
fun forceUpdate(allSongsFlow: Flow<List<Song>>, favoriteSongIdsFlow: Flow<Set<String>>) {
    scope.launch {
        _isUpdating.value = true
        try {
            // Limpiar cache actual
            _dailyMixSongs.value = persistentListOf()
            
            // Forzar regeneración
            val allSongs = allSongsFlow.first()
            val favoriteIds = favoriteSongIdsFlow.first()
            
            val newMix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
            _dailyMixSongs.value = newMix.toPersistentList()
            
            // Guardar timestamp
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            
            Timber.d("Daily Mix force updated with ${newMix.size} songs")
        } finally {
            _isUpdating.value = false
        }
    }
}
```

---

### 4.3 Actualización automática de Stats (eficiente)

**Prioridad**: 🟠 Alta  
**Ubicación**: [StatsViewModel.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/StatsViewModel.kt), [PlaybackStatsRepository.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/data/stats/PlaybackStatsRepository.kt)

**Problema**:
Stats no se actualiza automáticamente y la actualización manual es ineficiente.

**Implementación**:
```kotlin
// Usar invalidación basada en eventos en lugar de polling
class PlaybackStatsRepository {
    private val _statsInvalidated = MutableSharedFlow<Unit>(replay = 0)
    val statsInvalidated: SharedFlow<Unit> = _statsInvalidated.asSharedFlow()
    
    suspend fun recordPlayback(...) {
        // ... existing logic ...
        
        // Notificar que stats cambió
        _statsInvalidated.emit(Unit)
    }
}

// En StatsViewModel
init {
    // Recargar stats cuando hay nuevos datos
    viewModelScope.launch {
        playbackStatsRepository.statsInvalidated
            .debounce(5000) // Agrupar updates
            .collect {
                refreshStats()
            }
    }
}
```

---

## Fase 5: UI/UX Prioritarias

### 5.1 Fix: Skeleton loading de Library Tab

**Prioridad**: 🟡 Media  
**Ubicación**: [LibraryScreen.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt#L1919-L1940)

**Problema**:
El skeleton loading de la Library Tab se ve mal diseñado.

**Implementación**:
```kotlin
@Composable
fun SkeletonSongItem(modifier: Modifier = Modifier) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(AbsoluteSmoothCornerShape(12.dp, 60))
                .background(shimmerColor)
                .shimmerEffect()
        )
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // Title placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
                    .shimmerEffect()
            )
            Spacer(Modifier.height(4.dp))
            // Artist placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
                    .shimmerEffect()
            )
        }
    }
}
```

---

### 5.2 Fix: Placeholders de controles de reproducción (5 → 3 botones)

**Prioridad**: 🟡 Media  
**Ubicación**: [UnifiedPlayerSheet.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt)

**Problema**:
El placeholder de los controles de reproducción muestra 5 botones cuando deberían ser 3.

**Implementación**:
```kotlin
@Composable
fun PlayerControlsPlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Solo 3 botones: Previous, Play/Pause, Next
        repeat(3) { index ->
            val size = if (index == 1) 64.dp else 48.dp // Play button bigger
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            if (index < 2) {
                Spacer(Modifier.width(24.dp))
            }
        }
    }
}
```

---

### 5.3 Fix: Highlight de canciones repetidas en Queue

**Prioridad**: 🟡 Media  
**Ubicación**: [QueueBottomSheet.kt](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/java/com/theveloper/pixelplay/presentation/components/QueueBottomSheet.kt#L250-L260)

**Problema**:
Cuando hay canciones repetidas en la cola, todas las instancias muestran el highlight en lugar de solo la que se está reproduciendo actualmente.

**Implementación**:
```kotlin
// Usar índice de cola en lugar de song ID para determinar highlight
items(
    items = displayQueue,
    key = { item -> "${item.queueIndex}_${item.song.id}" }
) { queueItem ->
    val isCurrentlyPlaying = queueItem.queueIndex == currentSongIndex
    
    QueueSongItem(
        song = queueItem.song,
        isPlaying = isCurrentlyPlaying, // Solo true para el índice actual
        // ...
    )
}
```

---

### 5.4 Añadir Intent Filters en AndroidManifest

**Prioridad**: 🟠 Alta  
**Ubicación**: [AndroidManifest.xml](file:///Users/theo/AndroidStudioProjects/PixelPlay/app/src/main/AndroidManifest.xml#L87-L97)

**Problema**:
Falta el intent filter para `android.intent.action.MEDIA_BUTTON`, lo que impide que la app sea reconocida como reproductor de medios en Routines y apps como Tasker.

**Implementación**:
```xml
<service
    android:name=".data.service.MusicService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="android.media.browse.MediaBrowserService" />
        <action android:name="androidx.media3.session.MediaSessionService"/>
        <!-- AGREGAR -->
        <action android:name="android.intent.action.MEDIA_BUTTON" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.APP_MUSIC" />
    </intent-filter>
</service>

<!-- También añadir receiver para media buttons -->
<receiver
    android:name=".data.service.MediaButtonReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MEDIA_BUTTON" />
    </intent-filter>
</receiver>
```

---

## Verificación Plan

### Automated Tests
```bash
# Run unit tests
./gradlew :app:testDebugUnitTest

# Run connected tests
./gradlew :app:connectedDebugAndroidTest

# Run lint checks
./gradlew :app:lintDebug
```

### Manual Verification Checklist

- [ ] **Estabilidad**
  - [ ] Editar metadata de 10 canciones diferentes (MP3, FLAC, OGG)
  - [ ] Instalar app limpia y completar setup inicial
  - [ ] Verificar que no hay crashes en LogCat durante 1 hora de uso

- [ ] **Playback**
  - [ ] Reproducir Daily Mix completo sin interrupciones
  - [ ] Cambiar EQ y Virtualizer durante crossfade
  - [ ] Toggle shuffle múltiples veces durante reproducción

- [ ] **Conectividad**
  - [ ] Conectar a Chromecast y reproducir 5 canciones
  - [ ] Conectar a dispositivo Bluetooth y reproducir 30 minutos
  - [ ] Verificar que notificación de Cast no aparece sin conexión

- [ ] **Performance**
  - [ ] Library con 1000+ canciones carga en < 2 segundos
  - [ ] Scroll en Library mantiene 60 FPS
  - [ ] No hay jank visible en animaciones

---

## Notas de Implementación

> [!TIP]
> **Orden recomendado**: Implementar en orden de fases. No avanzar a la siguiente fase sin verificar la anterior.

> [!WARNING]
> **Breaking Changes**: Los cambios en `UserPreferencesRepository` (queue history default) afectan a usuarios existentes. Considerar migración.

---

## Métricas de Éxito

| Métrica | Objetivo |
|---------|----------|
| Crashes/día | 0 (relacionados con issues listados) |
| Cold start time | < 2 segundos |
| Memory usage (idle) | < 100MB |
| Frame rate (scroll) | 60 FPS consistente |
| Battery drain (playback) | < 5%/hora |

---

## Próximos Pasos (Beta 5.2)

Las siguientes tareas se abordarán en Beta 5.2:
- Rediseño de GenreDetailScreen (Material 3 Expressive)
- Sistema de escalado de Album Art configurable
- Reimplementación de AI para playlists y Daily Mix
- Mejoras de SplashScreen y animaciones
- Nuevo sistema de créditos en AboutScreen
- Y más...

Ver [IMPLE_BETA_5.2.md](file:///Users/theo/AndroidStudioProjects/PixelPlay/IMPLE_BETA_5.2.md) para detalles.
