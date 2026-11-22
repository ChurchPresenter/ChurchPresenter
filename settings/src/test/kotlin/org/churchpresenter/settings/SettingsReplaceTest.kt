package org.churchpresenter.settings

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SettingsReplaceTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-settings-replace").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `the new file takes the old one's place`() {
        val from = File(dir, "settings.json.tmp").apply { writeText("new") }
        val to = File(dir, "settings.json").apply { writeText("old") }

        replaceWithRetries(from, to, pause = {})

        assertEquals("new", to.readText())
        assertFalse(from.exists())
    }

    @Test
    fun `a refused move is tried again after a growing pause`() {
        val pauses = mutableListOf<Long>()
        var tries = 0

        replaceWithRetries(File(dir, "a"), File(dir, "b"), pause = { pauses += it }) { _, to ->
            if (++tries < 3) throw AccessDeniedException(to.path)
        }

        assertEquals(3, tries)
        assertEquals(listOf(100L, 200L), pauses)
    }

    @Test
    fun `a move refused every time throws the last refusal`() {
        var tries = 0

        assertFailsWith<AccessDeniedException> {
            replaceWithRetries(File(dir, "a"), File(dir, "b"), attempts = 4, pause = {}) { _, to ->
                tries++
                throw AccessDeniedException(to.path)
            }
        }

        assertEquals(4, tries)
    }

    @Test
    fun `an error that is not a refusal is not tried again`() {
        var tries = 0

        assertFailsWith<IllegalStateException> {
            replaceWithRetries(File(dir, "a"), File(dir, "b"), pause = {}) { _, _ ->
                tries++
                error("not a file system refusal")
            }
        }

        assertEquals(1, tries)
    }
}
