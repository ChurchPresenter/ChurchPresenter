package org.churchpresenter.omt

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MAC = "Mac OS X"
private const val WINDOWS = "Windows 11"
private const val LINUX = "Linux"
private val SEP = File.separator

class OmtRuntimeTest {

    @Test
    fun `each platform has its own library file name`() {
        assertEquals("libomt.dylib", OmtRuntime.libraryFileNameFor(MAC))
        assertEquals("libomt.dll", OmtRuntime.libraryFileNameFor(WINDOWS))
        assertEquals("libomt.so", OmtRuntime.libraryFileNameFor(LINUX))
        assertEquals("libomt.dylib", OmtRuntime.libraryFileNameFor("Darwin"))
    }

    @Test
    fun `Windows has no system directory to guess at, and the others do`() {
        assertTrue(OmtRuntime.systemDirsFor(WINDOWS).isEmpty())
        assertTrue("/opt/homebrew/lib" in OmtRuntime.systemDirsFor(MAC))
        assertTrue("/usr/lib/x86_64-linux-gnu" in OmtRuntime.systemDirsFor(LINUX))
    }

    @Test
    fun `the override comes first, then the bundled copy, then the system`() {
        val dirs = OmtRuntime.searchDirsFor(MAC, customPath = " /custom ", bundledDir = "/bundle")
        assertEquals(listOf("/custom", "/bundle", "/usr/local/lib", "/opt/homebrew/lib"), dirs)
    }

    @Test
    fun `an override naming the library file itself is taken as its folder`() {
        val dirs = OmtRuntime.searchDirsFor(LINUX, customPath = "/opt/omt/libomt.so")
        assertEquals("/opt/omt/", dirs.first())
    }

    @Test
    fun `blank settings add nothing and repeats are dropped`() {
        assertEquals(OmtRuntime.systemDirsFor(MAC), OmtRuntime.searchDirsFor(MAC, "  ", ""))
        assertEquals(OmtRuntime.systemDirsFor(MAC), OmtRuntime.searchDirsFor(MAC, bundledDir = "/usr/local/lib"))
    }

    @Test
    fun `the first directory holding the library wins`() {
        val found = OmtRuntime.locate(MAC, customPath = "/a/", bundledDir = "/b") { it == "/b${SEP}libomt.dylib" }
        assertEquals("/b${SEP}libomt.dylib", found)
    }

    @Test
    fun `nowhere holding it is null`() {
        assertNull(OmtRuntime.locate(LINUX, "/a", "/b") { false })
    }

    @Test
    fun `detect looks at the real filesystem`() {
        val dir = createTempDirectory("omt-detect").toFile()
        try {
            val name = OmtRuntime.libraryFileNameFor(System.getProperty("os.name").orEmpty())
            File(dir, name).writeText("stand-in")
            assertEquals(File(dir, name).path, OmtRuntime.detect(customPath = dir.path))
        } finally {
            dir.deleteRecursively()
        }
    }
}
