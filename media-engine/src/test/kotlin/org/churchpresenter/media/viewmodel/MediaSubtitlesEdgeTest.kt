package org.churchpresenter.media.viewmodel

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaSubtitlesEdgeTest {

    private val dir: File = Files.createTempDirectory("cp-subtitles-edge").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun srt(name: String) = File(dir, name).apply {
        writeText("1\n00:00:01,000 --> 00:00:02,000\nHello\n")
    }

    @Test
    fun `a stream has no folder to look for subtitles in`() {
        assertTrue(siblingSubtitleFiles("").isEmpty())
        assertTrue(siblingSubtitleFiles("clip.mp4").isEmpty(), "no parent folder")
        assertTrue(siblingSubtitleFiles(File(dir, "missing/clip.mp4").path).isEmpty())
    }

    @Test
    fun `a folder named like a subtitle is not one`() {
        File(dir, "sermon.srt").mkdirs()
        srt("sermon.en.srt")
        File(dir, "sermon.txt").writeText("not a subtitle")
        File(dir, "sermon.en.extra.srt").writeText("too many parts")
        File(dir, "other.srt").writeText("someone else's")
        val found = siblingSubtitleFiles(File(dir, "sermon.mp4").path).map { it.name }
        assertEquals(listOf("sermon.en.srt"), found)
    }

    @Test
    fun `the same file is not loaded twice, and a blank path is ignored`() {
        val subtitles = MediaSubtitles(position = { 0L })
        val file = srt("clip.srt")
        subtitles.addSubtitleFile(file.path)
        subtitles.addSubtitleFile(file.path)
        subtitles.addSubtitleFile("")
        assertEquals(1, subtitles.sidecarSubtitles.size)
    }

    @Test
    fun `a track that is not loaded cannot be changed`() {
        val subtitles = MediaSubtitles(position = { 0L })
        subtitles.addSubtitleFile(srt("clip.srt").path)
        subtitles.setSidecarEnabled(5, false)
        subtitles.setSidecarOutputs(-1, setOf("lobby"))
        assertTrue(subtitles.sidecarSubtitles.single().enabled)
        assertTrue(subtitles.sidecarSubtitles.single().outputs.isEmpty())
    }

    @Test
    fun `a folder that cannot be listed has no subtitles in it`() {
        val locked = File(dir, "locked").apply { mkdirs() }
        File(locked, "clip.srt").writeText("x")
        locked.setReadable(false)
        try {
            if (locked.listFiles() != null) return
            assertTrue(siblingSubtitleFiles(File(locked, "clip.mp4").path).isEmpty())
        } finally {
            locked.setReadable(true)
        }
    }
}
