# PixelPlayer Music Player 🎵

<p align="center">
  <img src="assets/icon.png" alt="App Icon" width="128"/>
</p>

<p align="center">
  <img src="assets/screenshot1.jpg" alt="Screenshot 1" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot2.jpg" alt="Screenshot 2" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot3.jpg" alt="Screenshot 3" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot4.jpg" alt="Screenshot 4" width="200" style="border-radius:26px;"/>
</p>

<p align="center">
    <a href="https://github.com/theovilardo/PixelPlay/releases/latest">
        <img src="https://img.shields.io/github/v/release/theovilardo/PixelPlay?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest Release">
    </a>
    <a href="https://github.com/theovilardo/PixelPlay/releases">
        <img src="https://img.shields.io/github/downloads/theovilardo/PixelPlay/total?logo=github&style=for-the-badge" alt="Total Downloads">
    </a>
</p>

PixelPlayer is a modern, offline-first music player for Android, built with Kotlin and Jetpack Compose. It's designed to provide a beautiful and seamless experience for listening to your local music library.

## ✨ Core Features

- **Local Music Playback**: Scans and plays your local audio files (MP3, FLAC, AAC, etc.).
- **Background Playback**: Listen to music while the app is in the background, thanks to a foreground service and Media3.
- **Modern UI**: A beautiful and responsive UI built with Jetpack Compose and Material 3 Expressive, supporting Dynamic Color and dark/light themes.
- **Music Library**: Organize and browse your music by songs, albums, and artists.
- **Widget**: Control your music from the home screen with a Glance-based app widget.
- **Tag Editor**: Edit song metadata (title, artist, album, etc.) with the built-in tag editor.
- **AI-Powered Features**: Explore advanced features powered by Gemini for a unique listening experience.

## 🛠️ Tech Stack & Architecture

- **Language**: 100% [Kotlin](https://kotlinlang.org/)
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) for a declarative and modern UI.
- **Audio Playback**: [Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3) for robust audio playback.
- **Architecture**: MVVM (Model-View-ViewModel) with a reactive approach using StateFlow and SharedFlow.
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/) for managing dependencies.
- **Database**: [Room](https://developer.android.com/training/data-storage/room) for local database storage.
- **Background Processing**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for background tasks like syncing the music library.
- **Asynchronous Operations**: [Kotlin Coroutines & Flow](https://kotlinlang.org/docs/coroutines-guide.html) for managing asynchronous operations.
- **Networking**: [Retrofit](https://square.github.io/retrofit/) for making HTTP requests.
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/) for loading and caching images.
- **Metadata**: [TagLib](https://github.com/Kyant0/taglib) for reading and writing audio file metadata (supports MP3, FLAC, M4A, and more).
- **Audio Processing**: [Amplituda](https://github.com/lincollincol/Amplituda) for audio processing and waveform generation.

## 🚀 Getting Started

To get a local copy up and running, follow these simple steps.

### Prerequisites

- Android Studio Iguana | 2023.2.1 or newer.
- Android SDK 29 or newer.

### Installation

1. Clone the repo
   ```sh
   git clone https://github.com/theovilardo/PixelPlay.git
   ```
2. Open the project in Android Studio.
3. Let Gradle sync and download the required dependencies.
4. Run the app on an emulator or a physical device.

## ⬇️ Download

You can download and install PixelPlayer using any of the following methods:

- **GitHub Releases**: You can download the latest APK from the [GitHub releases page](https://github.com/theovilardo/PixelPlay/releases/latest).
  <p align="center">
    <a href="https://github.com/theovilardo/PixelPlay/releases/latest"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60" style="max-width: 200px"></a>
  </p>

- **Obtainium**: You can also add the app to [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.theveloper.pixelplay%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Ftheovilardo%2FPixelPlay%22%2C%22author%22%3A%22theovilardo%22%2C%22name%22%3A%22PixelPlay%22%2C%22supportFixedAPKURL%22%3Afalse%7D) for easy updates and management.
  <p align="center">
    <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.theveloper.pixelplay%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Ftheovilardo%2FPixelPlay%22%2C%22author%22%3A%22theovilardo%22%2C%22name%22%3A%22PixelPlay%22%2C%22supportFixedAPKURL%22%3Afalse%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="40"></a>
  </p>

## 📂 Project Structure

The project follows the standard Android app structure, with a few key directories:

```
app/src/main/java/com/theveloper/pixelplay/
├── data
│   ├── local       # Room database, DAOs, and entities.
│   ├── remote      # Retrofit services for any network calls.
│   ├── repository  # Repositories that abstract data sources.
│   └── service     # The MusicService for background playback.
├── di              # Hilt dependency injection modules.
├── domain          # Use cases and domain models (if any).
├── presentation    # UI-related classes.
│   ├── components  # Reusable Jetpack Compose components.
│   ├── navigation  # Navigation graph and related utilities.
│   ├── screens     # Composable screens for different parts of the app.
│   └── viewmodel   # ViewModels for each screen.
├── ui
│   ├── glancewidget # Glance App Widget implementation.
│   └── theme       # App theme, colors, and typography.
└── utils           # Utility classes and extension functions.
```
