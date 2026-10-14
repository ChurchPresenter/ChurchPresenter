@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.ProfileFocus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfilesTabFlowsTest {

    private fun twoProfiles() = profileDocument().let { doc ->
        doc.copy(
            projectionSettings = doc.projectionSettings.copy(
                outputProfiles = doc.projectionSettings.outputProfiles + OutputProfile(id = "foyer", name = "Foyer"),
            ),
        )
    }

    @Test
    fun `with no profiles the tab says so and a tour's focus is still let go`() = runComposeUiTest {
        val session = GuideSession().apply { profileFocus = ProfileFocus(profileId = "gone") }
        setContent {
            CompositionLocalProvider(LocalGuideSession provides session) {
                ProfilesSettingsTab(
                    settings = AppSettings(projectionSettings = ProjectionSettings(outputProfiles = emptyList())),
                    onSettingsChange = {},
                )
            }
        }
        waitForIdle()

        assertNull(session.profileFocus)
        onNodeWithText("Create a profile to start styling it").assertExists()
    }

    @Test
    fun `deleting a profile asks first, and cancelling keeps it`() = profilesTab(twoProfiles()) { get ->
        onNodeWithTag(actionKeyTag("Delete")).performScrollTo().performClick()
        waitForIdle()
        onNode(hasText("Cancel") and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(2, get().projectionSettings.outputProfiles.size)

        onNodeWithTag(actionKeyTag("Delete")).performScrollTo().performClick()
        waitForIdle()
        onNode(hasText("OK") and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(listOf("foyer"), get().projectionSettings.outputProfiles.map { it.id })
    }

    @Test
    fun `the header's Basic switch and Assign output reach the document and the Outputs page`() =
        profilesTab(profileDocument()) { get ->
            onNodeWithTag(railTag(CustomizePane.BIBLE.name)).performClick()
            waitForIdle()
            onNodeWithTag(DETAIL_BASIC_TAG).performClick()
            waitForIdle()
            assertFalse(get().profilesAdvanced)

            onNodeWithTag(ASSIGN_OUTPUT_TAG).performClick()
            waitForIdle()
            assertTrue(onAllNodesWithText("Main · Bible").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `the Subtitles and Q&A pages open from the rail`() = profilesTab(profileDocument()) {
        listOf(CustomizePane.SUBTITLES, CustomizePane.QA).forEach { pane ->
            onNodeWithTag(railTag(pane.name)).performScrollTo().performClick()
            waitForIdle()
            assertTrue(onAllNodesWithText("Main · General").fetchSemanticsNodes().isEmpty(), pane.name)
        }
    }
}
