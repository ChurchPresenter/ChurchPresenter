package org.churchpresenter.app.churchpresenter

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderFallbackTest {

    private fun <T> inTempDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("render-fallback").toFile()
        try {
            return block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `only a Windows default that settled on OpenGL falls back to software`() {
        assertEquals(
            SOFTWARE_RENDER_API,
            renderFallbackAfterFirstFrame("Windows 10", requested = null, actual = "OPENGL"),
        )
        assertNull(renderFallbackAfterFirstFrame("Windows 10", requested = null, actual = "DIRECT3D"))
        assertNull(renderFallbackAfterFirstFrame("Windows 10", requested = null, actual = "ANGLE"))
        assertNull(renderFallbackAfterFirstFrame("Windows 10", requested = "OPENGL", actual = "OPENGL"))
        assertNull(renderFallbackAfterFirstFrame("Linux", requested = null, actual = "OPENGL"))
    }

    @Test
    fun `a saved fallback is read back only for the gpu it was saved for`() = inTempDir { dir ->
        val file = renderFallbackFile(dir)
        assertNull(savedRenderFallback(file, "Intel HD 3000"))

        saveRenderFallback(file, SOFTWARE_RENDER_API, "Intel HD 3000")
        assertEquals(SOFTWARE_RENDER_API, savedRenderFallback(file, "Intel HD 3000"))
        assertNull(savedRenderFallback(file, "NVIDIA RTX 4060"))
        assertNull(savedRenderFallback(file, null))

        saveRenderFallback(file, SOFTWARE_RENDER_API, null)
        assertEquals(SOFTWARE_RENDER_API, savedRenderFallback(file, null))
    }

    @Test
    fun `an unreadable fallback file is no fallback`() = inTempDir { dir ->
        val file = renderFallbackFile(dir)
        file.writeText("api=\\uZZZZ\n")
        assertNull(savedRenderFallback(file, null))

        file.delete()
        file.mkdir()
        assertNull(savedRenderFallback(file, null))
    }

    @Test
    fun `the replaced pid is read from the arguments`() {
        assertEquals(42L, replacedPid(arrayOf("x", "${REPLACING_PID_ARGUMENT}42")))
        assertNull(replacedPid(arrayOf("x")))
        assertNull(replacedPid(arrayOf("${REPLACING_PID_ARGUMENT}abc")))
    }

    @Test
    fun `the relaunch command names this process in place of an older one`() {
        assertEquals(
            listOf("app", "--flag", "${REPLACING_PID_ARGUMENT}7"),
            replacingCommand(listOf("app", "${REPLACING_PID_ARGUMENT}3", "--flag"), 7),
        )
    }

    @Test
    fun `waiting for the replaced process returns when there is none or it outlives the wait`() {
        awaitReplacedProcess(arrayOf())
        awaitReplacedProcess(arrayOf("${REPLACING_PID_ARGUMENT}${Long.MAX_VALUE}"))
        awaitReplacedProcess(arrayOf("${REPLACING_PID_ARGUMENT}${ProcessHandle.current().pid()}"), timeoutSeconds = 0)
    }

    @Test
    fun `switching saves the fallback then relaunches as the replacement`() = inTempDir { dir ->
        var command: List<String>? = null
        switchRenderApiAndRelaunch(SOFTWARE_RENDER_API, "OPENGL", dir, gpu = "Intel HD 3000") { command = it }

        assertEquals(SOFTWARE_RENDER_API, savedRenderFallback(renderFallbackFile(dir), "Intel HD 3000"))
        assertEquals("$REPLACING_PID_ARGUMENT${ProcessHandle.current().pid()}", command?.last())
    }

    @Test
    fun `a fallback that cannot be saved does not relaunch`() = inTempDir { dir ->
        var relaunched = false
        val missing = File(dir, "missing")
        switchRenderApiAndRelaunch(SOFTWARE_RENDER_API, "OPENGL", missing, gpu = null) { relaunched = true }
        assertTrue(!relaunched)
    }

    @Test
    fun `a relaunch that fails to start keeps this session`() = inTempDir { dir ->
        switchRenderApiAndRelaunch(SOFTWARE_RENDER_API, "OPENGL", dir, gpu = null) { throw IOException("no launcher") }
        assertEquals(SOFTWARE_RENDER_API, savedRenderFallback(renderFallbackFile(dir), null))
    }

    @Test
    fun `a saved fallback replaces the platform default but not an override`() {
        assertEquals("SOFTWARE", preferredRenderApi("Windows 10", override = null, saved = "SOFTWARE"))
        assertEquals("DIRECT3D", preferredRenderApi("Windows 10", override = "DIRECT3D", saved = "SOFTWARE"))
        assertNull(preferredRenderApi("Windows 10", override = null, saved = "METAL"))
        assertEquals("METAL", preferredRenderApi("Mac OS X", override = null, saved = "OPENGL"))
    }
}
