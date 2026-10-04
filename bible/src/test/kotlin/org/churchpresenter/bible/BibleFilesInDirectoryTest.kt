package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleFilesInDirectoryTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-files-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun file(path: String) = File(dir, path).apply { parentFile.mkdirs(); writeText("x") }

    @Test
    fun `every module in the folder and its subfolders is listed, sorted, with forward slashes`() {
        file("kjv.spb")
        file("en/web.SPB")
        file("de/luther/lut.spb")
        file("notes.txt")

        assertEquals(listOf("de/luther/lut.spb", "en/web.SPB", "kjv.spb"), bibleFilesInDirectory(dir.absolutePath))
    }

    @Test
    fun `a module nested deeper than the scan depth is not reached`() {
        file((1..MAX_BIBLE_SCAN_DEPTH).joinToString("/") { "d$it" } + "/deep.spb")

        assertTrue(bibleFilesInDirectory(dir.absolutePath).isEmpty())
    }

    @Test
    fun `a blank path, a missing folder and a file all list nothing`() {
        assertTrue(bibleFilesInDirectory("").isEmpty())
        assertTrue(bibleFilesInDirectory(File(dir, "missing").absolutePath).isEmpty())
        assertTrue(bibleFilesInDirectory(file("kjv.spb").absolutePath).isEmpty())
    }
}
