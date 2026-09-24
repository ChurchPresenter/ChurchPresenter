package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Resolving what an output actually renders with, once it only ever *assigns* a profile.
 *
 * The rule these hold to: a profile's [BibleSettings]/[SongSettings] styling always wins, but the
 * library folder, file list and translation-stack membership stay the *global* document's --
 * [styleTreeOf]'s keep-list, not [BIBLE_GLOBAL_KEYS]/[SONG_GLOBAL_KEYS]. Every other settings
 * category ([StageMonitorSettings]/[DictionarySettings]/[BackgroundSettings]) has no such "one per
 * install" fields, so the profile's copy is used wholesale.
 */
class OutputSettingsResolutionTest {

    private fun stack(vararg fileNames: String): BibleSettings =
        BibleSettings(translations = fileNames.map { BibleTranslationSettings(fileName = it) })

    // ── styleTreeOf: the keep-list projection ───────────────────────────────────────────────────

    @Test
    fun `styleTreeOf drops the ignored keys and keeps the rest`() {
        val song = SongSettings(storageDirectory = "/church/songs", colWidthTitle = 321, lyricsColor = "#00FF00")
        val tree = styleTreeOf(song, SongSettings.serializer(), SONG_GLOBAL_KEYS)

        assertEquals(false, "storageDirectory" in tree, "one per install, dropped")
        assertEquals(false, "colWidthTitle" in tree, "one per install, dropped")
        assertEquals(true, "lyricsColor" in tree, "style, kept")
    }

    // ── Bible/Song: content from global, style from the profile ────────────────────────────────

    @Test
    fun `a song profile's styling wins, and the library stays the global document's`() {
        val global = AppSettings(
            songSettings = SongSettings(storageDirectory = "/church/songs", lyricsColor = "#FFFFFF"),
        )
        val profile = OutputProfile(
            songSettings = SongSettings(storageDirectory = "/stale/elsewhere", lyricsColor = "#FF0000"),
        )
        val resolved = global.resolvedFor(profile)

        assertEquals("#FF0000", resolved.songSettings.lyricsColor, "the profile's styling wins")
        assertEquals(
            "/church/songs",
            resolved.songSettings.storageDirectory,
            "the folder is one per install -- the profile's stale copy never applies",
        )
    }

    @Test
    fun `a bible profile never carries the library selection or the panels`() {
        val global = AppSettings(
            bibleSettings = BibleSettings(storageDirectory = "/church/bibles", splitBrowseMode = true),
        )
        val profile = OutputProfile(
            bibleSettings = BibleSettings(
                storageDirectory = "/stale",
                splitBrowseMode = false,
                verticalAlignment = "Middle",
            ),
        )
        val resolved = global.resolvedFor(profile)

        assertEquals("/church/bibles", resolved.bibleSettings.storageDirectory)
        assertEquals(true, resolved.bibleSettings.splitBrowseMode)
        assertEquals("Middle", resolved.bibleSettings.verticalAlignment, "styling still comes from the profile")
    }

    // ── The translation stack: styled per profile, chosen per install ──────────────────────────

    @Test
    fun `the resolved stack is the global one, in the global order`() {
        val global = AppSettings(bibleSettings = stack("KJV.spb", "SYN.spb", "LUT.spb"))
        val profile = OutputProfile(bibleSettings = stack("LUT.spb", "KJV.spb"))
        val resolved = global.resolvedFor(profile)

        assertEquals(
            listOf("KJV.spb", "SYN.spb", "LUT.spb"),
            resolved.bibleSettings.translationList().map { it.fileName },
            "which translations present, and in what order, is the global document's decision",
        )
    }

    @Test
    fun `each translation takes its styling from the profile, matched by file name`() {
        val global = AppSettings(bibleSettings = stack("KJV.spb", "SYN.spb"))
        val profile = OutputProfile(
            bibleSettings = BibleSettings(
                translations = listOf(
                    BibleTranslationSettings(fileName = "SYN.spb", textFontSize = 44),
                    BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 22),
                ),
            ),
        )
        val resolved = global.resolvedFor(profile)

