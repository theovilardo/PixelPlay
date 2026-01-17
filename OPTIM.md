# OPTIM.md — Plan de optimización integral de PixelPlayer

## 0) Objetivo
Este documento propone un plan exhaustivo para optimizar **tiempo de ejecución, uso de RAM/CPU, IO y fluidez UI** en dispositivos de gama baja/media y gama alta antigua. Las acciones están priorizadas y cada una incluye un **estimado de impacto** en rendimiento real o percibido.

> **Nota de alcance:** El análisis se fundamenta en el comportamiento observado y en puntos de presión claros en el código actual (sin asumir cambios externos). Se citan archivos relevantes para justificar el diagnóstico.

---

## 1) Diagnóstico basado en código actual (principales focos de costo)

### 1.1 Sincronización/escaneo de biblioteca (IO + CPU + RAM)
* El `SyncWorker` carga listas completas y hace procesamiento en memoria (por ejemplo `getAllSongsList`, construcción de mapas y transformación de listas) antes de insertar/actualizar, lo que **aumenta RAM y tiempo de CPU** con librerías grandes (miles de canciones).【F:app/src/main/java/com/theveloper/pixelplay/data/worker/SyncWorker.kt†L170-L317】
* La deduplicación y el cálculo de artistas dependen de listas completas y estructuras auxiliares, lo que eleva **uso de memoria** en sincronizaciones grandes y repetidas.【F:app/src/main/java/com/theveloper/pixelplay/data/worker/SyncWorker.kt†L348-L520】
* El escaneo opcional de letras LRC lee archivos por cada canción sin lyrics, en paralelo (8 hilos), lo que es costoso en IO y puede provocar **stalls** en dispositivos lentos si se ejecuta de golpe sobre muchas canciones.【F:app/src/main/java/com/theveloper/pixelplay/data/repository/LyricsRepositoryImpl.kt†L709-L789】

### 1.2 Búsqueda y consultas SQL (CPU + latencia)
* Las búsquedas usan `LIKE '%query%'` en columnas de `songs`, `albums`, `artists`, lo que degrada a **full table scan** en colecciones grandes si no hay índices adecuados (y el wildcard inicial invalida índices clásicos).【F:app/src/main/java/com/theveloper/pixelplay/data/database/MusicDao.kt†L114-L210】
* `MusicRepositoryImpl` combina flujos y re-mapea entidades a modelos en cada emisión, con operaciones como `map`, `toSet`, `groupBy` y `associateBy`, lo cual es costoso cuando cambian muchos elementos y se vuelve a procesar en bloque.【F:app/src/main/java/com/theveloper/pixelplay/data/repository/MusicRepositoryImpl.kt†L104-L184】

### 1.3 UI: sorting y trabajo pesado en Composables (jank)
* En `LibraryScreen`, se ejecutan `sortedBy`/`sortedByDescending` dentro de Composables (aunque dentro de `remember`), lo que puede ser costoso cuando hay listas grandes y cambios frecuentes de estado (por ejemplo, cambios de filtros, navegación, etc.).【F:app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt†L1329-L1406】
* La UI del player tiene muchas recomposiciones y cálculos por frame (tensión de animaciones, `offset`, `derivedStateOf`, etc.), lo que **exige disciplina** en la gestión de estado y en el trabajo que se hace por frame.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L146-L220】

### 1.4 Lectura de metadata/audio y carátulas (IO + CPU + RAM)
* Para leer metadatos se crea un archivo temporal desde `Uri`, lo que implica **copias completas a disco** y gasto de IO en cada lectura de metadata desde content URIs.【F:app/src/main/java/com/theveloper/pixelplay/data/media/AudioMetadataUtils.kt†L13-L97】
* Se utiliza TagLib y `MediaMetadataRetriever` para extraer metadata/arte embebido. Si esto se hace masivamente sin cache o sin límites, puede generar **picos de memoria** y trabajo extra de CPU/GPU.【F:app/src/main/java/com/theveloper/pixelplay/data/media/AudioMetadataReader.kt†L27-L92】【F:app/src/main/java/com/theveloper/pixelplay/utils/AlbumArtUtils.kt†L34-L120】

### 1.5 Motor de audio
* Se mantienen **dos instancias de ExoPlayer** en paralelo para transiciones (crossfade), lo que incrementa consumo de RAM y CPU permanentemente, incluso cuando no hay transiciones activas.【F:app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt†L34-L158】

