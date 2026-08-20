package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where persistent data lands when the home directory is not usable.
 *
 * A Windows user reported startup failing to write the bundled KJV with
 * `FileNotFoundException (The system cannot find the path specified)`: `user.home` pointed at a
 * path that could not be created, `mkdirs()` had returned false unnoticed, and the app started
 * with no Bible. The resolution below is what stops an unusable home directory from being the end
 * of it.
 *
 * The cases drive the resolver through its parameters rather than by swapping `user.home` — the
 * property is JVM-wide and several singletons latch paths from it, which AGENT.md documents as a
 * source of cross-test leakage.
 */
class AppDataDirTest {

    private lateinit var scratch: File

    @BeforeTest
    fun createScratch() {
        scratch = Files.createTempDirectory("cp-appdatadir").toFile()
    }

    @AfterTest
    fun cleanUp() {
        scratch.deleteRecursively()
    }

    private fun dir(name: String): File = File(scratch, name).also { it.mkdirs() }

    /** A home directory that cannot be created: a path whose own parent is a regular file. */
    private fun unusableHome(): String =
        File(File(scratch, "blocker").also { it.writeText("not a directory") }, "home").path

    @Test
    fun `prefers the home directory`() {
        val home = dir("home")

        val resolved = AppDataDir.resolve(homeDir = home.path, osName = "Mac OS X", tempDir = scratch.path)

        assertEquals(File(home, ".churchpresenter").canonicalFile, resolved.canonicalFile)
        assertTrue(resolved.isDirectory, "the resolved directory is created")
    }

    @Test
    fun `falls back to the platform folder on Windows`() {
        val localAppData = dir("LocalAppData")

        val resolved = AppDataDir.resolve(
            homeDir = unusableHome(),
            osName = "Windows 11",
            tempDir = scratch.path,
            env = { name -> if (name == "LOCALAPPDATA") localAppData.path else null }
        )

        assertEquals(File(localAppData, "ChurchPresenter").canonicalFile, resolved.canonicalFile)
        assertTrue(resolved.isDirectory)
    }

    @Test
    fun `falls back to the XDG folder elsewhere`() {
        val xdg = dir("xdg")

        val resolved = AppDataDir.resolve(
            homeDir = unusableHome(),
            osName = "Linux",
            tempDir = scratch.path,
            env = { name -> if (name == "XDG_DATA_HOME") xdg.path else null }
        )

        assertEquals(File(xdg, "ChurchPresenter").canonicalFile, resolved.canonicalFile)
    }

    @Test
    fun `falls back to the temp directory when nothing else is usable`() {
        val resolved = AppDataDir.resolve(
            homeDir = unusableHome(),
            osName = "Windows 11",
            tempDir = scratch.path,
            env = { null }
        )

        assertEquals(File(scratch, "ChurchPresenter").canonicalFile, resolved.canonicalFile)
    }

    @Test
    fun `returns the home candidate when no location at all can be written`() {
        // Nothing usable anywhere: the caller then fails at its own write, naming the path it
        // wanted — the behaviour before this resolver existed, rather than a silent surprise.
        val home = unusableHome()

        val resolved = AppDataDir.resolve(homeDir = home, osName = "Linux", tempDir = null, env = { null })

        assertEquals(File(home, ".churchpresenter"), resolved)
        assertTrue(!resolved.exists())
    }
}
