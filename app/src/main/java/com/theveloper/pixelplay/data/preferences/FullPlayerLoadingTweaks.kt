package com.theveloper.pixelplay.data.preferences

data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = true,
    val delayAlbumCarousel: Boolean = false,
    val delaySongMetadata: Boolean = false,
    val delayProgressBar: Boolean = false,
    val delayControls: Boolean = false,
    val showPlaceholders: Boolean = false,
    val transparentPlaceholders: Boolean = false,
    val contentAppearThresholdPercent: Int = 100
)
