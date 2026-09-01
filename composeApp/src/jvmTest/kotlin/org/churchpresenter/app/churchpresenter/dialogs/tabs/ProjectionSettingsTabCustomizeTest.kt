@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-output Customize button on each assignment row, and the dialog it opens.
 *
 * Most of these drive a row into a display mode first, because that decides which categories the
 * dialog offers and which of the two style profiles its controls edit. Settings assertions go
 * through `get()`; nothing here trusts a control's own text right after a click.
 *
 * **A category that is following the global settings swallows pointer input** — see
 * `DimmedWhenFollowing` — so anything that clicks a control turns the override switch on first,
 * which is what an operator does too. Typing is not affected: `performTextReplacement` goes through
 * the semantics action rather than through hit testing.
 */
class ProjectionSettingsTabCustomizeTest {

    private fun rows(vararg modes: String): AppSettings = AppSettings(
        projectionSettings = ProjectionSettings(
            screenAssignments = modes.map { ScreenAssignment(displayMode = it) },
        ),
    )

    /** Margins with four distinct values, so a test can address one field by the number in it. */
    private fun withMargins(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(marginTop = 11, marginBottom = 22, marginLeft = 33, marginRight = 44),
        projectionSettings = ProjectionSettings(screenAssignments = listOf(ScreenAssignment(displayMode = mode))),
    )

