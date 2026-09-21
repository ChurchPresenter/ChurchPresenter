package org.churchpresenter.app.churchpresenter.dialogs.tabs

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The template dropdown's rows: what is in the lower-thirds folder, by name, after the no-template entry. */
class LottieTemplateOptionsTest {

    private fun folder(vararg names: String): File {
        val dir = Files.createTempDirectory("templates").toFile()
        names.forEach { File(dir, it).writeText("{}") }
        return dir
    }

    @Test
    fun `the folder's templates are listed by name, sorted, after the no-template entry`() {
        val dir = folder("zeta.json", "Alpha.json", "notes.txt", "beta.JSON")
        File(dir, "sub").mkdir()
        val options = templateOptions(dir, current = "", noneLabel = "No template")
        assertEquals("" to "No template", options.first())
        assertEquals(listOf("Alpha", "beta", "zeta"), options.drop(1).map { it.second })
        assertEquals(File(dir, "Alpha.json").absolutePath, options[1].first)
        dir.deleteRecursively()
    }

    @Test
    fun `a chosen file from outside the folder is still listed, and one inside is not listed twice`() {
        val dir = folder("inside.json")
        val elsewhere = Files.createTempDirectory("elsewhere").toFile().resolve("custom.json")
        val outside = templateOptions(dir, current = elsewhere.absolutePath, noneLabel = "None")
        assertEquals(listOf("None", "custom", "inside"), outside.map { it.second })
        val inside = templateOptions(dir, current = File(dir, "inside.json").absolutePath, noneLabel = "None")
        assertEquals(listOf("None", "inside"), inside.map { it.second })
        dir.deleteRecursively()
        elsewhere.parentFile.deleteRecursively()
    }

    @Test
    fun `no folder at all is just the no-template entry`() {
        assertEquals(listOf("" to "None"), templateOptions(null, current = "", noneLabel = "None"))
        val missing = File(Files.createTempDirectory("gone").toFile(), "nope")
        assertEquals(listOf("" to "None"), templateOptions(missing, current = "", noneLabel = "None"))
    }
}
