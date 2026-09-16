@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A screen's bilingual layout stays where it was put when something else on the same screen is
 * edited afterwards.
 *
 * Reported from the app for both categories: pick Top / Bottom, then change a style, and the row
 * snapped back to Left / Right. `BilingualOverrideRoundTripTest` in `:settings` shows the storage
 * cannot lose it, so these drive the real dialog instead -- the strip under the preview, then a
 * control in the column beside it, in that order.
 */
class ProjectionCustomizeBilingualTest {

    private fun songScreen() = AppSettings(
        songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                    songMode = Constants.SONG_LANG_BOTH,
                ),
            ),
        ),
    )

    private fun bibleScreen() = AppSettings(
        bibleSettings = BibleSettings(
            bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM,
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "rst.spb"),
            ),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                    bibleMode = Constants.SONG_LANG_BOTH,
                ),
            ),
        ),
    )

    private fun AppSettings.onScreen(): AppSettings = resolvedFor(projectionSettings.screenAssignments[0])

    @Test
    fun `the song layout survives a later style edit on the same screen`() {
        projectionTab(songScreen()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            chooseSegment("Top / Bottom")
            assertEquals(
                Constants.BILINGUAL_TOP_BOTTOM,
                get().onScreen().songSettings.bilingualLayout,
                "the layout the operator picked",
            )

            toggleCheckbox("Shadow")
            assertEquals(
                Constants.BILINGUAL_TOP_BOTTOM,
                get().onScreen().songSettings.bilingualLayout,
                "a style edit must not move the layout",
            )
        }
    }

    @Test
    fun `the bible layout survives a later style edit on the same screen`() {
        projectionTab(bibleScreen()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE)
            chooseSegment("Left / Right")
            assertEquals(
                Constants.BILINGUAL_SIDE_BY_SIDE,
                get().onScreen().bibleSettings.bilingualLayout,
                "the layout the operator picked",
            )

            toggleCheckbox("Shadow")
            assertEquals(
                Constants.BILINGUAL_SIDE_BY_SIDE,
                get().onScreen().bibleSettings.bilingualLayout,
                "a style edit must not move the layout",
            )
        }
    }
}
