package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "N of M enabled" summary on a projection output's row.
 *
 * The two halves have to agree about the same set of columns: a toggle the output is never offered
 * must be absent from both, or the row reads "4 of 7" about a dialog showing five switches.
 */
class ContentOutputsCountTest {

    private fun col(
        label: String,
        get: (ScreenAssignment) -> Boolean,
        visible: (ScreenAssignment) -> Boolean = { true },
    ) = ContentCol(label = label, getter = get, setter = { a, _ -> a }, visible = visible)

    private val pictures = col("Pictures", { it.showPictures })
    private val media = col("Media", { it.showMedia })
    private val qa = col("Q&A", { it.showQA })
    private val backgrounds = col("Backgrounds", { it.showAnnouncements })

    private val content = listOf(pictures, media, qa)
    private val background = listOf(backgrounds)

    private val allOn = ScreenAssignment()

    // ── the denominator ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the total is Bible and Songs plus every column offered`() {
        assertEquals(2 + 3 + 1, contentOutputsTotalCount(allOn, content, background))
    }

    @Test
    fun `Bible and Songs always count toward the total, even when both are off`() {
        val off = allOn.copy(bibleMode = Constants.SONG_LANG_OFF, songMode = Constants.SONG_LANG_OFF)
        assertEquals(2 + 3 + 1, contentOutputsTotalCount(off, content, background))
    }

    @Test
    fun `a column this output is not offered leaves the total`() {
        val hidden = listOf(pictures, col("Media", { it.showMedia }, visible = { false }), qa)
        assertEquals(2 + 2 + 1, contentOutputsTotalCount(allOn, hidden, background))
    }

    @Test
    fun `an output offered no columns at all still counts its two language cells`() {
        assertEquals(2, contentOutputsTotalCount(allOn, emptyList(), emptyList()))
    }

    // ── the numerator ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `everything on counts everything`() {
        assertEquals(
            contentOutputsTotalCount(allOn, content, background),
            contentOutputsEnabledCount(allOn, content, background),
        )
    }

    @Test
    fun `Bible off drops one from the count`() {
        val a = allOn.copy(bibleMode = Constants.SONG_LANG_OFF)
        assertEquals(contentOutputsEnabledCount(allOn, content, background) - 1,
            contentOutputsEnabledCount(a, content, background))
    }

    @Test
    fun `Songs off drops one from the count`() {
        val a = allOn.copy(songMode = Constants.SONG_LANG_OFF)
        assertEquals(contentOutputsEnabledCount(allOn, content, background) - 1,
            contentOutputsEnabledCount(a, content, background))
    }

    @Test
    fun `any song language other than off counts as one`() {
        listOf(Constants.SONG_LANG_PRIMARY, Constants.SONG_LANG_BOTH).forEach { mode ->
            assertEquals(6, contentOutputsEnabledCount(allOn.copy(songMode = mode), content, background), mode)
        }
    }

    @Test
    fun `a toggle switched off is not counted`() {
        val a = allOn.copy(showMedia = false, showQA = false)
        assertEquals(4, contentOutputsEnabledCount(a, content, background))
    }

    @Test
    fun `an output with nothing on counts nothing`() {
        val a = allOn.copy(
            bibleMode = Constants.SONG_LANG_OFF,
            songMode = Constants.SONG_LANG_OFF,
            showPictures = false,
            showMedia = false,
            showQA = false,
            showAnnouncements = false,
        )
        assertEquals(0, contentOutputsEnabledCount(a, content, background))
    }

    @Test
    fun `a column left out of the dialog is left out of the count, however it is set`() {
        val hidden = listOf(pictures, col("Media", { it.showMedia }, visible = { false }), qa)
        val on = contentOutputsEnabledCount(allOn, hidden, background)
        val off = contentOutputsEnabledCount(allOn.copy(showMedia = false), hidden, background)
        assertEquals(on, off, "a hidden column's own value must not move the numerator")
        assertEquals(5, on)
    }

    @Test
    fun `the count never exceeds the total`() {
        listOf(
            allOn,
            allOn.copy(showMedia = false),
            allOn.copy(bibleMode = Constants.SONG_LANG_OFF, songMode = Constants.SONG_LANG_OFF),
        ).forEach { a ->
            assertTrue(
                contentOutputsEnabledCount(a, content, background) <=
                    contentOutputsTotalCount(a, content, background),
            )
        }
    }

    // ── offeredTo ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `offeredTo keeps the columns this output can obey, in order`() {
        val cols = listOf(pictures, col("Media", { it.showMedia }, visible = { false }), qa)
        assertEquals(listOf("Pictures", "Q&A"), cols.offeredTo(allOn).map { it.label })
    }

    @Test
    fun `offeredTo can filter to nothing`() {
        val cols = listOf(col("Pictures", { it.showPictures }, visible = { false }))
        assertEquals(emptyList(), cols.offeredTo(allOn))
    }

    @Test
    fun `visibility is decided per output, not once for the column`() {
        val lowerThirdOnly = col("Lower third", { it.showMedia }, visible = {
            it.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
        })
        val cols = listOf(lowerThirdOnly)
        assertEquals(0, cols.offeredTo(allOn).size)
        assertEquals(
            1,
            cols.offeredTo(allOn.copy(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)).size,
        )
    }
}
