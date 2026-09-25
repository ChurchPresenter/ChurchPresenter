@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.core.models.songs.LyricSection
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Content Region reaching the operator's own live preview.
 *
 * `Modifier.contentRegion` was applied on two of the four things that render `BiblePresenter` and
 * `SongPresenter`: the projector windows and the two settings previews. It was missing from this
 * panel and from `OffscreenOutputContent`, so narrowing an output changed the projector while the
 * preview the booth watches all service went on showing full-width text — and on a Browser Source
 * or NDI output it genuinely did nothing at all.
 *
 * That is the same class of bug already fixed once on this branch, where this panel forwarded
 * neither `showSubtitles` nor `mediaSettings`, and it is why the fix needs a test per render path
 * rather than one test for the modifier.
 *
 * Measured as a width, because a region is a width: the verse drawn inside a 40% region has to come
 * out narrower than the same verse drawn without one. Absolute pixel values are not asserted — the
 * panel scales its thumbnails, and the font metrics under them differ per platform.
 */
class LivePreviewContentRegionTest {

    private val verseText = "For God so loved the world, that he gave his only begotten Son."
    private val lyric = "Amazing grace how sweet the sound"

    /**
     * A document with one full-screen output, carrying [bible]/[song] on the profile as well as on
     * the document.
     *
     * Both copies matter: the panel renders each output through its **profile-resolved** settings,
     * so a region set only on the document would be a test that could pass while the panel read a
     * different record entirely.
     */
    private fun doc(bible: BibleSettings = BibleSettings(), song: SongSettings = SongSettings()) =
        AppSettings(
            bibleSettings = bible,
            songSettings = song,
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(
                    OutputProfile(
                        id = PROFILE,
                        displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                        bibleSettings = bible,
                        songSettings = song,
                    ),
                ),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = PROFILE)),
            ),
        )

    private fun bible(region: ContentRegion) = doc(
        bible = BibleSettings(primaryBible = KJV, contentRegion = region).withTranslations(
            listOf(BibleTranslationSettings(fileName = KJV)),
        ),
    )

    private fun song(region: ContentRegion) = doc(
        song = SongSettings(layoutExtras = SongLayoutExtras(contentRegion = region)),
    )

    private fun verse() = SelectedVerse(
        translationFileName = KJV,
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = verseText,
    )

    // `setDisplayed*` rather than `setSelected*`: the panel draws what is *on screen*, and the
    // selected/displayed pair is the staged-versus-live distinction. Setting only the selection
    // leaves the preview blank, which reads as "the region hid the text" and is nothing of the sort.
    private fun bibleManager() = PresenterManager().apply {
        setPresentingMode(Presenting.BIBLE)
        setDisplayedVerses(listOf(verse()))
    }

    private fun songManager() = PresenterManager().apply {
        setPresentingMode(Presenting.LYRICS)
        setDisplayedLyricSection(
            LyricSection(type = "verse", title = "Amazing Grace", lines = listOf(lyric)),
        )
    }

    /** The width of the widest node carrying [text] in the panel, or 0 when it is not drawn. */
    private fun drawnWidth(settings: AppSettings, manager: PresenterManager, text: String): Int {
        var width = 0
        runComposeUiTest {
            setContent {
                MaterialTheme { LivePreviewPanel(presenterManager = manager, appSettings = settings) }
            }
            waitForIdle()
            width = onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes()
                .maxOfOrNull { it.size.width } ?: 0
        }
        return width
    }

    @Test
    fun `a narrowed Bible is drawn narrower in the live preview`() {
        val full = drawnWidth(bible(ContentRegion()), bibleManager(), verseText)
        val narrow = drawnWidth(
            bible(ContentRegion(widthPercent = 40, xOffsetPercent = -30)),
            bibleManager(),
            verseText,
        )
        assertTrue(full > 0, "the verse has to be on the preview at all before its width means anything")
        assertTrue(narrow < full, "a verse confined to 40% must draw narrower here too: $narrow vs $full")
    }

    @Test
    fun `a narrowed song is drawn narrower in the live preview`() {
        val full = drawnWidth(song(ContentRegion()), songManager(), lyric)
        val narrow = drawnWidth(
            song(ContentRegion(widthPercent = 40, xOffsetPercent = -30)),
            songManager(),
            lyric,
        )
        assertTrue(full > 0, "the lyric has to be on the preview at all before its width means anything")
        assertTrue(narrow < full, "lyrics confined to 40% must draw narrower here too: $narrow vs $full")
    }


    private companion object {
        const val KJV = "kjv.spb"
        const val PROFILE = "under-test"
    }
}
