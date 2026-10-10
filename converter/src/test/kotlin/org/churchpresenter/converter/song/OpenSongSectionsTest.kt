package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSongSectionsTest {

    @Test
    fun `chord lines, comments, separators and an empty marker are not lyrics`() {
        val body = "[V]\n.G   C\n;a comment\n1first verse\n2second verse\n---\n-!!\n|||\n[]\n1more of the first\n ____\n"
        val sections = OpenSongConverter.sectionsOf(body, emptyList())
        assertEquals(listOf(listOf("first verse", "more of the first"), listOf("second verse")), sections.map { it.lines })
    }

    @Test
    fun `the presentation order ignores names that are not there`() {
        val sections = OpenSongConverter.sectionsOf("[V1]\n one\n[C]\n chorus\n", listOf("C", "X9", "V1"))
        assertEquals(listOf("chorus", "one"), sections.map { it.lines.single() })
    }

    @Test
    fun `a song with no lyrics element has no sections`() {
        val dir = Files.createTempDirectory("opensong-sections").toFile()
        try {
            val file = File(dir, "Empty").apply { writeText("<song><title>Empty</title></song>") }
            assertTrue(OpenSongConverter.parse(file).sections.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
