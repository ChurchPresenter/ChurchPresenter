package org.churchpresenter.lowerthird.render

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Telling a Lottie animation from any other `.json` in the folder.
 *
 * What is left of `LowerThirdSettingsViewModelTest`. The view model drove a Lower Third settings
 * tab that duplicated the Lower Third content tab and has been removed; `isLottieFile` was never
 * part of it, and the content tab, the render cache, the ATEM bridge and the Server tab all ask
 * it which files in a folder are animations.
 */
class IsLottieFileTest {

    private lateinit var folder: File

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("cp-lowerthird-test").toFile()
    }

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    /** Minimal content that satisfies the Lottie sniff test. */
    private fun lottie(name: String) =
        File(folder, name).also { it.writeText("""{"v":"5.7.4","layers":[]}""") }

    private fun plainJson(name: String) =
        File(folder, name).also { it.writeText("""{"hello":"world"}""") }

    // ── isLottieFile ────────────────────────────────────────────────────────────

    @Test
    fun `a lottie file is recognised by its version and layers keys`() {
        assertTrue(isLottieFile(lottie("anim.json")))
    }

    @Test
    fun `ordinary json is not mistaken for a lottie`() {
        assertFalse(isLottieFile(plainJson("data.json")))
    }

    @Test
    fun `a missing or unreadable file is not a lottie`() {
        assertFalse(isLottieFile(File(folder, "nope.json")))
        assertFalse(isLottieFile(folder), "a directory is not a lottie")
    }

}
