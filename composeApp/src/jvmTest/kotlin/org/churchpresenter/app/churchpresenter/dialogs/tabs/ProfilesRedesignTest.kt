@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The redesigned Profiles tab's own controls: the content section's Show all / Hide all, the Bible
 * source's order, the preview shape's Custom, the Scale row, the Style tabs that follow the content,
 * the whole-form tabs writing to the profile alone, and styling every translation at once.
 */
class ProfilesRedesignTest {

    private fun twoTranslations(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        bible = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "niv.spb"),
                BibleTranslationSettings(fileName = "esv.spb"),
            ),
        ),
    )

    private fun SkikoComposeUiTest.clickTab(pane: CustomizePane) {
        onNodeWithTag(railTag(pane.name)).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.displayMode(label: String) {
        onNode(hasTextExactly(label) and hasClickAction() and !isToggleable()).performClick()
        waitForIdle()
    }

    // ── Content on this output ──────────────────────────────────────────────────────────────────

    @Test
    fun `the content switches are folded away until asked for`() {
        profilesTab(profileDocument()) { _ ->
            onNodeWithTag(PROFILE_CONTENT_LIST_TAG).assertDoesNotExist()
            onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
            waitForIdle()
            onNodeWithTag(PROFILE_CONTENT_LIST_TAG).assertExists()
        }
    }

    @Test
    fun `Hide all switches every kind of content off, scripture and songs included`() {
        profilesTab(profileDocument()) { get ->
            onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
            waitForIdle()
            onNodeWithText("Hide all").performClick()
            waitForIdle()

            val p = get().profile()
            assertFalse(p.showBible || p.showSongs || p.showMedia || p.showSubtitles || p.showQA || p.showSTT)
            assertFalse(p.showPictures || p.showCanvas || p.showFullscreenBackground)
        }
    }

    @Test
    fun `Hide all keeps the preview column in place`() {
        profilesTab(profileDocument()) { _ ->
            onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
            waitForIdle()
            onNodeWithText("Hide all").performClick()
            waitForIdle()

            onNodeWithText("PREVIEW").assertExists()
            onNodeWithText("Short").assertDoesNotExist()
        }
    }

    @Test
    fun `Show all switches them back on`() {
        val off = OutputProfile(bibleMode = Constants.SONG_LANG_OFF, showMedia = false, showQA = false)
        profilesTab(profileDocument(profile = off)) { get ->
            onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
            waitForIdle()
            onNodeWithText("Show all").performClick()
            waitForIdle()

            val p = get().profile()
            assertTrue(p.showBible && p.showMedia && p.showQA)
        }
    }

    @Test
    fun `switching songs off switches the look-ahead off with them`() {
        profilesTab(profileDocument(profile = OutputProfile(songLookAhead = true))) { get ->
            onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
            waitForIdle()
            onNode(isToggleable() and hasTextExactly("Songs")).performClick()
            waitForIdle()

            assertFalse(get().profile().showSongs)
            assertFalse(get().profile().songLookAhead, "there is nothing to look ahead in")
        }
    }

    @Test
    fun `the summary names what is hidden`() {
        profilesTab(profileDocument(profile = OutputProfile(showCanvas = false))) { _ ->
            // Song LA ships off, so it is named too; what matters is that Canvas is.
            onNode(hasText("hidden:", substring = true) and hasText("Canvas", substring = true)).assertExists()
        }
    }

    // ── Bible source order ──────────────────────────────────────────────────────────────────────

    @Test
    fun `moving a translation down stores the profile's own order`() {
        profilesTab(twoTranslations()) { get ->
            onNodeWithTag(BIBLE_SOURCE_TRIGGER_TAG).performClick()
            waitForIdle()
            onNode(hasContentDescription("Move down") and hasAnyAncestor(hasTestTag(bibleOrderRowTag(0))))
                .performClick()
            waitForIdle()

            assertEquals(listOf(1, 0, 2), get().profile().bibleTranslations)
        }
    }

    @Test
    fun `the first translation cannot move further up`() {
        profilesTab(twoTranslations()) { get ->
            onNodeWithTag(BIBLE_SOURCE_TRIGGER_TAG).performClick()
            waitForIdle()
            onNode(hasContentDescription("Move up") and hasAnyAncestor(hasTestTag(bibleOrderRowTag(0))))
                .performClick()
            waitForIdle()

            assertEquals(emptyList(), get().profile().bibleTranslations, "still the untouched whole stack")
        }
    }

    @Test
    fun `with no Bibles loaded the source menu says where to add them`() {
        profilesTab(profileDocument()) { _ ->
            onNodeWithTag(BIBLE_SOURCE_TRIGGER_TAG).performClick()
            waitForIdle()
            onNodeWithText("No Bibles loaded", substring = true).assertExists()
        }
    }

    // ── Preview shape ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a custom ratio is stored as a size 1080 tall`() {
        profilesTab(profileDocument()) { get ->
            onNodeWithTag(previewShapeTag("CUSTOM")).performClick()
            waitForIdle()
            retypeNumberField(showing = 16, to = 5)
            retypeNumberField(showing = 9, to = 4)

            assertEquals(1350, get().profile().previewWidth)
            assertEquals(1080, get().profile().previewHeight)
        }
    }

    @Test
    fun `a custom resolution is stored exactly as typed`() {
        profilesTab(profileDocument()) { get ->
            onNodeWithTag(previewShapeTag("CUSTOM")).performClick()
            waitForIdle()
            onNodeWithText("Resolution").performClick()
            waitForIdle()
            retypeNumberField(showing = 1920, to = 1280)
            retypeNumberField(showing = 1080, to = 1024)

            assertEquals(1280, get().profile().previewWidth)
            assertEquals(1024, get().profile().previewHeight)
        }
    }

    @Test
    fun `a resolution outside the allowed range is not stored`() {
        profilesTab(profileDocument()) { get ->
            onNodeWithTag(previewShapeTag("CUSTOM")).performClick()
            waitForIdle()
            onNodeWithText("Resolution").performClick()
            waitForIdle()
            retypeNumberField(showing = 1920, to = 4)

            assertEquals(1920, get().profile().previewWidth, "4 pixels wide is below the floor")
        }
    }

    @Test
    fun `a stored custom size opens on Custom, in resolution mode`() {
        profilesTab(profileDocument(profile = OutputProfile(previewWidth = 1280, previewHeight = 1024))) { _ ->
            // Not a preset, and not 1080 tall, so it was typed as a size: the fields show that size.
            onAllNodes(hasSetTextAction() and hasText("1280"))[0].assertExists()
            onAllNodes(hasSetTextAction() and hasText("1024"))[0].assertExists()
        }
    }

    @Test
    fun `picking a preset after Custom stores it and folds the fields away`() {
        profilesTab(profileDocument()) { get ->
            onNodeWithTag(previewShapeTag("CUSTOM")).performClick()
            waitForIdle()
            onNodeWithTag(previewShapeTag(PreviewShapePreset.STANDARD.name)).performClick()
            waitForIdle()

            assertEquals(1440 to 1080, get().profile().let { it.previewWidth to it.previewHeight })
            onNodeWithText("Ratio").assertDoesNotExist()
        }
    }

    @Test
    fun `a stored size too odd to be a small ratio opens its ratio fields on 16 to 9`() {
        profilesTab(profileDocument(profile = OutputProfile(previewWidth = 1921, previewHeight = 1080))) { _ ->
            // 1080 tall, so it opens in ratio mode -- and 1921:1080 is no ratio anyone typed.
            onAllNodes(hasSetTextAction() and hasText("16"))[0].assertExists()
            onAllNodes(hasSetTextAction() and hasText("9"))[0].assertExists()
        }
    }

    // ── Scale ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the Scale row sets pictures and media for this profile`() {
        profilesTab(profileDocument()) { get ->
            onAllNodesWithText("Fill")[0].performClick()
            waitForIdle()
            onAllNodesWithText("Stretch")[1].performClick()
            waitForIdle()

            assertEquals(OutputScaleMode.FILL, get().profile().pictureScaleMode)
            assertEquals(OutputScaleMode.STRETCH, get().profile().mediaScaleMode)
            assertEquals(OutputScaleMode.FIT, get().pictureSettings.scaleMode, "the document is not touched")
        }
    }

    @Test
    fun `a stage monitor has no Scale row`() {
        profilesTab(profileDocument(Constants.DISPLAY_MODE_STAGE_MONITOR)) { _ ->
            onNodeWithText("Scale").assertDoesNotExist()
        }
    }

    // ── Style tabs ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `switching to a stage monitor opens its own editor, whatever tab was open`() {
        profilesTab(profileDocument()) { _ ->
            // Q&A is one of the stage monitor's tabs too, so a pick carried across would stay on it.
            clickTab(CustomizePane.QA)
            displayMode("Stage Monitor")

            onNodeWithText("Screen Layout").assertExists()
        }
    }

    @Test
    fun `switching Q&A off under Content takes its Style tab away`() {
        profilesTab(profileDocument()) { _ ->
            onNodeWithTag(railTag(CustomizePane.QA.name)).assertExists()
            onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
            waitForIdle()
            onNode(isToggleable() and hasTextExactly("Q&A")).performClick()
            waitForIdle()

            onNodeWithTag(railTag(CustomizePane.QA.name)).assertDoesNotExist()
        }
    }

    @Test
    fun `a caption setting is written to the profile and not to the document`() {
        profilesTab(profileDocument()) { get ->
            clickTab(CustomizePane.CAPTIONS)
            toggleCheckbox("Word Highlighting")

            assertTrue(get().profile().sttSettings.showWordHighlighting)
            assertEquals(STTSettings().showWordHighlighting, get().sttSettings.showWordHighlighting)
        }
    }

    @Test
    fun `each whole-form tab draws a picture beside it`() {
        profilesTab(profileDocument()) { _ ->
            val formTabs = listOf(
                CustomizePane.CAPTIONS,
                CustomizePane.SUBTITLES,
                CustomizePane.QA,
                CustomizePane.DICTIONARY,
            )
            for (pane in formTabs) {
                clickTab(pane)
                onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists("${pane.name} has its preview")
            }
        }
    }

    // ── Every translation at once ───────────────────────────────────────────────────────────────

    @Test
    fun `the Bible tab opens on All, and an edit there reaches every translation`() {
        profilesTab(twoTranslations()) { get ->
            onNodeWithTag(translationChipTag(ALL_TRANSLATIONS)).assertExists()
            retypeNumberField(showing = 70, to = 90)

            assertEquals(listOf(90, 90, 90), get().bible().translationList().map { it.textFontSize })
        }
    }

    @Test
    fun `picking one translation styles that one alone`() {
        profilesTab(twoTranslations()) { get ->
            onNodeWithTag(translationChipTag(1)).performClick()
            waitForIdle()
            retypeNumberField(showing = 70, to = 90)

            assertEquals(listOf(70, 90, 70), get().bible().translationList().map { it.textFontSize })
        }
    }

    // ── The list ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Identify shows each display's number`() {
        var identified = 0
        profilesTab(profileDocument(), onIdentify = { identified++ }) { _ ->
            onNodeWithText("Identify").performClick()
            waitForIdle()
            assertEquals(1, identified)
        }
    }

    @Test
    fun `hovering a row that is not open shows its actions`() {
        val doc = profileDocument().let { d ->
            d.copy(
                projectionSettings = d.projectionSettings.copy(
                    outputProfiles = d.projectionSettings.outputProfiles + OutputProfile(id = "foyer", name = "Foyer"),
                ),
            )
        }
        profilesTab(doc) { _ ->
            onAllNodes(hasContentDescription("Duplicate")).assertCountEquals(1)
            onNodeWithText("Foyer").performMouseInput { moveTo(center) }
            waitForIdle()
            onAllNodes(hasContentDescription("Duplicate")).assertCountEquals(2)
        }
    }

    @Test
    fun `a profile used by a Browser Source and an NDI output names both`() {
        val doc = profileDocument().let { d ->
            d.copy(
                projectionSettings = d.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment(activeProfileId = PROFILE_ID)),
                    ndiOutputs = listOf(ScreenAssignment(activeProfileId = PROFILE_ID)),
                ),
            )
        }
        profilesTab(doc) { _ ->
            onAllNodesWithText("Used by Browser Source 1, NDI Output 1")[0].assertExists()
        }
    }

    @Test
    fun `a profile no output uses says so`() {
        profilesTab(profileDocument()) { _ ->
            onAllNodesWithText("Not assigned")[0].assertExists()
        }
    }
}
