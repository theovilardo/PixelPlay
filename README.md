---

<!-- PROJECT SHIELDS -->
<p align="center">
  <img src="https://img.shields.io/badge/Jetpack_Compose-1.5.12-4285F4?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=Kotlin&logoColor=white" alt="KOTLIN"/>
  <img src="https://img.shields.io/badge/Room-Optional-red?style=flat" alt="Room (Optional)"/>
  <img src="https://img.shields.io/badge/DI-Hilt-2.44-green?style=flat" alt="Hilt"/>
  <img src="https://img.shields.io/badge/Material3-Expressive-✨-purple?style=flat" alt="Material3 Expressive"/>
  <img src="https://img.shields.io/badge/Storage-DataStore-1.1.0-orange?style=flat" alt="DataStore"/>
</p>

---

# 🎵 PixelPlay AI Music Player

> “Listen like a boss, powered by AI & Material 3 Expressive!”  

PixelPlay is a **Jetpack Compose**-based Android music app, architected with **Hilt** DI, **DataStore** (and soon **Room**), featuring a slick **Material 3 Expressive** interface and AI-driven features in the works.  

---

## 🚀 Key Features

- **🖼️ Dynamic Themes**: Auto-adapt colors from album art with Material 3 Expressive  
- **⚙️ Hilt + KSP**: Seamless dependency injection for ViewModels, repositories & more
- **💾 Data Persistence**: User prefs and playback queue with Jetpack DataStore
- **🎨 Modern UI**: Fully built in Jetpack Compose for smooth, declarative layouts
- **🎵 Expandable Player**: Drag, swipe & predictive-back gestures for immersive controls  
- **🤖 AI-Ready**: Hooks in place for future AI-powered recommendations & lyrics  

---

## 📦 Tech Stack

| Layer               | Tech                       |
|---------------------|----------------------------|
| UI                  | Jetpack Compose 1.5.0-alpha|
| Design System       | Material 3 Expressive      |
| DI                  | Hilt 2.44 + KSP            |
| Persistence         | DataStore 1.1.0 (Room now) |
| Navigation          | Jetpack Navigation Compose |
| Coroutines & Flow   | Kotlin Coroutines & Flow   |
| Testing             | JUnit5, MockK, UI tests    |

---

## 📐 Architecture

┌───────────────────┐       ┌───────────────────┐
│    Compose UI     │  ←→   │  ViewModels (MVVM)│
└───────────────────┘       └───────────────────┘
▲                           ▲
│                           │
▼                           ▼
┌───────────────────┐       ┌───────────────────┐
│  Repositories     │  ←→   │   DataStore/Room  │
└───────────────────┘       └───────────────────┘
▲
│
▼
AI Services (TBD)


- **Modular**: UI, domain & data layers decoupled  
- **Scalable**: Easy to plug-in Room or Retrofit modules  
- **Testable**: ViewModels & Repos injected via Hilt  

---

## 🎨 Screenshots & GIFs

<p align="center">
  <img src="docs/ui/mini_player.gif" width="200" alt="Mini Player"/>
  <img src="docs/ui/full_player.gif" width="200" alt="Full Player"/>
  <img src="docs/ui/queue.gif" width="200" alt="Queue Sheet"/>
</p>

---

## 💻 Getting Started

1. **Clone** the repo  
   ```bash
   git clone https://github.com/your-org/pixel-play.git


2. **Open** in Android Studio (Arctic Fox+)
3. **Build** & **Run** on a device/emulator (min API 21)
4. **Enjoy** and rock on! 🤘

---

## 🛠️ Roadmap

* [x] Material 3 Expressive theming
* [x] Expandable bottom sheet player
* [x] DataStore preferences
* [ ] Room database for offline playlists
* [ ] AI-powered song recommendations
* [ ] Lyrics generation with ML

---

## 🤝 Contribute

1. Fork & create a feature branch
2. Commit & PR
3. Code review & merge
4. 🚀 Celebrate your contribution with 🎉!

---

## 📜 License

This project is **Apache 2.0** licensed. See [LICENSE](LICENSE) for details.

---

#### Made with ❤️ by \[Your Name]

---

Feel free to tweak badges, versions and screenshots paths. Rock on! \m/
```
