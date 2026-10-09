@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.ProfileFocus
import kotlin.test.Test
import kotlin.test.assertNull

/** A Wick tour opening the Profiles page on a profile, a page and the row it rings. */
class ProfilesGuideFocusTest {

    @Test
    fun `a tour opens the page that lists its row`() {
        val session = GuideSession().apply {
            profileFocus = ProfileFocus(profileId = PROFILE_ID, rowKey = ROW)
            activeTarget = GuideTargets.settingsRow(ROW)
        }
        profilesTab(profileDocument(), advanced = false, session = session) {
            waitForIdle()
            assertNull(session.profileFocus, "the page takes the focus and puts it back")
            onAllNodesWithText("Main · Background").onFirst().assertExists()
        }
    }

    @Test
    fun `a focus naming no profile keeps the one selected, and one leading nowhere is still let go`() {
        val session = GuideSession().apply { profileFocus = ProfileFocus(page = "OUTPUTS") }
        profilesTab(profileDocument(), session = session) {
            waitForIdle()
            assertNull(session.profileFocus)
            session.profileFocus = ProfileFocus(profileId = "gone", page = "GONE", rowKey = "not_a_string_key")
            waitForIdle()
            assertNull(session.profileFocus)
        }
    }

    private companion object {
        const val ROW = "background_above_band_caption"
    }
}
