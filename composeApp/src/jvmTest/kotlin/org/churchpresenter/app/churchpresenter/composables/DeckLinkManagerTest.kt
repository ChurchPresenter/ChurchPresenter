package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The BlackMagic DeckLink JNI wrapper. No DeckLink SDK/hardware exists in this test JVM, so
 * `System.loadLibrary("decklink_jni")` always throws `UnsatisfiedLinkError`, which [DeckLinkManager]
 * catches and memoizes as `isAvailable() == false` — permanently, for the rest of this JVM's life,
 * since the result is cached in a private var with no reset hook (by design: real hardware doesn't
 * appear mid-process). That makes every public function's "hardware unavailable" guard clause
 * deterministic and safe to exercise directly: every method below is expected to hit its early
 * `if (!isAvailable()) return ...` branch, never the native call inside. The native calls
 * themselves (`nativeXxx`) are therefore permanently unreachable in this suite, by construction —
 * consistent with this project's rule that hardware-only code paths are not force-tested.
 *
 * What each native call's *result* is turned into is a different matter, and that part is covered:
 * the `parseXxx` helpers below are the decision logic lifted out of those hardware-gated methods,
 * so only the one unreachable JNI call is left uncovered in each.
 *
 * **Driving a real card is a separate, opt-in step.** [DeckLinkHardwareTest] does that, but it is
 * gated behind `-PdecklinkHardware=true` and inert in every ordinary run — including this one.
 * Reaching the native calls means opening the output device for real, which pushes a frame to
 * whatever the card is wired to, installs a JVM shutdown hook and costs driver-init time; it also
 * has to reset the memoized `available` field that the assertions here rely on. None of that
 * belongs in a suite that runs on every change, so it stays behind the flag.
 */
class DeckLinkManagerTest {

    private var realHome: String? = null
    private var tempHome: File? = null

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-decklink-test").toFile()
        System.setProperty("user.home", tempHome!!.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome?.deleteRecursively()
    }

