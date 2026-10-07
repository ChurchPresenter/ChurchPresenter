@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.controlin.ControlHub
import org.churchpresenter.controlin.ControlOutput
import org.churchpresenter.controlin.ControlPorts
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.controlin.MidiSink
import org.churchpresenter.controlin.OutMessage
import org.churchpresenter.controlin.OutputEvents
import org.churchpresenter.controlin.PortState
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.clearLayer
import org.churchpresenter.liveoutput.showLowerThird
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting

/** The MIDI/OSC effects: ports follow the settings, and going live, clearing and Take are sent out. */
class ControlInEffectsTest {

    private val sent = CopyOnWriteArrayList<Int>()
    private val closed = CopyOnWriteArrayList<String>()

    private fun hub() = ControlHub(
        onMapping = {},
        ports = ControlPorts(
            openMidiIn = { _, _ -> null },
            openMidiOut = {
                object : MidiSink {
                    override fun send(bytes: ByteArray) { sent += bytes[1].toInt() }
                    override fun close() { closed += "out" }
                }
            },
            openOscIn = { _, _ -> null },
            openOscOut = { _, _ -> null },
        ),
    )

    private fun note(on: String, number: Int) =
        ControlOutput("o$number", on, OutMessage(TriggerKinds.MIDI_NOTE, 1, number))

    @Test
    fun `go live, clear and Take are each sent, and the ports close with the effect`() = runComposeUiTest {
        val hub = hub()
        val manager = PresenterManager(showPresenterWindowInitially = false)
        val settings = ControlSettings(
            midiOutput = "Desk",
            outputs = listOf(
                note(OutputEvents.GO_LIVE, 1),
                note(OutputEvents.CLEAR, 2),
                note(OutputEvents.TAKE, 3),
            ),
        )
        var shown by mutableStateOf(true)
        setContent {
            MaterialTheme { if (shown) ControlInEffects(hub, settings, manager) }
        }
        waitUntil(timeoutMillis = 5_000) { hub.status.value.midiOutput == PortState.OPEN }

        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        waitUntil(timeoutMillis = 5_000) { sent.contains(1) }
        manager.clearLayer(Layer.ANNOUNCEMENTS)
        waitUntil(timeoutMillis = 5_000) { sent.contains(2) }
        manager.previewBus.setEnabled(true)
        manager.previewBus.showLowerThird("{}", false, -1f, 0L, "Pastor")
        manager.previewBus.take()
        waitUntil(timeoutMillis = 5_000) { sent.contains(3) }

        shown = false
        waitUntil(timeoutMillis = 5_000) { closed.isNotEmpty() }
        assertTrue(hub.status.value.midiOutput != PortState.OPEN)
        assertEquals(setOf(1, 2, 3), sent.toSet())
    }

    @Test
    fun `new settings reopen the ports, and settings with none close them`() = runComposeUiTest {
        val hub = hub()
        val manager = PresenterManager(showPresenterWindowInitially = false)
        var settings by mutableStateOf(ControlSettings(midiOutput = "Desk"))
        setContent { MaterialTheme { ControlInEffects(hub, settings, manager) } }
        waitUntil(timeoutMillis = 5_000) { hub.status.value.midiOutput == PortState.OPEN }
        settings = ControlSettings()
        waitUntil(timeoutMillis = 5_000) { hub.status.value.midiOutput == PortState.OFF }
        assertTrue(closed.isNotEmpty())
    }
}