    /** Two translations in the stack, which is what makes the translation chips appear. */
    private fun twoTranslations(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 70),
                BibleTranslationSettings(fileName = "niv.spb", textFontSize = 70),
            ),
        ),
        projectionSettings = ProjectionSettings(screenAssignments = listOf(ScreenAssignment(displayMode = mode))),
    )

    private fun ComposeUiTest.openCustomize(row: Int) {
        gridButton(Grid.customize(row)).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.selectPane(pane: CustomizePane) {
        onNodeWithTag(railTag(pane.name)).performClick()
        waitForIdle()
    }

    /** Turns this category's override on, so its controls accept a click. */
    private fun ComposeUiTest.enableOverride() {
        onNodeWithTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG).performClick()
        waitForIdle()
    }

    // ── The button ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every assignment row offers the overwrite button`() = projectionTab { _ ->
        for (row in 0..1) {
            gridButton(Grid.customize(row)).performScrollTo().assertTextEquals("Overwrite Styles")
        }
    }

    @Test
    fun `the button counts the categories this output has overwritten`() {
        val customized = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(bibleOverride = BibleSettings(), songOverride = SongSettings()),
                ),
            ),
        )
        projectionTab(customized) { _ ->
            gridButton(Grid.customize(0)).performScrollTo().assertTextEquals("Overwritten · 2")
        }
    }

    @Test
    fun `an untouched output follows the global settings`() = projectionTab { get ->
        openCustomize(row = 0)
        onNodeWithTag(CUSTOMIZE_STATUS_TAG).assertTextEquals("0 of 4 customized")
        assertFalse(get().projectionSettings.screenAssignments[0].isCustomized)
    }

    // ── Which categories each display mode offers ───────────────────────────────────────────────

    @Test
    fun `a stage monitor row offers its zones and the dictionary, not Bible or Songs`() {
        projectionTab(rows(Constants.DISPLAY_MODE_STAGE_MONITOR, Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            onNodeWithTag(railTag(CustomizePane.STAGE_MONITOR.name)).assertExists()
            onNodeWithTag(railTag(CustomizePane.DICTIONARY.name)).assertExists()
            onNodeWithTag(railTag(CustomizePane.BIBLE.name)).assertDoesNotExist()
            onNodeWithTag(railTag(CustomizePane.SONGS.name)).assertDoesNotExist()
        }
    }

    @Test
    fun `a fullscreen row offers Bible, Songs, Dictionary and Background`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            for (pane in listOf(
                CustomizePane.BIBLE,
                CustomizePane.SONGS,
                CustomizePane.DICTIONARY,
                CustomizePane.BACKGROUND,
            )) {
                onNodeWithTag(railTag(pane.name)).assertExists()
            }
            onNodeWithTag(railTag(CustomizePane.STAGE_MONITOR.name)).assertDoesNotExist()
        }
    }

    // ── The element chips ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the Bible pane chips its two elements and previews the output`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            onNodeWithTag(elementChipTag(CustomizeElement.BIBLE_TEXT.name)).assertExists()
            onNodeWithTag(elementChipTag(CustomizeElement.BIBLE_REFERENCE.name)).assertExists()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `the stage monitor pane takes the whole width, with no chips and no preview`() {
        projectionTab(rows(Constants.DISPLAY_MODE_STAGE_MONITOR)) { _ ->
            openCustomize(row = 0)
            selectPane(CustomizePane.STAGE_MONITOR)
            onNodeWithTag(CUSTOMIZE_ELEMENT_ROW_TAG).assertDoesNotExist()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `the Songs pane offers no chord colour on a full screen`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            selectPane(CustomizePane.SONGS)
            // A chart is drawn only by the stage monitor, so its colour would write nowhere here.
            onNodeWithText("Chords").assertDoesNotExist()
        }
    }

    @Test
    fun `the Songs pane offers no chord colour on a lower third either`() {
        projectionTab(rows(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { _ ->
            openCustomize(row = 0)
            selectPane(CustomizePane.SONGS)
            onNodeWithText("Chords").assertDoesNotExist()
        }
    }

    // ── The translation selector ────────────────────────────────────────────────────────────────

    @Test
    fun `one translation in the stack needs no selector`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            onNodeWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `editing the second translation leaves the first alone`() {
        projectionTab(twoTranslations()) { get ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            onNodeWithTag(translationChipTag(1)).performClick()
            waitForIdle()
            onNode(hasSetTextAction() and hasText("70")).performScrollTo().performTextReplacement("41")
            waitForIdle()

            val stack = assertNotNull(get().projectionSettings.screenAssignments[0].bibleOverride)
                .translationList()
            assertEquals(41, stack[1].textFontSize, "the selected translation must take the edit")
            assertEquals(70, stack[0].textFontSize, "and the one beside it must be untouched")
        }
    }

    // ── The category strip under the preview ────────────────────────────────────────────────────

    @Test
    fun `a margin typed on the strip stores an override on that row alone`() {
        val two = withMargins().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    screenAssignments = it.projectionSettings.screenAssignments +
                        ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
                ),
            )
        }
        projectionTab(two) { get ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            onNode(hasSetTextAction() and hasText("11")).performTextReplacement("31")
            waitForIdle()

            val edited = get().projectionSettings.screenAssignments[0]
            assertTrue(edited.isCustomized, "an edit must create this output's override")
            assertEquals(31, assertNotNull(edited.bibleOverride).marginTop)
            assertFalse(
                get().projectionSettings.screenAssignments[1].isCustomized,
                "the other row must still be following the global settings",
            )
            assertEquals(11, get().bibleSettings.marginTop, "and the global document must be untouched")
        }
    }

    @Test
    fun `a full screen writes the full-screen bilingual layout`() {
        projectionTab(twoTranslations()) { get ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            enableOverride()
            onNodeWithText("Left / Right").performClick()
            waitForIdle()

            val bible = assertNotNull(get().projectionSettings.screenAssignments[0].bibleOverride)
            assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, bible.bilingualLayout)
            assertEquals(
                Constants.BILINGUAL_SIDE_BY_SIDE,
                bible.bilingualLayoutLowerThird,
                "the band keeps its own value, which happens to be the same by default",
            )
        }
    }

    @Test
    fun `a lower third writes the band's bilingual layout, not the full screen's`() {
        projectionTab(twoTranslations(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            enableOverride()
            onNodeWithText("Top / Bottom").performClick()
            waitForIdle()

            val bible = assertNotNull(get().projectionSettings.screenAssignments[0].bibleOverride)
            assertEquals(Constants.BILINGUAL_TOP_BOTTOM, bible.bilingualLayoutLowerThird)
            assertEquals(
                Constants.BILINGUAL_TOP_BOTTOM,
                bible.bilingualLayout,
                "the full screen keeps its own value, which happens to be the same by default",
            )
        }
    }

    @Test
    fun `a single-language output is offered no bilingual layout`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            selectPane(CustomizePane.BIBLE)
            onNodeWithText("Left / Right").assertDoesNotExist()
        }
    }

    // ── Reset ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Reset to global clears the selected category's override`() {
        val customized = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(
                        bibleOverride = BibleSettings(marginTop = 12),
                        songOverride = SongSettings(marginTop = 12),
                    ),
                ),
            ),
        )
        projectionTab(customized) { get ->
            openCustomize(row = 0)
            onNodeWithTag(CUSTOMIZE_STATUS_TAG).assertTextEquals("2 of 4 customized")

            selectPane(CustomizePane.BIBLE)
            onNodeWithText("Reset to global").performClick()
            waitForIdle()

            val reset = get().projectionSettings.screenAssignments[0]
            assertNull(reset.bibleOverride, "the selected category is cleared")
            assertNotNull(reset.songOverride, "and the others are left alone")
        }
    }
}
