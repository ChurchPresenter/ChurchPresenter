package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.Alignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where an announcement, a question and a caption land on the screen.
 *
 * The same nine-position picker appears in three settings tabs, and each of the three presenters
 * carries its own copy of the string → [Alignment] mapping. Three copies of a table is three
 * chances for one of them to disagree, and the disagreement is not visible to whoever edits it:
 * the setting says "Bottom Right", the announcement obeys, and the caption underneath it quietly
 * sits somewhere else — on the output screen only, which nobody is looking at while configuring.
 *
 * So this pins each table against the picker's own nine values, and the three against each other.
 * All three are private, so they are reached by reflection on their file classes.
 */
class PresenterPositioningTest {

    private fun mapper(fileClass: String, method: String): (String) -> Alignment {
        val m = Class.forName("org.churchpresenter.app.churchpresenter.presenter.$fileClass")
            .getDeclaredMethod(method, String::class.java)
            .apply { isAccessible = true }
        return { position -> m.invoke(null, position) as Alignment }
    }

    private val announcements = mapper("AnnouncementsPresenterKt", "positionToAlignment")
    private val questions = mapper("QAPresenterKt", "positionToAlignment")
    private val captions = mapper("STTPresenterKt", "sttPositionToAlignment")

    private val allThree = mapOf(
        "announcements" to announcements,
        "questions" to questions,
        "captions" to captions,
    )

    /** The nine positions the settings picker offers, and where each has to put the content. */
    private val expected = mapOf(
        Constants.TOP_LEFT to Alignment.TopStart,
        Constants.TOP_CENTER to Alignment.TopCenter,
        Constants.TOP_RIGHT to Alignment.TopEnd,
        Constants.CENTER_LEFT to Alignment.CenterStart,
        Constants.CENTER to Alignment.Center,
        Constants.CENTER_RIGHT to Alignment.CenterEnd,
        Constants.BOTTOM_LEFT to Alignment.BottomStart,
        Constants.BOTTOM_CENTER to Alignment.BottomCenter,
        Constants.BOTTOM_RIGHT to Alignment.BottomEnd,
    )

    // ── Each presenter against the picker ───────────────────────────────────────

    @Test
    fun `every position the picker offers lands where it says`() {
        allThree.forEach { (name, map) ->
            expected.forEach { (position, alignment) ->
                assertEquals(alignment, map(position), "$name puts '$position' in the wrong place")
            }
        }
    }

    @Test
    fun `no two positions share a corner`() {
        allThree.forEach { (name, map) ->
            val placed = expected.keys.associateWith { map(it) }

            assertEquals(
                9,
                placed.values.toSet().size,
                "$name has two positions landing in the same place, so one of them cannot be chosen: $placed",
            )
        }
    }

    // ── The three against each other ────────────────────────────────────────────

    @Test
    fun `the three presenters agree on where every position is`() {
        expected.keys.forEach { position ->
            val placed = allThree.mapValues { (_, map) -> map(position) }

            assertEquals(
                1,
                placed.values.toSet().size,
                "'$position' means different things to different presenters: $placed",
            )
        }
    }

    // ── Settings that name nothing ──────────────────────────────────────────────

    @Test
    fun `an unrecognised position puts an announcement or a question in the middle`() {
        listOf("announcements" to announcements, "questions" to questions).forEach { (name, map) ->
            listOf("", "somewhere else", "top left").forEach { unknown ->
                assertEquals(
                    Alignment.Center,
                    map(unknown),
                    "$name would push '$unknown' off an edge instead of showing it",
                )
            }
        }
    }

    @Test
    fun `an unrecognised position puts a caption at the bottom`() {
        // Deliberately not the centre: a caption is read as a subtitle, and centred captions sit
        // over the face of whoever is speaking.
        listOf("", "somewhere else", "top left").forEach { unknown ->
            assertEquals(Alignment.BottomCenter, captions(unknown))
        }
    }

    @Test
    fun `captions also answer to the three older position names`() {
        // Settings written before the nine-way picker stored these; they must not fall through to
        // the default and silently move an existing church's captions.
        assertEquals(Alignment.BottomCenter, captions(Constants.BOTTOM))
        assertEquals(Alignment.TopCenter, captions(Constants.TOP))
        assertEquals(Alignment.Center, captions(Constants.MIDDLE))
    }

    @Test
    fun `the position strings are matched exactly, not loosely`() {
        // The stored value comes from the picker verbatim; a case-insensitive or trimming match
        // here would hide a settings file that has drifted rather than surfacing it as centred.
        assertEquals(Alignment.Center, announcements("TOP LEFT"))
        assertEquals(Alignment.Center, announcements(" Top Left "))
    }
}
