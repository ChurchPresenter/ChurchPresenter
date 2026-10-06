@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.settingspages.screenshot

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import org.churchpresenter.settingspages.OmtOutputsCard
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.captureComponent

private val BUNDLED = OmtRuntimeStatus.Ready("/app/omt/libomt.dylib", bundled = true)

/**
 * The OMT outputs card, in each state a reviewer should see.
 *
 * Shot on its own rather than through the Projection tab, for the reason the Camera Capture card is:
 * it sits below the NDI card on a scrolling page, well past where the tab's images are clipped.
 * Everything the card would read from the machine — the library, the receiver count, the network
 * name — is pinned.
 */
class ProjectionOmtCardScreenshotTest {

    private companion object {
        const val SECTION = "projectionOmtCard"
        val CARD_WIDTH = 900.dp
    }

    private fun settings(
        vararg outputs: ScreenAssignment,
        libraryPath: String = "",
        discoveryServer: String = "",
    ) = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(OutputProfile(id = "main", name = "Main")),
            omtOutputs = outputs.toList(),
            omtLibraryPath = libraryPath,
            omtDiscoveryServer = discoveryServer,
        ),
    )

    private fun shoot(
        name: String,
        settings: AppSettings,
        status: OmtRuntimeStatus = BUNDLED,
        receivers: Int = 0,
        address: String = "",
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = captureComponent(SECTION, name, rootIndex = rootIndex, drive = drive) {
        Box(Modifier.width(CARD_WIDTH)) {
            OmtOutputsCard(
                settings = settings,
                onSettingsChange = {},
                status = status,
                receiverCount = { receivers },
                addressOf = { address },
                recheck = { _, _ -> },
            )
        }
    }

    @Test
    fun `the bundled library with nothing configured yet`() = shoot("bundled_empty", settings())

    @Test
    fun `an alpha output someone is watching`() = shoot(
        "output_alpha_watched",
        settings(ScreenAssignment(omtName = "Lower Third", activeProfileId = "main")),
        receivers = 1,
        address = "BOOTH-MAC (Lower Third)",
    )

    @Test
    fun `a fill output at a fixed quality, switched off`() = shoot(
        "output_fill_disabled",
        settings(
            ScreenAssignment(
                omtName = "Overflow", omtEnabled = false, omtMode = Constants.OMT_MODE_FILL,
                omtQuality = Constants.OMT_QUALITY_HIGH, omtWidth = 1280, omtHeight = 720,
                activeProfileId = "main",
            ),
        ),
    )

    @Test
    fun `the quality menu open`() = shoot(
        "quality_menu",
        settings(ScreenAssignment(activeProfileId = "main")),
        rootIndex = 1,
        drive = {
            onNodeWithText("Automatic").performClick()
            waitForIdle()
        },
    )

    @Test
    fun `a library chosen by the operator, with a discovery server`() = shoot(
        "custom_library",
        settings(libraryPath = "/opt/omt", discoveryServer = "omt://10.0.0.2:6400"),
        status = OmtRuntimeStatus.Ready("/opt/omt/libomt.dylib", bundled = false),
    )

    @Test
    fun `no library on this computer`() = shoot(
        "not_found",
        settings(),
        status = OmtRuntimeStatus.NotInstalled,
    )

    @Test
    fun `a Linux machine without the Avahi service`() = shoot(
        "avahi_missing",
        settings(),
        status = OmtRuntimeStatus.DiscoveryServiceMissing,
    )

    @Test
    fun `a library that would not load`() = shoot(
        "load_failed",
        settings(libraryPath = "/opt/omt"),
        status = OmtRuntimeStatus.LoadFailed("/opt/omt/libomt.dylib"),
    )
}
