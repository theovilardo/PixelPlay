# 功能: 内容详情

## 1. 功能描述

内容详情功能是用户深入了解特定媒体项目的核心。当用户从搜索结果、媒体库或推荐中选择一个项目时，应用会展示一个专门的详情页面。这包括专辑的曲目列表、艺术家的生平与作品、特定流派的代表性歌曲，以及播放列表的内容。

## 2. 技术实现

该功能由一组独立的、但功能相似的屏幕组成，每个屏幕都为特定类型的内容量身定制。它们都遵循着相似的 MVVM 模式：

-   **UI (Jetpack Compose)**: 每个详情页都使用 Jetpack Compose 构建，其布局经过优化以展示相应内容类型最重要的信息（例如，专辑封面、曲目列表、艺术家照片等）。
-   **导航**: 应用的导航组件负责接收一个唯一的标识符（如专辑 ID 或艺术家 ID），并将其传递给对应的详情屏幕。
-   **数据获取**: 每个屏幕的 ViewModel 使用接收到的 ID 从数据层（仓库）获取详细信息，并管理 UI 状态（如加载、成功、错误）。

## 3. 关键文件

以下是与此功能直接相关的主要文件，按内容类型分组：

-   **专辑详情**: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/AlbumDetailScreen.kt`
-   **艺术家详情**: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/ArtistDetailScreen.kt`
-   **流派详情**: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/GenreDetailScreen.kt`
-   **播放列表详情**: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistDetailScreen.kt`