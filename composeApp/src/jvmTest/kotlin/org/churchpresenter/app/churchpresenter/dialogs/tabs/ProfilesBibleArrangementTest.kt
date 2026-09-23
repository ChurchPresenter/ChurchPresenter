@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How parallel translations are arranged, on a full screen.
 *
 * The full-screen path read the seven-option setting as a *boolean* -- side by side, or not -- so
 * every named grid an operator picked drew as a plain stack. 2x2, 4x1 and 1x4 were all
 * byte-identical to Top/Bottom, which is the state this suite exists to keep the presenter out of.
 * The band had read the grid since it gained 3- and 4-translation support; only the full screen
 * had not.
 *
 * Asserted as the *shape* of the arrangement -- how many distinct rows and columns the translations
 * occupy -- rather than on pixel positions, which move with the font, the margins and the auto-fit.
 */
class ProfilesBibleArrangementTest {

    private val sampleVerse = "For God so loved the world"

    private fun doc(
        layout: String,
        translations: Int = 4,
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
    ) = profileDocument(
        mode = mode,
        profile = OutputProfile(bibleMode = Constants.SONG_LANG_BOTH),
        bible = BibleSettings(
            bilingualLayout = layout,
            bilingualLayoutLowerThird = layout,
            translations = List(translations) { BibleTranslationSettings(fileName = "t$it.spb") },
        ),
    )

    /** The distinct left edges and top edges the translations occupy: the grid's columns and rows. */
    private fun SkikoComposeUiTest.shape(): Pair<Int, Int> {
        val nodes = onAllNodesWithText(sampleVerse, substring = true).fetchSemanticsNodes()
        val cols = nodes.map { it.positionInRoot.x.toInt() }.distinct().size
        val rows = nodes.map { it.positionInRoot.y.toInt() }.distinct().size
        return cols to rows
    }

    private fun assertShape(
        layout: String,
        cols: Int,
        rows: Int,
        translations: Int = 4,
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
    ) {
        profilesTab(doc(layout, translations, mode)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertEquals(
                cols to rows,
                shape(),
                "$layout on $mode must be $cols column(s) by $rows row(s)",
            )
        }
    }

    // ── The two original arrangements, which must not have moved ────────────────────────────────

    @Test
    fun `side by side puts every translation in one row`() =
        assertShape(Constants.BILINGUAL_SIDE_BY_SIDE, cols = 4, rows = 1)

    @Test
    fun `top and bottom puts every translation in one column`() =
        assertShape(Constants.BILINGUAL_TOP_BOTTOM, cols = 1, rows = 4)

    // ── The grids, which used to do nothing here ────────────────────────────────────────────────

    @Test
    fun `2x2 draws two columns of two`() =
        assertShape(Constants.BILINGUAL_GRID_2X2, cols = 2, rows = 2)

    @Test
    fun `4x1 draws four rows of one`() =
        assertShape(Constants.BILINGUAL_GRID_4X1, cols = 1, rows = 4)

    @Test
    fun `1x4 draws one row of four`() =
        assertShape(Constants.BILINGUAL_GRID_1X4, cols = 4, rows = 1)

    @Test
    fun `3x1 draws three rows of one`() =
        assertShape(Constants.BILINGUAL_GRID_3X1, cols = 1, rows = 3, translations = 3)

    @Test
    fun `1x3 draws one row of three`() =
        assertShape(Constants.BILINGUAL_GRID_1X3, cols = 3, rows = 1, translations = 3)

    // ── The grids are genuinely different from each other ───────────────────────────────────────

    @Test
    fun `the grids no longer collapse onto the stacked layout`() {
        val shapes = listOf(
            Constants.BILINGUAL_TOP_BOTTOM,
            Constants.BILINGUAL_GRID_2X2,
            Constants.BILINGUAL_GRID_1X4,
        ).map { layout ->
            var shape = 0 to 0
            profilesTab(doc(layout)) { _ ->
                openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
                shape = shape()
            }
            shape
        }
        assertEquals(shapes.size, shapes.distinct().size, "each arrangement must draw its own shape")
    }

    // ── A grid that does not divide evenly ──────────────────────────────────────────────────────

    @Test
    fun `three translations in a 2x2 fill the first row and start a second`() {
        // Two columns, two rows: two on top, one below. The short row keeps the column width the
        // row above it set rather than stretching its one cell across the frame.
        assertShape(Constants.BILINGUAL_GRID_2X2, cols = 2, rows = 2, translations = 3)
    }

    // ── The band, which used to discard whatever the arrangement had no cell for ────────────────

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    /**
     * A band laid its translations out in `rows x cols` and dropped the rest, so four translations
     * under Side-by-side drew two and the other two vanished with nothing saying so. It reflows
     * like the full screen now: the arrangement decides the columns, and the rows follow from how
     * many translations there are.
     */
    @Test
    fun `a band draws every translation under each arrangement`() {
        assertShape(Constants.BILINGUAL_SIDE_BY_SIDE, cols = 4, rows = 1, mode = band)
        assertShape(Constants.BILINGUAL_TOP_BOTTOM, cols = 1, rows = 4, mode = band)
        assertShape(Constants.BILINGUAL_GRID_2X2, cols = 2, rows = 2, mode = band)
        assertShape(Constants.BILINGUAL_GRID_1X4, cols = 4, rows = 1, mode = band)
        assertShape(Constants.BILINGUAL_GRID_4X1, cols = 1, rows = 4, mode = band)
    }

    /** Two translations keep the band's original pair of layouts exactly. */
    @Test
    fun `a band with two translations is unchanged`() {
        assertShape(Constants.BILINGUAL_SIDE_BY_SIDE, cols = 2, rows = 1, translations = 2, mode = band)
        assertShape(Constants.BILINGUAL_TOP_BOTTOM, cols = 1, rows = 2, translations = 2, mode = band)
    }
}
