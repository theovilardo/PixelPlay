package com.theveloper.pixelplay.ui.glancewidget

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.preview.ExperimentalGlancePreviewApi
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.model.QueueItem

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "1x1", widthDp = 60, heightDp = 60)
@Composable
fun OneByOneWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "1x2", widthDp = 60, heightDp = 120)
@Composable
fun OneByTwoWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "2x1", widthDp = 120, heightDp = 60)
@Composable
fun TwoByOneWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "2x2", widthDp = 120, heightDp = 120)
@Composable
fun TwoByTwoWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "3x1", widthDp = 180, heightDp = 60)
@Composable
fun ThreeByOneWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "3x2", widthDp = 180, heightDp = 120)
@Composable
fun ThreeByTwoWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "3x3", widthDp = 180, heightDp = 180)
@Composable
fun ThreeByThreeWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "4x2", widthDp = 240, heightDp = 120)
@Composable
fun FourByTwoWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "4x3", widthDp = 240, heightDp = 180)
@Composable
fun FourByThreeWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "4x4", widthDp = 240, heightDp = 240)
@Composable
fun FourByFourWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "5x3", widthDp = 300, heightDp = 180)
@Composable
fun FiveByThreeWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "5x4", widthDp = 300, heightDp = 240)
@Composable
fun FiveByFourWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(group = "5x5", widthDp = 300, heightDp = 300)
@Composable
fun FiveByFiveWidgetPreview() {
    val playerInfo = PlayerInfo(
        songTitle = "Song Title",
        artistName = "Artist Name",
        isPlaying = true,
        albumArtBitmapData = null,
        currentPositionMs = 10000,
        totalDurationMs = 100000,
        isFavorite = true,
        queue = listOf(
            QueueItem(1L, null),
            QueueItem(2L, null),
            QueueItem(3L, null),
            QueueItem(4L, null)
        )
    )
    PixelPlayGlanceWidget().WidgetUi(
        playerInfo = playerInfo,
        size = LocalSize.current,
        context = LocalContext.current
    )
}
