@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Profiles → Content switches that put a lower third, announcements or captions over the
 * content rather than in place of it: off to begin with, each offered only while its content is
 * shown, and left out of the "N of M shown" summary and Show all, since they are not content.
 */
class ProfileOverContentSwitchTest {

    private val lowerThird = "Lower third goes over the content"
    private val announcements = "Announcements go over the content"
    private val captions = "Captions go over the content"

    private fun page(profile: OutputProfile, block: ComposeUiTest.(() -> OutputProfile) -> Unit) {
        var current = profile
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    // Scrollable, as in the dialog: the overlay switches sit below the fold.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ProfileContentPage(AppSettings(), current, onProfileChange = { current = it })
                    }
                }
            }
            block { current }
        }
    }

    private fun ComposeUiTest.offered(label: String): Boolean =
        onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun ComposeUiTest.summary(): String =
        onNodeWithTag(PROFILE_CONTENT_SUMMARY_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }

    @Test
    fun `all three are offered, off, on a profile showing everything`() = page(OutputProfile(id = "p")) { current ->
        assertTrue(offered(lowerThird) && offered(announcements) && offered(captions))
        val profile = current()
        assertFalse(profile.lowerThirdOverContent || profile.announcementsOverContent || profile.captionsOverContent)
    }

    @Test
    fun `each switch stores its own setting`() = page(OutputProfile(id = "p")) { current ->
        onAllNodesWithText(lowerThird)[0].performScrollTo().performClick()
        waitForIdle()
        assertTrue(current().lowerThirdOverContent)
        assertFalse(current().announcementsOverContent)
        onAllNodesWithText(announcements)[0].performScrollTo().performClick()
        waitForIdle()
        assertTrue(current().announcementsOverContent)
        onAllNodesWithText(captions)[0].performScrollTo().performClick()
        waitForIdle()
        assertTrue(current().captionsOverContent)
    }

    @Test
    fun `a switch is offered only while its content is shown here`() = page(
        OutputProfile(id = "p", showStreaming = false, showAnnouncements = false, showSTT = false),
    ) { _ ->
        assertFalse(offered(lowerThird) || offered(announcements) || offered(captions))
    }

    @Test
    fun `the summary does not count them as hidden content`() = page(OutputProfile(id = "p")) { _ ->
        val text = summary()
        assertFalse(lowerThird in text || announcements in text || captions in text, "summary was: $text")
    }

    @Test
    fun `Show all leaves them alone`() = page(OutputProfile(id = "p", showQA = false)) { current ->
        onAllNodesWithText("Show all")[0].performClick()
        waitForIdle()
        assertTrue(current().showQA, "Show all shows the hidden content")
        assertEquals(false, current().lowerThirdOverContent, "and does not move the lower third over the content")
    }
}
