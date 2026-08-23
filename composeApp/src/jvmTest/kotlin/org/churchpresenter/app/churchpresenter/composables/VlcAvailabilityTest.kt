package org.churchpresenter.app.churchpresenter.composables

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.CommandResult

/**
 * What the app concludes about VLC, and which of the three "no video" messages the operator gets.
 *
 * The distinction is the whole point: "VLC isn't installed" tells someone to go and install it,
 * which is actively wrong advice when VLC is sitting right there but built for the wrong CPU, or
 * installed but missing the Visual C++ runtime it needs. Those are different problems with different
 * fixes, and the only thing separating them is the text libvlc failed with.
 *
 * The public `isVlcArchMismatch`/`isVlcLoadFailed` getters read two globals that a test cannot drive:
 * `isVlcAvailable` caches the result of actually loading libvlc on whichever machine runs the suite,
 * so on a box with working VLC it can only ever answer one way and the interesting branches are
 * unreachable. The decisions are therefore taken over their two inputs directly, and the getters are
 * one call each onto them.
 *
 * Detection is likewise driven per-platform through an `osName` parameter rather than by faking the
 * system property, which skiko latches JVM-wide.
 */
class VlcAvailabilityTest {

    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }

    private fun dirWithVlcLib(libName: String = "libvlc.so"): Path {
        val dir = Files.createTempDirectory("cp-vlc-avail")
        tempDirs.add(dir)
        Files.createFile(dir.resolve(libName))
        return dir
    }

    private fun emptyDir(): Path {
        val dir = Files.createTempDirectory("cp-vlc-empty")
        tempDirs.add(dir)
        return dir
    }

    // ── Telling the three failures apart ──────────────────────────────────────────────────────

    @Test
    fun `an incompatible architecture is reported as an architecture problem`() {
        // x86_64 VLC under an arm64 JVM. VLC is installed; telling the user to install it is useless.
        val reason = "dlopen(/Applications/VLC.app/.../libvlc.dylib, 1): no suitable image found. " +
            "mach-o, but wrong architecture: incompatible architecture"

        assertTrue(vlcArchMismatchFrom(available = false, reason = reason))
        assertFalse(
            vlcLoadFailedFrom(available = false, reason = reason),
            "an arch mismatch has its own message and must not also read as a generic load failure",
        )
    }

    @Test
    fun `the architecture check ignores the case libvlc happened to use`() {
        assertTrue(vlcArchMismatchFrom(false, "Incompatible Architecture (arm64e)"))
    }

    @Test
    fun `a missing install is neither an architecture problem nor a load failure`() {
        // "not_found" is the sentinel checkVlcAvailable writes when nothing is on disk at all.
        assertFalse(vlcArchMismatchFrom(available = false, reason = "not_found"))
        assertFalse(
            vlcLoadFailedFrom(available = false, reason = "not_found"),
            "this is the one case where 'install VLC' is the right advice",
        )
    }

    @Test
    fun `an install that is present but will not load is a load failure`() {
        // Windows reports a missing VC++ redistributable this way, with libvlc.dll itself present.
        val reason = "Unable to load library 'libvlc': The specified module could not be found."

        assertTrue(vlcLoadFailedFrom(available = false, reason = reason))
        assertFalse(vlcArchMismatchFrom(available = false, reason = reason))
    }

    @Test
    fun `a blank reason is not reported as a load failure`() {
        assertFalse(vlcLoadFailedFrom(available = false, reason = ""))
    }

    @Test
    fun `working VLC is never any kind of failure, whatever the stale reason says`() {
        // The reason string is not cleared on success, so availability has to win over it.
        assertFalse(vlcArchMismatchFrom(available = true, reason = "incompatible architecture"))
        assertFalse(vlcLoadFailedFrom(available = true, reason = "some earlier error"))
    }

    @Test
    fun `the two failure states are mutually exclusive across a range of real reasons`() {
        val reasons = listOf(
            "not_found",
            "",
            "incompatible architecture",
            "The specified module could not be found.",
            "unknown error",
        )

        reasons.forEach { reason ->
            val both = vlcArchMismatchFrom(false, reason) && vlcLoadFailedFrom(false, reason)
            assertFalse(both, "\"$reason\" must select at most one message, not two")
        }
    }

    // ── Finding an install ────────────────────────────────────────────────────────────────────

    @Test
    fun `a configured custom directory holding libvlc settles it without running anything`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        assertTrue(vlcInstalledOn("linux", dirWithVlcLib().toString(), runner::run))
        assertTrue(runner.calls.isEmpty(), "a deliberate choice needs no corroboration")
    }

    @Test
    fun `a custom directory is honoured on every platform`() {
        val runner = FakeCommandRunner.alwaysFailing()

        listOf("linux", "windows 11", "mac os x").forEach { osName ->
            assertTrue(vlcInstalledOn(osName, dirWithVlcLib("libvlc.dylib").toString(), runner::run))
        }
    }

    @Test
    fun `a custom directory without libvlc in it contributes nothing`() {
        // Compared against the no-custom-path answer rather than asserted `false`: the machine running
        // the suite may well have VLC in one of the standard places, and that is allowed to win. What
        // must hold is that an empty custom directory neither invents an install nor hides a real one.
        val runner = FakeCommandRunner.alwaysFailing()

        assertEquals(
            vlcInstalledOn("mac os x", "", runner::run),
            vlcInstalledOn("mac os x", emptyDir().toString(), runner::run),
        )
    }

    @Test
    fun `a custom path that is not a usable path at all is survived`() {
        val runner = FakeCommandRunner.alwaysFailing()

        // A NUL is the one character no filesystem accepts, so Paths.get itself throws on it.
        assertEquals(
            vlcInstalledOn("mac os x", "", runner::run),
            vlcInstalledOn("mac os x", "\u0000not/a/path", runner::run),
            "a malformed setting must not throw, and must not change the answer",
        )
    }

    @Test
    fun `linux falls back to asking whether vlc is on the PATH`() {
        val found = FakeCommandRunner { CommandResult(0, "/usr/bin/vlc") }

        assertTrue(vlcInstalledOn("linux", "", found::run))
        assertEquals(listOf("which", "vlc"), found.calls.single())
    }

    @Test
    fun `linux with vlc nowhere on the PATH has no install`() {
        val missing = FakeCommandRunner { CommandResult(1, "") }

        assertFalse(vlcInstalledOn("linux", "", missing::run))
    }

    @Test
    fun `windows and macOS do not fall back to which`() {
        // `which vlc` proves the player is on PATH, not that libvlc is anywhere JNA will look. Only
        // Linux distributions reliably ship the two together.
        listOf("windows 11", "mac os x", "darwin").forEach { osName ->
            val runner = FakeCommandRunner { CommandResult(0, "/usr/bin/vlc") }

            vlcInstalledOn(osName, "", runner::run)

            assertTrue(runner.calls.isEmpty(), "$osName must not consult which")
        }
    }

    // ── Per-platform search paths ─────────────────────────────────────────────────────────────

    @Test
    fun `a linux search does not stray onto the windows or mac candidates`() {
        // Asserted as an invariant rather than a literal: the suite runs on all three platforms and
        // the answer depends on what is installed. What must hold is that a platform only ever
        // returns one of its own paths.
        val path = detectVlcInstallPathFor("linux")

        assertTrue(
            path.isEmpty() || path.startsWith("/usr/lib") || path.startsWith("/snap"),
            "a linux search returned \"$path\"",
        )
    }

    @Test
    fun `a mac search only ever points into VLC's app bundle`() {
        val path = detectVlcInstallPathFor("mac os x")

        assertTrue(
            path.isEmpty() || path.startsWith("/Applications/VLC.app"),
            "a mac search returned \"$path\"",
        )
    }

    @Test
    fun `a windows search only ever points at a VideoLAN directory`() {
        val path = detectVlcInstallPathFor("windows 11")

        assertTrue(
            path.isEmpty() || path.contains("VideoLAN") || path.contains("VLC"),
            "a windows search returned \"$path\"",
        )
    }

    @Test
    fun `an unrecognised platform is searched as if it were linux`() {
        // The `else` branch is the Linux one, so a BSD or Solaris JVM still gets a sensible search
        // rather than being told VLC cannot exist.
        val path = detectVlcInstallPathFor("freebsd")

        assertTrue(path.isEmpty() || path.startsWith("/usr/lib") || path.startsWith("/snap"))
    }

    @Test
    fun `detection agrees with itself when asked twice`() {
        assertEquals(detectVlcInstallPathFor("linux"), detectVlcInstallPathFor("linux"))
    }
}
