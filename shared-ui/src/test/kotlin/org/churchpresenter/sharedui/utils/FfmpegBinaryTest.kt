package org.churchpresenter.sharedui.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which ffmpeg the app decides to run, and why.
 *
 * The order is the whole of it. The app ships its own ffmpeg (issue #464: on a Mac with none
 * installed, nothing ever opens a camera, so macOS is never asked for permission and the app never
 * gets a Privacy → Camera entry), and an operator may still point at their own build. Getting the
 * precedence wrong is invisible — a camera that works, on a binary nobody chose.
 *
 * These drive the pure functions rather than [FfmpegBinary] itself, which caches a real probe of
 * the machine the suite happens to run on.
 */
class FfmpegBinaryTest {

    private val tempRoot: File = Files.createTempDirectory("ffmpeg-binary").toFile()

    @AfterTest
    fun cleanUp() {
        tempRoot.deleteRecursively()
    }

    private fun newFolder(): File = Files.createTempDirectory(tempRoot.toPath(), "dir").toFile()

    @Test
    fun `the operators own ffmpeg wins over the bundled one`() {
        val order = ffmpegSearchOrder(
            customPath = "/opt/mine/ffmpeg",
            bundledPath = "/app/resources/ffmpeg",
            discovered = listOf("ffmpeg", "/usr/bin/ffmpeg"),
        )

        assertEquals("/opt/mine/ffmpeg", order.first(), "an explicit choice is not a suggestion")
    }

    @Test
    fun `the bundled ffmpeg wins over whatever is installed on the machine`() {
        val order = ffmpegSearchOrder(
            customPath = "",
            bundledPath = "/app/resources/ffmpeg",
            discovered = listOf("ffmpeg", "/opt/homebrew/bin/ffmpeg"),
        )

        assertEquals(
            listOf("/app/resources/ffmpeg", "ffmpeg", "/opt/homebrew/bin/ffmpeg"),
            order,
            "the app behaves the same on every machine it is installed on",
        )
    }

    @Test
    fun `a build with no bundled ffmpeg falls back to the installed one`() {
        val order = ffmpegSearchOrder(
            customPath = "",
            bundledPath = null,
            discovered = listOf("ffmpeg", "/usr/bin/ffmpeg"),
        )

        assertEquals(listOf("ffmpeg", "/usr/bin/ffmpeg"), order)
    }

    @Test
    fun `a blank custom path is not a candidate`() {
        val order = ffmpegSearchOrder(
            customPath = "   ",
            bundledPath = null,
            discovered = listOf("ffmpeg"),
        )

        assertEquals(listOf("ffmpeg"), order, "whitespace is how a cleared field arrives")
    }

    @Test
    fun `a custom path already in the discovered list is not tried twice`() {
        val order = ffmpegSearchOrder(
            customPath = "/usr/bin/ffmpeg",
            bundledPath = null,
            discovered = listOf("ffmpeg", "/usr/bin/ffmpeg"),
        )

        assertEquals(listOf("/usr/bin/ffmpeg", "ffmpeg"), order)
    }

    @Test
    fun `the bundled ffmpeg is found by the name its platform packages it under`() {
        val resources = newFolder()
        File(resources, "ffmpeg.exe").writeText("not really ffmpeg")

        assertEquals(
            File(resources, "ffmpeg.exe").absolutePath,
            bundledFfmpegPath("Windows 11", resources),
        )
        assertNull(bundledFfmpegPath("Mac OS X", resources), "the mac bundle names it 'ffmpeg'")
    }

    @Test
    fun `a build whose fetch task has not run bundles nothing`() {
        assertNull(bundledFfmpegPath("Mac OS X", newFolder()), "an empty resources directory")
        assertNull(bundledFfmpegPath("Mac OS X", null), "an unpackaged run with no source tree")
    }

    @Test
    fun `a directory named ffmpeg is not a program`() {
        val resources = newFolder()
        File(resources, "ffmpeg").mkdir()

        assertNull(bundledFfmpegPath("Linux", resources))
    }

    @Test
    fun `a packaged app reads the resources directory it was told about`() {
        val packaged = newFolder()

        assertEquals(
            packaged,
            appResourcesDirFrom(packaged.absolutePath, newFolder(), "Mac OS X"),
            "compose.application.resources.dir wins over any search",
        )
    }

