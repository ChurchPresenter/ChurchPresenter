@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The preview is what the screen will show.
 *
 * Every setting that decides whether something is *drawn* -- the song number, the title, the
 * end-of-song marker, which languages and translations appear -- has to change the picture beside
 * the controls as well as the output. The one thing allowed to differ is the shape of the frame,
 * which the operator picks from the preview's own resolution dropdown.
 *
 * These drive each control through the pane and read the preview back, so a setting that stops
 * reaching the picture fails here rather than being discovered on a projector.
 */
class ProfilesPreviewFidelityTest {

    private val sampleNumber = "427"
    private val sampleTitle = "Amazing Grace"
    private val sampleVerse = "For God so loved the world"

    private fun doc(
        profile: OutputProfile = OutputProfile(),
        song: SongSettings = SongSettings(),
        bible: BibleSettings = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
        ),
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
    ) = profileDocument(mode = mode, profile = profile, song = song, bible = bible)

    private fun SkikoComposeUiTest.drawn(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    // ── The song number ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the song number appears in the preview when it is switched on`() {
        profilesTab(doc(song = SongSettings(showNumber = Constants.EVERY_PAGE))) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertTrue(drawn(sampleNumber) > 0, "the number must be on the slide")
        }
    }

    @Test
    fun `switching the song number off takes it out of the preview`() {
        profilesTab(doc(song = SongSettings(showNumber = Constants.EVERY_PAGE))) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            val before = drawn(sampleNumber)
            chooseSegment("None")

            assertTrue(drawn(sampleNumber) < before, "the number must leave the picture with the setting")
        }
    }

    // ── The song title ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `switching the song title off takes it out of the preview`() {
        profilesTab(doc(song = SongSettings(titleDisplay = Constants.EVERY_PAGE))) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            val before = drawn(sampleTitle)
            chooseSegment("None")

            assertTrue(drawn(sampleTitle) < before, "the title must leave the picture with the setting")
        }
    }

    // ── The content region ──────────────────────────────────────────────────────────────────────

    /**
     * The region the output confines its text to. `PresenterModeContent` applies it as a modifier
     * around the presenter; the preview has to do the same or an operator moving the block watches
     * nothing happen.
     */
    @Test
    fun `the content region is applied to the Bible preview`() {
        val narrow = doc(
            bible = BibleSettings(
                translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
                contentRegion = ContentRegion(widthPercent = 40, xOffsetPercent = -30),
            ),
        )
        val full = doc()
        var narrowWidth = 0
        var fullWidth = 0
        profilesTab(narrow) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            narrowWidth = onAllNodesWithText(sampleVerse, substring = true)
                .fetchSemanticsNodes().first().size.width
        }
        profilesTab(full) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            fullWidth = onAllNodesWithText(sampleVerse, substring = true)
                .fetchSemanticsNodes().first().size.width
        }
        assertTrue(
            narrowWidth < fullWidth,
            "a verse confined to 40% of the screen must be drawn narrower than one that is not " +
                "(got $narrowWidth vs $fullWidth)",
        )
    }

    @Test
    fun `the content region is applied to the Song preview`() {
        val narrow = doc(
            song = SongSettings(
                layoutExtras = org.churchpresenter.settings.SongLayoutExtras(
                    contentRegion = ContentRegion(widthPercent = 40, xOffsetPercent = -30),
                ),
            ),
        )
        var narrowWidth = 0
        var fullWidth = 0
        profilesTab(narrow) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            narrowWidth = onAllNodesWithText("How sweet the sound", substring = true)
                .fetchSemanticsNodes().first().size.width
        }
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            fullWidth = onAllNodesWithText("How sweet the sound", substring = true)
                .fetchSemanticsNodes().first().size.width
        }
        assertTrue(
            narrowWidth < fullWidth,
            "lyrics confined to 40% must be drawn narrower (got $narrowWidth vs $fullWidth)",
        )
    }

    // ── Which band shape this profile is ────────────────────────────────────────────────────────

    /**
     * A band stacks its parallel text when the output is portrait. That is a property of *this*
     * profile's own shape -- read off any other profile and a landscape band previews as a
     * portrait one purely because some unrelated output is portrait.
     */
    @Test
    fun `a landscape band previews as a landscape band even beside a portrait one`() {
        val portraitElsewhere = doc(
            profile = OutputProfile(previewWidth = 1920, previewHeight = 1080),
            mode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            bible = BibleSettings(
                translations = listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "niv.spb"),
                ),
            ),
        ).let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    outputProfiles = it.projectionSettings.outputProfiles +
                        OutputProfile(
                            id = "tower",
                            name = "Tower",
                            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                            previewWidth = 1080,
                            previewHeight = 1920,
                        ),
                ),
            )
        }
        profilesTab(portraitElsewhere) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            val verses = onAllNodesWithText(sampleVerse, substring = true).fetchSemanticsNodes()
            assertEquals(2, verses.size, "both translations are drawn")
            // Side by side, not stacked: two verses at the same height on a landscape band.
            assertEquals(
                verses[0].positionInRoot.y,
                verses[1].positionInRoot.y,
                "a landscape band sets its translations side by side",
            )
        }
    }

    /**
     * The mirror of the test above, and the half that was missing: a **portrait** band stacks its
     * translations instead of setting them side by side.
     *
     * Both halves are needed. One alone passes against a preview that has picked an orientation and
     * stuck to it -- landscape-only coverage cannot tell "reads the profile's shape" from "always
     * draws side by side", and portrait-only coverage cannot tell it from "always stacks".
     */
    @Test
    fun `a portrait band previews as a stacked band`() {
        profilesTab(twoTranslationBand(width = 1080, height = 1920)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            val verses = onAllNodesWithText(sampleVerse, substring = true).fetchSemanticsNodes()
            assertEquals(2, verses.size, "both translations are drawn")
            assertTrue(
                verses[0].positionInRoot.y != verses[1].positionInRoot.y,
                "a portrait band stacks its translations rather than setting them side by side",
            )
        }
    }

    /** And the landscape case read straight through, so the pair reads as one statement. */
    @Test
    fun `a landscape band previews as a side-by-side band`() {
        profilesTab(twoTranslationBand(width = 1920, height = 1080)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            val verses = onAllNodesWithText(sampleVerse, substring = true).fetchSemanticsNodes()
            assertEquals(2, verses.size, "both translations are drawn")
            assertEquals(
                verses[0].positionInRoot.y,
                verses[1].positionInRoot.y,
                "a landscape band sets its translations side by side",
            )
        }
    }

    /**
     * A portrait band reads *its own* shape, not a landscape sibling's.
     *
     * The guard beside the landscape one already in this suite, pointing the other way: an output
     * whose orientation is read off some other profile is wrong in both directions, and only a pair
     * of tests says so.
     */
    @Test
    fun `a portrait band stays stacked even beside a landscape one`() {
        val beside = twoTranslationBand(width = 1080, height = 1920).let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    outputProfiles = it.projectionSettings.outputProfiles +
                        OutputProfile(
                            id = "wall",
                            name = "Wall",
                            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                            previewWidth = 1920,
                            previewHeight = 1080,
                        ),
                ),
            )
        }
        profilesTab(beside) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            val verses = onAllNodesWithText(sampleVerse, substring = true).fetchSemanticsNodes()
            assertEquals(2, verses.size, "both translations are drawn")
            assertTrue(
                verses[0].positionInRoot.y != verses[1].positionInRoot.y,
                "the portrait band must keep stacking beside a landscape sibling",
            )
        }
    }

    /**
     * The content region narrows a **portrait** full screen too.
     *
     * The two content-region tests above are landscape. A region is a percentage of the width, and
     * a portrait screen has much less of it -- which is where a rounding or a min-width floor would
     * show up first, and where the operator's "it shrinks from all sides" report came from.
     */
    @Test
    fun `the content region is applied to a portrait Bible preview`() {
        val portrait = OutputProfile(previewWidth = 1080, previewHeight = 1920)
        val narrow = doc(
            profile = portrait,
            bible = BibleSettings(
                translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
                contentRegion = ContentRegion(widthPercent = 40, xOffsetPercent = -30),
            ),
        )
        var narrowWidth = 0
        var fullWidth = 0
        profilesTab(narrow) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            narrowWidth = onAllNodesWithText(sampleVerse, substring = true)
                .fetchSemanticsNodes().first().size.width
        }
        profilesTab(doc(profile = portrait)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            fullWidth = onAllNodesWithText(sampleVerse, substring = true)
                .fetchSemanticsNodes().first().size.width
        }
        assertTrue(
            narrowWidth < fullWidth,
            "a portrait verse confined to 40% must be drawn narrower (got $narrowWidth vs $fullWidth)",
        )
    }

    /** A two-translation band on a profile of the given shape, which is what decides its stacking. */
    private fun twoTranslationBand(width: Int, height: Int) = doc(
        profile = OutputProfile(previewWidth = width, previewHeight = height),
        mode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
        bible = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "niv.spb"),
            ),
        ),
    )
}
