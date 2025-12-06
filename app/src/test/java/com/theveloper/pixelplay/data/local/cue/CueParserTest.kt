package com.theveloper.pixelplay.data.local.cue

import org.junit.Test
import org.junit.Assert.*

class CueParserTest {

    @Test
    fun `parse valid CUE content with multiple tracks`() {
        val cueContent = """
            PERFORMER "Test Artist"
            TITLE "Test Album"
            FILE "album.flac" WAVE
            TRACK 01 AUDIO
              TITLE "Track 1"
              PERFORMER "Artist 1"
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              TITLE "Track 2"
              PERFORMER "Artist 2"
              INDEX 01 03:45:23
            TRACK 03 AUDIO
              TITLE "Track 3"
              INDEX 01 07:12:45
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNotNull(cueSheet)
        assertEquals("album.flac", cueSheet!!.audioFileName)
        assertEquals("Test Artist", cueSheet.albumPerformer)
        assertEquals("Test Album", cueSheet.albumTitle)
        assertEquals(3, cueSheet.tracks.size)

        // Check first track
        val track1 = cueSheet.tracks[0]
        assertEquals(1, track1.trackNumber)
        assertEquals("Track 1", track1.title)
        assertEquals("Artist 1", track1.performer)
        assertEquals(0L, track1.startMs)
        assertNotNull(track1.endMs)

        // Check second track
        val track2 = cueSheet.tracks[1]
        assertEquals(2, track2.trackNumber)
        assertEquals("Track 2", track2.title)
        assertEquals("Artist 2", track2.performer)
        // 3 minutes 45 seconds 23 frames = 3*60*1000 + 45*1000 + 23*1000/75
        val expectedStart2 = 3 * 60 * 1000L + 45 * 1000L + (23 * 1000.0 / 75.0).toLong()
        assertEquals(expectedStart2, track2.startMs)
        assertNotNull(track2.endMs)

        // Check third track
        val track3 = cueSheet.tracks[2]
        assertEquals(3, track3.trackNumber)
        assertEquals("Track 3", track3.title)
        assertEquals("Test Artist", track3.performer) // Should inherit from album
        val expectedStart3 = 7 * 60 * 1000L + 12 * 1000L + (45 * 1000.0 / 75.0).toLong()
        assertEquals(expectedStart3, track3.startMs)
        assertNull(track3.endMs) // Last track has no end time
    }

    @Test
    fun `parse CUE with missing optional fields`() {
        val cueContent = """
            FILE "test.flac" WAVE
            TRACK 01 AUDIO
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              INDEX 01 02:30:00
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNotNull(cueSheet)
        assertEquals("test.flac", cueSheet!!.audioFileName)
        assertNull(cueSheet.albumPerformer)
        assertNull(cueSheet.albumTitle)
        assertEquals(2, cueSheet.tracks.size)

        val track1 = cueSheet.tracks[0]
        assertNull(track1.title)
        assertNull(track1.performer)
        assertEquals(0L, track1.startMs)
    }

    @Test
    fun `parse CUE with case insensitive keywords`() {
        val cueContent = """
            performer "Artist"
            title "Album"
            file "audio.flac" WAVE
            track 01 AUDIO
              title "Song"
              index 01 00:00:00
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNotNull(cueSheet)
        assertEquals("audio.flac", cueSheet!!.audioFileName)
        assertEquals("Artist", cueSheet.albumPerformer)
        assertEquals("Album", cueSheet.albumTitle)
        assertEquals(1, cueSheet.tracks.size)
        assertEquals("Song", cueSheet.tracks[0].title)
    }

    @Test
    fun `parse CUE with REM comments`() {
        val cueContent = """
            REM COMMENT "This is a comment"
            FILE "album.flac" WAVE
            REM Another comment
            TRACK 01 AUDIO
              TITLE "Track 1"
              INDEX 01 00:00:00
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNotNull(cueSheet)
        assertEquals(1, cueSheet!!.tracks.size)
    }

    @Test
    fun `parse CUE returns null for empty content`() {
        val cueContent = ""

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNull(cueSheet)
    }

    @Test
    fun `parse CUE returns null when no FILE specified`() {
        val cueContent = """
            TRACK 01 AUDIO
              INDEX 01 00:00:00
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNull(cueSheet)
    }

    @Test
    fun `parse CUE returns null when no tracks specified`() {
        val cueContent = """
            FILE "album.flac" WAVE
            PERFORMER "Artist"
            TITLE "Album"
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNull(cueSheet)
    }

    @Test
    fun `timestamp conversion is accurate`() {
        val cueContent = """
            FILE "test.flac" WAVE
            TRACK 01 AUDIO
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              INDEX 01 01:30:74
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNotNull(cueSheet)
        assertEquals(2, cueSheet!!.tracks.size)
        
        // 1 minute 30 seconds 74 frames (74 frames at 75 fps)
        // = 1*60*1000 + 30*1000 + 74*1000/75 = 60000 + 30000 + 986 = 90986
        val expectedMs = 1 * 60 * 1000L + 30 * 1000L + (74 * 1000.0 / 75.0).toLong()
        assertEquals(expectedMs, cueSheet.tracks[1].startMs)
    }

    @Test
    fun `track end times are set correctly`() {
        val cueContent = """
            FILE "album.flac" WAVE
            TRACK 01 AUDIO
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              INDEX 01 03:00:00
            TRACK 03 AUDIO
              INDEX 01 06:00:00
        """.trimIndent()

        val cueSheet = CueParser.parseCueContent(cueContent)

        assertNotNull(cueSheet)
        assertEquals(3, cueSheet!!.tracks.size)

        // First track end time should be second track start time
        assertEquals(3 * 60 * 1000L, cueSheet.tracks[0].endMs)

        // Second track end time should be third track start time
        assertEquals(6 * 60 * 1000L, cueSheet.tracks[1].endMs)

        // Last track should have null end time
        assertNull(cueSheet.tracks[2].endMs)
    }
}
