package org.churchpresenter.app.churchpresenter

import org.churchpresenter.telemetry.GpuInfo
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StartupChecksTest {

    private fun <T> inTempDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("startup-checks").toFile()
        try {
            return block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `fonts that load let startup carry on`() {
        exitIfSystemFontsUnavailable(olderThanWindows10 = false)
        exitIfSystemFontsUnavailable(olderThanWindows10 = true)
    }

    @Test
    fun `a fallback file with no api names no fallback`() = inTempDir { dir ->
        val file = renderFallbackFile(dir).apply { writeText("gpu=Intel HD 3000\n") }
        assertNull(savedRenderFallback(file, "Intel HD 3000"))
    }

    @Test
    fun `a fallback saved without a gpu line belongs to an unknown gpu`() = inTempDir { dir ->
        val file = renderFallbackFile(dir).apply { writeText("api=$SOFTWARE_RENDER_API\n") }
        assertEquals(SOFTWARE_RENDER_API, savedRenderFallback(file, null))
        assertNull(savedRenderFallback(file, "Intel HD 3000"))
    }

    @Test
    fun `the gpu the fallback is saved against is the crash reporter's adapter`() {
        assertEquals(GpuInfo.crashTags()["gpu.adapter"], renderFallbackGpu())
    }

    @Test
    fun `switching without naming a gpu saves against this machine's`() = inTempDir { dir ->
        var relaunched = 0
        switchRenderApiAndRelaunch(SOFTWARE_RENDER_API, "OPENGL", dir, relaunch = { relaunched++ })

        assertEquals(1, relaunched)
        assertEquals(SOFTWARE_RENDER_API, savedRenderFallback(renderFallbackFile(dir), renderFallbackGpu()))
    }
}
