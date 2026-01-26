# UPS_LAG_REPORT — Diagnóstico de recomposiciones y alpha en el gesto del player

## Contexto observado
- Tap: ~40–60 recomposiciones.
- Swipe rápido: ~100–120 recomposiciones.
- Swipe lento: ~400 recomposiciones.
- Alpha de placeholders y algunos componentes no cambia de forma monotónica durante el gesto.

Este análisis se basa en la estructura actual del `UnifiedPlayerSheet` y del `FullPlayerContent`, donde el progreso del swipe se actualiza en cada evento de `detectVerticalDragGestures` y se propaga a múltiples subárboles vía `playerContentExpansionFraction` y `expansionFractionProvider`.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L264-L352】【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L980-L1120】【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1000-L1217】

---

## 1) Recomposiciones: por qué varían tanto con el gesto

### Hallazgos técnicos
1. **La frecuencia de recomposición está acoplada al número de eventos de drag.**
   - El `onVerticalDrag` actualiza `currentSheetTranslationY` y `playerContentExpansionFraction` con `snapTo` por cada delta de arrastre. Eso produce recomposición cada vez que el `Animatable` cambia, y la frecuencia depende del número de eventos de input. Un swipe lento genera más eventos → más actualizaciones → más recomposiciones.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L1017-L1090】

2. **`playerContentExpansionFraction` se lee en muchos lugares del árbol.**
   - Se usa directamente para alfas, offsets, zIndex, transición y como `expansionFractionProvider` para el `FullPlayerContent`, propagando recomposición a múltiples capas y subcomponentes durante el drag.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L295-L352】【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L1185-L1236】

3. **`expansionFractionProvider()` se consulta varias veces por recomposición.**
   - En `FullPlayerContent` y sus secciones se invoca el proveedor repetidas veces (ej. `DelayedContent`, placeholders y secciones de UI) lo que amplifica trabajo cuando el valor cambia rápidamente.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1008-L1217】

4. **Transiciones y alfas dependientes de `expansionFraction` disparan recomposición en cadena.**
   - Derivados como `fullPlayerContentAlpha` y el uso de `rememberExpansionTransition` también reaccionan a cada cambio del `Animatable`.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L295-L352】【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L844-L852】

### Diagnóstico resumido
- **Sí, es esperable** que una interacción más lenta dispare más recomposiciones, porque estás actualizando el estado en cada evento de drag y ese estado se consume en múltiples composables.
- **No es ideal** en performance, ya que muchas recomposiciones son “visuales” y podrían moverse a capas de render (graphics layer) o desacoplarse del ritmo del input.

### Propuestas concretas para reducir recomposiciones

> **Objetivo:** disminuir el número de recomposiciones sin perder la respuesta del gesto ni bloquear el main thread.

1. **Throttle/quantize del `expansionFraction` para subárboles costosos** (sin afectar el gesto base).
   - Mantener el `Animatable` exacto para el gesto, pero crear una versión “coarse” para UI pesada:
     - `val coarseFraction by remember { derivedStateOf { (playerContentExpansionFraction.value * 120).roundToInt() / 120f } }`
   - Usar `coarseFraction` en secciones costosas (placeholders, textos complejos) y dejar el valor exacto para posiciones clave (offset principal, drag, etc.).
   - Esto reduce recomposiciones por debajo de la frecuencia de eventos sin afectar el input base.

2. **Cache local del `expansionFraction` por recomposición.**
   - Dentro de `FullPlayerContent` y sub-secciones, leer el provider una sola vez y pasar el valor a funciones internas para evitar múltiples lecturas divergentes.
   - Esto limita invalidaciones cruzadas y ayuda a mantener coherencia por frame.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1008-L1217】

3. **Aislar recomposiciones con `derivedStateOf` + `snapshotFlow` en subárboles críticos.**
   - Usar `snapshotFlow { playerContentExpansionFraction.value }` con `distinctUntilChanged()` y, si aplica, `conflate()` para evitar ráfagas de recomposición en subcomponentes que no necesitan cada pixel del gesto.
   - Mantener el gesto fluido al no bloquear el hilo principal (solo filtrar actualizaciones visuales).

