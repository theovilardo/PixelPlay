# Reporte de Diagnóstico: Performance en UnifiedPlayerSheet (UPS)

Tras analizar el código fuente de `UnifiedPlayerSheet.kt` y `Expansion.kt`, he identificado las causas exactas de las recomposiciones excesivas y el comportamiento errático (jitter) en la animación del alpha.

A continuación presento el diagnóstico técnico y las propuestas de corrección.

---

## 1. Diagnóstico de Recomposiciones Excesivas

El problema principal no es la cantidad de views, sino **cómo se leen los estados de animación dentro de la Composición**.

### El Problema: Lectura directa de Estado en el Scope principal
En Jetpack Compose, si lees `state.value` en el cuerpo de una función `@Composable`, esa función se suscribe a cada cambio de ese valor.

En `UnifiedPlayerSheet.kt`, la variable `playerContentExpansionFraction` (que es un `Animatable`) cambia en cada frame durante un swipe.

Observé este patrón crítico:

```kotlin
// LÍNEA 544 (aprox)
.zIndex(if (playerContentExpansionFraction.value < 0.5f) 1f else 0f)
```

**Por qué causa lag:**
Aunque el resultado del `if` solo cambia una vez (al cruzar 0.5), **la lectura** de `playerContentExpansionFraction.value` ocurre en cada frame. Esto obliga a Compose a re-ejecutar (recomponer) todo el bloque `Box` (y potencialmente sus padres/hijos si no están aislados) **400 veces** en un swipe lento, solo para verificar si el valor sigue siendo menor a 0.5.

### El Problema: Layout Thrashing (Re-medición constante)
Existen modificadores que cambian el tamaño o padding basados en la fracción:

```kotlin
// Padding cambia cada frame
.padding(bottom = currentBottomPadding) // currentBottomPadding depende de derivedStateOf(fraction)

// Height cambia cada frame
.height(playerContentAreaHeightDp) // playerContentAreaHeightDp depende de derivedStateOf(fraction)
```

**Impacto:** Cambiar `height` o `padding` invalida la fase de **Measure** y **Layout**, que es mucho más costosa que la fase de **Draw**. Esto causa que toda la jerarquía se tenga que volver a medir en cada pixel del arrastre.

---

## 2. Diagnóstico del Alpha "Jitter" (Saltos visuales)

El comportamiento no monotónico (subidas y bajadas de alpha durante un swipe continuo) se debe al uso incorrecto de `updateTransition`.

### El Problema: `rememberExpansionTransition`
En `UnifiedPlayerSheet.kt`:
```kotlin
val t = rememberExpansionTransition(playerContentExpansionFraction.value)
val miniAlpha by t.animateFloat(...) { f -> (1f - f * 2f)... }
```

Y en `Expansion.kt`:
```kotlin
fun rememberExpansionTransition(expansionFraction: Float) =
    updateTransition(targetState = expansionFraction, ...)
```

**Explicación:**
`updateTransition` está diseñado para cambios de estado discretos (ej: `Expanded` -> `Collapsed`). Al pasarle un `Float` continuo (ej: `0.01`, `0.012`, `0.015`), estás creando un **nuevo estado objetivo** en cada frame.
El sistema de animación interno intenta interpolar desde el valor anterior ("0.01") al nuevo ("0.012") usando curvas de animación (Spring/Tween). Como el target cambia cada 16ms, la animación nunca "alcanza" el destino y se reinicia constantemente, causando conflictos matemáticos que resultan en valores que oscilan levemente.

---

## 3. Propuestas de Optimización

Para corregir esto y bajar las recomposiciones a ~0 (solo draw passes) durante el gesto, propongo los siguientes cambios:

### Solución A: Eliminar `updateTransition` (Corrige el Jitter)
Reemplazar la transición compleja por interpolación lineal directa. Es más rápido, predecible y suave para gestos 1:1.

**Código actual:**
```kotlin
val t = rememberExpansionTransition(playerContentExpansionFraction.value)
val miniAlpha by t.animateFloat(...) { ... }
```

**Código propuesto:**
```kotlin
// Usar derivedStateOf para calcular el valor exacto basado en la fracción actual
val miniAlpha by remember {
    derivedStateOf {
        val fraction = playerContentExpansionFraction.value
        (1f - fraction * 2f).coerceIn(0f, 1f)
    }
}
```

### Solución B: Aislar lecturas de estado (Corrige Recomposiciones)
Para evitar que `zIndex` recomposicione todo el árbol, debemos evitar leer `.value` directamente en el cuerpo del composable.

**Estrategia:**
Usar `derivedStateOf` para el booleano resultante, no para el float.

```kotlin
// Calcular solo el booleano
val isMiniPlayerAbove by remember {
    derivedStateOf { playerContentExpansionFraction.value < 0.5f }
}

// Usar el booleano. Compose es inteligente: si isMiniPlayerAbove no cambia, no recompone.
.zIndex(if (isMiniPlayerAbove) 1f else 0f)
```

Al leer `isMiniPlayerAbove`, Compose solo invalidará el scope cuando ese booleano cambie (una vez por swipe), en lugar de 400 veces.

### Solución C: Evitar Layout Changes (Mejora Performance Gráfico)
En lugar de animar `height` y `padding` (que causan remidición), intentar usar `graphicsLayer` (transformaciones visuales) siempre que sea posible.

Si el `height` es estrictamente necesario para empujar contenido, considerar usar un `Layout` personalizado o aceptar el costo pero optimizar lo demás. Sin embargo, para `currentSheetTranslationY` ya se está usando `offset`, lo cual es bueno.

Para `miniPlayer` y `fullPlayer`, asegurarse de que su visibilidad/alpha se maneje exclusivamente en `graphicsLayer`:

```kotlin
Modifier.graphicsLayer {
    alpha = miniAlpha // Leer miniAlpha aquí es seguro y ocurre en fase de Draw
}
```

### Resumen de Acciones Recomendadas

1.  **Refactorizar Alpha:** Eliminar `rememberExpansionTransition` y calcular `miniAlpha` y `playerAreaElevation` usando `lerp` dentro de `derivedStateOf`.
2.  **Optimizar Z-Index:** Crear una variable derivada `val isAbove by remember { derivedStateOf { fraction < 0.5f } }` y usarla en el modificador.
3.  **Deferir Lecturas:** Mover cualquier lógica que dependa del valor exacto de la fracción *dentro* de lambdas de `graphicsLayer` o `drawBehind` siempre que sea posible.

Estos cambios deberían reducir las recomposiciones de ~400 a casi 0 (excepto cuando cruza el umbral de 0.5) y eliminar completamente el parpadeo del alpha.
