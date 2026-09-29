package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a profile changes from the defaults, and putting one back. */
class ProfileDefaultsTest {

    private val kjv = "kjv.spb"
    private val rst = "rst.spb"

    private fun profile(
        bible: BibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = kjv, customAbbreviation = "KJV"),
                BibleTranslationSettings(fileName = rst, customAbbreviation = "RST"),
            ),
        ),
        song: SongSettings = SongSettings(),
    ) = newOutputProfile(emptyList(), "Sanctuary").copy(bibleSettings = bible, songSettings = song)

    @Test
    fun `a new profile changes nothing, whatever its translations are called`() {
        assertEquals(emptyList(), defaultChanges(profile()))
    }

    @Test
    fun `a new profile's Bible and song backgrounds follow its own default`() {
        val fresh = newOutputProfile(emptyList())
        val backgrounds = fresh.backgroundSettings
        listOf(
            backgrounds.bibleBackground,
            backgrounds.bibleLowerThirdBackground,
            backgrounds.songBackground,
            backgrounds.songLowerThirdBackground,
        ).forEach { assertEquals(Constants.BACKGROUND_DEFAULT, it.backgroundType) }
        assertEquals(
            setOf("BIBLE", "BIBLE_LOWER_THIRD", "SONG", "SONG_LOWER_THIRD"),
            fresh.backgroundOverrides,
        )
    }

    @Test
    fun `a translation's own size is listed by its path`() {
        val p = profile()
        val changed = p.copy(
            bibleSettings = p.bibleSettings.copy(
                translations = p.bibleSettings.translations.map {
                    if (it.fileName == rst) it.copy(textFontSize = 40) else it
                },
            ),
        )
        assertEquals(listOf("bibleSettings.translations[$rst].textFontSize"), defaultChanges(changed))
    }

    @Test
    fun `bookkeeping is not a change -- the All layer, own keys and which surfaces are owned`() {
        val p = profile()
        val bible = p.bibleSettings.copy(
            allTranslationStyle = BibleTranslationSettings(textFontSize = 99),
            translations = p.bibleSettings.translations.map { it.copy(ownStyleKeys = setOf("textFontSize")) },
        )
        val quiet = p.copy(bibleSettings = bible, backgroundOverrides = emptySet())
        assertEquals(emptyList(), defaultChanges(quiet))
    }

    @Test
    fun `the own background kept aside while a surface follows its default is not a change`() {
        val p = profile()
        val backgrounds = p.backgroundSettings.copy(
            bibleBackground = p.backgroundSettings.bibleBackground.copy(ownBackgroundType = Constants.BACKGROUND_IMAGE),
        )
        assertEquals(emptyList(), defaultChanges(p.copy(backgroundSettings = backgrounds)))
    }

    @Test
    fun `a value set where the default has none is listed by what it holds, and put back to none`() {
        val p = profile(
            song = SongSettings(
                layoutExtras = SongLayoutExtras(
                    lyricsOffset = ElementOffset(xPercent = 56, yPercent = 31),
                ),
            ),
        )
        val changes = defaultChanges(p)
        assertEquals(
            listOf(
                "songSettings.layoutExtras.lyricsOffset.xPercent",
                "songSettings.layoutExtras.lyricsOffset.yPercent",
            ),
            changes,
        )
        val reset = p.withDefaultAt(changes.first())
        assertNull(reset.songSettings.layoutExtras.lyricsOffset)
        assertEquals(emptyList(), defaultChanges(reset))
    }

    @Test
    fun `Revert puts a changed value back at its default`() {
        val p = profile()
        val moved = p.copy(
            bibleSettings = p.bibleSettings.copy(
                translations = p.bibleSettings.translations.map { it.copy(referenceShiftY = 250) },
            ),
        )
        val path = "bibleSettings.translations[$kjv].referenceShiftY"
        val reset = moved.withDefaultAt(path)
        assertEquals(0, reset.bibleSettings.translations.first { it.fileName == kjv }.referenceShiftY)
        assertEquals(250, reset.bibleSettings.translations.first { it.fileName == rst }.referenceShiftY)
    }

    @Test
    fun `a map entry a new profile does not have is taken out`() {
        val p = profile(
            song = SongSettings().withElementShift(songElementShiftKey("LYRICS", false, 1), SongElementShift(0, 30)),
        )
        val changes = defaultChanges(p)
        assertTrue(changes.all { it.startsWith("songSettings.layoutExtras.elementShifts.") }, "$changes")
        val reset = p.withDefaultAt(changes.first())
        assertEquals(emptyMap(), reset.songSettings.layoutExtras.elementShifts)
    }

    @Test
    fun `a path that leads nowhere changes nothing`() {
        val p = profile()
        assertEquals(p, p.withDefaultAt("songSettings.nothingHere.atAll"))
    }
}