4. **Separar UI “response-to-gesture” vs UI “response-to-state”.**
   - Posiciones y alpha global del player pueden seguir el gesto a 1:1.
   - Elementos internos (placeholders, metadata, sliders secundarios) pueden reaccionar en escalones o con easing interno sin perder percepción de fluidez.

5. **Usar la estrategia ya presente para “smooth progress” como guía para el swipe.**
   - `rememberSmoothProgress` ya desacopla updates de alta frecuencia y mantiene animación visual suave a través de `animateFloatAsState`. Este mismo patrón se puede aplicar al `expansionFraction` si hay subcomponentes no críticos que no requieren sincronización exacta al gesto.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/scoped/ComposeLoader.kt†L44-L98】

---

## 2) Alpha/animación: por qué no es suave/monotónica

### Hallazgos técnicos
1. **Alfas simultáneas y crossfade sobre umbrales.**
   - En `DelayedContent`, la visibilidad depende de `isDelayGateOpen` y usa `Crossfade`. Si el `expansionFraction` oscila alrededor del `delayAppearThreshold`, el estado puede alternar y el `Crossfade` introduce micro-variaciones en alpha (sube/baja) en lugar de ser estrictamente monotónico.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1069-L1129】

2. **Alpha aplicada en múltiples capas con valores diferentes.**
   - Placeholders usan `graphicsLayer { alpha = expansionFraction }` mientras `content` usa `baseAlpha` que a su vez depende de umbrales distintos. La combinación de alfas no necesariamente es monotónica cuando se suman crossfades + thresholds distintos.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1069-L1141】【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1367-L1402】

3. **`expansionFractionProvider()` se lee múltiples veces por frame.**
   - En un swipe lento, entre lecturas la `Animatable` puede haber avanzado unos micros. Esto puede generar pequeñas diferencias de alpha dentro del mismo frame si distintas secciones leen valores ligeramente distintos. El resultado percibido puede ser “micro-alternancias” en el alpha.

4. **Curvas de alpha con zonas muertas/escala.**
   - `fullPlayerContentAlpha` no comienza en 0, sino desde `0.25f` a 1.0f, lo que puede generar un ritmo de alfa distinto respecto a otras piezas que usan el valor lineal directo. En combinación con otras capas, pueden verse diferencias de pendiente o micro-picos.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheet.kt†L295-L307】

### Propuestas concretas para alpha monotónico

1. **Histeresis en el gate de `DelayedContent` (evitar rebotes de threshold).**
   - Cuando el gate se abre una vez durante el gesto, mantenerlo abierto hasta terminar el gesto o hasta colapso completo.
   - Esto evita que `Crossfade` se dispare repetidamente si el valor está cerca del umbral.

2. **Unificar fuente de alpha por subárbol.**
   - Capturar `val expansionFraction = expansionFractionProvider()` una vez y derivar `val alpha = ...` para toda la sección, en vez de recalcularlo en cada subcomponente.
   - Aplicar **una sola capa de alpha** en un `Box` contenedor y eliminar alfas internas redundantes (p. ej. placeholders + contenido). Esto tiende a producir un alpha más estable y monotónico.【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1069-L1141】【F:app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt†L1367-L1402】

3. **Separar “alpha de gesto” y “alpha de transición”.**
   - Si se usa `Crossfade`, dejar que el crossfade tenga control exclusivo sobre la visibilidad, y usar el alpha del gesto solo en la capa superior (o viceversa). Evitar multiplicar ambos al mismo tiempo.

4. **Clamping y easing coherente.**
   - Alinear los umbrales (`normalStartThreshold`, `delayAppearThreshold`, `fullPlayerContentAlpha` start) para que todas las secciones respondan con la misma pendiente y evitar “desfase” visual.

---

## Plan recomendado (mínimo riesgo)

1. **Cache local del fraction** en `FullPlayerContent` y sections críticas (reducir lecturas múltiples).
2. **Añadir una versión “coarse” del fraction** para secciones pesadas (placeholders, textos largos, sliders secundarios).
3. **Implementar histéresis en `DelayedContent`** para evitar crossfade “oscillante”.
4. **Consolidar alpha por secciones** (contenedor único con alpha y contenido interno sin alpha extra).

Estos pasos reducen recomposiciones, estabilizan el alpha y mantienen una UI fluida, sin bloquear el main thread y sin sacrificar la calidad visual actual.
