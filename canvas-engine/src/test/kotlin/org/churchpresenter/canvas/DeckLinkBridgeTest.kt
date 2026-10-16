package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckLinkBridgeTest {

    private val card = FakeDeckLinkCard()
    private var loads = 0
    private val bridge = DeckLinkBridge(card, loadLibrary = { loads++; true }, settle = {})
    private val absent = DeckLinkBridge(card, loadLibrary = { false })

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-decklink-bridge").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    @Test
    fun `the library is loaded once however often it is asked`() {
        bridge.isAvailable()
        bridge.isAvailable()
        bridge.listDevices()

        assertEquals(1, loads)
    }

    @Test
    fun `devices are numbered in the order the card lists them`() {
        assertEquals(
            listOf(DeckLinkManager.DeckLinkDevice(0, "Mini Recorder"), DeckLinkManager.DeckLinkDevice(1, "Duo 2")),
            bridge.listDevices(),
        )
    }

    @Test
    fun `an opened output is active until it is closed`() {
        assertTrue(bridge.open(1, 1280, 720))
        assertTrue(bridge.isOutputActive(1))

        bridge.close(1)

        assertFalse(bridge.isOutputActive(1))
        assertTrue("close 1" in card.calls)
    }

    @Test
    fun `an output the card refuses is not counted as active`() {
        card.opens = false

        assertFalse(bridge.open(0))
        assertFalse(bridge.isOutputActive(0))
    }

    @Test
    fun `closing every output sends each one black at its own size first`() {
        bridge.open(0)
        bridge.open(1)

        bridge.closeAllOutputs()

        assertTrue("send 0 1280x720" in card.calls)
        assertTrue("close 0" in card.calls && "close 1" in card.calls)
        assertFalse(bridge.isOutputActive(0))
    }

    @Test
    fun `an output that cannot say its size is sent black at 1080p`() {
        card.outputInfo = intArrayOf()
        bridge.open(0)

        bridge.closeAllOutputs()

        assertTrue("send 0 1920x1080" in card.calls)
    }

    @Test
    fun `the output's mode is read as its size and frame rate`() {
        val info = assertNotNull(bridge.getOutputInfo(0))

        assertEquals(1280, info.width)
        assertEquals(60000.0 / 1001, info.fps, 0.001)
    }

    @Test
    fun `frames and playback go straight to the card`() {
        bridge.sendFrame(0, IntArray(4), 2, 2)
        assertTrue(bridge.startScheduledPlayback(0, 29.97))
        bridge.scheduleFrame(0, IntArray(4), 2, 2)
        bridge.stopPlayback(0)

        assertEquals(listOf("send 0 2x2", "start 0 29.97", "schedule 0", "stop 0"), card.calls)
    }

    @Test
    fun `an opened input is active until it is closed`() {
        assertTrue(bridge.openInput(2, "Hp30", 1))
        assertTrue(bridge.isInputActive(2))
        assertContentEquals(intArrayOf(1, 1, 7), bridge.getInputFrame(2))

        bridge.closeInput(2)

        assertFalse(bridge.isInputActive(2))
    }

    @Test
    fun `an input the card refuses is not counted as active`() {
        card.opens = false

        assertFalse(bridge.openInput(2))
        assertFalse(bridge.isInputActive(2))
    }

    @Test
    fun `the card's input modes and connectors are read off its encoded lists`() {
        assertEquals(listOf(DeckLinkManager.InputMode("1080p30", "Hp30")), bridge.listInputModes(0))
        assertTrue(bridge.hasInput(0))
        assertEquals(
            listOf(DeckLinkManager.VideoConnection("SDI", 1), DeckLinkManager.VideoConnection("HDMI", 2)),
            bridge.listVideoConnections(0),
        )
        assertEquals(listOf(DeckLinkManager.VideoConnection("SDI", 1)), bridge.listOutputConnections(0))
    }

    @Test
    fun `embedded audio is passed through in both directions`() {
        assertTrue(bridge.enableAudioInput(0))
        val frame = assertNotNull(bridge.getInputAudio(0))
        assertEquals(2, frame.channels)
        assertTrue(bridge.enableAudioOutput(0, 8))
        assertEquals(64, bridge.writeAudioSamples(0, ShortArray(128), 64))
        bridge.disableAudioOutput(0)

        assertTrue("audioOut 0 8" in card.calls && "audioOff 0" in card.calls)
    }

    @Test
    fun `no audio waiting reads as no frame`() {
        card.inputAudio = null

        assertNull(bridge.getInputAudio(0))
    }

    @Test
    fun `the keyer and output connector go straight to the card`() {
        assertTrue(bridge.enableKeyer(0, isExternal = true))
        bridge.setKeyerLevel(0, 128)
        bridge.keyerRampUp(0, 15)
        bridge.keyerRampDown(0, 15)
        bridge.disableKeyer(0)
        assertTrue(bridge.setOutputConnection(0, 2))

        assertEquals(
            listOf("keyer 0 true", "level 0 128", "up 0 15", "down 0 15", "keyerOff 0", "outConn 0 2"),
            card.calls,
        )
    }

    @Test
    fun `the device status is read as lock, busy and mode`() {
        val status = assertNotNull(bridge.getDeviceStatus(0))

        assertTrue(status.signalLocked)
        assertEquals(42, status.detectedModeCode)
    }

    @Test
    fun `a card that throws answers as if it were not there`() {
        card.fails = true

        assertTrue(bridge.listDevices().isEmpty())
        assertFalse(bridge.open(0))
        assertNull(bridge.getOutputInfo(0))
        bridge.sendFrame(0, IntArray(1), 1, 1)
        assertFalse(bridge.startScheduledPlayback(0))
        bridge.scheduleFrame(0, IntArray(1), 1, 1)
        bridge.stopPlayback(0)
        bridge.close(0)
        assertTrue(bridge.listInputModes(0).isEmpty())
        assertTrue(bridge.listVideoConnections(0).isEmpty())
        assertFalse(bridge.openInput(0))
        assertNull(bridge.getInputFrame(0))
        bridge.closeInput(0)
        assertFalse(bridge.enableAudioInput(0))
        assertNull(bridge.getInputAudio(0))
        assertFalse(bridge.enableAudioOutput(0))
        assertEquals(0, bridge.writeAudioSamples(0, ShortArray(2), 1))
        bridge.disableAudioOutput(0)
        assertFalse(bridge.enableKeyer(0))
        bridge.setKeyerLevel(0, 1)
        bridge.keyerRampUp(0)
        bridge.keyerRampDown(0)
        bridge.disableKeyer(0)
        assertFalse(bridge.setOutputConnection(0, 1))
        assertTrue(bridge.listOutputConnections(0).isEmpty())
        assertNull(bridge.getDeviceStatus(0))
    }

    @Test
    fun `without the library nothing reaches the card`() {
        absent.listDevices()
        absent.open(0)
        absent.getOutputInfo(0)
        absent.openInput(0)
        absent.getInputAudio(0)
        absent.listOutputConnections(0)
        absent.getDeviceStatus(0)

        assertTrue(card.calls.isEmpty())
    }

    @Test
    fun `an input configured in a scene counts, whether passed in or saved`() {
        val camera = SceneSource.CameraSource(id = "c", name = "C", isDeckLink = true, deckLinkIndex = 3)
        val scene = Scene(id = "s", name = "S", sources = listOf(camera))
        assertTrue(bridge.isInputConfigured(3, listOf(scene)))
        assertFalse(bridge.isInputConfigured(4, listOf(scene)))

        File(home, ".churchpresenter").mkdirs()
        File(home, ".churchpresenter/scenes.json").writeText("""[{"deckLinkIndex": 4}]""")
        assertTrue(bridge.isInputConfigured(4))
        assertFalse(bridge.isInputConfigured(5))
    }
}
