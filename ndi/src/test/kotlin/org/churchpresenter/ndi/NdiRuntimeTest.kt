package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WINDOWS = "Windows 11"
private const val MAC = "Mac OS X"
private const val LINUX = "Linux"

class NdiRuntimeTest {

    @Test
    fun `windows looks for the x64 dll`() {
        assertEquals(listOf("Processing.NDI.Lib.x64.dll"), NdiRuntime.libraryFileNamesFor(WINDOWS))
    }

    @Test
    fun `macos looks for the dylib`() {
        assertEquals(listOf("libndi.dylib"), NdiRuntime.libraryFileNamesFor(MAC))
    }

    @Test
    fun `linux tries the versioned sonames newest first`() {
        // The exact assumption that left Devolay unable to load an NDI 6 runtime: it hardcoded
        // .so.5, which NDI 6 no longer ships. so.6 must be tried, and before so.5.
        val names = NdiRuntime.libraryFileNamesFor(LINUX)
        assertEquals(listOf("libndi.so.6", "libndi.so.5", "libndi.so"), names)
    }

    @Test
    fun `an unknown os name falls back to the linux sonames`() {
        assertEquals(NdiRuntime.libraryFileNamesFor(LINUX), NdiRuntime.libraryFileNamesFor("SunOS"))
    }

    @Test
    fun `the v6 runtime dir env var is searched before the v5 one`() {
        val dirs = NdiRuntime.searchDirsFor(
            LINUX,
            mapOf("NDI_RUNTIME_DIR_V5" to "/opt/ndi5", "NDI_RUNTIME_DIR_V6" to "/opt/ndi6"),
        )
        assertTrue(dirs.indexOf("/opt/ndi6") < dirs.indexOf("/opt/ndi5"))
    }

    @Test
    fun `a custom path outranks both env vars and the defaults`() {
        val dirs = NdiRuntime.searchDirsFor(
            LINUX,
            mapOf("NDI_RUNTIME_DIR_V6" to "/opt/ndi6"),
            customPath = "/home/op/ndi",
        )
        assertEquals("/home/op/ndi", dirs.first())
    }

    @Test
    fun `a blank or whitespace custom path is not searched`() {
        assertTrue(NdiRuntime.searchDirsFor(LINUX, emptyMap(), customPath = "   ").none { it.isBlank() })
        assertEquals(
            NdiRuntime.searchDirsFor(LINUX, emptyMap()),
            NdiRuntime.searchDirsFor(LINUX, emptyMap(), customPath = "  "),
        )
    }

    @Test
    fun `a directory named twice is only searched once`() {
        val dirs = NdiRuntime.searchDirsFor(
            LINUX,
            mapOf("NDI_RUNTIME_DIR_V6" to "/usr/lib", "NDI_RUNTIME_DIR_V5" to "/usr/lib"),
        )
        assertEquals(dirs.size, dirs.distinct().size)
    }

    @Test
    fun `an empty env var is ignored rather than searched as an empty path`() {
        val dirs = NdiRuntime.searchDirsFor(LINUX, mapOf("NDI_RUNTIME_DIR_V6" to ""))
        assertTrue(dirs.none { it.isEmpty() })
    }

    @Test
    fun `windows defaults are derived from ProgramFiles`() {
        val dirs = NdiRuntime.defaultInstallDirsFor(WINDOWS, mapOf("ProgramFiles" to "C:\\Program Files"))
        assertTrue(dirs.any { it.contains("NDI 6 Runtime") })
        assertTrue(dirs.any { it.contains("NDI 5 Runtime") })
    }

    @Test
    fun `windows defaults are empty rather than wrong when ProgramFiles is unset`() {
        assertTrue(NdiRuntime.defaultInstallDirsFor(WINDOWS, emptyMap()).isEmpty())
    }

    @Test
    fun `macos defaults cover both the sdk install and homebrew on apple silicon`() {
        val dirs = NdiRuntime.defaultInstallDirsFor(MAC, emptyMap())
        assertTrue(dirs.any { it.contains("NDI SDK for Apple") })
        assertTrue(dirs.contains("/opt/homebrew/lib"))
    }

    @Test
    fun `locate returns the first library that exists`() {
        val found = NdiRuntime.locate(
            LINUX,
            mapOf("NDI_RUNTIME_DIR_V6" to "/opt/ndi6"),
            exists = { it == "/opt/ndi6/libndi.so.6" },
        )
        assertEquals("/opt/ndi6/libndi.so.6", found)
    }

    @Test
    fun `locate falls through to an older soname in the same directory`() {
        val found = NdiRuntime.locate(
            LINUX,
            mapOf("NDI_RUNTIME_DIR_V6" to "/opt/ndi"),
            exists = { it == "/opt/ndi/libndi.so.5" },
        )
        assertEquals("/opt/ndi/libndi.so.5", found)
    }

    @Test
    fun `locate tolerates a trailing separator on the search directory`() {
        val found = NdiRuntime.locate(
            LINUX,
            mapOf("NDI_RUNTIME_DIR_V6" to "/opt/ndi/"),
            exists = { it == "/opt/ndi/libndi.so.6" },
        )
        assertEquals("/opt/ndi/libndi.so.6", found)
    }

    @Test
    fun `locate returns null when nothing is installed`() {
        assertNull(NdiRuntime.locate(LINUX, emptyMap(), exists = { false }))
    }

    @Test
    fun `detect against the real machine never throws`() {
        // Whatever this machine has or hasn't got, discovery must be a lookup and not a failure.
        NdiRuntime.detect()
    }
}
