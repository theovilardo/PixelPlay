# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Development
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew installDebug` - Install debug build to connected device
- `./gradlew build` - Full build with tests

### Testing
- `./gradlew test` - Run unit tests
- `./gradlew connectedAndroidTest` - Run instrumented tests

### Code Quality
- `./gradlew lint` - Run Android lint checks
- `./gradlew lintDebug` - Run lint on debug variant only

## Architecture Overview

### Core Architecture
- **MVVM Pattern**: ViewModels manage UI state and business logic
- **Hilt Dependency Injection**: Centralized DI with @HiltAndroidApp application class
- **Single Activity**: Navigation handled through Jetpack Navigation Compose
- **Repository Pattern**: Data abstraction with MusicRepository interface

### Key Components
- **PlayerViewModel**: Central music playback state management using Media3/ExoPlayer
- **MusicService**: MediaSessionService for background playback and media controls
- **UnifiedPlayerSheet**: Expandable bottom sheet player with gesture support
- **Dynamic Theming**: Album art color extraction with Material 3 Expressive themes

### Data Layer
- **MediaStore Integration**: Primary music source from device storage with pagination
- **Room Database**: Caches album art color schemes (AlbumArtThemeEntity)
- **DataStore Preferences**: User settings, favorites, and playlists with JSON serialization
- **Proto DataStore**: Widget state management with protocol buffers

### UI Architecture
- **100% Jetpack Compose**: Modern declarative UI with Material 3
- **Theme System**: Multiple sources (system dynamic, album art, defaults) with per-component override
- **Navigation**: Type-safe compose navigation with screen arguments
- **Performance**: Optimized recomposition with proper state management

## Key Technologies
- **Target SDK**: 35 (Android 15), **Min SDK**: 29 (Android 10)
- **Compose BOM**: Latest stable Compose versions
- **Media3**: ExoPlayer integration for audio playback
- **KSP**: Annotation processing (replaces kapt)
- **Kotlin Serialization**: JSON handling for preferences
- **Coil**: Image loading with caching for album art
- **Palette API**: Color extraction from album artwork

## Development Notes

### Music Playback
- Media3 MediaSessionService handles foreground service and external controls
- PlayerViewModel provides reactive state through StateFlow/Flow
- Queue management with shuffle, repeat modes, and seek operations
- Widget integration with throttled updates for performance

### Theming System
- Material 3 Expressive with dynamic color support
- Album art color extraction cached in Room database
- Separate global and player theme preferences
- Real-time theme switching without restart

### Performance Considerations
- Pagination for large music libraries through MediaStore
- Image compression and caching for widget updates
- Throttled UI updates to prevent excessive recomposition
- Background theme preloading for smooth transitions

### Testing Structure
- Unit tests in `src/test/` using JUnit
- Instrumented tests in `src/androidTest/` with Compose UI testing
- Hilt test modules for dependency injection in tests