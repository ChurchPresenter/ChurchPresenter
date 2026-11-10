package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A translation's look staying with its place in the stack, or going with the Bible, as each
 * profile's own [BibleSettings.looksFollowPosition] says.
 *
 * The stack is `[kjv, rst]`; the profile draws kjv at 70 and rst at 40, so where a size ends up says
 * which look a Bible was given.
 */
class BibleLooksByPositionTest {

    private fun profile(
        id: String = "main",
        byPlace: Boolean = true,
        boxes: Map<String, TextBox> = emptyMap(),
        overrides: Set<String> = emptySet(),
        parentId: String? = null,
        extra: List<BibleTranslationSettings> = emptyList(),
    ) = OutputProfile(
        id = id,
        parentId = parentId,
        bibleSettings = BibleSettings(
            looksFollowPosition = byPlace,
            translations = listOf(
                BibleTranslationSettings(fileName = KJV, textFontSize = 70),
                BibleTranslationSettings(fileName = RST, textFontSize = 40),
            ) + extra,
            textBoxes = boxes,
        ),
        overrides = overrides,
    )

    private fun app(vararg profiles: OutputProfile) = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            listOf(KJV, RST).map { BibleTranslationSettings(fileName = it) },
        ),
        projectionSettings = ProjectionSettings(outputProfiles = profiles.toList()),
    )

    /** Each translation's file and size as an output on profile [id] draws it. */
    private fun AppSettings.drawn(id: String = "main") =
        bibleSettingsFor(projectionSettings.outputProfiles.first { it.id == id })
            .translationList().map { it.fileName to it.textFontSize }

    private fun AppSettings.stored(id: String = "main") = projectionSettings.outputProfiles.first { it.id == id }

    // ── Place ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Bible put in a place takes that place's look`() {
        val after = app(profile()).replaceBibleTranslation(0, NIV)

        assertEquals(listOf(NIV to 70, RST to 40), after.drawn(), "niv is drawn as kjv was, not in the defaults")
    }

    @Test
    fun `a swap leaves each place looking as it did`() {
        val after = app(profile()).swapBibleTranslations()

        assertEquals(listOf(RST to 70, KJV to 40), after.drawn())
    }

    @Test
    fun `removing the first moves the next up into its look`() {
        val after = app(profile()).removeBibleTranslation(0)

        assertEquals(listOf(RST to 70), after.drawn())
    }

    @Test
    fun `a place's text boxes go with its look`() {
        val box = TextBox(enabled = true, xPercent = 10f)
        val after = app(
            profile(boxes = mapOf(textBoxKey(BIBLE_REFERENCE_BOX, lowerThird = true, language = KJV) to box)),
        ).swapBibleTranslations()

        assertEquals(
            mapOf(textBoxKey(BIBLE_REFERENCE_BOX, lowerThird = true, language = RST) to box),
            after.stored().bibleSettings.textBoxes,
        )
    }

    @Test
    fun `a box that names no translation is left alone`() {
        val box = TextBox(enabled = true)
        val after = app(profile(boxes = mapOf("PLAIN" to box))).swapBibleTranslations()

        assertEquals(mapOf("PLAIN" to box), after.stored().bibleSettings.textBoxes)
    }

    @Test
    fun `a follower's own values are renamed with the look`() {
        val after = app(
            profile(id = "follower", overrides = setOf("bibleSettings.translations[kjv.spb].textFontSize")),
        ).swapBibleTranslations()

        assertEquals(setOf("bibleSettings.translations[rst.spb].textFontSize"), after.stored("follower").overrides)
    }

    @Test
    fun `a stale look under the incoming name is replaced`() {
        // The profile once styled niv, which has since left the stack; putting it back in place 1
        // must give it place 1's look, not that old one.
        val stale = BibleTranslationSettings(fileName = NIV, textFontSize = 12)
        val niv = textBoxKey(BIBLE_TEXT_BOX, lowerThird = false, language = NIV)
        val after = app(
            profile(
                extra = listOf(stale),
                boxes = mapOf(niv to TextBox(enabled = true)),
                overrides = setOf("bibleSettings.translations[niv.spb].textFontSize", "bibleSettings.marginTop"),
            ),
        ).replaceBibleTranslation(0, NIV)

        assertEquals(listOf(NIV to 70, RST to 40), after.drawn())
        assertEquals(emptyMap(), after.stored().bibleSettings.textBoxes, "niv's old box is gone with its look")
        assertEquals(setOf("bibleSettings.marginTop"), after.stored().overrides, "and so is its own value")
    }

    @Test
    fun `a follower inherits its master's renamed look`() {
        // The follower keeps its looks by Bible as its own choice, but its sizes are its master's,
        // which follow the place -- so after a swap it is drawn with the master's renamed look.
        val master = profile(id = "master")
        val follower = profile(id = "follower", byPlace = false, parentId = "master")
            .copy(overrides = setOf("bibleSettings.looksFollowPosition"))
        val after = app(master, follower).swapBibleTranslations()

        assertEquals(listOf(RST to 70, KJV to 40), after.drawn("follower"))
    }

    // ── Bible version ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `by Bible a swap carries each look with its Bible`() {
        val after = app(profile(byPlace = false)).swapBibleTranslations()

        assertEquals(listOf(RST to 40, KJV to 70), after.drawn())
    }

    @Test
    fun `by Bible a Bible this profile never styled takes its All look`() {
        val all = BibleTranslationSettings(textFontSize = 55)
        val byBible = profile(byPlace = false).let {
            it.copy(bibleSettings = it.bibleSettings.copy(allTranslationStyle = all))
        }
        val after = app(byBible).replaceBibleTranslation(0, NIV)

        assertEquals(listOf(NIV to 55, RST to 40), after.drawn())
    }

    @Test
    fun `each profile follows its own choice through one swap`() {
        val after = app(profile(id = "place"), profile(id = "bible", byPlace = false)).swapBibleTranslations()

        assertEquals(listOf(RST to 70, KJV to 40), after.drawn("place"))
        assertEquals(listOf(RST to 40, KJV to 70), after.drawn("bible"))
    }

    // ── Replacing ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `replacing clears the old Bible's book names and keeps its rename`() {
        val start = app(profile()).let {
            it.copy(
                bibleSettings = it.bibleSettings.updateTranslation(0) { t ->
                    t.copy(customName = "Main", bookNameOverrides = mapOf(1 to "Gen"))
                },
            )
        }
        val niv = start.replaceBibleTranslation(0, NIV).bibleSettings.translationList()[0]

        assertEquals(NIV, niv.fileName)
        assertEquals(emptyMap(), niv.bookNameOverrides, "book names belong to the module they were written for")
        assertEquals("Main", niv.customName, "the slot's rename stays, as the slot picker always kept it")
    }

    @Test
    fun `replacing with a Bible already in the stack, a blank name or no such place changes nothing`() {
        val before = app(profile())

        assertEquals(before, before.replaceBibleTranslation(0, RST))
        assertEquals(before, before.replaceBibleTranslation(0, ""))
        assertEquals(before, before.replaceBibleTranslation(5, NIV))
    }

    private companion object {
        const val KJV = "kjv.spb"
        const val RST = "rst.spb"
        const val NIV = "niv.spb"
    }
}
