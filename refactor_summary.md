# Refactorización del Reproductor de Música con Media3

A continuación se presenta un resumen de la refactorización llevada a cabo para modernizar el reproductor de música de la aplicación, optimizar su rendimiento y solucionar problemas de consumo de recursos.

## 1. Plan de Refactorización

El plan se centró en los siguientes puntos clave:

1.  **Migración a `MediaSessionService`**: Convertir el servicio de música en un `MediaSessionService` de Media3 para delegar la gestión de notificaciones y el ciclo de vida en primer plano.
2.  **Optimización del Ciclo de Vida**: Asegurar que el servicio solo se ejecute cuando sea estrictamente necesario, deteniéndose automáticamente tras un período de inactividad.
3.  **Actualización Eficiente de Widgets**: Mantener y refinar el sistema de actualización de widgets basado en eventos para eliminar cualquier tipo de sondeo (polling) y evitar ANRs.
4.  **Notificación `MediaStyle` Estándar**: Utilizar la notificación nativa de Media3 en lugar de una personalizada.

---

## 2. Ejemplos de Código y Cambios Aplicados

### a. `AndroidManifest.xml`: Declaración del Servicio

La declaración del servicio en el manifiesto es crucial. Ya estaba correctamente configurado para `MediaSessionService`, lo cual es una excelente base.

**`app/src/main/AndroidManifest.xml`**
```xml
<application ...>
    ...
    <service
        android:name=".data.service.MusicService"
        android:exported="true"
        android:foregroundServiceType="mediaPlayback">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaSessionService"/>
        </intent-filter>
    </service>
    ...
</application>
```
*   **Explicación**:
    *   `android:name=".data.service.MusicService"` apunta a nuestra clase de servicio.
    *   `android:foregroundServiceType="mediaPlayback"` es obligatorio para servicios que reproducen multimedia.
    *   El `intent-filter` con la acción `androidx.media3.session.MediaSessionService` permite que los controladores de medios (como el sistema Android, Android Auto, etc.) descubran y se conecten a nuestro servicio.

### b. `MusicService.kt`: El Corazón del Reproductor

Esta fue el área con los cambios más significativos.

**Código Refactorizado de `MusicService.kt` (Fragmentos Clave)**

```kotlin
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stopServiceJob: Job? = null
    private val STOP_DELAY = 30000L // 30 segundos

    // 1. onCreate simplificado
    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeMediaSession()
    }

    // 2. Conexión ExoPlayer <-> MediaSession
    private fun initializePlayer() {
        // ... configuración de AudioAttributes ...
        exoPlayer.addListener(playerListener)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(/* ... PendingIntent a la UI ... */)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 3. Gestión del ciclo de vida
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // ... manejo de intents ...
        return START_NOT_STICKY // El servicio no se recrea si el sistema lo mata
    }

    // 4. Listener para actualización de widgets y ciclo de vida
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            requestWidgetFullUpdate() // Actualiza el widget en cambio de estado

            if (!isPlaying) {
                // Programa la detención del servicio si la música se pausa
                stopServiceJob = serviceScope.launch {
                    delay(STOP_DELAY)
                    stopSelf() // Detiene el servicio
                }
            } else {
                // Cancela la detención si la música se reanuda
                stopServiceJob?.cancel()
            }
        }
        // ... otros overrides (onMediaItemTransition, etc.) ...
    }

    override fun onDestroy() {
        mediaSession?.release()
        exoPlayer.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

*   **Explicación de Cambios y Beneficios**:
    1.  **`onCreate` Limpio**: Se eliminó toda la gestión manual de notificaciones (`createNotificationChannel`, `startForeground`). **Beneficio**: `MediaSessionService` ahora gestiona la notificación `MediaStyle` automáticamente. Se inicia en primer plano solo cuando `exoPlayer.play()` es llamado y se detiene cuando la reproducción cesa. Esto es más eficiente y sigue las guías de Android.
    2.  **Conexión `ExoPlayer` y `MediaSession`**: El `MediaSession` se construye con la instancia de `ExoPlayer`. **Beneficio**: `MediaSession` actúa como un puente. Traduce los comandos de los controladores de medios (UI, notificación, sistema) en acciones sobre `ExoPlayer` y viceversa, manteniendo todo sincronizado.
    3.  **Ciclo de Vida `START_NOT_STICKY`**: El servicio ya no es "pegajoso". **Beneficio**: Evita que el servicio se ejecute indefinidamente en segundo plano. Solo se ejecutará cuando un componente (UI, widget) lo inicie.
    4.  **Listener Inteligente**: El `playerListener` es el núcleo de la optimización.
        *   **Actualización de Widgets**: Llama a `requestWidgetFullUpdate()` solo en eventos clave (`onIsPlayingChanged`, `onMediaItemTransition`). **Beneficio**: Elimina completamente el *polling*. La CPU solo trabaja cuando es necesario, evitando lag y consumo de batería. Esto previene ANRs causados por actualizaciones de UI demasiado frecuentes.
        *   **Autodestrucción del Servicio**: Usa una corrutina con `delay` para llamar a `stopSelf()`. **Beneficio**: El servicio no se detiene instantáneamente al pausar, permitiendo al usuario reanudar rápidamente. Si la inactividad persiste, el servicio se detiene para liberar memoria y recursos.

### c. Configuración de Notificación `MediaStyle` Automática

No se necesita código explícito para esto. Al hacer lo siguiente, Media3 se encarga de todo:
1.  Heredar de `MediaSessionService`.
2.  Conectar un `MediaSession` al `ExoPlayer`.
3.  Poner metadatos (título, artista, carátula) en los `MediaItem` que se añaden a `ExoPlayer`.

**Beneficio**: Se obtiene una notificación estándar, reconocida por los usuarios y compatible con todo el ecosistema Android (relojes, Auto, Asistente) sin escribir una sola línea de código de UI para la notificación.

---

## 3. Prevención de ANRs y Optimización

*   **ANR por Actualización de Widgets**: El problema se resuelve al cambiar de un modelo de *polling* (actualización cada X segundos) a un **modelo basado en eventos**. El `Player.Listener` asegura que el código de actualización del widget solo se ejecute cuando el estado de la reproducción cambia. El uso de `debounce` en `requestWidgetFullUpdate` agrupa cambios rápidos (ej. saltar varias canciones rápidamente) en una sola actualización, reduciendo aún más la carga.

*   **ANR por Carga en Hilo Principal**: La carga de carátulas (`loadBitmapDataFromUri`) y las operaciones de actualización de Glance (`updateAppWidgetState`) se ejecutan explícitamente en `Dispatchers.IO` o `Dispatchers.Default` dentro de corrutinas, liberando el hilo principal para que la UI de la app permanezca fluida.

*   **Consumo de CPU y Batería**: La combinación de un ciclo de vida de servicio no-pegajoso y la autodestrucción inteligente asegura que el servicio no consuma recursos cuando no está reproduciendo música activamente. Este es el cambio más importante para la eficiencia a largo plazo.
