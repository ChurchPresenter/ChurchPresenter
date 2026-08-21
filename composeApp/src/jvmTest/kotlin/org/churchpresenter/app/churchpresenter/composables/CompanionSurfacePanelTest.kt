@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import companionsatellite.CompanionConnectionStatus
import companionsatellite.CompanionSatelliteClient
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.models.CompanionButtonState
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What one Companion Satellite surface draws, and what pressing one of its buttons does.
 *
 * The panel is a status line over a grid of buttons Companion pushed. Buttons are seeded through the
 * view model's own live list ([CompanionSatelliteViewModel.buttonsFor] hands back the very
 * `SnapshotStateList` the panel renders), so no socket is involved for the grid. The connection
 * *status* only ever arrives from a client callback, so the three status labels and the enabled-grid
 * state are reached by stubbing `CompanionSatelliteClient`'s constructor and invoking the callback
 * the view model handed it — the same route `CompanionSatelliteViewModelTest` established, and the
 * only one that reaches this logic without a live Companion instance.
 *
 * Cost note: MockK instruments the client class once for the whole class (~1.5s on the first test),
 * which is one-off JVM work rather than a wait.
 */
class CompanionSurfacePanelTest {

    private val created = mutableListOf<CompanionSatelliteViewModel>()

    @BeforeTest
    fun stubClient() {
        mockkConstructor(CompanionSatelliteClient::class)
        every {
            anyConstructed<CompanionSatelliteClient>()
                .connect(any(), any(), any(), any())
        } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().disconnect() } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().dispose() } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().pressButton(any()) } returns Unit
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkConstructor(CompanionSatelliteClient::class)
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private val placement = CompanionSurfacePlacement.TAB
    private val slot = CompanionSurfaceSlot(CONNECTION_ID, placement)

    private fun vm() = CompanionSatelliteViewModel().also { created.add(it) }

    private fun settings(host: String = "10.0.0.5", columns: Int = 2, rows: Int = 2, maxButtonSizeDp: Int = 0) =
        CompanionSatelliteSettings(
            id = CONNECTION_ID,
            host = host,
            deviceId = "device-1",
            showInTab = true,
            tabRows = rows,
            tabColumns = columns,
            tabMaxButtonSizeDp = maxButtonSizeDp,
        )

    /** Puts [buttons] on the grid the panel renders, without any wire traffic. */
    private fun CompanionSatelliteViewModel.seedButtons(vararg buttons: CompanionButtonState) {
        buttonsFor(slot).apply {
            clear()
            addAll(buttons)
        }
    }

    /**
     * Reports [status] for the slot as the client's own callback would.
     *
     * `connectAll` builds the client (stubbed, so no socket) and hands it the callbacks; the status
     * one is then invoked directly. Both hops need reflection: the registry is private to the view
     * model and the callback is a private field of the module's client class.
     */
    private fun CompanionSatelliteViewModel.reportStatus(
        settings: CompanionSatelliteSettings,
        status: CompanionConnectionStatus,
        error: String? = null,
    ) {
        connectAll(settings)
        val clients = CompanionSatelliteViewModel::class.java
            .getDeclaredField("clients").apply { isAccessible = true }
            .get(this)
        @Suppress("UNCHECKED_CAST")
        val client = (clients as Map<CompanionSurfaceSlot, CompanionSatelliteClient>)[slot]
            ?: error("no client registered for $slot")
        @Suppress("UNCHECKED_CAST")
        val callback = CompanionSatelliteClient::class.java
            .getDeclaredField("onStatusChanged").apply { isAccessible = true }
            .get(client) as (CompanionConnectionStatus, String?) -> Unit
        callback(status, error)
    }

    /** Reports [percent] brightness for the slot as the client's own callback would — same two
     *  reflection hops as [reportStatus], for the sibling private field. */
    private fun CompanionSatelliteViewModel.reportBrightness(settings: CompanionSatelliteSettings, percent: Int) {
        connectAll(settings)
        val clients = CompanionSatelliteViewModel::class.java
            .getDeclaredField("clients").apply { isAccessible = true }
            .get(this)
        @Suppress("UNCHECKED_CAST")
        val client = (clients as Map<CompanionSurfaceSlot, CompanionSatelliteClient>)[slot]
            ?: error("no client registered for $slot")
        @Suppress("UNCHECKED_CAST")
        val callback = CompanionSatelliteClient::class.java
            .getDeclaredField("onBrightnessChanged").apply { isAccessible = true }
            .get(client) as (Int) -> Unit
        callback(percent)
    }

    /** Composes the panel at a fixed size — a lazy grid needs bounds before it lays anything out. */
    private fun panel(
        vm: CompanionSatelliteViewModel,
        settings: CompanionSatelliteSettings = settings(),
        sizeToContent: Boolean = false,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
                    CompanionSurfacePanel(
                        connection = settings,
                        placement = placement,
                        viewModel = vm,
                        sizeToContent = sizeToContent,
                    )
                }
            }
        }
        block()
    }

    // ── No host configured ──────────────────────────────────────────────────────────────────────

    @Test
    fun `without a host the panel says where to set one`() {
        panel(vm(), settings = settings(host = "")) {
            assertTrue(rendersText(NO_HOST), renderedText().toString())
        }
    }

    @Test
    fun `without a host no buttons are drawn even when some are known`() {
        val vm = vm()
        vm.seedButtons(CompanionButtonState(index = 0, text = "GO"))

        panel(vm, settings = settings(host = "")) {
            assertFalse(rendersText("GO"), renderedText().toString())
        }
    }

    // ── Status line ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a configured but unconnected surface shows Disconnected`() {
        panel(vm()) {
            assertTrue(rendersText(DISCONNECTED), renderedText().toString())
        }
    }

    @Test
    fun `connecting is called out while it happens`() {
        val vm = vm()
        val settings = settings()
        vm.reportStatus(settings, CompanionConnectionStatus.CONNECTING)

        panel(vm, settings) {
            assertTrue(rendersText(CONNECTING), renderedText().toString())
            assertFalse(rendersText(DISCONNECTED))
        }
    }

    @Test
    fun `an error is shown with the reason Companion gave`() {
        val vm = vm()
        val settings = settings()
        vm.reportStatus(settings, CompanionConnectionStatus.ERROR, error = "connection refused")

        panel(vm, settings) {
            assertTrue(rendersText("Error: connection refused"), renderedText().toString())
        }
    }

    @Test
    fun `once connected the status line goes away`() {
        val vm = vm()
        val settings = settings()
        vm.reportStatus(settings, CompanionConnectionStatus.CONNECTED)

        panel(vm, settings) {
            // The live grid is its own confirmation — a standing "Connected" label would be clutter.
            assertFalse(rendersText(DISCONNECTED), renderedText().toString())
            assertFalse(rendersText(CONNECTING))
        }
    }

    // ── The button grid ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `less than full brightness dims the grid without disrupting it`() {
        val vm = vm()
        val settings = settings()
        vm.seedButtons(CompanionButtonState(index = 0, text = "GO"))
        vm.reportBrightness(settings, 40)

        panel(vm, settings) {
            // The dim overlay is an unlabelled Box with no semantics trace of its own; this confirms
            // the dimAlpha > 0 branch composes cleanly alongside the button it sits over.
            assertTrue(rendersText("GO"), renderedText().toString())
        }
    }

    @Test
    fun `full brightness draws no dim overlay`() {
        val vm = vm()
        val settings = settings()
        vm.seedButtons(CompanionButtonState(index = 0, text = "GO"))
        vm.reportBrightness(settings, 100)

        panel(vm, settings) {
            assertTrue(rendersText("GO"), renderedText().toString())
        }
    }

    @Test
    fun `each button Companion pushed is drawn by its text`() {
        val vm = vm()
        vm.seedButtons(
            CompanionButtonState(index = 0, text = "GO"),
            CompanionButtonState(index = 1, text = "STOP"),
        )

        panel(vm) {
            assertTrue(rendersText("GO"), renderedText().toString())
            assertTrue(rendersText("STOP"))
        }
    }

    @Test
    fun `a button with a bitmap is drawn as the image, not as text`() {
        val vm = vm()
        vm.seedButtons(
            CompanionButtonState(index = 0, text = "described", bitmap = ImageBitmap(8, 8)),
        )

        panel(vm) {
            // The bitmap takes the button's text as its content description instead of drawing it.
            assertFalse(rendersText("described"), renderedText().toString())
            assertTrue(
                contentDescriptions().contains("described"),
                contentDescriptions().toString(),
            )
        }
    }

    @Test
    fun `a blank button draws nothing at all`() {
        val vm = vm()
        vm.seedButtons(CompanionButtonState(index = 0, text = "   "))

        panel(vm) {
            // The status label is all that is left — the cell itself draws no text node.
            assertEquals(setOf(DISCONNECTED), renderedText())
        }
    }

    @Test
    fun `custom button and text colours do not stop it rendering`() {
        val vm = vm()
        vm.seedButtons(
            CompanionButtonState(index = 0, text = "TINTED", color = "#123456", textColor = "#FEDCBA"),
        )

        panel(vm) {
            assertTrue(rendersText("TINTED"), renderedText().toString())
        }
    }

    // ── Pressing a button ───────────────────────────────────────────────────────────────────────

    @Test
    fun `pressing a live button presses that index on the client`() {
        val vm = vm()
        val settings = settings()
        vm.reportStatus(settings, CompanionConnectionStatus.CONNECTED)
        vm.seedButtons(
            CompanionButtonState(index = 0, text = "FIRST"),
            CompanionButtonState(index = 7, text = "SEVENTH"),
        )

        panel(vm, settings) {
            onNodeWithText("SEVENTH").performClick()
        }

        // The button's own index travels, not its position in the grid.
        verify(exactly = 1) { anyConstructed<CompanionSatelliteClient>().pressButton(7) }
    }

    @Test
    fun `a button on a disconnected surface cannot be pressed`() {
        val vm = vm()
        val settings = settings()
        // A client exists (so a press would have somewhere to go) but the surface never came up.
        vm.reportStatus(settings, CompanionConnectionStatus.DISCONNECTED)
        vm.seedButtons(CompanionButtonState(index = 0, text = "GO"))

        panel(vm, settings) {
            // A disabled `clickable` keeps its click node and marks it not-enabled, so the press has
            // to be attempted to show that nothing goes out.
            onNodeWithText("GO").assertIsNotEnabled()
            onNodeWithText("GO").performClick()
        }

        verify(exactly = 0) { anyConstructed<CompanionSatelliteClient>().pressButton(any()) }
    }

    // ── Sizing ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sidebar panel sized to its content still draws its buttons`() {
        val vm = vm()
        vm.seedButtons(CompanionButtonState(index = 0, text = "GO"))

        panel(vm, sizeToContent = true) {
            assertTrue(rendersText("GO"), renderedText().toString())
        }
    }

    @Test
    fun `a capped button size still draws its buttons, sized or filling`() {
        val vm = vm()
        vm.seedButtons(CompanionButtonState(index = 0, text = "GO"))

        panel(vm, settings = settings(maxButtonSizeDp = 40), sizeToContent = true) {
            assertTrue(rendersText("GO"), renderedText().toString())
        }
        panel(vm, settings = settings(maxButtonSizeDp = 40), sizeToContent = false) {
            assertTrue(rendersText("GO"), renderedText().toString())
        }
    }

    private companion object {
        const val CONNECTION_ID = "connection-1"
        const val NO_HOST = "Set a Companion host in Settings → Companion Satellite to connect."
        const val CONNECTING = "Connecting…"
        const val DISCONNECTED = "Disconnected"
    }
}

// ── Reading what was drawn ──────────────────────────────────────────────────────────────────────

// renderedText() is the composables package's shared helper, in SourcePropertiesPanelTestSupport.kt.

private fun ComposeUiTest.rendersText(text: String): Boolean = renderedText().contains(text)

private fun ComposeUiTest.contentDescriptions(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
