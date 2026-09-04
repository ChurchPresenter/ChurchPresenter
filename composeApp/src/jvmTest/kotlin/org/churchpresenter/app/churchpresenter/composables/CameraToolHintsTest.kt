package org.churchpresenter.app.churchpresenter.composables

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_camera_ffmpeg_required
import churchpresenter.composeapp.generated.resources.canvas_camera_none_found
import churchpresenter.composeapp.generated.resources.canvas_camera_unopenable_listing
import churchpresenter.composeapp.generated.resources.canvas_camera_v4l2_hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the operator is told about the tools their cameras need.
 *
 * Both camera pickers used to decide this inline, and they disagreed — which is the bug this covers.
 * The decision is a pure function now, so the platform and the presence of ffmpeg are parameters
 * rather than properties of the machine running the suite: `FfmpegBinary` resolves its path in a
 * `by lazy` and must not grow a mutable seam, and `os.name` cannot be faked in a JVM that has
 * composed anything (skiko latches it).
 */
class CameraToolHintsTest {

    private fun camera(name: String = "Integrated Webcam") =
        CameraDevice(name = name, path = "dshow://:dshow-vdev=$name", displayName = name)

    @Test
    fun `nothing is said before the first enumeration answers`() {
        assertEquals(
            emptyList(), cameraHintStringRes("Windows 11", devices = null, ffmpegAvailable = false),
            "\"No cameras found\" before anything has looked is a wrong answer the operator acts on",
        )
    }

    @Test
    fun `a windows machine without ffmpeg is told so even though the list is not empty`() {
        val hints = cameraHintStringRes("Windows 11", listOf(camera()), ffmpegAvailable = false)

        assertTrue(
            Res.string.canvas_camera_ffmpeg_required in hints,
            "this is the whole bug: the PnP fallback fills the picker with names that cannot be " +
                "opened, so a hint shown only on an empty list is one the operator never sees",
        )
        assertTrue(Res.string.canvas_camera_none_found !in hints, "there is a name in the list")
    }

    @Test
    fun `a windows machine without ffmpeg and no cameras is told both`() {
        val hints = cameraHintStringRes("Windows 11", emptyList(), ffmpegAvailable = false)

        assertEquals(
            listOf(Res.string.canvas_camera_none_found, Res.string.canvas_camera_ffmpeg_required),
            hints,
            "what was found, then what to do about it",
        )
    }

    @Test
    fun `a mac without ffmpeg is told the same thing as windows`() {
        assertEquals(
            cameraHintStringRes("Windows 11", listOf(camera()), ffmpegAvailable = false),
            cameraHintStringRes("Mac OS X", listOf(camera()), ffmpegAvailable = false),
            "both open every camera through ffmpeg, and the remedy is the same sentence",
        )
    }

    @Test
    fun `linux is never told to install ffmpeg`() {
        val hints = cameraHintStringRes("Linux", listOf(camera()), ffmpegAvailable = false)

        assertEquals(
            emptyList(), hints,
            "linux opens cameras through v4l2 and wants ffmpeg only for virtual ones",
        )
    }

    @Test
    fun `linux with no cameras is pointed at v4l2loopback`() {
        val hints = cameraHintStringRes("Linux", emptyList(), ffmpegAvailable = true)

        assertEquals(listOf(Res.string.canvas_camera_none_found, Res.string.canvas_camera_v4l2_hint), hints)
    }

    // ── A listing nothing can open ────────────────────────────────────────────────────────────

    @Test
    fun `a fallback listing is explained even though ffmpeg is present`() {
        val hints = cameraHintStringRes(
            "Windows 11",
            listOf(camera()),
            ffmpegAvailable = true,
            enumerator = CameraEnumerator.PNP_FALLBACK,
        )

        assertEquals(
            listOf(Res.string.canvas_camera_unopenable_listing), hints,
            "bundling ffmpeg made the old condition always false, which left a dropdown of cameras " +
                "that cannot open with nothing on screen saying so",
        )
    }

    @Test
    fun `the mac fallback is explained the same way`() {
        val hints = cameraHintStringRes(
            "Mac OS X",
            listOf(camera()),
            ffmpegAvailable = true,
            enumerator = CameraEnumerator.SYSTEM_PROFILER_FALLBACK,
        )

        assertEquals(listOf(Res.string.canvas_camera_unopenable_listing), hints)
    }

    @Test
    fun `a listing ffmpeg itself produced is not explained away`() {
        val hints = cameraHintStringRes(
            "Windows 11",
            listOf(camera()),
            ffmpegAvailable = true,
            enumerator = CameraEnumerator.DSHOW,
        )

        assertEquals(emptyList(), hints, "these open perfectly well and need no sentence")
    }

    @Test
    fun `a missing ffmpeg still outranks the unopenable-listing sentence`() {
        val hints = cameraHintStringRes(
            "Windows 11",
            listOf(camera()),
            ffmpegAvailable = false,
            enumerator = CameraEnumerator.PNP_FALLBACK,
        )

        assertEquals(
            listOf(Res.string.canvas_camera_ffmpeg_required), hints,
            "with no ffmpeg at all, finding one is the first thing to do and the more specific " +
                "sentence would only be noise beside it",
        )
    }

    @Test
    fun `an unopenable listing with nothing in it says only that nothing was found`() {
        val hints = cameraHintStringRes(
            "Windows 11",
            emptyList(),
            ffmpegAvailable = true,
            enumerator = CameraEnumerator.PNP_FALLBACK,
        )

        assertEquals(
            listOf(Res.string.canvas_camera_none_found), hints,
            "there is no listing to explain, and a machine with no camera is not a fault",
        )
    }

    @Test
    fun `an unknown enumerator adds no sentence`() {
        assertEquals(
            emptyList(),
            cameraHintStringRes("Windows 11", listOf(camera()), ffmpegAvailable = true, enumerator = null),
        )
    }

    @Test
    fun `a machine with ffmpeg and a camera is told nothing`() {
        assertEquals(
            emptyList(), cameraHintStringRes("Windows 11", listOf(camera()), ffmpegAvailable = true),
            "a working picker needs no explanation, and one that always explains is read past",
        )
    }
}
