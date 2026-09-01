package org.churchpresenter.app.churchpresenter.composables

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

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

    @get:Rule
    val temp = TemporaryFolder()

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
        val resources = temp.newFolder()
        File(resources, "ffmpeg.exe").writeText("not really ffmpeg")

        assertEquals(
            File(resources, "ffmpeg.exe").absolutePath,
            bundledFfmpegPath("Windows 11", resources),
        )
        assertNull(bundledFfmpegPath("Mac OS X", resources), "the mac bundle names it 'ffmpeg'")
    }

    @Test
    fun `a build whose fetch task has not run bundles nothing`() {
        assertNull(bundledFfmpegPath("Mac OS X", temp.newFolder()), "an empty resources directory")
        assertNull(bundledFfmpegPath("Mac OS X", null), "an unpackaged run with no source tree")
    }

    @Test
    fun `a directory named ffmpeg is not a program`() {
        val resources = temp.newFolder()
        File(resources, "ffmpeg").mkdir()

        assertNull(bundledFfmpegPath("Linux", resources))
    }

    @Test
    fun `a packaged app reads the resources directory it was told about`() {
        val packaged = temp.newFolder()

        assertEquals(
            packaged,
            appResourcesDirFrom(packaged.absolutePath, temp.newFolder(), "Mac OS X"),
            "compose.application.resources.dir wins over any search",
        )
    }

    @Test
    fun `a run from source walks up to this platforms appResources directory`() {
        val repo = temp.newFolder()
        val resources = File(repo, "composeApp/src/jvmMain/appResources/macos").apply { mkdirs() }
        val deepInside = File(repo, "composeApp/build/classes").apply { mkdirs() }

        assertEquals(resources, appResourcesDirFrom(null, deepInside, "Mac OS X"))
    }

    @Test
    fun `a run from nowhere near the repo finds nothing rather than guessing`() {
        assertNull(appResourcesDirFrom(null, temp.newFolder(), "Mac OS X"))
        assertNull(appResourcesDirFrom(null, temp.newFolder(), "FreeBSD"), "an OS we do not package")
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
        val resources = temp.newFolder()
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
}
