@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.companionsurface.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.companionsatellite.CompanionConnectionStatus
import org.churchpresenter.companionsurface.CompanionConnectionUiState
import org.churchpresenter.companionsurface.CompanionSurfacePanel
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.churchpresenter.companionsatellite.CompanionSatelliteClient
import org.churchpresenter.companionsurface.CompanionButtonState
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.companionsurface.CompanionSurfaceTab
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.bothThemes
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.PinnedFonts

/**
 * The Companion Surface tab: the hint with nothing configured, one surface's button grid, and the
 * chooser over several. Buttons are seeded into the view model's live grid, and the satellite client
 * is stubbed, so no socket is opened and every surface reads Disconnected.
 */
class CompanionSurfaceTabScreenshotTest {

    private val created = mutableListOf<CompanionSatelliteViewModel>()

    @BeforeTest
    fun stubClient() {
        mockkConstructor(CompanionSatelliteClient::class)
        every { anyConstructed<CompanionSatelliteClient>().connect(any(), any(), any(), any()) } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().disconnect() } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().dispose() } returns Unit
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkConstructor(CompanionSatelliteClient::class)
    }

    private fun connection(id: String, name: String) = CompanionSatelliteSettings(
        id = id,
        name = name,
        host = "10.0.0.5",
        deviceId = "device-$id",
        showInTab = true,
        tabRows = 2,
        tabColumns = 4,
    )

    private val sundayButtons = listOf(
        CompanionButtonState(0, text = "Walk-in", color = "#2E7D32", textColor = "#FFFFFF"),
        CompanionButtonState(1, text = "Worship", color = "#1565C0", textColor = "#FFFFFF", pressed = true),
        CompanionButtonState(2, text = "Sermon", color = "#6A1B9A", textColor = "#FFFFFF"),
        CompanionButtonState(3, text = "Blackout", color = "#000000", textColor = "#FF5252"),
        CompanionButtonState(4, text = "Cam 1", color = "#37474F", textColor = "#FFFFFF"),
        CompanionButtonState(5, text = "Cam 2", color = "#37474F", textColor = "#FFFFFF"),
        CompanionButtonState(6),
        CompanionButtonState(7, text = "Stream", color = "#C62828", textColor = "#FFFFFF"),
    )

    private fun CompanionSatelliteViewModel.seed(id: String, buttons: List<CompanionButtonState>) {
        buttonsFor(CompanionSurfaceSlot(id, CompanionSurfacePlacement.TAB)).apply {
            clear()
            addAll(buttons)
        }
    }

    private fun shoot(
        name: String,
        connections: List<CompanionSatelliteSettings>,
        withButtons: CompanionSatelliteViewModel.() -> Unit = {},
        drive: ComposeUiTest.() -> Unit = {},
    ) = bothThemes(SECTION, name) { mode, file ->
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            val vm = CompanionSatelliteViewModel().also { created += it }.apply(withButtons)
            setContent {
                PinnedFonts {
                    ChurchPresenterTheme(themeMode = mode) {
                        Surface(Modifier.fillMaxSize()) {
                            CompanionSurfaceTab(
                                appSettings = AppSettings(companionSatelliteConnections = connections),
                                viewModel = vm,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            drive()
            captureTo(file)
        }
    }

    @Test
    fun `nothing configured`() = shoot("no_host", emptyList())

    @Test
    fun `one surface with its buttons`() =
        shoot("one_surface", listOf(connection("a", "Sanctuary")), withButtons = { seed("a", sundayButtons) })

    @Test
    fun `several surfaces to choose from`() = shoot(
        "two_surfaces",
        listOf(connection("a", "Sanctuary"), connection("b", "Youth Room")),
        withButtons = {
            seed("a", sundayButtons)
            seed("b", sundayButtons.take(4).map { it.copy(color = "#455A64") })
        },
    ) {
        onNodeWithText("Youth Room").performClick()
        waitForIdle()
    }

    private fun CompanionSatelliteViewModel.status(
        id: String,
        status: CompanionConnectionStatus,
        error: String = "",
        placement: CompanionSurfacePlacement = CompanionSurfacePlacement.TAB,
    ) {
        val slot = CompanionSurfaceSlot(id, placement)
        connectionStatesState[slot] = CompanionConnectionUiState(slot, status, error)
    }

    @Test
    fun `one surface connected`() = shoot("connected", listOf(connection("a", "Sanctuary")), withButtons = {
        seed("a", sundayButtons)
        status("a", CompanionConnectionStatus.CONNECTED)
    })

    @Test
    fun `one surface connecting`() = shoot("connecting", listOf(connection("a", "Sanctuary")), withButtons = {
        seed("a", sundayButtons)
        status("a", CompanionConnectionStatus.CONNECTING)
    })

    @Test
    fun `one surface whose connection failed`() =
        shoot("connection_error", listOf(connection("a", "Sanctuary")), withButtons = {
            seed("a", sundayButtons)
            status("a", CompanionConnectionStatus.ERROR, error = "Connection refused")
        })

    @Test
    fun `a surface in the sidebar`() = bothThemes(SECTION, "sidebar") { mode, file ->
        val sidebar = connection("a", "Sanctuary").copy(
            showInTab = false,
            showInLeftSidebar = true,
            leftSidebarRows = 2,
            leftSidebarColumns = 4,
        )
        runDesktopComposeUiTest(width = SIDEBAR_WIDTH, height = HEIGHT) {
            val vm = CompanionSatelliteViewModel().also { created += it }
            vm.buttonsFor(CompanionSurfaceSlot("a", CompanionSurfacePlacement.LEFT_SIDEBAR)).addAll(sundayButtons)
            vm.status("a", CompanionConnectionStatus.CONNECTED, placement = CompanionSurfacePlacement.LEFT_SIDEBAR)
            setContent {
                PinnedFonts {
                    ChurchPresenterTheme(themeMode = mode) {
                        Surface(Modifier.fillMaxSize()) {
                            CompanionSurfacePanel(
                                connection = sidebar,
                                placement = CompanionSurfacePlacement.LEFT_SIDEBAR,
                                viewModel = vm,
                                modifier = Modifier.width(SIDEBAR_WIDTH.dp),
                                sizeToContent = true,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            captureTo(file)
        }
    }

    private companion object {
        const val SECTION = "companionSurfaceTab"
        const val WIDTH = 900
        const val HEIGHT = 520
        const val SIDEBAR_WIDTH = 340
    }
}
