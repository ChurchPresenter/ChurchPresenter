package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Settings -> Profiles shows in Basic. The rule every page keeps: an on/off switch is Basic and
 * its fine values Advanced, and a control has one level wherever it appears -- so nothing a Basic
 * user can see the effect of (a crossfade, an end-of-song marker on by default, a shadow) is out of
 * their reach, and the fine-tuning still waits for Advanced.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileBasicLevelTest {

    private fun SkikoComposeUiTest.rowsLabelled(label: String): Int =
        onAllNodes(hasText(label)).fetchSemanticsNodes().size

    private fun SkikoComposeUiTest.assertShown(vararg labels: String) = labels.forEach { label ->
        assertTrue(rowsLabelled(label) > 0, "Basic should show \"$label\"")
    }

    private fun SkikoComposeUiTest.assertHidden(vararg labels: String) = labels.forEach { label ->
        assertEquals(0, rowsLabelled(label), "\"$label\" is a fine value and belongs to Advanced")
    }

    @Test
    fun `the Songs page shows its switches in Basic and keeps the fine values Advanced`() =
        profilesTab(profileDocument(), advanced = false) {
            openCustomizePane(CustomizePane.SONGS)
            assertShown("Crossfade between items", "End-of-song marker", "Letter case", "Shadow", "Opacity")
            assertHidden("Letter spacing", "Word spacing", "Background follows Content Region")
        }

    @Test
    fun `the Bible page has the crossfade and the shadow in Basic`() =
        profilesTab(profileDocument(), advanced = false) {
            openCustomizePane(CustomizePane.BIBLE)
            assertShown("Crossfade between items", "Shadow", "Opacity")
            assertHidden("Letter spacing")
        }

    @Test
    fun `live captions type out, and style the translation, in Basic`() = profilesTab(
        profileDocument(profile = OutputProfile(sttSettings = STTSettings(displayMode = "both"))),
        advanced = false,
    ) {
        openCustomizePane(CustomizePane.CAPTIONS)
        assertShown("Type words out as they arrive", "Bold translation", "Italic translation")
        assertHidden("Letter spacing", "Segments kept")
    }

    @Test
    fun `Q&A shows its text shadow and both opacities in Basic`() =
        profilesTab(profileDocument(), advanced = false) {
            openCustomizePane(CustomizePane.QA)
            assertShown("Shadow")
            assertEquals(2, rowsLabelled("Opacity"), "the box's opacity and the QR code's, each beside its colour")
        }

    @Test
    fun `the Dictionary card has its text shadow in Basic`() =
        profilesTab(profileDocument(), advanced = false) {
            openCustomizePane(CustomizePane.DICTIONARY)
            assertShown("Shadow")
        }
}
