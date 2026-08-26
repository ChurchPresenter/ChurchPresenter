package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Resolving one output's overrides against the global settings document.
 *
 * The rule these hold to: an override supplies appearance and nothing else. Whatever names a folder,
 * selects content or sizes a panel comes from the global document however the override was written,
 * because an output holding its own copy of those could point at a library the operator has moved.
 */
class OutputSettingsResolutionTest {

    private fun stack(vararg fileNames: String): BibleSettings =
        BibleSettings(translations = fileNames.map { BibleTranslationSettings(fileName = it) })

    // ── The uncustomized path ───────────────────────────────────────────────────────────────────

    @Test
    fun `an output with no override renders from the very same settings instance`() {
        val global = AppSettings()
        assertSame(
            global,
            global.resolvedFor(ScreenAssignment()),
            "the common path must not allocate: the presenter windows key remember off this object",
        )
    }

    @Test
    fun `a fresh assignment reports itself as uncustomized`() {
        assertFalse(ScreenAssignment().isCustomized)
    }

    @Test
    fun `any one override is enough to make an output customized`() {
        val each = listOf(
            ScreenAssignment(stageMonitorOverride = StageMonitorSettings()),
            ScreenAssignment(bibleOverride = BibleSettings()),
            ScreenAssignment(songOverride = SongSettings()),
            ScreenAssignment(streamingOverride = StreamingSettings()),
            ScreenAssignment(dictionaryOverride = DictionarySettings()),
        )
        for (assignment in each) assertTrue(assignment.isCustomized)
    }

    // ── Each override replaces only its own section ─────────────────────────────────────────────

    @Test
    fun `a stage monitor override replaces the stage monitor settings and nothing else`() {
        val global = AppSettings()
        val mine = StageMonitorSettings(metronomePosition = MetronomePosition.CENTER)
        val resolved = global.resolvedFor(ScreenAssignment(stageMonitorOverride = mine))

        assertEquals(MetronomePosition.CENTER, resolved.stageMonitorSettings.metronomePosition)
        assertSame(global.songSettings, resolved.songSettings, "songs must be left alone")
        assertSame(global.bibleSettings, resolved.bibleSettings, "and so must the Bible")
        assertSame(global.dictionarySettings, resolved.dictionarySettings)
    }

    @Test
    fun `a dictionary override is taken whole because it is nothing but appearance`() {
        val global = AppSettings()
        val mine = DictionarySettings(wordColor = "#FF0000", wordFontSize = 12)
        val resolved = global.resolvedFor(ScreenAssignment(dictionaryOverride = mine))

        assertSame(mine, resolved.dictionarySettings)
    }

    @Test
    fun `two outputs resolve independently from one global document`() {
        val global = AppSettings()
        val red = ScreenAssignment(songOverride = SongSettings(lyricsColor = "#FF0000"))
        val blue = ScreenAssignment(songOverride = SongSettings(lyricsColor = "#0000FF"))

        assertEquals("#FF0000", global.resolvedFor(red).songSettings.lyricsColor)
        assertEquals("#0000FF", global.resolvedFor(blue).songSettings.lyricsColor)
        assertEquals(
            SongSettings().lyricsColor,
            global.songSettings.lyricsColor,
            "and the global document itself is untouched by either",
        )
    }

    // ── Song: appearance over the global library ────────────────────────────────────────────────

    @Test
    fun `a song override keeps the global library and list columns`() {
        val global = SongSettings(
            storageDirectory = "/church/songs",
            songFiles = listOf("a.song", "b.song"),
            colWidthTitle = 321,
            lyricsPanelWidthDp = 654,
            editorShowChords = false,
            lyricsColor = "#FFFFFF",
        )
        val override = SongSettings(
            storageDirectory = "/somewhere/stale",
            songFiles = listOf("gone.song"),
            colWidthTitle = 10,
            lyricsPanelWidthDp = 20,
            editorShowChords = true,
            lyricsColor = "#00FF00",
        )
        val resolved = global.withAppearanceOf(override)

        assertEquals("#00FF00", resolved.lyricsColor, "appearance comes from the override")
        assertEquals("/church/songs", resolved.storageDirectory, "the library does not")
        assertEquals(listOf("a.song", "b.song"), resolved.songFiles)
        assertEquals(321, resolved.colWidthTitle)
        assertEquals(654, resolved.lyricsPanelWidthDp)
        assertFalse(resolved.editorShowChords, "nor does an editor preference")
    }

    // ── Bible: the global stack decides which translations, the override how they look ──────────

