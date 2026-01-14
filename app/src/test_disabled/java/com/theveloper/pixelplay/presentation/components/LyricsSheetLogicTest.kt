package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSheetLogicTest {

    @Test
    fun sanitizeSyncedWords_removesLeadingTags_preventsOverlap() {
        val words = listOf(
            SyncedWord(time = 0, word = "v1:"),
            SyncedWord(time = 120, word = "Hello"),
            SyncedWord(time = 240, word = "world")
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(listOf("Hello", "world"), sanitized.map { it.word })
        assertEquals(listOf(120, 240), sanitized.map { it.time })
    }

    @Test
    fun highlightSnapOffsetPx_alignsLineWithHighlightZone() {
        val viewportHeight = 960
        val itemSize = 120
        val highlightOffsetPx = 80f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)

        val expectedCenter = viewportHeight / 2f - highlightOffsetPx
        assertEquals(expectedCenter - itemSize / 2f, offset.toFloat(), 0.5f)
    }

    @Test
    fun highlightSnapOffsetPx_clampsWithinViewportForEndOfList() {
        val viewportHeight = 420
        val itemSize = 320
        val highlightOffsetPx = -160f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)
        val center = offset + itemSize / 2f

        assertTrue(center <= viewportHeight.toFloat())
        assertTrue(center >= itemSize / 2f)
    }

    @Test
    fun highlightSnapOffsetPx_handlesOversizedItems() {
        val viewportHeight = 200
        val itemSize = 260

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx = 60f)

        assertEquals(0, offset)
    }

    @Test
    fun calculateHighlightMetrics_reservesBottomSpace() {
        val metrics = calculateHighlightMetrics(
            containerHeight = 480.dp,
            highlightZoneFraction = 0.22f,
            highlightOffset = 48.dp
        )

        assertTrue(metrics.bottomPadding > 0.dp)
        assertTrue(metrics.topPadding >= 0.dp)
        assertTrue(metrics.zoneHeight > 0.dp)
    }

    @Test
    fun sanitizeLyricLineText_stripsLrcTimestampTags() {
        val raw = "[00:26.42][01:12.34] Three in the morning, I ain't slept all weekend"

        val sanitized = sanitizeLyricLineText(raw)

        assertEquals("Three in the morning, I ain't slept all weekend", sanitized)
    }
}
