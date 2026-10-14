@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfilePageGroupsDefaultsTest {

    @Test
    fun `position paths default to nothing marked`() {
        assertEquals(emptyList(), PositionPaths().all)
    }

    @Test
    fun `a Position group with its defaults writes the region and the margins`() = runComposeUiTest {
        var region by mutableStateOf(ContentRegion())
        var margins by mutableStateOf(Margins(10, 10, 10, 10))
        setContent {
            MaterialTheme {
                Column {
                    PositionGroup(
                        verticalAlignment = Constants.TOP,
                        onVerticalAlignment = {},
                        margins = margins,
                        onMargins = { margins = it },
                        region = region,
                        onRegion = { region = it },
                        reset = null,
                    )
                }
            }
        }
        onAllNodes(hasText("Left") and hasClickAction()).onFirst().performClick()
        waitForIdle()
        assertEquals(ContentRegion.OFFSET_RANGE.first, region.xOffsetPercent)

        onNodeWithTag(MARGIN_LEFT_TAG).performTextReplacement("25")
        waitForIdle()
        assertEquals(25, margins.left)
    }

    @Test
    fun `a text summary with no font names only the size`() = runComposeUiTest {
        setContent { MaterialTheme { Text(textSummary("", 48)) } }
        assertTrue(onAllNodesWithText("48", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("·", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `placing an element freely edits both axes and switches off`() = runComposeUiTest {
        var offset by mutableStateOf<ElementOffset?>(ElementOffset())
        setContent {
            MaterialTheme {
                Column { ElementPlacementRows(offset = offset, onChange = { offset = it }, tagPrefix = "place") }
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextReplacement("20")
        waitForIdle()
        assertEquals(20, offset?.xPercent)
        onAllNodes(hasSetTextAction())[1].performTextReplacement("70")
        waitForIdle()
        assertEquals(70, offset?.yPercent)

        onNodeWithTag("place_enabled").performClick()
        waitForIdle()
        assertNull(offset)
        onNodeWithTag("place_enabled").performClick()
        waitForIdle()
        assertEquals(ElementOffset(), offset)
    }

    @Test
    fun `a Transition group with no settings prefix still shows its rows`() = runComposeUiTest {
        var fadeIn by mutableStateOf(false)
        setContent {
            MaterialTheme {
                Column {
                    TransitionGroup(
                        fadeIn = fadeIn,
                        fadeOut = false,
                        crossfade = null,
                        durationMs = 300f,
                        onFadeIn = { fadeIn = it },
                        onFadeOut = {},
                        onCrossfade = {},
                        onDuration = {},
                        reset = null,
                    )
                }
            }
        }
        onNodeWithText("Transition", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun `picking the band source it already has changes nothing`() = runComposeUiTest {
        val profile = OutputProfile(id = "p", displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        var changed = 0
        setContent {
            MaterialTheme {
                Column {
                    BandGroup(
                        scope = BackgroundScope.BIBLE_LOWER_THIRD,
                        heightPercent = 30,
                        onHeight = {},
                        draft = AppSettings(),
                        profile = profile,
                        onProfileChange = { changed++ },
                        onSettingsChange = {},
                        reset = null,
                    )
                }
            }
        }
        onNodeWithTag(BAND_SOURCE_APP_DEFAULT_TAG).performClick()
        waitForIdle()
        assertEquals(0, changed)
    }

    @Test
    fun `an applies-to strip with one target and no element draws neither row`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AppliesToStrip(
                    targets = listOf(RowOption(1, "Only")),
                    target = 1,
                    onTarget = {},
                    elements = listOf(RowOption("a", "A"), RowOption("b", "B")),
                    element = null,
                    onElement = {},
                )
            }
        }
        assertTrue(onAllNodesWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithTag(CUSTOMIZE_ELEMENT_ROW_TAG).fetchSemanticsNodes().isEmpty())
    }
}
