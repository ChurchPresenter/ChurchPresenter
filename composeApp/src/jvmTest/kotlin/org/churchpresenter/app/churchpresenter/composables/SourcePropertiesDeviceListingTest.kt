package org.churchpresenter.app.churchpresenter.composables

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The device and window listings behind the Camera and Screen Capture panels.
 *
 * These are the app's hardware discovery: what a camera is called, how to address it, which
 * resolutions it offers, and which windows are open. All of it is scraped out of the text that
 * `ffmpeg`, `v4l2-ctl`, `system_profiler`, `xprop`, `wmctrl`, PowerShell and AppleScript print — and
 * every one of those tools prints something different, has changed its output between versions, and
 * only exists on one platform. That combination is why the parsing is worth testing directly: on any
 * given machine at most a third of it can ever run, the output can't be arranged by a fixture, and a
 * regex that has quietly stopped matching shows up as "No cameras found" rather than as an error.
 *
 * Each parser is fed real output shapes captured from the tools, including the older and newer
 * formats the code deliberately handles side by side, and the malformed lines it has to survive.
 *
 * This class covers the parsing only. The sequences that *drive* the tools — which command is built,
 * which platform's listing applies, when a fallback tool is consulted — are in `DeviceEnumerationTest`,
 * which supplies a stand-in [CommandRunner] in place of the machine.
 */
class SourcePropertiesDeviceListingTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile().also { tempDirs += it }

    // ── DirectShow formats (Windows) ──────────────────────────────────────────

    @Test
    fun `dshow formats are read from the min-max form ffmpeg prints`() {
        val formats = parseDshowFormats(
            """
            [dshow @ 0x1] DirectShow video device options (from video devices)
            [dshow @ 0x1]  pixel_format=yuyv422  min s=640x480 fps=5 max s=640x480 fps=30
            [dshow @ 0x1]  pixel_format=yuyv422  min s=1920x1080 fps=30 max s=1920x1080 fps=30
            """.trimIndent()
        )

        assertEquals(
            listOf("1920x1080@30", "640x480@30", "640x480@5"),
            formats.map { it.encodedValue },
            "every rate on a line is a real option, and the largest area is offered first",
        )
    }

    @Test
    fun `a dshow line naming several rates yields one format per rate`() {
        val formats = parseDshowFormats("min s=1280x720 fps=15 max fps=60")

        assertEquals(setOf(15, 60), formats.map { it.fps }.toSet())
        assertEquals(2, formats.size)
    }

    @Test
    fun `a dshow size with no frame rate is dropped`() {
        // Without a rate there is nothing to open the device with, unlike v4l2 where 30 is assumed.
        assertEquals(emptyList(), parseDshowFormats("pixel_format=yuyv422 s=640x480"))
    }

    @Test
    fun `dshow lines that are not format listings are ignored`() {
        assertEquals(
            emptyList(),
            parseDshowFormats("[dshow @ 0x1] Could not find video device with name [nope]"),
        )
    }

    @Test
    fun `duplicate dshow formats are listed once`() {
        val formats = parseDshowFormats(
            """
            min s=1920x1080 fps=30 max s=1920x1080 fps=30
            min s=1920x1080 fps=30 max s=1920x1080 fps=30
            """.trimIndent()
        )

        assertEquals(1, formats.size, "the same resolution twice is one option, not two")
    }

    @Test
    fun `a dshow format names itself in a form an operator can read`() {
        val format = parseDshowFormats("min s=1920x1080 fps=30 max fps=30").single()

        assertEquals("1920x1080 @ 30fps", format.displayName, "this is what the dropdown shows")
        assertEquals("1920x1080@30", format.encodedValue, "and this is what gets stored")
    }

    @Test
    fun `no output at all yields no dshow formats`() {
        assertEquals(emptyList(), parseDshowFormats(""))
    }

    // ── v4l2 formats (Linux) ──────────────────────────────────────────────────

    @Test
    fun `v4l2 sizes are read and sorted largest first`() {
        val formats = parseV4l2Formats(
            """
            [video4linux2,v4l2 @ 0x1] Raw       :     yuyv422 :           YUYV 4:2:2 : 640x480 1280x720
            [video4linux2,v4l2 @ 0x1] Compressed:       mjpeg :          Motion-JPEG : 1920x1080
            """.trimIndent()
        )

        assertEquals(
            listOf("1920x1080@30", "640x480@30"),
            formats.map { it.encodedValue },
            "one size per line is read, and a line's first size wins",
        )
    }

    @Test
    fun `a v4l2 size with no frame rate is assumed to be thirty`() {
        assertEquals(30, parseV4l2Formats("Raw: 1280x720").single().fps)
    }

    @Test
    fun `a v4l2 frame rate on the line is used instead of the assumption`() {
        assertEquals(60, parseV4l2Formats("Raw: 1280x720 (60 fps)").single().fps)
    }

    @Test
    fun `a fractional v4l2 frame rate is truncated to whole frames`() {
        assertEquals(
            29, parseV4l2Formats("Raw: 1920x1080 (29.97 fps)").single().fps,
            "29.97 is stored as 29 — the encoded value has no room for a fraction",
        )
    }

    @Test
    fun `numbers too small to be a resolution are not read as one`() {
        assertEquals(
            emptyList(), parseV4l2Formats("Raw: 16x9 aspect"),
            "the pattern needs at least three digits a side, so an aspect ratio cannot match",
        )
    }

    @Test
    fun `no output at all yields no v4l2 formats`() {
        assertEquals(emptyList(), parseV4l2Formats(""))
    }

    // ── v4l2-ctl formats (Linux fallback) ─────────────────────────────────────

    @Test
    fun `v4l2-ctl rates are attributed to the size printed above them`() {
        val formats = parseV4l2CtlFormats(
            """
            	Size: Discrete 1920x1080
            		Interval: Discrete 0.033s (30.000 fps)
            		Interval: Discrete 0.067s (15.000 fps)
            	Size: Discrete 1280x720
            		Interval: Discrete 0.017s (60.000 fps)
            """.trimIndent()
        )

        assertEquals(
            listOf("1920x1080@30", "1920x1080@15", "1280x720@60"),
            formats.map { it.encodedValue },
            "each indented rate belongs to the last size seen, and areas sort largest first",
        )
    }

    @Test
    fun `a v4l2-ctl rate printed before any size is ignored`() {
        assertEquals(
            emptyList(),
            parseV4l2CtlFormats("		Interval: Discrete 0.033s (30.000 fps)"),
            "there is nothing to attribute it to",
        )
    }

    @Test
    fun `a v4l2-ctl size with no rates beneath it contributes nothing`() {
        assertEquals(
            emptyList(), parseV4l2CtlFormats("	Size: Discrete 1920x1080"),
            "a size alone cannot be opened — the rate is what completes it",
        )
    }

    @Test
    fun `a v4l2-ctl line carrying both a size and a rate is read whole`() {
        val format = parseV4l2CtlFormats("Size: 800x600 at 24 fps").single()

        assertEquals("800x600@24", format.encodedValue)
    }

    // ── AVFoundation formats (macOS) ──────────────────────────────────────────

    @Test
    fun `avfoundation sizes are read and sorted largest first`() {
        val formats = parseAvfoundationFormats(
            """
            [AVFoundation indev @ 0x1] Supported modes:
            [AVFoundation indev @ 0x1]   1280x720@[1.000000 30.000000]fps
            [AVFoundation indev @ 0x1]   1920x1080@[1.000000 60.000000]fps
            """.trimIndent()
        )

        assertEquals(listOf(1920, 1280), formats.map { it.width })
        assertEquals(
            listOf(60, 30), formats.map { it.fps },
            "each mode runs at the top of the range ffmpeg printed for it",
        )
    }

    @Test
    fun `an avfoundation size with no frame rate is assumed to be thirty`() {
        assertEquals(30, parseAvfoundationFormats("  1280x720").single().fps)
    }

    @Test
    fun `no output at all yields no avfoundation formats`() {
        assertEquals(emptyList(), parseAvfoundationFormats(""))
    }

    // ── Windows cameras ───────────────────────────────────────────────────────

    @Test
    fun `the newer ffmpeg device listing is read, including untyped capture cards`() {
        val devices = parseWindowsCameras(
            dshowOutput = """
                [dshow @ 0x1] "Integrated Webcam" (video)
                [dshow @ 0x1]   Alternative name "@device_pnp_\\?\usb#vid_0c45"
                [dshow @ 0x1] "Blackmagic WDM Capture" (none)
                [dshow @ 0x1] "Microphone Array" (audio)
            """.trimIndent(),
            pnpOutput = "",
        )

        assertEquals(
            listOf("Integrated Webcam", "Blackmagic WDM Capture"),
            devices.map { it.name },
            "a device typed (none) is a capture card, not a non-device; audio is not a video source",
        )
    }

    @Test
    fun `a windows device is addressed by the exact name ffmpeg gave it`() {
        val device = parseWindowsCameras("\"Logitech BRIO\" (video)", "").single()

        assertEquals("dshow://:dshow-vdev=Logitech BRIO", device.path)
        assertEquals("Logitech BRIO", device.displayName)
    }

    @Test
    fun `the older sectioned ffmpeg listing is read too`() {
        val devices = parseWindowsCameras(
            dshowOutput = """
                [dshow @ 0x1] DirectShow video devices
                [dshow @ 0x1]  "Integrated Webcam"
                [dshow @ 0x1] DirectShow audio devices
                [dshow @ 0x1]  "Microphone Array"
            """.trimIndent(),
            pnpOutput = "",
        )

        assertEquals(
            listOf("Integrated Webcam"), devices.map { it.name },
            "names under the audio header must not be offered as cameras",
        )
    }

    @Test
    fun `PowerShell fills in cameras ffmpeg did not report`() {
        val devices = parseWindowsCameras(
            dshowOutput = "\"Integrated Webcam\" (video)",
            pnpOutput = "Integrated Webcam\nSurface Camera Front\n",
        )

        assertEquals(
            listOf("Integrated Webcam", "Surface Camera Front"), devices.map { it.name },
            "the one both listings know is offered once, under ffmpeg's name for it",
        )
    }

    @Test
    fun `a device named differently only by case is not offered twice`() {
        val devices = parseWindowsCameras("\"Integrated Webcam\" (video)", "INTEGRATED WEBCAM")

        assertEquals(
            listOf("Integrated Webcam"), devices.map { it.name },
            "matching is case-insensitive, and ffmpeg's spelling is the one that must be kept",
        )
    }

    @Test
    fun `blank PowerShell lines are not offered as devices`() {
        val devices = parseWindowsCameras("", "\n  \nSurface Camera Front\n\n")

        assertEquals(listOf("Surface Camera Front"), devices.map { it.name })
    }

    @Test
    fun `neither tool reporting anything yields no windows cameras`() {
        assertEquals(emptyList(), parseWindowsCameras("", ""))
    }

    // ── macOS cameras ─────────────────────────────────────────────────────────

    @Test
    fun `system_profiler names the cameras when ffmpeg is not installed to list them`() {
        val devices = parseMacCameras(
            systemProfilerOutput = """
                Camera:

                    FaceTime HD Camera:

                      Model ID: FaceTime HD Camera
                    Studio Display Camera:

                      Model ID: Studio Display Camera
            """.trimIndent(),
            ffmpegOutput = "",
        )

        assertEquals(listOf("FaceTime HD Camera", "Studio Display Camera"), devices.map { it.name })
        assertEquals(
            listOf("avfoundation://0", "avfoundation://1"), devices.map { it.path },
            "with no ffmpeg there is no index to read, and no capture either — these entries exist " +
                "so the operator sees their camera named beside the hint telling them to install it",
        )
    }

    @Test
    fun `a capture card is addressed by ffmpeg's index, not its position among physical cameras`() {
        // The device listing from issue #431, where three virtual cameras precede the real card.
        val devices = parseMacCameras(
            systemProfilerOutput = "    USB3. 0 capture:\n",
            ffmpegOutput = """
                [AVFoundation indev @ 0x1] AVFoundation video devices:
                [AVFoundation indev @ 0x1] [0] Meld Studio Virtual Camera
                [AVFoundation indev @ 0x1] [1] OBS Virtual Camera
                [AVFoundation indev @ 0x1] [2] USB3 Video
                [AVFoundation indev @ 0x1] [3] NDI Virtual Camera
                [AVFoundation indev @ 0x1] [4] Capture screen 0
                [AVFoundation indev @ 0x1] AVFoundation audio devices:
                [AVFoundation indev @ 0x1] [0] USB3 Digital Audio
            """.trimIndent(),
        )

        assertEquals(
            "avfoundation://2", devices.single { it.name == "USB3 Video" }.path,
            "numbering the card by its position in system_profiler would address it as 0, " +
                "which is a virtual camera nobody is feeding — that is issue #431",
        )
        assertEquals(
            listOf("avfoundation://0", "avfoundation://1", "avfoundation://2", "avfoundation://3", "avfoundation://4"),
            devices.map { it.path },
        )
        assertTrue(
            devices.none { it.name == "USB3. 0 capture" },
            "system_profiler's name for the same card must not be offered as a second, unopenable device",
        )
    }

    @Test
    fun `an audio device sharing a video device's index is never offered as a camera`() {
        val devices = parseMacCameras(
            systemProfilerOutput = "",
            ffmpegOutput = """
                [AVFoundation indev @ 0x1] AVFoundation video devices:
                [AVFoundation indev @ 0x1] [0] FaceTime HD Camera
                [AVFoundation indev @ 0x1] AVFoundation audio devices:
                [AVFoundation indev @ 0x1] [0] MacBook Pro Microphone
            """.trimIndent(),
        )

        assertEquals(listOf("FaceTime HD Camera"), devices.map { it.name })
    }

    @Test
    fun `the Camera heading itself is not offered as a camera`() {
        val devices = parseMacCameras("Camera:\n\n    FaceTime HD Camera:\n", "")

        assertEquals(listOf("FaceTime HD Camera"), devices.map { it.name })
    }

    @Test
    fun `the newer unindexed listing is numbered by its own order, not system_profiler's count`() {
        val devices = parseMacCameras(
            systemProfilerOutput = "    FaceTime HD Camera:\n",
            ffmpegOutput = """
                [AVFoundation indev @ 0x1] "FaceTime HD Camera" (video)
                [AVFoundation indev @ 0x1] "OBS Virtual Camera" (video)
            """.trimIndent(),
        )

        assertEquals(listOf("FaceTime HD Camera", "OBS Virtual Camera"), devices.map { it.name })
        assertEquals(
            listOf("avfoundation://0", "avfoundation://1"), devices.map { it.path },
            "this listing prints no index, so position within it is the address — " +
                "counting on from system_profiler's total would offset every device",
        )
    }

    @Test
    fun `the older sectioned ffmpeg listing uses the index ffmpeg printed`() {
        val devices = parseMacCameras(
            systemProfilerOutput = "",
            ffmpegOutput = """
                [AVFoundation indev @ 0x1] AVFoundation video devices:
                [AVFoundation indev @ 0x1] [0] FaceTime HD Camera
                [AVFoundation indev @ 0x1] [1] OBS Virtual Camera
                [AVFoundation indev @ 0x1] AVFoundation audio devices:
                [AVFoundation indev @ 0x1] [0] MacBook Pro Microphone
            """.trimIndent(),
        )

        assertEquals(
            listOf("FaceTime HD Camera", "OBS Virtual Camera"), devices.map { it.name },
            "devices under the audio header must not be offered as cameras",
        )
        assertEquals(listOf("avfoundation://0", "avfoundation://1"), devices.map { it.path })
    }

    @Test
    fun `a camera both tools report is offered once, on ffmpeg's terms`() {
        val devices = parseMacCameras(
            systemProfilerOutput = "    FaceTime HD Camera:\n",
            ffmpegOutput = "\"FaceTime HD Camera\" (video)",
        )

        assertEquals(1, devices.size, "the two tools describe one camera, not two")
        assertEquals("avfoundation://0", devices.single().path)
    }

    @Test
    fun `a camera only system_profiler can see is dropped once ffmpeg has spoken`() {
        val devices = parseMacCameras(
            systemProfilerOutput = "    Studio Display Camera:\n",
            ffmpegOutput = """
                [AVFoundation indev @ 0x1] AVFoundation video devices:
                [AVFoundation indev @ 0x1] [0] FaceTime HD Camera
            """.trimIndent(),
        )

        assertEquals(
            listOf("FaceTime HD Camera"), devices.map { it.name },
            "ffmpeg lists everything AVFoundation will open, so a name it omits has no address " +
                "and could only be offered with an invented one",
        )
    }

    @Test
    fun `neither tool reporting anything yields no mac cameras`() {
        assertEquals(emptyList(), parseMacCameras("", ""))
    }

    // ── Linux cameras ─────────────────────────────────────────────────────────

    @Test
    fun `video nodes are listed, named from the kernel and sorted`() {
        val dev = tempDir("cp-dev")
        val sys = tempDir("cp-sys")
        listOf("video1", "video0", "null", "tty").forEach { File(dev, it).writeText("") }
        File(sys, "video0").mkdirs()
        File(sys, "video0/name").writeText("Integrated Camera\n")

        val devices = listLinuxCameras(dev, sys)

        assertEquals(
            listOf("Integrated Camera", "video1"), devices.map { it.name },
            "only video* nodes count; one is named by the kernel, the other falls back to its node",
        )
        assertEquals("Integrated Camera (video0)", devices.first().displayName)
        assertEquals("v4l2://${File(dev, "video0").absolutePath}", devices.first().path)
    }

    @Test
    fun `a machine with no video nodes lists no cameras`() {
        assertEquals(emptyList(), listLinuxCameras(tempDir("cp-dev"), tempDir("cp-sys")))
    }

    @Test
    fun `a missing dev directory is survived rather than thrown from`() {
        val gone = File(tempDir("cp-dev"), "not-there")

        assertEquals(emptyList(), listLinuxCameras(gone, gone))
    }

    // ── Windows and X11 window listings ───────────────────────────────────────

    @Test
    fun `xprop window ids are read out of the stacking list`() {
        val ids = parseXpropWindowIds(
            "_NET_CLIENT_LIST_STACKING(WINDOW): window id # 0x2200003, 0x2400001, 0x1e00007"
        )

        assertEquals(listOf("0x2200003", "0x2400001", "0x1e00007"), ids)
    }

    @Test
    fun `an empty stacking list yields no window ids`() {
        assertEquals(
            emptyList(),
            parseXpropWindowIds("_NET_CLIENT_LIST_STACKING(WINDOW): window id # "),
        )
    }

    @Test
    fun `an xprop window is named and its hexadecimal id decoded`() {
        val window = xpropWindow("0x2200003", "_NET_WM_NAME(UTF8_STRING) = \"Firefox\"")

        assertEquals("Firefox", window?.title)
        assertEquals(0x2200003L, window?.id, "the id is hexadecimal, and is stored decoded")
    }

    @Test
    fun `an xprop window with no name is not offered`() {
        assertNull(
            xpropWindow("0x2200003", "_NET_WM_NAME:  not found."),
            "an unnamed window is of no use to an operator picking one from a list",
        )
    }

    @Test
    fun `an unparseable xprop window id is kept as window zero`() {
        val window = xpropWindow("0xZZZ", "_NET_WM_NAME(UTF8_STRING) = \"Terminal\"")

        assertEquals(0L, window?.id, "the window is still offered, addressed by title alone")
        assertEquals("Terminal", window?.title)
    }

    /**
     * Characterises a defect, deliberately — see the note on [parseWmctrlWindows] below.
     *
     * `wmctrl -l` prints four columns: id, desktop, host, title. The parser splits with `limit = 5`
     * and takes `parts[4]`, which is the title *minus its first word* — and for a one-word title
     * there is no fifth part at all, so the window is dropped outright. These two tests pin what the
     * code does today rather than what it should do, so that a fix breaks them loudly instead of
     * passing unnoticed. Fixing it is a behaviour change to window capture on Linux and is left to
     * the maintainer; nothing else in this file is a characterisation test.
     */
    @Test
    fun `a wmctrl title keeps every word, including a one-word one`() {
        val windows = parseWmctrlWindows(
            """
            0x02200003  0 hostname Mozilla Firefox — Church Presenter
            0x02400001  0 hostname Terminal
            """.trimIndent()
        )

        assertEquals(
            listOf("Mozilla Firefox — Church Presenter", "Terminal"),
            windows.map { it.title },
            "the title is the fourth column and runs to end of line; a one-word title is still a window",
        )
        assertEquals(0x02200003L, windows.first().id, "the id is still read correctly")
    }

    @Test
    fun `a wmctrl line with too few columns is not a window`() {
        assertEquals(emptyList(), parseWmctrlWindows("0x02200003  0 hostname"))
    }

    @Test
    fun `blank wmctrl lines are skipped`() {
        val windows = parseWmctrlWindows("\n0x02200003  0 host My Terminal\n\n")

        assertEquals(1, windows.size, "one blank-free line yields one window")
    }

    @Test
    fun `AppleScript window titles are split on commas and trimmed`() {
        val windows = parseMacWindowTitles("Firefox, Terminal , Church Presenter")

        assertEquals(listOf("Firefox", "Terminal", "Church Presenter"), windows.map { it.title })
        assertTrue(windows.all { it.id == 0L }, "macOS capture matches on title, so there is no id")
    }

    @Test
    fun `an empty AppleScript answer yields no windows`() {
        assertEquals(emptyList(), parseMacWindowTitles("   "))
    }
}
