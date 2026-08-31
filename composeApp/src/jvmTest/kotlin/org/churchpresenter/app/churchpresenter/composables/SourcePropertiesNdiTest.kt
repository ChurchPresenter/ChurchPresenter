@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ndi.NdiSourceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val CAMERA = NdiSourceInfo("BOOTH (Camera 1)", "192.168.1.20:5961")
private val GRAPHICS = NdiSourceInfo("BOOTH (Graphics)")

/**
 * The NDI panel's decisions, and what it shows on a machine with no NDI Runtime.
 *
 * The dropdown's contents are the network — no fixture can put a sender on it — so what the panel
 * *offers* is taken as functions and driven directly here. Both are about a source configured
 * somewhere else: a scene built in the booth and opened on a laptop, or a camera switched off
 * between services. Whether the panel still names it is the difference between an operator seeing
 * that their source is missing and being shown a blank picker for a layer that is configured.
 */
class SourcePropertiesNdiTest {

    private fun ndi(sourceName: String = "", sourceAddress: String = "", lowBandwidth: Boolean = false) =
        SceneSource.NdiSource(
            id = "ndi", name = "NDI", sourceName = sourceName,
            sourceAddress = sourceAddress, lowBandwidth = lowBandwidth,
        )

    // ── What the picker offers ────────────────────────────────────────────────

    @Test
    fun `an unconfigured layer offers exactly what discovery found`() {
        assertEquals(
            listOf("BOOTH (Camera 1)", "BOOTH (Graphics)"),
            ndiSourceChoices(listOf(CAMERA, GRAPHICS), configured = ""),
        )
    }

    @Test
    fun `a discovered source is offered once, not twice`() {
        assertEquals(
            listOf("BOOTH (Camera 1)", "BOOTH (Graphics)"),
            ndiSourceChoices(listOf(CAMERA, GRAPHICS), configured = "BOOTH (Camera 1)"),
        )
    }

    @Test
    fun `a configured source discovery cannot see is still offered, and still selected`() {
        // The camera is switched off, or discovery has not reached it yet. Dropping it from its own
        // dropdown would show the layer as pointing at nothing while the scene says otherwise.
        assertEquals(
            listOf("BOOTH (Graphics)", "BOOTH (Camera 1)"),
            ndiSourceChoices(listOf(GRAPHICS), configured = "BOOTH (Camera 1)"),
        )
    }

    @Test
    fun `nothing found and nothing configured offers nothing`() {
        assertEquals(emptyList(), ndiSourceChoices(emptyList(), configured = ""))
    }

    @Test
    fun `a configured source is offered even when the network is empty`() {
        assertEquals(listOf("BOOTH (Camera 1)"), ndiSourceChoices(emptyList(), "BOOTH (Camera 1)"))
    }

    // ── What choosing one stores ──────────────────────────────────────────────

    @Test
    fun `choosing a source stores its name and the address discovery reported`() {
        val updated = ndiSourceOn(ndi(), listOf(CAMERA, GRAPHICS), "BOOTH (Camera 1)")

        assertEquals("BOOTH (Camera 1)", updated.sourceName)
        assertEquals("192.168.1.20:5961", updated.sourceAddress)
    }

    @Test
    fun `switching to a source with no address clears the old one`() {
        // Otherwise the layer keeps the previous source's address and a receiver on another subnet
        // dials the wrong machine.
        val updated = ndiSourceOn(ndi("BOOTH (Camera 1)", "192.168.1.20:5961"), listOf(GRAPHICS), "BOOTH (Graphics)")

        assertEquals("BOOTH (Graphics)", updated.sourceName)
        assertEquals("", updated.sourceAddress)
    }

    @Test
    fun `choosing a source that is no longer on the list keeps the name and drops the address`() {
        val updated = ndiSourceOn(ndi(), emptyList(), "BOOTH (Camera 1)")

        assertEquals("BOOTH (Camera 1)", updated.sourceName)
        assertEquals("", updated.sourceAddress)
    }

    @Test
    fun `everything else about the layer survives being pointed somewhere new`() {
        val original = ndi("BOOTH (Graphics)", lowBandwidth = true)

        val updated = ndiSourceOn(original, listOf(CAMERA), "BOOTH (Camera 1)")

        assertEquals(original.id, updated.id)
        assertEquals(original.name, updated.name)
        assertEquals(original.transform, updated.transform)
        assertTrue(updated.lowBandwidth, "the bandwidth choice is not the source choice")
    }

    // ── What the panel shows with no runtime ──────────────────────────────────

    @Test
    fun `with no NDI Runtime the panel says so instead of showing an empty picker`() =
        sourcePanel(ndi()) { _ ->
            // No NDI Runtime is installed on a test machine, so this is the state every CI run and
            // every developer's first look at the panel is in.
            assertTrue(
                renderedText().any { it.contains("NDI Runtime") },
                "got ${renderedText()}",
            )
        }

    @Test
    fun `the panel still shows the shared header, so the layer can be named and moved`() =
        sourcePanel(ndi()) { _ ->
            assertTrue(renderedText().any { it == "NDI" }, "got ${renderedText()}")
        }
}
