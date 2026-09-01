package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the camera and window listings *drive* the tools whose output
 * `SourcePropertiesDeviceListingTest` parses.
 *
 * The parsers were always testable; the sequences around them were not, because each ran its command
 * inline. Each now takes a [CommandRunner], so what is asserted here is the part that decides what
 * the operator sees: which command is built for a given device path, which platform's enumeration a
 * machine qualifies for, and — the part with real behaviour in it — when a fallback tool is consulted
 * and when it is skipped. Getting one of those wrong shows up as an empty dropdown, never as an error.
 *
 * `osName` is passed in rather than faked through the system property on purpose: skiko resolves the
 * host OS from `os.name` in a JVM-wide `by lazy`, so swapping it here would break every later Compose
 * test in the same JVM.
 */
class DeviceEnumerationTest {

    // ── Which enumeration a machine qualifies for ──────────────────────────────────────────────

    @Test
    fun `a windows machine is asked for its DirectShow cameras`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        cameraDevicesFor("windows 11", runner::run)

        assertEquals(
            listOf("ffmpeg", "powershell"), runner.programs,
            "the PnP query is the fallback, and an empty ffmpeg listing is what calls for it",
        )
    }

    @Test
    fun `PowerShell is not run when ffmpeg already listed the cameras`() {
        // Exit code 1 on purpose: `-i dummy` always exits non-zero, and the listing is read off
        // stderr regardless — the code must go on ignoring the exit code.
        val runner = FakeCommandRunner { command ->
            if (command.first() == "ffmpeg") CommandResult(1, "[dshow @ 0x1] \"Integrated Webcam\" (video)")
            else null
        }

        val devices = cameraDevicesFor("windows 11", runner::run)

        assertEquals(listOf("ffmpeg"), runner.programs, "the slowest call on this path is skipped outright")
        assertEquals(listOf("Integrated Webcam"), devices.map { it.name })
    }

    @Test
    fun `an absent ffmpeg leaves the PowerShell fallback to name the cameras`() {
        // Exactly what an unstartable process yields, which is how a machine without ffmpeg reads.
        val runner = FakeCommandRunner { command ->
            if (command.first() == "ffmpeg") CommandResult(-1, "") else CommandResult(0, "Surface Camera Front")
        }

        val devices = cameraDevicesFor("windows 11", runner::run)

        assertEquals(listOf("ffmpeg", "powershell"), runner.programs)
        assertEquals(
            listOf("Surface Camera Front"), devices.map { it.name },
            "the operator still sees their camera named, beside the hint saying to install ffmpeg",
        )
    }

    @Test
    fun `the windows enumeration commands are bounded`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        cameraDevicesFor("windows 11", runner::run)

        assertEquals(
            listOf(10L, 15L), runner.timeouts,
            "both used to wait forever, so a DirectShow filter or a slow WMI query hung the caller",
        )
    }

    // ── What the enumeration reports about itself ─────────────────────────────────────────────

    @Test
    fun `a listing ffmpeg produced is attributed to ffmpeg`() {
        val runner = FakeCommandRunner { command ->
            if (command.first() == "ffmpeg") {
                CommandResult(1, "[dshow @ 0x1] \"Integrated Webcam\" (video)\n[dshow @ 0x1] \"Capture Card\" (video)")
            } else {
                null
            }
        }

        val facts = enumerateCameras("windows 11", runner::run).facts

        assertEquals(CameraEnumerator.DSHOW, facts.enumerator)
        assertEquals(2, facts.ffmpegListedCount)
        assertEquals(0, facts.fallbackListedCount, "the fallback was never consulted, let alone counted")
    }

    @Test
    fun `a listing the PnP inventory produced is attributed to the fallback`() {
        val runner = FakeCommandRunner { command ->
            if (command.first() == "ffmpeg") CommandResult(-1, "") else CommandResult(0, "Surface Camera Front")
        }

        val facts = enumerateCameras("windows 11", runner::run).facts

        assertEquals(CameraEnumerator.PNP_FALLBACK, facts.enumerator)
        assertEquals(0, facts.ffmpegListedCount)
        assertEquals(1, facts.fallbackListedCount)
    }

    @Test
    fun `a machine neither tool found a camera on counts nothing`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        val facts = enumerateCameras("windows 11", runner::run).facts

        assertEquals(0, facts.ffmpegListedCount)
        assertEquals(
            0, facts.fallbackListedCount,
            "a machine with no camera must be distinguishable from one whose cameras cannot be opened",
        )
    }

    @Test
    fun `a mac listing ffmpeg produced is attributed to avfoundation`() {
        val runner = FakeCommandRunner { command ->
            if (command.first() == "ffmpeg") {
                CommandResult(
                    1,
                    """
                    [AVFoundation indev @ 0x1] AVFoundation video devices:
                    [AVFoundation indev @ 0x1] [0] FaceTime HD Camera
                    """.trimIndent(),
                )
            } else {
                CommandResult(0, "")
            }
        }

        assertEquals(CameraEnumerator.AVFOUNDATION, enumerateCameras("mac os x", runner::run).facts.enumerator)
    }

    @Test
    fun `a mac listing only system_profiler produced is attributed to the fallback`() {
        val runner = FakeCommandRunner { command ->
            if (command.first() == "system_profiler") CommandResult(0, "    FaceTime HD Camera:\n")
            else CommandResult(-1, "")
        }

        assertEquals(
            CameraEnumerator.SYSTEM_PROFILER_FALLBACK,
            enumerateCameras("mac os x", runner::run).facts.enumerator,
        )
    }

    @Test
    fun `an OS with no enumerator says so rather than looking like a machine with no camera`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        assertEquals(CameraEnumerator.UNSUPPORTED_OS, enumerateCameras("TestOS", runner::run).facts.enumerator)
    }

    @Test
    fun `the pure enumeration never decides whether ffmpeg is installed`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        assertEquals(
            false, enumerateCameras("windows 11", runner::run).facts.ffmpegAvailable,
            "that is the impure caller's to fill in, which is what keeps this function drivable from a fake",
        )
    }

    @Test
    fun `the PowerShell query asks only for camera-class devices`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        cameraDevicesFor("windows 11", runner::run)

        val query = runner.calls.first { it.first() == "powershell" }.last()
        assertTrue(query.contains("PNPClass -eq 'Camera'"), "cameras are what the picker offers")
        assertTrue(
            !query.contains("'Image'"),
            "the Image class is scanners and other still-image devices, offered as cameras until now",
        )
    }

    @Test
    fun `a mac is asked for both its physical and its virtual cameras`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        macCamerasFrom(runner::run)

        assertEquals(listOf("system_profiler", "ffmpeg"), runner.programs)
    }

    @Test
    fun `a linux machine reads its cameras off the filesystem instead of running anything`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        cameraDevicesFor("linux", runner::run)

        assertTrue(runner.calls.isEmpty(), "the /dev/video* nodes need no external tool")
    }

    @Test
    fun `an unrecognised platform runs nothing and offers no cameras`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        assertEquals(emptyList(), cameraDevicesFor("plan 9", runner::run))
        assertTrue(runner.calls.isEmpty())
    }

    // ── Format listings ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a dshow format query addresses the device by the name ffmpeg needs back`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        dshowFormatsFrom(":dshow-vdev=Integrated Webcam", runner::run)

        assertEquals(
            listOf("ffmpeg", "-f", "dshow", "-list_options", "true", "-i", "video=Integrated Webcam"),
            runner.calls.single(),
            "the stored path's prefix has to come off before ffmpeg will recognise the name",
        )
    }

    @Test
    fun `a format query that opens the device is given a timeout`() {
        // These probes open the device to interrogate it, and a camera already held by another
        // application never answers. Without the bound the properties panel would hang on selection.
        val dshow = FakeCommandRunner.alwaysReturning("")
        val avfoundation = FakeCommandRunner.alwaysReturning("")

        dshowFormatsFrom("Cam", dshow::run)
        avfoundationFormatsFrom("0", avfoundation::run)

        assertEquals(5L, dshow.timeouts.single())
        assertEquals(5L, avfoundation.timeouts.single())
    }

    @Test
    fun `v4l2 formats come from ffmpeg when ffmpeg knows any`() {
        val runner = FakeCommandRunner { command ->
            if (command.first() == "ffmpeg") CommandResult(0, "[video4linux2] 1280x720") else null
        }

        val formats = v4l2FormatsFrom("/dev/video0", runner::run)

        assertEquals(listOf("1280x720 @ 30fps"), formats.map { it.displayName })
        assertEquals(listOf("ffmpeg"), runner.programs, "v4l2-ctl must not be run for nothing")
    }

    @Test
    fun `v4l2 falls back to v4l2-ctl when ffmpeg reports no sizes`() {
        val runner = FakeCommandRunner { command ->
            when (command.first()) {
                "ffmpeg" -> CommandResult(0, "no formats here")
                else -> CommandResult(0, "Size: Discrete 640x480\n  Interval: 0.033s (30.000 fps)")
            }
        }

        val formats = v4l2FormatsFrom("/dev/video0", runner::run)

        assertEquals(listOf("640x480 @ 30fps"), formats.map { it.displayName })
        assertEquals(listOf("ffmpeg", "v4l2-ctl"), runner.programs)
    }

    @Test
    fun `v4l2 offers nothing when neither tool reports a size`() {
        val runner = FakeCommandRunner.alwaysFailing()

        assertEquals(emptyList(), v4l2FormatsFrom("/dev/video0", runner::run))
        assertEquals(listOf("ffmpeg", "v4l2-ctl"), runner.programs, "the fallback is still tried")
    }

    @Test
    fun `an avfoundation format query asks for an impossible size, not the v4l2 list option`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        avfoundationFormatsFrom("0", runner::run)

        assertEquals(
            listOf("ffmpeg", "-f", "avfoundation", "-video_size", "1x1", "-i", "0:none"),
            runner.calls.single(),
            "avfoundation has no -list_formats; ffmpeg rejects the whole command line if it is passed",
        )
    }

    // ── Pairing a device path with the backend that can read it ───────────────────────────────

    @Test
    fun `each platform's format listing is reached by its own device path`() {
        val cases = listOf(
            Triple("windows 11", "dshow://:dshow-vdev=Cam", "dshow"),
            Triple("linux", "v4l2:///dev/video0", "v4l2"),
            Triple("mac os x", "avfoundation://0", "avfoundation"),
        )

        cases.forEach { (osName, devicePath, expectedBackend) ->
            val runner = FakeCommandRunner.alwaysReturning("")

            cameraFormatsFor(osName, devicePath, "Cam", runner::run)

            assertTrue(
                runner.calls.first().contains(expectedBackend),
                "$osName with $devicePath must be read by $expectedBackend",
            )
        }
    }

    @Test
    fun `a device path saved on another platform asks nothing of a backend that is not there`() {
        // A scene file moves between machines. A Windows dshow path opened on a Mac has no format
        // list to offer, and must not invoke a DirectShow query that cannot work.
        val runner = FakeCommandRunner.alwaysReturning("")

        val formats = cameraFormatsFor("mac os x", "dshow://:dshow-vdev=Cam", "Cam", runner::run)

        assertEquals(emptyList(), formats)
        assertTrue(runner.calls.isEmpty())
    }

    @Test
    fun `a path matching no known scheme has no formats on any platform`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        listOf("windows 11", "linux", "mac os x").forEach { osName ->
            assertEquals(emptyList(), cameraFormatsFor(osName, "bogus://x", "Cam", runner::run))
        }
        assertTrue(runner.calls.isEmpty())
    }

    // ── Window listings ───────────────────────────────────────────────────────────────────────

    private val stackingList = "_NET_CLIENT_LIST_STACKING(WINDOW): window id # 0x1400003, 0x1600005"

    @Test
    fun `linux windows are named one xprop call at a time`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.contains("0x1400003") -> CommandResult(0, "_NET_WM_NAME(UTF8_STRING) = \"Firefox\"")
                else -> CommandResult(0, "_NET_WM_NAME(UTF8_STRING) = \"Terminal\"")
            }
        }

        val windows = linuxWindowsFrom(runner::run)

        assertEquals(listOf("Firefox", "Terminal"), windows.map { it.title })
        assertEquals(0x1400003L, windows.first().id, "the hexadecimal id is decoded, not stored as text")
    }

    @Test
    fun `an unnamed window is dropped without stopping the walk`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.contains("0x1400003") -> CommandResult(0, "_NET_WM_NAME:  not found.")
                else -> CommandResult(0, "_NET_WM_NAME(UTF8_STRING) = \"Terminal\"")
            }
        }

        assertEquals(listOf("Terminal"), linuxWindowsFrom(runner::run).map { it.title })
    }

    @Test
    fun `wmctrl is consulted only when xprop lists no windows at all`() {
        val runner = FakeCommandRunner { command ->
            if (command.contains("-root")) CommandResult(0, "no windows here")
            else CommandResult(0, "0x02400003  0 host Mozilla Firefox")
        }

        val windows = linuxWindowsFrom(runner::run)

        assertEquals(listOf("Mozilla Firefox"), windows.map { it.title })
        assertEquals(listOf("xprop", "wmctrl"), runner.programs)
    }

    @Test
    fun `wmctrl is consulted when xprop names none of the windows it listed`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "wmctrl" -> CommandResult(0, "0x02400003  0 host Mozilla Firefox")
                else -> CommandResult(0, "_NET_WM_NAME:  not found.")
            }
        }

        assertEquals(listOf("Mozilla Firefox"), linuxWindowsFrom(runner::run).map { it.title })
    }

    @Test
    fun `a wmctrl that is not installed is not parsed as a window list`() {
        // An absent tool comes back non-zero, and the shell's own "command not found" on stdout
        // would otherwise be split into columns and offered as a window.
        val runner = FakeCommandRunner { command ->
            if (command.first() == "wmctrl") CommandResult(127, "wmctrl: command not found")
            else CommandResult(0, "")
        }

        assertEquals(emptyList(), linuxWindowsFrom(runner::run))
    }

    @Test
    fun `a wmctrl that runs but lists nothing yields no windows`() {
        val runner = FakeCommandRunner { CommandResult(0, "") }

        assertEquals(emptyList(), linuxWindowsFrom(runner::run))
        assertEquals(listOf("xprop", "wmctrl"), runner.programs)
    }

    @Test
    fun `mac windows come from one AppleScript call`() {
        val runner = FakeCommandRunner.alwaysReturning("Inbox, Calendar, Notes")

        val windows = macWindowsFrom(runner::run)

        assertEquals(listOf("Inbox", "Calendar", "Notes"), windows.map { it.title })
        assertEquals("osascript", runner.programs.single())
    }

    @Test
    fun `each platform's window listing is reached by its own os name`() {
        val linux = FakeCommandRunner.alwaysReturning("")
        openWindowsFor("linux", linux::run)
        assertEquals("xprop", linux.programs.first())

        val mac = FakeCommandRunner.alwaysReturning("")
        openWindowsFor("mac os x", mac::run)
        assertEquals("osascript", mac.programs.single())
    }

    @Test
    fun `an unrecognised platform lists no windows and runs nothing`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        assertEquals(emptyList(), openWindowsFor("plan 9", runner::run))
        assertTrue(runner.calls.isEmpty())
    }
}
