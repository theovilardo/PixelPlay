package com.theveloper.pixelplay.data.preferences

data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = false,
    val delayAlbumCarousel: Boolean = false,
    val delaySongMetadata: Boolean = false,
    val delayProgressBar: Boolean = false,
    val delayControls: Boolean = false,
    val showPlaceholders: Boolean = false
)