---

## 2) Plan de optimización (exhaustivo y priorizado)

> Las estimaciones se dan como **rango** por variabilidad de hardware y tamaño de librería. Se incluyen impactos en CPU, RAM, IO y fluidez UI.

### PRIORIDAD 0 — Medición y presupuesto de rendimiento (base para validar mejoras)
1) **Perf baseline y métricas estables**
   * Crear “presupuestos” de tiempos para: búsqueda, apertura de biblioteca, apertura de player, scroll en listas grandes.
   * Instrumentar trazas (ya se usa `Trace` en el player) para más secciones críticas.
   * **Impacto estimado:** 0–5% directo, pero habilita priorización correcta (reduce regresiones).【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L146-L220】

### PRIORIDAD 1 — Biblioteca y sincronización (CPU/IO/RAM)
2) **Procesamiento incremental real por lotes y streaming**
   * Evitar cargar toda la librería en memoria; procesar en **batches** y generar `SongEntity` incrementalmente.
   * Si el DAO no soporta streaming, agregar métodos específicos que paginen por `MediaStore` o por IDs.
   * **Impacto:** 15–45% menos RAM durante sync; 10–25% menos tiempo de sync en librerías grandes.【F:app/src/main/java/com/theveloper/pixelplay/data/worker/SyncWorker.kt†L170-L317】

3) **Optimizar deduplicación de artistas y crossRefs con cache en DB**
   * Hoy se recalcula y se construyen mapas en memoria (potencialmente O(n)).
   * Proponer **tabla auxiliar** o cache persistente de `artistName -> artistId` para evitar recreación en cada sync.
   * **Impacto:** 10–30% menos CPU en sync y menor GC, especialmente con muchos artistas.【F:app/src/main/java/com/theveloper/pixelplay/data/worker/SyncWorker.kt†L348-L520】

4) **Escaneo LRC incremental y por prioridad**
   * Ejecutar LRC scan solo para archivos nuevos/actualizados en la última sync (no para toda la biblioteca).
   * Reducir concurrencia en dispositivos lentos (con heurística de RAM/CPU).
   * **Impacto:** 20–60% menos IO total en bibliotecas grandes; reduce “stalls”.【F:app/src/main/java/com/theveloper/pixelplay/data/repository/LyricsRepositoryImpl.kt†L709-L789】

### PRIORIDAD 2 — Base de datos y búsqueda (latencia + CPU)
5) **FTS5 o búsqueda indexada**
   * Reemplazar `LIKE '%query%'` por **FTS5** o índices de búsqueda con normalización y tokens.
   * Mantener tabla FTS sincronizada por triggers de Room.
   * **Impacto:** 3–20x más rápido en búsquedas grandes; reducción fuerte de CPU en cada consulta.【F:app/src/main/java/com/theveloper/pixelplay/data/database/MusicDao.kt†L114-L210】

6) **Reducir transformaciones masivas en `MusicRepositoryImpl`**
   * Evitar `map/associateBy/groupBy` sobre listas completas en cada emisión.
   * Precalcular estructuras en capa DAO (joins) o usar caches derivadas actualizadas incrementalmente.
   * **Impacto:** 10–35% menos CPU + menor GC cuando se actualiza la biblioteca.【F:app/src/main/java/com/theveloper/pixelplay/data/repository/MusicRepositoryImpl.kt†L104-L184】

### PRIORIDAD 3 — UI/Compose (fluidez y jank)
7) **Mover sorting pesado fuera de Composables**
   * `LibraryScreen` ordena listas en Composables. Se debe preordenar en ViewModel y exponer estado ya ordenado.
   * **Impacto:** 5–20% menos jank en scroll/cambio de filtro con listas grandes.【F:app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt†L1329-L1406】

8) **Simplificar recomposiciones de player**
   * El player concentra animaciones y estado. Usar `derivedStateOf` y state hoisting para garantizar que los subárboles más costosos no se recompongan ante cambios irrelevantes.
   * **Impacto:** 10–25% mejora de fluidez en transiciones en gama baja/media.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L146-L220】

