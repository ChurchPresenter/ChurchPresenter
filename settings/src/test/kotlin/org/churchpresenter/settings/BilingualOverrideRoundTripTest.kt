package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A screen's own bilingual layout survives an unrelated edit on the same screen.
 *
 * Reported twice from the app: set one screen's songs to Top / Bottom, then make its lyrics bold,
 * and the layout snapped back to Left / Right -- which is `SongSettings.bilingualLayout`'s class
 * default. The Bible pane was reported doing the same. These walk the exact path the Customize
 * dialog walks on every edit: diff the edited copy against the document, store the difference, then
 * resolve the screen again from the document plus that difference.
 */
class BilingualOverrideRoundTripTest {

    private val global = AppSettings(
        songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
        bibleSettings = BibleSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM),
    )

    /** One turn of the dialog's `edit`: what the screen holds after [transform] is typed into it. */
    private fun AppSettings.editedOnScreen(
        assignment: ScreenAssignment,
        transform: (AppSettings) -> AppSettings,
    ): Pair<ScreenAssignment, AppSettings> {
        val draft = resolvedFor(assignment)
        val edited = transform(draft)
        val next = assignment.copy(
            songOverride = songOverrideOf(songSettings, edited.songSettings) ?: emptyTree(),
            bibleOverride = bibleOverrideOf(bibleSettings, edited.bibleSettings) ?: emptyTree(),
        )
        return next to resolvedFor(next)
    }

    private fun emptyTree() = kotlinx.serialization.json.JsonObject(emptyMap())

    @Test
    fun `a screen's song layout survives a later unrelated edit`() {
        val (afterLayout, resolvedA) = global.editedOnScreen(ScreenAssignment()) { draft ->
            draft.copy(songSettings = draft.songSettings.copy(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM))
        }
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            resolvedA.songSettings.bilingualLayout,
            "the layout the operator picked",
        )

        val (_, resolvedB) = global.editedOnScreen(afterLayout) { draft ->
            draft.copy(songSettings = draft.songSettings.copy(lyricsBold = true))
        }
        assertEquals(true, resolvedB.songSettings.lyricsBold, "the edit that was actually typed")
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            resolvedB.songSettings.bilingualLayout,
            "bolding the lyrics must not move the layout",
        )
    }

    @Test
    fun `a screen's bible layout survives a later unrelated edit`() {
        val (afterLayout, resolvedA) = global.editedOnScreen(ScreenAssignment()) { draft ->
            draft.copy(
                bibleSettings = draft.bibleSettings.copy(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
            )
        }
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, resolvedA.bibleSettings.bilingualLayout)

        val (_, resolvedB) = global.editedOnScreen(afterLayout) { draft ->
            draft.copy(bibleSettings = draft.bibleSettings.copy(marginTop = 123))
        }
        assertEquals(123, resolvedB.bibleSettings.marginTop)
        assertEquals(
            Constants.BILINGUAL_SIDE_BY_SIDE,
            resolvedB.bibleSettings.bilingualLayout,
            "typing a margin must not move the layout",
        )
    }

    /** The narrower claim the two above rest on: a scalar equal to nothing in particular round-trips. */
    @Test
    fun `the sparse override carries a changed scalar back`() {
        val edited = global.songSettings.copy(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM)
        val tree = songOverrideOf(global.songSettings, edited)
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            withSparseOverride(global.songSettings, tree, SongSettings.serializer()).bilingualLayout,
        )
    }
}
