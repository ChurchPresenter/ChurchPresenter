@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.PropsDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.propEditTag
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Props dialog, in both themes: with no props, and with three -- two up -- and one open for
 * editing. Shot through `PropsDialogContent`, since a `DialogWindow` cannot be photographed headless.
 */
class PropsDialogScreenshotTest {

    private val props = listOf(
        PropDefinition("prop1", "Live", PropKind.BADGE, PropCorner.TOP_LEFT, 7, text = "LIVE"),
        PropDefinition("prop2", "Clock", PropKind.CLOCK, PropCorner.TOP_RIGHT, 7),
        PropDefinition("prop3", "Service starts", PropKind.COUNTDOWN, PropCorner.BOTTOM_RIGHT, 8, countdownTo = "10:30"),
    )

    private fun shoot(name: String, props: List<PropDefinition>, drive: ComposeUiTest.() -> Unit = {}) =
        stackedThemes(SECTION, name) { mode, file ->
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(560.dp, 620.dp)) {
                            PropsDialogContent(props, {}, setOf("prop1", "prop2"), { _, _ -> }, { null }, {})
                        }
                    }
                }
                drive()
                waitForIdle()
                captureTo(file)
            }
        }

    @Test
    fun `the dialog with no props`() = shoot("empty", emptyList())

    @Test
    fun `three props, two up, one open for editing`() = shoot("editing", props) {
        onNodeWithTag(propEditTag("prop3")).performClick()
    }

    private companion object {
        const val SECTION = "propsDialog"
    }
}
