# Optimization Analysis & Plan (PixelPlay)

Este documento detalla un plan exhaustivo para optimizar el rendimiento, uso de memoria y fluidez de la UI de PixelPlay. El análisis se centra en dispositivos de gama media/baja y optimización de recursos.

---

## 1. UI Rendering & Jetpack Compose Optimization
**Impacto Estimado:** Alto (Mejora drástica en scroll y animaciones - 30% a 50% menos "jank").

### A. Estabilidad de Clases de Datos (Critical)
El compilador de Compose considera inestables las interfaces de colección (`List`, `Map`, `Set`) si no se usan wrappers o librerías específicas.
*   **Problema:** En `Song.kt`, la propiedad `val artists: List<ArtistRef>` es considerada inestable. Esto causa que cualquier composable que reciba un objeto `Song` se recomponga innecesariamente incluso si la canción no ha cambiado.
*   **Solución:** Migrar todas las `List` en los modelos de datos (`Song`, `Album`, `Artist`) a `kotlinx.collections.immutable.ImmutableList`.
    *   **Archivo:** `app/src/main/java/com/theveloper/pixelplay/data/model/Song.kt`
    *   **Acción:** Cambiar `List<ArtistRef>` a `ImmutableList<ArtistRef>`.

### B. Optimización de Imágenes (`SmartImage.kt`)
*   **Problema:** `SmartImage` utiliza `SubcomposeAsyncImage`. Este componente es costoso porque interrumpe la composición para medir y renderizar sub-composables (el placeholder/error). En listas largas (`LazyColumn`), esto causa caídas de frames significativas.
*   **Solución:** Refactorizar `SmartImage` para usar `AsyncImage` estándar.
    *   **Acción:** Usar `placeholder(painterResource(...))` y `error(painterResource(...))` directamente en el `model` o en `AsyncImage`. Si se necesita un placeholder complejo, dibujarlo en un `Box` detrás y dejar que la imagen cargue encima con `crossfade`.
    *   **Beneficio:** Renderizado de listas mucho más ligero.

### C. UnifiedPlayerSheet & Animaciones
*   **Problema:** Hay múltiples `derivedStateOf` que dependen de animaciones corriendo en cada frame.
*   **Solución:**
    1.  Asegurar que todas las transformaciones de animación (alpha, translation, scale) ocurran estrictamente dentro de bloques `modifier.graphicsLayer {}` o `modifier.drawWithContent {}` para evitar la fase de recomposición y layout.
    2.  Verificar que `FullPlayerContent` no se esté recomponiendo innecesariamente. Usar `remember { ... }` para lambdas que se pasan a hijos.

---

## 2. Gestión de Memoria y Carga de Datos
**Impacto Estimado:** Medio/Alto (Reducción de RAM y Startup Time - 40%).

### A. Carga Inicial en `PlayerViewModel`
*   **Problema:** `loadInitialLibraryDataParallel` carga **toda** la biblioteca (Canciones, Álbumes, Artistas) en memoria al inicio. En bibliotecas grandes (5000+ canciones), esto bloquea el hilo principal momentáneamente y consume mucha RAM.
*   **Solución:** Implementar "Lazy Loading" basado en Tabs.
    *   Cargar solo la pestaña inicial (ej. Canciones).
    *   Cargar Álbumes y Artistas solo cuando el usuario navegue a esas pestañas (`onLibraryTabSelected`).
    *   **Network Spam:** La carga de artistas dispara una corrutina para buscar imágenes en Deezer para *cada artista sin imagen*. Esto debe ser paginado o hecho bajo demanda al visualizar el artista, no en loop masivo al inicio.

### B. Listas Inmutables en ViewModel
*   **Problema:** `sortSongs` crea una nueva lista completa cada vez que se ordena.
*   **Solución:**
    1.  Ejecutar ordenamientos en `Dispatchers.Default`.
    2.  Cachear listas ordenadas si la lista fuente (`_masterAllSongs`) no ha cambiado.

---

## 3. Eficiencia en Background (SyncWorker)
**Impacto Estimado:** Alto (Menor consumo de batería y disco).

### A. Escaneo de Archivos Innecesario
*   **Problema:** `triggerMediaScanForNewFiles` recorre manualmente el sistema de archivos (`walkTopDown`) en las carpetas de Música y Descargas en cada sincronización incremental. Esto es IO-blocking y lento.
*   **Solución:**
    *   Eliminar el escaneo manual por defecto. Confiar en el `MediaStore` de Android que se actualiza automáticamente.
    *   Mover el "Escaneo profundo" a una opción explícita en Configuración -> "Rescan Library".

### B. Operaciones en Batch
*   **Optimización:** El `SyncWorker` ya usa transacciones por lotes (chunks de 500), lo cual es bueno. Mantener esta lógica.

---

## 4. Plan de Ejecución (Paso a Paso)

### Paso 1: Dependencias y Modelos (Base)
1.  Asegurar que `kotlinx-collections-immutable` esté implementado.
2.  Refactorizar `Song.kt`, `Album.kt`, `Artist.kt` para usar `ImmutableList`.
3.  Actualizar Mappers en `MusicRepository` para retornar tipos inmutables.

### Paso 2: Optimización de UI Core
1.  Reescribir `SmartImage.kt` para eliminar `SubcomposeAsyncImage`.
2.  Auditar `SongListItem` (y componentes de lista) para asegurar que reciben parámetros estables y usan `key` en `LazyColumn`.

### Paso 3: Optimización del ViewModel
1.  Modificar `PlayerViewModel` para cargar datos de forma perezosa (Lazy Loading).
2.  Optimizar la lógica de fetch de imágenes de artistas (hacerla on-demand o en background con baja prioridad y rate-limit).
3.  Mover lógica de ordenamiento (`sortSongs`) a `Dispatchers.Default`.

### Paso 4: Optimización de Background
1.  Modificar `SyncWorker` para saltar el `walkTopDown` en modo incremental.

---

## Estimaciones de Mejora

| Área | Ganancia Esperada | Métrica |
|------|-------------------|---------|
| **Scroll en Listas** | 30-50% | FPS estables (60/90/120Hz) |
| **Inicio de App** | 40% | Tiempo hasta interactividad (TTI) |
| **Uso de RAM** | 20% | Menor retención de bitmaps/listas duplicadas |
| **Sincronización** | 80% | Velocidad de sync incremental |

Este plan cubre las optimizaciones más críticas detectadas en el análisis del código fuente.
