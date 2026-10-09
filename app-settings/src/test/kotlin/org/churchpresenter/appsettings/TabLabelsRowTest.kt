@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import kotlin.test.Test

/** Every tab-label style and every margin names itself on its button. */
class TabLabelsRowTest {

    private val styleNames = mapOf(
        TabLabelStyle.TEXT to "Text only",
        TabLabelStyle.ICONS_AND_TEXT to "Icons and text",
        TabLabelStyle.ICONS to "Icons only",
    )
    private val marginNames = mapOf(
        TabLabelMargin.SMALL to "Small",
        TabLabelMargin.SMALL_NORMAL to "Medium-small",
        TabLabelMargin.NORMAL to "Normal",
        TabLabelMargin.NORMAL_LARGE to "Medium-large",
        TabLabelMargin.LARGE to "Large",
    )

    @Test
    fun `each style and margin is shown by its own name`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent { MaterialTheme { SystemSettingsTab(settings = settings) } }
        for ((style, name) in styleNames) {
            settings = settings.copy(tabLabelStyle = style)
            waitForIdle()
            onNode(hasText(name) and hasClickAction()).performScrollTo().assertExists()
        }
        for ((margin, name) in marginNames) {
            settings = settings.copy(tabLabelMargin = margin)
            waitForIdle()
            onNode(hasText(name) and hasClickAction()).performScrollTo().assertExists()
        }
    }
}