        assertEquals(22, resolved.bibleSettings.translationList()[0].textFontSize, "KJV takes KJV's size")
        assertEquals(44, resolved.bibleSettings.translationList()[1].textFontSize)
    }

    @Test
    fun `a translation the profile has never heard of keeps the global styling`() {
        val global = AppSettings(
            bibleSettings = BibleSettings(
                translations = listOf(
                    BibleTranslationSettings(fileName = "KJV.spb"),
                    BibleTranslationSettings(fileName = "NEW.spb", textFontSize = 55),
                ),
            ),
        )
        val profile = OutputProfile(
            bibleSettings = BibleSettings(
                translations = listOf(BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 22)),
            ),
        )
        val resolved = global.resolvedFor(profile)

        assertEquals(22, resolved.bibleSettings.translationList()[0].textFontSize)
        assertEquals(
            55,
            resolved.bibleSettings.translationList()[1].textFontSize,
            "a translation the profile has never heard of is not left unstyled",
        )
    }

    @Test
    fun `a translation's rename comes from the global entry`() {
        val global = AppSettings(
            bibleSettings = BibleSettings(
                translations = listOf(BibleTranslationSettings(fileName = "KJV.spb", customName = "King James")),
            ),
        )
        val profile = OutputProfile(
            bibleSettings = BibleSettings(
                translations = listOf(
                    BibleTranslationSettings(fileName = "KJV.spb", customName = "stale", textFontSize = 22),
                ),
            ),
        )
        val resolved = global.resolvedFor(profile)

        assertEquals("King James", resolved.bibleSettings.translationList()[0].customName)
        assertEquals(22, resolved.bibleSettings.translationList()[0].textFontSize)
    }

    // ── The other three categories: carried wholesale ───────────────────────────────────────────

    @Test
    fun `a profile's stage monitor styling changes the stage monitor and nothing else`() {
        val global = AppSettings(songSettings = SongSettings(lyricsColor = "#ABCDEF"))
        val profile = OutputProfile(
            stageMonitorSettings = StageMonitorSettings(layout = StageMonitorLayout.LEFT_RIGHT),
            songSettings = global.songSettings,
        )
        val resolved = global.resolvedFor(profile)

        assertEquals(StageMonitorLayout.LEFT_RIGHT, resolved.stageMonitorSettings.layout)
        assertEquals("#ABCDEF", resolved.songSettings.lyricsColor, "one category at a time")
    }

    @Test
    fun `two profiles resolve independently from one global document`() {
        val global = AppSettings(songSettings = SongSettings(lyricsColor = "#FFFFFF"))
        val first = OutputProfile(songSettings = global.songSettings.copy(lyricsColor = "#FF0000"))
        val second = OutputProfile(songSettings = global.songSettings.copy(lyricsColor = "#0000FF"))

        assertEquals("#FF0000", global.resolvedFor(first).songSettings.lyricsColor)
        assertEquals("#0000FF", global.resolvedFor(second).songSettings.lyricsColor)
        assertEquals("#FFFFFF", global.songSettings.lyricsColor, "and the document is untouched")
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an assignment written before profiles existed decodes with no active profile`() {
        val assignment = Json { ignoreUnknownKeys = true }
            .decodeFromString<ScreenAssignment>("""{"targetDisplay":1}""")
        assertNull(assignment.activeProfileId)
    }

    @Test
    fun `a profile round-trips through json`() {
        val profile = OutputProfile(id = "p1", name = "Choir", songSettings = SongSettings(lyricsColor = "#FF0000"))
        val json = Json { ignoreUnknownKeys = true }
        val read = json.decodeFromString<OutputProfile>(json.encodeToString(profile))

        assertEquals(profile, read)
    }
}
