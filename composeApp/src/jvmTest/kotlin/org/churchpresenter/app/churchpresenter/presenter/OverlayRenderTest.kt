package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An announcement going up while a verse is live, through a real off-screen output: in place of
 * the verse by default, over it when the output's profile says so -- and a lock still wins.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayRenderTest {

    private val verseText = "For God so loved the world"
    private val notice = "Parents of child 42"

    private fun output(
        profile: OutputProfile,
        lockedTo: Presenting? = null,
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        // A still announcement: the default slides in from off screen and is not there to find yet.
        val base = AppSettings(announcementsSettings = AnnouncementsSettings(animationType = Constants.ANIMATION_NONE))
        val settings = base.copy(
            projectionSettings = base.projectionSettings.copy(outputProfiles = listOf(profile.copy(id = "p"))),
        )
        val manager = PresenterManager(showPresenterWindowInitially = false).apply {
            setPresentingMode(Presenting.BIBLE)
            setDisplayedVerses(
                listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = verseText)),
            )
            setDisplayedAnnouncementText(notice)
            setPresentingMode(Presenting.ANNOUNCEMENTS)
        }
        setContent {
            Box(Modifier.size(480.dp, 270.dp)) {
                OffscreenOutputContent(
                    OffscreenOutputContext(
                        presenterManager = manager,
                        appSettingsState = mutableStateOf(settings),
                        screenAssignmentState = mutableStateOf(ScreenAssignment(activeProfileId = "p")),
                        effectiveModeState = mutableStateOf(lockedTo ?: manager.presentingMode.value),
                        outputIndex = 0,
                        kind = OffscreenOutputKind.NDI,
                    ),
                    transparentBlanking = false,
                )
            }
        }
        waitForIdle()
        body()
    }

    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `by default the announcement replaces the verse`() = output(OutputProfile()) {
        assertTrue(shows(notice))
        assertFalse(shows(verseText), "the verse must be taken down, as it always was")
    }

    @Test
    fun `an output set to put announcements over the content keeps the verse`() =
        output(OutputProfile(announcementsOverContent = true)) {
            assertTrue(shows(notice))
            assertTrue(shows(verseText))
        }

    @Test
    fun `an output locked to other content shows that and nothing over it`() =
        output(OutputProfile(announcementsOverContent = true), lockedTo = Presenting.LYRICS) {
            assertFalse(shows(verseText))
            assertFalse(shows(notice))
        }
}
