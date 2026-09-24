package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A profile's bilingual layout survives a later, unrelated edit to the same profile.
 *
 * Reported twice from the app, back when a screen's *override* was a sparse diff written by hand on
 * every edit: set one screen's songs to Top / Bottom, then make its lyrics bold, and the layout
 * snapped back to `SongSettings.bilingualLayout`'s class default -- because the diff for the second
 * edit was computed fresh each time and dropped whatever the first edit had stored. A profile is now
 * edited as one whole object (`profile.copy(...)`), which makes that particular failure structurally
 * impossible -- there is no separate diff step left to compute wrong. This pins the property that
 * replaced it: resolving a profile against two different global documents changes only what the
 * global document owns, never what the profile itself set.
 */
class BilingualOverrideRoundTripTest {

    private val global = AppSettings(
        songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
        bibleSettings = BibleSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM),
    )

    @Test
    fun `a profile's song layout survives a later unrelated edit`() {
        val afterLayout = OutputProfile(
            songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM),
        )
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            global.resolvedFor(afterLayout).songSettings.bilingualLayout,
            "the layout the operator picked",
        )

        val afterBold = afterLayout.copy(songSettings = afterLayout.songSettings.copy(lyricsBold = true))
        val resolvedB = global.resolvedFor(afterBold)
        assertEquals(true, resolvedB.songSettings.lyricsBold, "the edit that was actually typed")
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            resolvedB.songSettings.bilingualLayout,
            "bolding the lyrics must not move the layout",
        )
    }

    @Test
    fun `a profile's bible layout survives a later unrelated edit`() {
        val afterLayout = OutputProfile(
            bibleSettings = BibleSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
        )
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, global.resolvedFor(afterLayout).bibleSettings.bilingualLayout)

        val afterMargin = afterLayout.copy(bibleSettings = afterLayout.bibleSettings.copy(marginTop = 123))
        val resolvedB = global.resolvedFor(afterMargin)
        assertEquals(123, resolvedB.bibleSettings.marginTop)
        assertEquals(
            Constants.BILINGUAL_SIDE_BY_SIDE,
            resolvedB.bibleSettings.bilingualLayout,
            "typing a margin must not move the layout",
        )
    }
}