9) **Estabilizar listas con keys y cargas diferidas**
   * Asegurar `key` estable en `LazyColumn/LazyGrid` y usar `LazyListState` recordado por screen.
   * Usar placeholders ligeros y tamaño de imagen correcto para evitar re-decode.
   * **Impacto:** 5–15% menos jank y mejor “first scroll”.

### PRIORIDAD 4 — Metadata, imágenes y cachés (IO/RAM)
10) **Evitar copias completas de audio en metadata**
    * `AudioMetadataUtils` crea archivos temporales para cada lectura desde `Uri`.
    * Proponer lectura directa con `FileDescriptor` siempre que sea posible o cachear resultados por ID.
    * **Impacto:** 20–50% menos IO y menos latencia al editar metadata o cargar info de canciones.【F:app/src/main/java/com/theveloper/pixelplay/data/media/AudioMetadataUtils.kt†L13-L97】

11) **Cache de album art con límites y downscaling**
    * `AlbumArtUtils` cachea en `cacheDir` sin límites explícitos. Implementar LRU con límite de tamaño + downscale a dimensiones UI necesarias.
    * **Impacto:** 10–30% menos RAM y menos GC en scroll de listas con carátulas.【F:app/src/main/java/com/theveloper/pixelplay/utils/AlbumArtUtils.kt†L34-L120】

### PRIORIDAD 5 — Audio engine y configuración adaptativa
12) **Modo “Low-End”**
    * Actualmente `DualPlayerEngine` mantiene dos `ExoPlayer` siempre activos. Para dispositivos con poca RAM, usar un solo player o instanciar `playerB` solo cuando se inicia una transición.
    * **Impacto:** 15–35% menos RAM en reproducción continua y menos CPU en background.【F:app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt†L34-L158】

---

## 3) Mejoras adicionales (para “exprimir” al máximo)

> Estas acciones no son “críticas” pero ayudan a eliminar micro-jank, ahorrar batería y memoria.

13) **Debounce/Throttle de búsqueda en UI**
    * Aplicar `debounce` en la entrada de texto para evitar múltiples queries por segundo.
    * **Impacto:** 5–15% menos CPU durante búsqueda activa.

14) **Programar Sync en condiciones favorables**
    * Ejecutar `SyncWorker` con `Constraints` (cargando, batería suficiente, idle) para no competir con UI.
    * **Impacto:** mejora perceptible en fluidez cuando el sync corre en background.

15) **Warm caches con bajo costo**
    * Precargar listas más usadas en background y/o caches ligeras para evitar picos de carga cuando el usuario abre la app.
    * **Impacto:** 10–20% menos latencia en navegación inicial.

16) **Control de imágenes (tamaño/resolución)**
    * Forzar tamaño de decode a 200–400px en listas, evitar decodificar arte de 4K en miniaturas.
    * **Impacto:** 10–30% menos RAM, menos GC y mejor scroll.

17) **Reducir trabajo en listas grandes**
    * Aplicar paginación o “windowing” adicional donde haya listas enormes.
    * **Impacto:** 15–40% menos memoria pico durante scroll.

---

## 4) Estimaciones globales (si se aplican todas las mejoras)

| Área | Ahorro esperado | Comentarios |
|------|-----------------|-------------|
| Sync + IO | 20–50% menos tiempo en bibliotecas grandes | Depende de tamaño de librería y tipo de almacenamiento. |
| RAM (global) | 15–40% menos RAM pico | Principalmente por caches limitadas y reducción de duplicados. |
| Búsqueda | 3–20x más rápido | FTS5 es el mayor multiplicador. |
| UI fluidez | 10–30% menos jank | En listas largas y player expandido. |
| Batería | 10–25% ahorro durante uso activo | Menos CPU + menos IO redundante. |

---

## 5) Orden recomendado de ejecución
1. FTS5 / índices de búsqueda.
2. Sync incremental real (batches) + LRC scan incremental.
3. Reducción de trabajo en `MusicRepositoryImpl` (mapas/sets masivos).
4. Optimización de sorting y recomposición en UI.
5. Cache/escala de imágenes + metadata sin copia completa.
6. Modo Low-End en DualPlayerEngine.

---

## 6) Resultado esperado
Con estas acciones implementadas en conjunto, **PixelPlayer debería alcanzar una experiencia fluida (60fps sostenidos) incluso en dispositivos de 3–4 años**, reducir picos de RAM y eliminar los “stutters” observados durante sincronización y navegación.