    @Test
    fun `a run from source walks up to this platforms appResources directory`() {
        val repo = newFolder()
        val resources = File(repo, "composeApp/src/jvmMain/appResources/macos").apply { mkdirs() }
        val deepInside = File(repo, "composeApp/build/classes").apply { mkdirs() }

        assertEquals(resources, appResourcesDirFrom(null, deepInside, "Mac OS X"))
    }

    @Test
    fun `a run from nowhere near the repo finds nothing rather than guessing`() {
        assertNull(appResourcesDirFrom(null, newFolder(), "Mac OS X"))
        assertNull(appResourcesDirFrom(null, newFolder(), "FreeBSD"), "an OS we do not package")
    }

    @Test
    fun `each packaged platform knows its own appResources directory`() {
        assertEquals("macos", appResourcesOsDirName("Mac OS X"))
        assertEquals("windows", appResourcesOsDirName("Windows 11"))
        assertEquals("linux", appResourcesOsDirName("Linux"))
        assertNull(appResourcesOsDirName("FreeBSD"), "we do not package for it, so we ship none")
    }

    @Test
    fun `the search stops at the first candidate that actually runs`() {
        val resources = newFolder()
        val bundled = File(resources, "ffmpeg").apply { writeText("x"); setExecutable(true) }
        val tried = mutableListOf<String>()

        val chosen = resolveFfmpegPath(
            candidates = ffmpegSearchOrder("", bundled.absolutePath, listOf("ffmpeg", "/usr/bin/ffmpeg")),
            isExecutable = { true },
        ) { tried += it; it == bundled.absolutePath }

        assertEquals(bundled.absolutePath, chosen)
        assertEquals(listOf(bundled.absolutePath), tried, "nothing after the winner is probed")
    }

    @Test
    fun `nothing answering leaves the bare name, which is what the missing message is about`() {
        val chosen = resolveFfmpegPath(
            candidates = ffmpegSearchOrder("/nope/ffmpeg", null, listOf("ffmpeg")),
            isExecutable = { false },
        ) { false }

        assertEquals("ffmpeg", chosen)
        assertTrue(chosen.isNotBlank(), "a caller never has to handle a null path")
    }

    @Test
    fun `ffmpeg is looked for where it is installed, not only where PATH points`() {
        // A desktop app does not inherit the shell's PATH: a Finder-launched macOS .app gets
        // launchd's default, which has neither Homebrew prefix on it. Resolving only the bare name
        // is what made a camera appear in the dropdown and then never show a picture (issue #431).
        val mac = ffmpegCandidatePaths("Mac OS X") { null }
        assertEquals("ffmpeg", mac.first(), "a configured PATH must still win: $mac")
        assertTrue("/opt/homebrew/bin/ffmpeg" in mac, mac.toString())
        assertTrue("/usr/local/bin/ffmpeg" in mac, mac.toString())

        val linux = ffmpegCandidatePaths("Linux") { null }
        assertTrue("/usr/bin/ffmpeg" in linux, linux.toString())
        assertTrue("/snap/bin/ffmpeg" in linux, linux.toString())

        val windows = ffmpegCandidatePaths("Windows 11") { if (it == "ProgramFiles") "C:\\Program Files" else null }
        assertTrue(windows.any { it.endsWith("ffmpeg.exe") }, windows.toString())
        assertTrue(windows.none { it.startsWith("/") }, "no POSIX paths on Windows: $windows")
    }

    @Test
    fun `resolution takes the first candidate that actually runs`() {
        val candidates = listOf("ffmpeg", "/opt/homebrew/bin/ffmpeg")

        val tried = mutableListOf<String>()
        val resolved = resolveFfmpegPath(candidates, isExecutable = { false }) { tried += it; false }
        // Nothing answered, so the bare name is reported and the callers say "install ffmpeg" —
        // which is the right thing to say when no candidate exists.
        assertEquals("ffmpeg", resolved)
        assertEquals(listOf("ffmpeg"), tried, "an absolute path that does not exist must not be launched")

        // The Homebrew install a Finder-launched app cannot see on its PATH, but can still run.
        assertEquals(
            "/opt/homebrew/bin/ffmpeg",
            resolveFfmpegPath(candidates, isExecutable = { true }) { it != "ffmpeg" }
        )
    }
}