    @Test
    fun `a bible override keeps the global library selection and panels`() {
        val global = BibleSettings(
            storageDirectory = "/church/bibles",
            bibleFiles = listOf("KJV.spb"),
            bibleColWidthBook = 111,
            splitBrowseMode = true,
            crossReferencesEnabled = false,
            verticalAlignment = "Top",
        )
        val override = BibleSettings(
            storageDirectory = "/stale",
            bibleFiles = listOf("gone.spb"),
            bibleColWidthBook = 9,
            splitBrowseMode = false,
            crossReferencesEnabled = true,
            verticalAlignment = "Middle",
        )
        val resolved = global.withAppearanceOf(override)

        assertEquals("Middle", resolved.verticalAlignment, "appearance comes from the override")
        assertEquals("/church/bibles", resolved.storageDirectory)
        assertEquals(listOf("KJV.spb"), resolved.bibleFiles)
        assertEquals(111, resolved.bibleColWidthBook)
        assertTrue(resolved.splitBrowseMode)
        assertFalse(resolved.crossReferencesEnabled)
    }

    @Test
    fun `the resolved stack is the global one, in the global order`() {
        val global = stack("KJV.spb", "SYN.spb", "LUT.spb")
        val override = stack("LUT.spb", "KJV.spb")     // a different set, in a different order
        val resolved = global.withAppearanceOf(override)

        assertEquals(
            listOf("KJV.spb", "SYN.spb", "LUT.spb"),
            resolved.translationList().map { it.fileName },
            "which translations present, and in what order, is the global document's decision",
        )
    }

    @Test
    fun `each translation takes its styling from the override, matched by file name`() {
        val global = stack("KJV.spb", "SYN.spb")
        val override = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "SYN.spb", textFontSize = 44),
                BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 22),
            ),
        )
        val resolved = global.withAppearanceOf(override)

        assertEquals(22, resolved.translationList()[0].textFontSize, "KJV takes KJV's size, not the first entry's")
        assertEquals(44, resolved.translationList()[1].textFontSize)
    }

    @Test
    fun `a translation added after the override was made keeps the global styling`() {
        val global = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 70),
                BibleTranslationSettings(fileName = "NEW.spb", textFontSize = 65),
            ),
        )
        val override = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 22)),
        )
        val resolved = global.withAppearanceOf(override)

        assertEquals(22, resolved.translationList()[0].textFontSize)
        assertEquals(
            65,
            resolved.translationList()[1].textFontSize,
            "the newcomer must keep the global styling rather than vanish from the output",
        )
    }

    @Test
    fun `a translation the global stack has dropped does not reappear`() {
        val global = stack("KJV.spb")
        val override = stack("KJV.spb", "REMOVED.spb")
        val resolved = global.withAppearanceOf(override)

        assertEquals(listOf("KJV.spb"), resolved.translationList().map { it.fileName })
    }

    @Test
    fun `a translation's rename comes from the global entry`() {
        val global = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "KJV.spb", customName = "Pew Bible", customAbbreviation = "PEW"),
            ),
        )
        val override = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "KJV.spb", customName = "Stale", customAbbreviation = "OLD"),
            ),
        )
        val resolved = global.withAppearanceOf(override)

        assertEquals("Pew Bible", resolved.translationList()[0].customName)
        assertEquals("PEW", resolved.translationList()[0].customAbbreviation)
    }

    // ── Lower third: the four insets and nothing else ───────────────────────────────────────────

    @Test
    fun `a streaming override supplies the insets and keeps the global lottie library`() {
        val global = StreamingSettings(
            lowerThirdFolder = "/church/lottie",
            savedSearchStrings = listOf("name"),
            lottiePresets = listOf(LottiePreset(id = "keep")),
            lowerThirdListWidthDp = 300,
            windowLeft = 0,
        )
        val override = StreamingSettings(
            lowerThirdFolder = "/stale",
            savedSearchStrings = emptyList(),
            lottiePresets = emptyList(),
            lowerThirdListWidthDp = 1,
            windowLeft = 40, windowTop = 41, windowRight = 42, windowBottom = 43,
        )
        val resolved = global.withAppearanceOf(override)

        assertEquals(40, resolved.windowLeft)
        assertEquals(41, resolved.windowTop)
        assertEquals(42, resolved.windowRight)
        assertEquals(43, resolved.windowBottom)
        assertEquals("/church/lottie", resolved.lowerThirdFolder)
        assertEquals(listOf("name"), resolved.savedSearchStrings)
        assertEquals(listOf(LottiePreset(id = "keep")), resolved.lottiePresets)
        assertEquals(300, resolved.lowerThirdListWidthDp)
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an assignment written before the overrides existed loads with none of them`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<ScreenAssignment>("""{"targetDisplay":1}""")

        assertNull(decoded.stageMonitorOverride)
        assertNull(decoded.bibleOverride)
        assertNull(decoded.songOverride)
        assertNull(decoded.streamingOverride)
        assertNull(decoded.dictionaryOverride)
        assertFalse(decoded.isCustomized, "an old document must keep following the global settings")
    }

    @Test
    fun `an override round-trips through json`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val original = ScreenAssignment(
            songOverride = SongSettings(lyricsColor = "#ABCDEF"),
            dictionaryOverride = DictionarySettings(wordFontSize = 13),
        )
        val decoded = json.decodeFromString<ScreenAssignment>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals("#ABCDEF", decoded.songOverride?.lyricsColor)
        assertEquals(13, decoded.dictionaryOverride?.wordFontSize)
    }
}
