# 功能: 设置与关于

## 1. 功能描述

此功能模块为用户提供了配置应用行为和查看应用信息的标准渠道。

-   **设置 (`SettingsScreen.kt`)**: 允许用户自定义应用体验，例如调整主题（白天/夜间模式）、管理通知、设置播放质量或配置账户信息。
-   **关于 (`AboutScreen.kt`)**: 提供有关应用本身的信息，如版本号、开发者信息、许可协议、隐私政策以及指向支持页面的链接。

## 2. 技术实现

这两个屏幕通常是静态或半静态的，主要由信息展示和简单的用户输入控件（如下拉菜单、开关）组成。

-   **UI (Jetpack Compose)**: 使用标准的 Compose 组件（如 `Row`, `Text`, `Switch`, `Clickable`）来构建清晰、易于导航的列表。
-   **数据持久化**: 设置屏幕的 ViewModel 会与数据层交互，以读取和保存用户的偏好设置，通常使用 `DataStore` 或 `SharedPreferences`。
-   **导航**: 从应用的主导航菜单（如侧边栏或底部标签栏）可以轻松访问这两个屏幕。

## 3. 关键文件

以下是与此功能直接相关的主要文件：

-   **设置屏幕**: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsScreen.kt`
-   **关于屏幕**: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/AboutScreen.kt`