    // ── Availability ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable is false with no native library on the test machine`() {
        assertFalse(DeckLinkManager.isAvailable())
    }

    @Test
    fun `listDevices is empty when unavailable`() {
        assertEquals(emptyList(), DeckLinkManager.listDevices())
    }

    // ── Output API guard clauses ──────────────────────────────────────────────────────────────

    @Test
    fun `output functions no-op or return their unavailable default`() {
        assertFalse(DeckLinkManager.open(0))
        assertFalse(DeckLinkManager.open(0, 1280, 720))
        assertNull(DeckLinkManager.getOutputInfo(0))
        DeckLinkManager.sendFrame(0, IntArray(0), 0, 0)
        assertFalse(DeckLinkManager.startScheduledPlayback(0))
        assertFalse(DeckLinkManager.startScheduledPlayback(0, 25.0))
        DeckLinkManager.scheduleFrame(0, IntArray(0), 0, 0)
        DeckLinkManager.stopPlayback(0)
        DeckLinkManager.close(0)
        DeckLinkManager.closeAllOutputs() // no devices ever opened — loop body never runs
    }

    @Test
    fun `isOutputActive is false for any device index, opened or not`() {
        assertFalse(DeckLinkManager.isOutputActive(0))
        assertFalse(DeckLinkManager.isOutputActive(99))
    }

    // ── Input capture API guard clauses ───────────────────────────────────────────────────────

    @Test
    fun `input functions no-op or return their unavailable default`() {
        assertEquals(emptyList(), DeckLinkManager.listInputModes(0))
        assertEquals(emptyList(), DeckLinkManager.listVideoConnections(0))
        assertFalse(DeckLinkManager.openInput(0))
        assertFalse(DeckLinkManager.openInput(0, "1080p60", 1))
        assertNull(DeckLinkManager.getInputFrame(0))
        DeckLinkManager.closeInput(0)
    }

    @Test
    fun `isInputActive is false for any device index, opened or not`() {
        assertFalse(DeckLinkManager.isInputActive(0))
        assertFalse(DeckLinkManager.isInputActive(99))
    }

    // ── Audio input/output API guard clauses ──────────────────────────────────────────────────

    @Test
    fun `audio functions no-op or return their unavailable default`() {
        assertFalse(DeckLinkManager.enableAudioInput(0))
        assertFalse(DeckLinkManager.enableAudioInput(0, 6))
        assertNull(DeckLinkManager.getInputAudio(0))
        assertFalse(DeckLinkManager.enableAudioOutput(0))
        assertFalse(DeckLinkManager.enableAudioOutput(0, 6))
        assertEquals(0, DeckLinkManager.writeAudioSamples(0, ShortArray(0), 0))
        DeckLinkManager.disableAudioOutput(0)
    }

    // ── Keyer API guard clauses ────────────────────────────────────────────────────────────────

    @Test
    fun `keyer functions no-op or return their unavailable default`() {
        assertFalse(DeckLinkManager.enableKeyer(0))
        assertFalse(DeckLinkManager.enableKeyer(0, isExternal = true))
        DeckLinkManager.setKeyerLevel(0, 128)
        DeckLinkManager.keyerRampUp(0)
        DeckLinkManager.keyerRampUp(0, 10)
        DeckLinkManager.keyerRampDown(0)
        DeckLinkManager.keyerRampDown(0, 10)
        DeckLinkManager.disableKeyer(0)
    }

    // ── Output connection + status API guard clauses ──────────────────────────────────────────

    @Test
    fun `output connection and status functions return their unavailable default`() {
        assertFalse(DeckLinkManager.setOutputConnection(0, 1))
        assertEquals(emptyList(), DeckLinkManager.listOutputConnections(0))
        assertNull(DeckLinkManager.getDeviceStatus(0))
    }

    // ── isInputConfigured — real logic, not hardware-gated ────────────────────────────────────

    private fun cameraSource(isDeckLink: Boolean, deckLinkIndex: Int) = SceneSource.CameraSource(
        id = "cam1", name = "Camera", transform = SourceTransform(),
        isDeckLink = isDeckLink, deckLinkIndex = deckLinkIndex,
    )

    @Test
    fun `isInputConfigured is true when a scene has a matching DeckLink camera source`() {
        val scene = Scene(sources = listOf(cameraSource(isDeckLink = true, deckLinkIndex = 2)))
        assertTrue(DeckLinkManager.isInputConfigured(2, listOf(scene)))
    }

    @Test
    fun `isInputConfigured is false when no scene's index matches`() {
        val scene = Scene(sources = listOf(cameraSource(isDeckLink = true, deckLinkIndex = 2)))
        assertFalse(DeckLinkManager.isInputConfigured(3, listOf(scene)))
    }

    @Test
    fun `isInputConfigured is false when the matching source is not a DeckLink camera`() {
        val scene = Scene(sources = listOf(cameraSource(isDeckLink = false, deckLinkIndex = 2)))
        assertFalse(DeckLinkManager.isInputConfigured(2, listOf(scene)))
    }

    @Test
    fun `isInputConfigured is false when no scenes list is given and no scenes file exists`() {
        assertFalse(DeckLinkManager.isInputConfigured(2))
    }

    @Test
    fun `isInputConfigured falls back to a persisted scenes file when the index matches, no-space form`() {
        val appDir = File(tempHome, ".churchpresenter").apply { mkdirs() }
        File(appDir, "scenes.json").writeText("""{"sources":[{"deckLinkIndex":4}]}""")
        assertTrue(DeckLinkManager.isInputConfigured(4))
    }

    @Test
    fun `isInputConfigured falls back to a persisted scenes file when the index matches, spaced form`() {
        val appDir = File(tempHome, ".churchpresenter").apply { mkdirs() }
        File(appDir, "scenes.json").writeText("""{"sources":[{"deckLinkIndex": 5}]}""")
        assertTrue(DeckLinkManager.isInputConfigured(5))
    }

    @Test
    fun `isInputConfigured returns false when the persisted scenes file exists but does not match`() {
        val appDir = File(tempHome, ".churchpresenter").apply { mkdirs() }
        File(appDir, "scenes.json").writeText("""{"sources":[{"deckLinkIndex":4}]}""")
        assertFalse(DeckLinkManager.isInputConfigured(9))
    }

    // ── Data classes ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `OutputInfo computes fps from numerator over denominator`() {
        val info = DeckLinkManager.OutputInfo(1920, 1080, 60000, 1001)
        assertEquals(60000.0 / 1001, info.fps)
    }

    @Test
    fun `OutputInfo falls back to 30 fps when the denominator is zero`() {
        val info = DeckLinkManager.OutputInfo(1920, 1080, 60, 0)
        assertEquals(30.0, info.fps)
    }

    @Test
    fun `the remaining data classes hold the fields they are constructed with`() {
        assertEquals(DeckLinkManager.DeckLinkDevice(0, "UltraStudio"), DeckLinkManager.DeckLinkDevice(0, "UltraStudio"))
        assertEquals(DeckLinkManager.InputMode("1080p60", "abcd"), DeckLinkManager.InputMode("1080p60", "abcd"))
        assertEquals(DeckLinkManager.VideoConnection("SDI", 1), DeckLinkManager.VideoConnection("SDI", 1))
        val status = DeckLinkManager.DeviceStatus(signalLocked = true, busy = 0, detectedModeCode = 7)
        assertTrue(status.signalLocked)
        assertEquals(7, status.detectedModeCode)
        val audio = DeckLinkManager.AudioFrame(sampleFrames = 2, channels = 2, samples = shortArrayOf(1, 2, 3, 4))
        assertEquals(2, audio.sampleFrames)
        assertEquals(4, audio.samples.size)
    }

    // ── parseInputModes ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseInputModes splits name and encoded value on pipe`() {
        val result = DeckLinkManager.parseInputModes(arrayOf("1080p60|Hp60"))
        assertEquals(1, result.size)
        assertEquals("1080p60", result[0].name)
        assertEquals("Hp60", result[0].encodedValue)
    }

    @Test
    fun `parseInputModes handles entries with no pipe as name-only`() {
        val result = DeckLinkManager.parseInputModes(arrayOf("Auto"))
        assertEquals(1, result.size)
        assertEquals("Auto", result[0].name)
        assertEquals("", result[0].encodedValue)
    }

    @Test
    fun `parseInputModes parses multiple entries`() {
        val result = DeckLinkManager.parseInputModes(arrayOf("720p50|mod1", "1080i60|mod2", "2160p30|mod3"))
        assertEquals(3, result.size)
        assertEquals("720p50", result[0].name)
        assertEquals("mod1", result[0].encodedValue)
        assertEquals("2160p30", result[2].name)
        assertEquals("mod3", result[2].encodedValue)
    }

    @Test
    fun `parseInputModes handles pipe in the value portion`() {
        val result = DeckLinkManager.parseInputModes(arrayOf("1080p60|enc|extra"))
        assertEquals(1, result.size)
        assertEquals("1080p60", result[0].name)
        assertEquals("enc|extra", result[0].encodedValue)
    }

    @Test
    fun `parseInputModes returns empty list for empty array`() {
        assertTrue(DeckLinkManager.parseInputModes(emptyArray()).isEmpty())
    }

    // ── parseVideoConnections ────────────────────────────────────────────────────────────────────

    @Test
    fun `parseVideoConnections splits name and numeric value on pipe`() {
        val result = DeckLinkManager.parseVideoConnections(arrayOf("SDI|1"))
        assertEquals(1, result.size)
        assertEquals("SDI", result[0].name)
        assertEquals(1, result[0].value)
    }

    @Test
    fun `parseVideoConnections defaults value to 0 when missing`() {
        val result = DeckLinkManager.parseVideoConnections(arrayOf("HDMI"))
        assertEquals(1, result.size)
        assertEquals("HDMI", result[0].name)
        assertEquals(0, result[0].value)
    }

    @Test
    fun `parseVideoConnections defaults to 0 when value is not numeric`() {
        val result = DeckLinkManager.parseVideoConnections(arrayOf("SDI|abc"))
        assertEquals(1, result.size)
        assertEquals("SDI", result[0].name)
        assertEquals(0, result[0].value)
    }

    @Test
    fun `parseVideoConnections parses multiple connections`() {
        val result = DeckLinkManager.parseVideoConnections(arrayOf("SDI|1", "HDMI|2", "Component|4"))
        assertEquals(3, result.size)
        assertEquals(1, result[0].value)
        assertEquals(2, result[1].value)
        assertEquals(4, result[2].value)
    }

    // ── parseOutputInfo ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseOutputInfo returns OutputInfo from a valid 4-element array`() {
        val info = DeckLinkManager.parseOutputInfo(intArrayOf(1920, 1080, 60000, 1001))
        assertNotNull(info)
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertEquals(60000, info.fpsNumerator)
        assertEquals(1001, info.fpsDenominator)
    }

    @Test
    fun `parseOutputInfo returns null when array is too short`() {
        assertNull(DeckLinkManager.parseOutputInfo(intArrayOf(1920, 1080, 60000)))
    }

    @Test
    fun `parseOutputInfo returns null when width is zero`() {
        assertNull(DeckLinkManager.parseOutputInfo(intArrayOf(0, 1080, 60000, 1001)))
    }

    @Test
    fun `parseOutputInfo returns null when height is zero`() {
        assertNull(DeckLinkManager.parseOutputInfo(intArrayOf(1920, 0, 60000, 1001)))
    }

    @Test
    fun `parseOutputInfo returns null for an empty array`() {
        assertNull(DeckLinkManager.parseOutputInfo(intArrayOf()))
    }

    @Test
    fun `parseOutputInfo accepts extra trailing elements`() {
        val info = DeckLinkManager.parseOutputInfo(intArrayOf(3840, 2160, 30, 1, 99))
        assertNotNull(info)
        assertEquals(3840, info.width)
        assertEquals(2160, info.height)
    }

    // ── parseDeviceStatus ────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseDeviceStatus returns status from a valid 3-element array`() {
        val status = DeckLinkManager.parseDeviceStatus(intArrayOf(1, 0, 14))
        assertNotNull(status)
        assertTrue(status.signalLocked)
        assertEquals(0, status.busy)
        assertEquals(14, status.detectedModeCode)
    }

    @Test
    fun `parseDeviceStatus maps zero to signalLocked false`() {
        val status = DeckLinkManager.parseDeviceStatus(intArrayOf(0, 2, 7))
        assertNotNull(status)
        assertFalse(status.signalLocked)
        assertEquals(2, status.busy)
    }

    @Test
    fun `parseDeviceStatus returns null when array is too short`() {
        assertNull(DeckLinkManager.parseDeviceStatus(intArrayOf(1, 0)))
    }

    @Test
    fun `parseDeviceStatus returns null for an empty array`() {
        assertNull(DeckLinkManager.parseDeviceStatus(intArrayOf()))
    }

    // ── parseInputAudio ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseInputAudio extracts frames, channels and sample data from the native format`() {
        val data = shortArrayOf(2, 2, 100, 200, 300, 400)
        val frame = DeckLinkManager.parseInputAudio(data)
        assertNotNull(frame)
        assertEquals(2, frame.sampleFrames)
        assertEquals(2, frame.channels)
        assertEquals(4, frame.samples.size)
        assertEquals(100, frame.samples[0])
        assertEquals(400, frame.samples[3])
    }

    @Test
    fun `parseInputAudio returns null when the array is too short for the header`() {
        assertNull(DeckLinkManager.parseInputAudio(shortArrayOf(1)))
    }

    @Test
    fun `parseInputAudio returns null when sampleFrames is zero`() {
        assertNull(DeckLinkManager.parseInputAudio(shortArrayOf(0, 2, 100)))
    }

    @Test
    fun `parseInputAudio returns null when channels is zero`() {
        assertNull(DeckLinkManager.parseInputAudio(shortArrayOf(2, 0, 100)))
    }

    @Test
    fun `parseInputAudio returns null when sampleFrames is negative`() {
        assertNull(DeckLinkManager.parseInputAudio(shortArrayOf(-1, 2)))
    }

    @Test
    fun `parseInputAudio handles mono audio`() {
        val data = shortArrayOf(3, 1, 10, 20, 30)
        val frame = DeckLinkManager.parseInputAudio(data)
        assertNotNull(frame)
        assertEquals(3, frame.sampleFrames)
        assertEquals(1, frame.channels)
        assertEquals(3, frame.samples.size)
    }

    @Test
    fun `parseInputAudio handles 6-channel audio`() {
        val samples = ShortArray(12) { it.toShort() }
        val data = shortArrayOf(2, 6) + samples
        val frame = DeckLinkManager.parseInputAudio(data)
        assertNotNull(frame)
        assertEquals(2, frame.sampleFrames)
        assertEquals(6, frame.channels)
        assertEquals(12, frame.samples.size)
    }
}
