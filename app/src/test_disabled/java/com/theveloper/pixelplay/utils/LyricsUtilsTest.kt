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

    @Test
    fun parseLyrics_handlesWhitespacesBeforeTimestamp() {
        val lrc = "\uFEFF   [00:03.80]Time is standing still\r\n\t[00:09.86]Tracing my body"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals(3_800, synced[0].time)
        assertEquals("Time is standing still", synced[0].line)
        assertEquals(9_860, synced[1].time)
        assertEquals("Tracing my body", synced[1].line)
    }

    @Test
    fun parseLyrics_parsesFullSampleWithBom() {
        val lrc = """\uFEFF[00:03.80]Time is standing still and I don't wanna leave your lips\n""" +
            """[00:09.86]Tracing my body with your fingertips\n""" +
            """[00:16.53]I know what you're feeling and I know you wanna say it (yeah, say it)\n""" +
            """[00:22.76]I do too, but we gotta be patient (gotta be patient)\n""" +
            """[00:28.32]'Cause someone like me (and someone like you)\n""" +
            """[00:31.65]Really shouldn't work, yeah, the history is proof\n""" +
            """[00:34.75]Damned if I don't (damned if I do)\n""" +
            """[00:38.08]You know, by now, we've seen it all\n""" +
            """[00:41.67]Said, oh, we should fall in love with our eyes closed\n""" +
            """[00:46.76]Better if we keep it where we don't know\n""" +
            """[00:49.97]The beds we've been in, the names and the faces of who we were with\n""" +
            """[00:54.36]And, oh, ain't nobody perfect, but it's all good\n""" +
            """[00:59.52]The past can't hurt us if we don't look\n""" +
            """[01:02.71]Let's let it go, better if we fall in love with our eyes closed\n""" +
            """[01:09.05](Oh, oh, oh)\n""" +
            """[01:13.94]I got tunnel vision every second that you're with me\n""" +
            """[01:19.88]No, I don't care what anybody says, just kiss me (oh)\n""" +
            """[01:26.05]'Cause you look like trouble, but it could be good\n""" +
            """[01:29.09]I've been the same, kind of misunderstood\n""" +
            """[01:32.32]Whatever you've done, trust, it ain't nothing new\n""" +
            """[01:35.48]You know by now, we've seen it all\n""" +
            """[01:39.23]Said, oh, we should fall in love with our eyes closed\n""" +
            """[01:44.48]Better if we keep it where we don't know\n""" +
            """[01:47.55]The beds we've been in, the names and the faces of who we were with\n""" +
            """[01:52.13]And, oh, ain't nobody perfect, but it's all good\n""" +
            """[01:57.17]The past can't hurt us if we don't look\n""" +
            """[02:00.27]Let's let it go, better if we fall in love with our eyes closed\n""" +
            """[02:06.25](Oh, oh, keep your eyes closed)\n""" +
            """[02:10.76]'Cause someone like me and someone like you\n""" +
            """[02:13.86]Really shouldn't work, yeah, the history is proof\n""" +
            """[02:17.25]Damned if I don't, damned if I do\n""" +
            """[02:20.26]You know by now, we've seen it all\n""" +
            """[02:24.13]Said, oh, we should fall in love with our eyes closed\n""" +
            """[02:29.09]Better if we keep it where we don't know\n""" +
            """[02:32.13]The beds we've been in, the names and the faces of who we were with\n""" +
            """[02:36.95]And, oh, ain't nobody perfect, but it's all good\n""" +
            """[02:41.75]The past can't hurt us if we don't look\n""" +
            """[02:45.08]Let's let it go, better if we fall in love with our eyes closed (oh)\n""" +
            """[02:54.13]With our eyes closed\n""" +
            """[02:58.92]"""

        val lyrics = LyricsUtils.parseLyrics(lrc)

        val synced = requireNotNull(lyrics.synced)
        assertEquals(40, synced.size)
        assertEquals(3_800, synced.first().time)
        assertEquals("Time is standing still and I don't wanna leave your lips", synced.first().line)
        assertEquals(178_920, synced.last().time)
        assertEquals("", synced.last().line)
    }

    @Test
    fun parseLyrics_ignoresFormatCharactersInsideTimestamp() {
        val lrc = "\u202a[00:03.80\u202c]Time is standing still\n[00:09.86]Tracing my body"

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals(3_800, synced[0].time)
        assertEquals("Time is standing still", synced[0].line)
        assertEquals(9_860, synced[1].time)
        assertEquals("Tracing my body", synced[1].line)
    }

    @Test
    fun parseLyrics_parsesSampleWrappedInQuotes() {
        val lrc = buildString {
            append('"')
            appendLine("[00:03.80]Time is standing still and I don't wanna leave your lips")
            appendLine("[00:09.86]Tracing my body with your fingertips")
            appendLine("[00:16.53]I know what you're feeling and I know you wanna say it (yeah, say it)")
            appendLine("[00:22.76]I do too, but we gotta be patient (gotta be patient)")
            appendLine("[00:28.32]'Cause someone like me (and someone like you)")
            appendLine("[00:31.65]Really shouldn't work, yeah, the history is proof")
            appendLine("[00:34.75]Damned if I don't (damned if I do)")
            appendLine("[00:38.08]You know, by now, we've seen it all")
            appendLine("[00:41.67]Said, oh, we should fall in love with our eyes closed")
            appendLine("[00:46.76]Better if we keep it where we don't know")
            appendLine("[00:49.97]The beds we've been in, the names and the faces of who we were with")
            appendLine("[00:54.36]And, oh, ain't nobody perfect, but it's all good")
            appendLine("[00:59.52]The past can't hurt us if we don't look")
            appendLine("[01:02.71]Let's let it go, better if we fall in love with our eyes closed")
            appendLine("[01:09.05](Oh, oh, oh)")
            appendLine("[01:13.94]I got tunnel vision every second that you're with me")
            appendLine("[01:19.88]No, I don't care what anybody says, just kiss me (oh)")
            appendLine("[01:26.05]'Cause you look like trouble, but it could be good")
            appendLine("[01:29.09]I've been the same, kind of misunderstood")
            appendLine("[01:32.32]Whatever you've done, trust, it ain't nothing new")
            appendLine("[01:35.48]You know by now, we've seen it all")
            appendLine("[01:39.23]Said, oh, we should fall in love with our eyes closed")
            appendLine("[01:44.48]Better if we keep it where we don't know")
            appendLine("[01:47.55]The beds we've been in, the names and the faces of who we were with")
            appendLine("[01:52.13]And, oh, ain't nobody perfect, but it's all good")
            appendLine("[01:57.17]The past can't hurt us if we don't look")
            appendLine("[02:00.27]Let's let it go, better if we fall in love with our eyes closed")
            appendLine("[02:06.25](Oh, oh, keep your eyes closed)")
            appendLine("[02:10.76]'Cause someone like me and someone like you")
            appendLine("[02:13.86]Really shouldn't work, yeah, the history is proof")
            appendLine("[02:17.25]Damned if I don't, damned if I do")
            appendLine("[02:20.26]You know by now, we've seen it all")
            appendLine("[02:24.13]Said, oh, we should fall in love with our eyes closed")
            appendLine("[02:29.09]Better if we keep it where we don't know")
            appendLine("[02:32.13]The beds we've been in, the names and the faces of who we were with")
            appendLine("[02:36.95]And, oh, ain't nobody perfect, but it's all good")
            appendLine("[02:41.75]The past can't hurt us if we don't look")
            appendLine("[02:45.08]Let's let it go, better if we fall in love with our eyes closed (oh)")
            appendLine("[02:54.13]With our eyes closed")
            append("[02:58.92]")
            append('"')
        }

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(40, synced.size)
        assertEquals(3_800, synced.first().time)
        assertEquals("Time is standing still and I don't wanna leave your lips", synced.first().line)
        assertEquals(178_920, synced.last().time)
        assertEquals("", synced.last().line)
    }

    @Test
    fun parseLyrics_stripsAdditionalLrcTimestampsFromLines() {
        val lrc = """
            [00:12.57] Sinking under
            [00:26.42][01:12.34] Three in the morning, I ain't slept all weekend
            [00:41.71][00:52.96][01:27.42] My heart keeps breaking
        """.trimIndent()

        val lyrics = LyricsUtils.parseLyrics(lrc)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(
            listOf(
                "Sinking under",
                "Three in the morning, I ain't slept all weekend",
                "My heart keeps breaking"
            ),
            synced.map { it.line }
        )
    }
}
