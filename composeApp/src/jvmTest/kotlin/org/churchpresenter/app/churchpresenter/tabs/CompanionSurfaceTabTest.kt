@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.companionsatellite.CompanionSatelliteClient
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.models.CompanionButtonState
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which Companion surface the tab shows, and what happens to that choice when the surfaces change
 * underneath it.
 *
 * The tab is a chooser over the connections marked "show in tab" and nothing else, so its whole job
 * is picking one. Three of those decisions are only visible here: a connection the operator did not
 * mark for the tab must not appear even though it is configured and connected; a single surface must
 * not be wrapped in a one-item chooser; and — the one that has teeth — a surface removed in Settings
 * while it is the one on screen has to fall back to another rather than leaving the tab pointed at a
 * connection that no longer exists.
 *
 * Surfaces are told apart by seeding a differently-labelled button into each one's grid, which is
 * the view model's own live list, so no socket is involved for the grid itself.
 *
 * The satellite client's constructor is stubbed for the same reason as
 * [org.churchpresenter.app.churchpresenter.composables.CompanionSurfacePanelTest] — and the stub is
 * load-bearing, not defensive: without it, selecting a surface opens a real connection to the
 * configured host and the suite hangs rather than failing. That was checked by removing it.
 *
 * Cost note: MockK instruments the client class once per JVM, which lands on whichever test in this
 * class runs first (~1.5s). It is one-off instrumentation rather than a wait, and it is shared with
 * `CompanionSurfacePanelTest` when both run — but in a `tabs`-only run this class pays it alone, so
 * the slowest `time=` here is not a per-test cost anyone can remove by rewriting the test.
 */
class CompanionSurfaceTabTest {

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

    private fun vm() = CompanionSatelliteViewModel().also { created.add(it) }

    private fun connection(id: String, name: String, showInTab: Boolean = true) =
        CompanionSatelliteSettings(
            id = id,
            name = name,
            host = "10.0.0.5",
            deviceId = "device-$id",
            showInTab = showInTab,
            tabRows = 1,
            tabColumns = 1,
        )

    /** Puts one identifiable button on a connection's grid, so its surface can be told from another's. */
    private fun CompanionSatelliteViewModel.seed(id: String, text: String) {
        buttonsFor(CompanionSurfaceSlot(id, CompanionSurfacePlacement.TAB)).apply {
            clear()
            add(CompanionButtonState(index = 0, text = text))
        }
    }

    private companion object {
        const val NO_HOST = "Set a Companion host in Settings → Companion Satellite to connect."
        const val DROP_LABEL = "drop the second surface"
    }

    // ── Which surfaces are offered ──────────────────────────────────────────────

    @Test
    fun `with nothing configured the tab explains where to set a host`() {
        runComposeUiTest {
            val model = vm()
            setContent {
                CompanionSurfaceTab(
                    appSettings = AppSettings(companionSatelliteConnections = emptyList()),
                    viewModel = model,
                )
            }
            waitForIdle()

            assertTrue(showsExactly(NO_HOST), "an empty tab has to say what to do about it: ${renderedText()}")
        }
    }

    @Test
    fun `a connection not marked for the tab is not offered here`() {
        // It is still configured and may well be connected — it just belongs on a different surface.
        runComposeUiTest {
            val model = vm()
            model.seed("hidden", "HIDDEN")
            setContent {
                CompanionSurfaceTab(
                    appSettings = AppSettings(
                        companionSatelliteConnections = listOf(connection("hidden", "Booth", showInTab = false))
                    ),
                    viewModel = model,
                )
            }
            waitForIdle()

            assertTrue(showsExactly(NO_HOST))
            assertFalse(showsContainingText("HIDDEN"), "a surface hidden from the tab must not render here")
        }
    }

    @Test
    fun `a single surface is shown without a chooser`() {
        runComposeUiTest {
            val model = vm()
            model.seed("only", "ONLY")
            setContent {
                CompanionSurfaceTab(
                    appSettings = AppSettings(companionSatelliteConnections = listOf(connection("only", "Booth"))),
                    viewModel = model,
                )
            }
            waitForIdle()

            assertTrue(showsContainingText("ONLY"), "the one surface is shown straight away: ${renderedText()}")
            assertFalse(showsExactly("Booth"), "one surface needs no chooser to pick it from")
        }
    }

    // ── Choosing between surfaces ───────────────────────────────────────────────

    @Test
    fun `two surfaces get a chooser, and picking one shows it`() {
        runComposeUiTest {
            val model = vm()
            model.seed("a", "FIRST")
            model.seed("b", "SECOND")
            setContent {
                CompanionSurfaceTab(
                    appSettings = AppSettings(
                        companionSatelliteConnections = listOf(connection("a", "Booth"), connection("b", "Stage"))
                    ),
                    viewModel = model,
                )
            }
            waitForIdle()

            assertTrue(showsExactly("Booth") && showsExactly("Stage"), "both are offered: ${renderedText()}")
            assertTrue(showsContainingText("FIRST"), "the first is shown by default")

            onNodeWithText("Stage").performClick()
            waitUntil("the second surface to take over") { showsContainingText("SECOND") }

            assertFalse(showsContainingText("FIRST"), "one surface at a time")
        }
    }

    // ── The surface list changing underneath ────────────────────────────────────

    @Test
    fun `a surface removed in settings hands the tab back to one that still exists`() {
        // Unticking "show in tab" for the surface currently on screen is an ordinary Settings edit.
        // Without the fallback the tab keeps a selected id that matches nothing and goes blank —
        // showing the operator the no-host message while a working surface sits right there.
        runComposeUiTest {
            val model = vm()
            model.seed("a", "FIRST")
            model.seed("b", "SECOND")
            setContent {
                var connections by remember {
                    mutableStateOf(listOf(connection("a", "Booth"), connection("b", "Stage")))
                }
                Column {
                    // Stands in for the Settings edit: the tab reads its connection list from
                    // AppSettings, so removing one there is what the tab actually has to survive.
                    Button(onClick = { connections = connections.filter { it.id != "b" } }) {
                        Text(DROP_LABEL)
                    }
                    CompanionSurfaceTab(
                        appSettings = AppSettings(companionSatelliteConnections = connections),
                        viewModel = model,
                    )
                }
            }
            waitForIdle()

            onNodeWithText("Stage").performClick()
            waitUntil("the second surface to take over") { showsContainingText("SECOND") }

            onNodeWithText(DROP_LABEL).performClick()

            waitUntil("the tab to fall back to the surface that is left") { showsContainingText("FIRST") }
            assertFalse(showsExactly(NO_HOST), "there is still a surface to show, so the empty state is wrong")
        }
    }
}
