@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
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
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Content Region reaching the two **virtual** outputs — Browser Source and NDI.
 *
 * Both render through [OffscreenOutputContent], which is one of the four things in the app that
 * draw `BiblePresenter`/`SongPresenter`. `Modifier.contentRegion` was on two of the four, and this
 * was not one of them: an operator narrowing an output to free up half the screen for a signer saw
 * it work on the projector and do *nothing whatever* on the OBS feed.
 *
 * The companion of [org.churchpresenter.app.churchpresenter.composables.LivePreviewContentRegionTest],
 * and separate from it for the reason both exist: the modifier being correct says nothing about
 * which call sites remembered to apply it, so the check has to be once per render path.
 */
class OffscreenContentRegionTest {

    private val verseText = "For God so loved the world, that he gave his only begotten Son."
    private val lyric = "Amazing grace how sweet the sound"

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
            ),
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

    /** The width of the widest node carrying [text] on a virtual output, or 0 when it is not drawn. */
    private fun drawnWidth(settings: AppSettings, mode: Presenting, text: String): Int {
        var width = 0
        val manager = PresenterManager().apply {
            setPresentingMode(mode)
            setDisplayedVerses(listOf(verse()))
            setDisplayedLyricSection(
                LyricSection(type = "verse", title = "Amazing Grace", lines = listOf(lyric)),
            )
        }
        val context = OffscreenOutputContext(
            presenterManager = manager,
            appSettingsState = mutableStateOf(settings),
            screenAssignmentState = mutableStateOf(ScreenAssignment(activeProfileId = PROFILE)),
            effectiveModeState = mutableStateOf(mode),
        )
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(OUTPUT_W.dp, OUTPUT_H.dp)) { OffscreenOutputContent(context) }
                }
            }
            waitForIdle()
            width = onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes()
                .maxOfOrNull { it.size.width } ?: 0
        }
        return width
    }

    @Test
    fun `a narrowed Bible is drawn narrower on a virtual output`() {
        val stack = listOf(BibleTranslationSettings(fileName = KJV))
        val full = drawnWidth(
            doc(bible = BibleSettings(primaryBible = KJV).withTranslations(stack)),
            Presenting.BIBLE,
            verseText,
        )
        val narrow = drawnWidth(
            doc(
                bible = BibleSettings(
                    primaryBible = KJV,
                    contentRegion = ContentRegion(widthPercent = 40, xOffsetPercent = -30),
                ).withTranslations(stack),
            ),
            Presenting.BIBLE,
            verseText,
        )
        assertTrue(full > 0, "the verse has to be on the output at all before its width means anything")
        assertTrue(narrow < full, "a verse confined to 40% must draw narrower here too: $narrow vs $full")
    }

    @Test
    fun `a narrowed song is drawn narrower on a virtual output`() {
        val full = drawnWidth(doc(), Presenting.LYRICS, lyric)
        val narrow = drawnWidth(
            doc(
                song = SongSettings(
                    layoutExtras = SongLayoutExtras(
                        contentRegion = ContentRegion(widthPercent = 40, xOffsetPercent = -30),
                    ),
                ),
            ),
            Presenting.LYRICS,
            lyric,
        )
        assertTrue(full > 0, "the lyric has to be on the output at all before its width means anything")
        assertTrue(narrow < full, "lyrics confined to 40% must draw narrower here too: $narrow vs $full")
    }

    private companion object {
        const val KJV = "kjv.spb"
        const val PROFILE = "under-test"
        const val OUTPUT_W = 1280
        const val OUTPUT_H = 720
    }
}
