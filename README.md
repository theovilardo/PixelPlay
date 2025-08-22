# PixelPlay 🎵

*Reproductor de música **local** para Android construido con **Kotlin** y **Jetpack Compose**.*

> Este README fue reescrito y modernizado en **agosto de 2025**. Ajusta cualquier punto que no refleje el estado real del repo.

---

## ✨ Resumen

**PixelPlay** es una app de música **offline** que escanea tu biblioteca local y ofrece una experiencia fluida con una interfaz moderna basada en **Material 3**. Soporta controles del sistema, notificación persistente, cola de reproducción, y navegación por artistas, álbumes, canciones y carpetas.

---

## 🔎 Características (principales)

* 🔊 **Reproducción local** con soporte para formatos comunes (MP3/AAC/FLAC/OGG, etc.)
* 🎚️ **Controles**: play/pause, siguiente/anterior, repetir/aleatorio, seek
* 🧭 **Biblioteca**: Canciones · Álbumes · Artistas · Carpetas · Listas
* 📦 **Cola de reproducción** (añadir/quitar, reordenar)
* 🪄 **Búsqueda** rápida por texto
* 🎨 **Material 3 / Dynamic Color** (modo claro/oscuro)
* 🔔 **Notificación** y **MediaSession** (controles desde lockscreen/auriculares)
* 📱 **Compact/Expanded** player (miniplayer y pantalla “Now Playing”)
* 🗂️ Lectura de **MediaStore** y carátulas embebidas/externas
* 🌐 **Idiomas**: ES/EN (si aplica)

> Marca con ✅ lo que ya esté implementado y mueve lo restante a *Roadmap*.

---

## 🧰 Pila técnica

* **Lenguaje**: Kotlin
* **UI**: Jetpack Compose (BOM), Material 3
* **Media**: AndroidX **Media3** (ExoPlayer, Session, UI)
* **Arquitectura**: MVVM + Flows/Coroutines
* **DI**: Hilt/Koin (si aplica)
* **Data**: MediaStore · Room (si aplica)
* **Testing**: JUnit/MockK/Compose UI Test (si aplica)

---

## 🏗️ Estructura (sugerida)

```
app/
  ├─ data/            # Repositorios, fuentes de datos (MediaStore, Room)
  ├─ domain/          # Casos de uso y modelos de dominio
  ├─ ui/              # Pantallas Compose, theming, navegación
  ├─ playback/        # ExoPlayer, MediaSession, notificación
  └─ core/            # Utilidades comunes, ext, result wrappers
```

> Adapta a la estructura real del proyecto si difiere.

---

## 🚀 Empezar

### Requisitos

* **Android Studio** reciente (Koala/Ladybug o superior)
* **Android Gradle Plugin** y **Gradle** acordes a la versión del proyecto
* **Min SDK**: el del repo (ej.: 24+)

### Clonar y ejecutar

```bash
git clone https://github.com/<tu-usuario>/PixelPlay.git
cd PixelPlay
# Abre en Android Studio y sincroniza Gradle
```

Selecciona un dispositivo/emulador y ejecuta **Run** ▶️.

---

## 📦 Dependencias clave (build.gradle.kts)

> Usa **BOM de Compose** y **Media3**. Ajusta versiones si ya estás en otras.

```kotlin
dependencies {
    // BOM de Compose (bloquea versiones compatibles)
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")

    // Media3 (ExoPlayer/Session/UI)
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Kotlin Coroutines, Lifecycle, etc. (opcional)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<ver>")
    implementation("androidx.lifecycle:lifecy
```
