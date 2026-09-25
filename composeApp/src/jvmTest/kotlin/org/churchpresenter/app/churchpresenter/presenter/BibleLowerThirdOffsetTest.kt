package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.elementStyle
import org.churchpresenter.app.churchpresenter.dialogs.tabs.withElementStyle
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ElementOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The band's own verse and reference offsets — #613's remaining item.
 *
 * `textOffsetFor` returned null for a band whatever was stored, so the two controls the Bible pane
 * offered were full-screen only. The reason recorded at the time was that a band is a strip with
 * little room to position anything in; the real reason it had to stay that way is that only the two
 * *grid* searches knew how to account for a positioned half, and the band's two hand-rolled
 * layouts did not. They do now.
 *
 * Both halves of the fix are pinned: the stored pair is per output and independent, and reading it
 * for a band gives the band's own value rather than the full screen's.
 */
class BibleLowerThirdOffsetTest {

    private val full = ElementOffset(xPercent = 10, yPercent = 20)
    private val band = ElementOffset(xPercent = 90, yPercent = 80)

    @Test
    fun `a band reads its own offsets, not the full screen's`() {
        val t = BibleTranslationSettings(
            textOffset = full,
            referenceOffset = full,
            lowerThirdTextOffset = band,
            lowerThirdReferenceOffset = band,
        )
        assertEquals(full, t.textOffsetFor(lowerThird = false))
        assertEquals(full, t.referenceOffsetFor(lowerThird = false))
        assertEquals(band, t.textOffsetFor(lowerThird = true))
        assertEquals(band, t.referenceOffsetFor(lowerThird = true))
    }

    @Test
    fun `nothing is positioned by default, on either output`() {
        val t = BibleTranslationSettings()
        assertNull(t.textOffsetFor(lowerThird = false))
        assertNull(t.textOffsetFor(lowerThird = true))
        assertNull(t.referenceOffsetFor(lowerThird = false))
        assertNull(t.referenceOffsetFor(lowerThird = true))
    }

    @Test
    fun `positioning a band element leaves the full screen in the stack`() {
        // The case the shared field would have broken: a band offset must not move the projector.
        val t = BibleTranslationSettings(lowerThirdTextOffset = band)
        assertNull(t.textOffsetFor(lowerThird = false))
        assertEquals(band, t.textOffsetFor(lowerThird = true))
    }

    @Test
    fun `the panel round-trips an offset on each of the four profiles`() {
        // The pane reads and writes through `elementStyle`, whose lower-third branches used to read
        // and write null outright -- so a switch turned on there appeared to do nothing and then
        // forgot itself on the next read.
        for (element in listOf(BibleStyleElement.TEXT, BibleStyleElement.REFERENCE)) {
            for (target in listOf(BibleStyleTarget.FULL_SCREEN, BibleStyleTarget.LOWER_THIRD)) {
                val written = BibleTranslationSettings().let {
                    it.withElementStyle(element, target, it.elementStyle(element, target).copy(offset = band))
                }
                assertEquals(band, written.elementStyle(element, target).offset, "$element on $target")
                // And only that one profile.
                val others = listOf(
                    BibleStyleElement.TEXT to BibleStyleTarget.FULL_SCREEN,
                    BibleStyleElement.TEXT to BibleStyleTarget.LOWER_THIRD,
                    BibleStyleElement.REFERENCE to BibleStyleTarget.FULL_SCREEN,
                    BibleStyleElement.REFERENCE to BibleStyleTarget.LOWER_THIRD,
                ).filterNot { it.first == element && it.second == target }
                for ((otherElement, otherTarget) in others) {
                    assertNull(
                        written.elementStyle(otherElement, otherTarget).offset,
                        "writing $element/$target also set $otherElement/$otherTarget",
                    )
                }
            }
        }
    }
}
