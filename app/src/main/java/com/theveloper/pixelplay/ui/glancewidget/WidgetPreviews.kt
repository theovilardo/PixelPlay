package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.model.QueueItem

/**
 * This class is a provider for the Glance widget preview.
 * It's used to set up the widget's content within the preview environment.
 */
class PixelPlayGlanceWidgetPreviewProvider : GlanceAppWidget() {
    // An empty implementation is sufficient for previewing purposes.
    override suspend fun provideGlance(context: Context, id: GlanceId) = Unit

    @Composable
    fun Content() {
        // Define mock data for the preview
        val playerInfo = PlayerInfo(
            songTitle = "Song Title",
            artistName = "Artist Name",
            isPlaying = true,
            albumArtBitmapData = null,
            currentPositionMs = 10000,
            totalDurationMs = 100000,
            isFavorite = true,
            queue = listOf(
                QueueItem(1L, null), QueueItem(2L, null), QueueItem(3L, null), QueueItem(4L, null)
            )
        )

        Box(
            modifier = GlanceModifier.fillMaxSize().background(Color(0xFFADD8E6)),
            contentAlignment = Alignment.Center
        ) {
            // The LocalSize and LocalContext are automatically provided by the @Preview environment
            PixelPlayGlanceWidget().WidgetUi(
                playerInfo = playerInfo, size = LocalSize.current, context = LocalContext.current
            )
        }
    }
}

/**
 * This is the main preview function for the PixelPlay Glance Widget.
 * It uses the @Preview annotation from androidx.glance.preview.
 * This sets up the correct rendering environment for Glance composables.
 */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 60, heightDp = 60)
@Composable
fun PixelPlayWidget_Preview_1x1() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 60, heightDp = 120)
@Composable
fun PixelPlayWidget_Preview_1x2() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 120, heightDp = 60)
@Composable
fun PixelPlayWidget_Preview_2x1() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 120, heightDp = 120)
@Composable
fun PixelPlayWidget_Preview_2x2() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 60)
@Composable
fun PixelPlayWidget_Preview_3x1() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 120)
@Composable
fun PixelPlayWidget_Preview_3x2() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 180)
@Composable
fun PixelPlayWidget_Preview_3x3() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 240, heightDp = 120)
@Composable
fun PixelPlayWidget_Preview_4x2() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 240, heightDp = 180)
@Composable
fun PixelPlayWidget_Preview_4x3() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 240, heightDp = 240)
@Composable
fun PixelPlayWidget_Preview_4x4() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 300, heightDp = 180)
@Composable
fun PixelPlayWidget_Preview_5x3() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 300, heightDp = 240)
@Composable
fun PixelPlayWidget_Preview_5x4() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 300, heightDp = 300)
@Composable
fun PixelPlayWidget_Preview_5x5() {
    PixelPlayGlanceWidgetPreviewProvider().Content()
}
