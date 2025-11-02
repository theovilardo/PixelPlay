package com.theveloper.pixelplay.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyricsUtilsTest {

    @Test
    fun parseLyrics_handlesBomAtStartOfSyncedLine() {
        val lrc = "\uFEFF[00:03.80]Time is standing still\n[00:09.86]Tracing my body"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = lyrics.synced

        assertNotNull(synced)
        val syncedLines = requireNotNull(synced)
        assertEquals(2, syncedLines.size)
        assertEquals(3_800, syncedLines[0].time)
        assertEquals("Time is standing still", syncedLines[0].line)
        assertEquals(9_860, syncedLines[1].time)
        assertEquals("Tracing my body", syncedLines[1].line)
    }
}
