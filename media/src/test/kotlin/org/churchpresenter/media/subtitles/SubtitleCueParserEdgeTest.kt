package org.churchpresenter.media.subtitles

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtitleCueParserEdgeTest {

    private val dir: File = Files.createTempDirectory("cp-cue-parser").toFile()

    @AfterTest
    fun cleanUp() {
        dir.setReadable(true)
        dir.listFiles()?.forEach { it.setReadable(true) }
        dir.deleteRecursively()
    }

    @Test
    fun `a cue with times but no words is left out`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n\n2\n00:00:03,000 --> 00:00:04,000\nKept\n"
        val cues = SubtitleCueParser.parseSrt(srt)
        assertEquals(listOf("Kept"), cues.map { it.text })
    }

    @Test
    fun `a block with no timing line is left out`() {
        assertTrue(SubtitleCueParser.parseWebVtt("WEBVTT\n\nNOTE nothing to see\n").isEmpty())
    }

    @Test
    fun `a file that cannot be read yields nothing rather than failing`() {
        val file = File(dir, "locked.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:02,000\nHi\n")
            setReadable(false)
        }
        if (file.canRead()) return
        assertTrue(SubtitleCueParser.parseSubtitleFile(file).isEmpty())
    }
}
