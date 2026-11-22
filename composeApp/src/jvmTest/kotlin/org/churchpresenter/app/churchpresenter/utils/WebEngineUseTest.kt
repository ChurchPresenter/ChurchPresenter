package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebEngineUseTest {

    private val dir = Files.createTempDirectory("cp-web-use").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a data folder that never saw the web engine has no mark`() {
        assertFalse(WebEngineUse.wasUsed(dir))
    }

    @Test
    fun `once marked the folder remembers, and marking again changes nothing`() {
        WebEngineUse.mark(dir)
        WebEngineUse.mark(dir)

        assertTrue(WebEngineUse.wasUsed(dir))
    }

    @Test
    fun `a folder that does not exist yet is created for the mark`() {
        val nested = File(dir, "not/yet")

        WebEngineUse.mark(nested)

        assertTrue(WebEngineUse.wasUsed(nested))
    }

    @Test
    fun `a folder that cannot be written is no failure, only no mark`() {
        val blocker = File(dir, "file").apply { writeText("not a folder") }

        WebEngineUse.mark(File(blocker, "inside"))

        assertFalse(WebEngineUse.wasUsed(File(blocker, "inside")))
    }
}
