@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.presenter

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.friwi.jcefmaven.CefAppBuilder
import org.churchpresenter.web.offscreen.testPage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The engine's side of off-screen rendering: the windowless start and its switches, the Cmd+Q the
 * app handler takes for the app, the jcefmaven shutdown hook taken out after a build, and an output
 * window drawing an off-screen page.
 */
class OffscreenEngineTest {
    private val tmp: File = Files.createTempDirectory("offscreen-engine").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
        CefManager.onQuitRequested = null
    }

    @Test
    fun `an off-screen engine starts windowless, with autoplay and without smooth scrolling`() {
        val builder = CefAppBuilder()
        JcefInstall.configure(builder, tmp, dmiTexts = emptyList(), offscreen = true)

        assertTrue(builder.cefSettings.windowless_rendering_enabled)
        assertTrue(
            builder.jcefArgs.containsAll(
                listOf("--autoplay-policy=no-user-gesture-required", "--disable-smooth-scrolling"),
            ),
        )
    }

    @Test
    fun `a windowed engine gets none of the off-screen switches`() {
        val builder = CefAppBuilder()
        JcefInstall.configure(builder, tmp, dmiTexts = emptyList())

        assertFalse(builder.cefSettings.windowless_rendering_enabled)
        assertTrue(builder.jcefArgs.none { it == "--disable-smooth-scrolling" })
    }

    @Test
    fun `Cmd+Q is handed to the app's own quit and JCEF is told it is handled`() {
        var quits = 0
        CefManager.onQuitRequested = { quits++ }

        assertTrue(QuitThroughApp.onBeforeTerminate())
        assertEquals(1, quits)
    }

    @Test
    fun `a build keeps the hooks that are not jcefmaven's`() {
        val other = Thread {}
        val result = JcefShutdownHook.withoutJcefmavenHook {
            Runtime.getRuntime().addShutdownHook(other)
            "built"
        }

        assertEquals("built", result)
        assertTrue(Runtime.getRuntime().removeShutdownHook(other), "a hook that is not jcefmaven's stays registered")
        assertFalse(JcefShutdownHook.isJcefmavenHook(other))
    }

    @Test
    fun `a hook jcefmaven registers during the build is taken out, and one there before is not`() {
        val before = Thread {}
        val jcefmaven = Thread {}
        val table = mutableSetOf(before)
        Runtime.getRuntime().addShutdownHook(before)
        try {
            JcefShutdownHook.withoutJcefmavenHook(hooks = { table.toSet() }, isJcefmaven = { true }) {
                Runtime.getRuntime().addShutdownHook(jcefmaven)
                table += jcefmaven
            }
            assertFalse(Runtime.getRuntime().removeShutdownHook(jcefmaven), "jcefmaven's hook was already removed")
        } finally {
            assertTrue(Runtime.getRuntime().removeShutdownHook(before), "the earlier hook stayed")
        }
    }

    @Test
    fun `without the JDK's hook table nothing is removed`() {
        val hook = Thread {}
        JcefShutdownHook.withoutJcefmavenHook(hooks = { null }, isJcefmaven = { true }) {
            Runtime.getRuntime().addShutdownHook(hook)
        }
        assertTrue(Runtime.getRuntime().removeShutdownHook(hook))
    }

    @Test
    fun `an output window with an off-screen page draws it rather than a browser of its own`() = runComposeUiTest {
        val page = testPage()
        try {
            setContent {
                WebsitePresenter(
                    url = "https://example.org/",
                    modifier = Modifier.size(400.dp, 225.dp).testTag("web"),
                    offscreenPage = page,
                )
            }
            waitForIdle()
            onNodeWithTag("web").assertExists()
        } finally {
            page.close()
        }
    }
}
