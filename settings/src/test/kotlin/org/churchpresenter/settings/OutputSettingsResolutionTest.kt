package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Resolving one output's overrides against the global settings document.
 *
 * The rule these hold to: an override says what one screen *changed*, and nothing else. A setting it
 * does not mention follows the document — today, and after that setting's neighbours have been added
 * to, renamed around or defaulted differently. A screen can only hold a value it was given.
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
            "the common path must not pay for a copy, and the presenter windows key remember off it",
        )
    }

    @Test
    fun `a fresh assignment reports itself as uncustomized`() {
        assertFalse(ScreenAssignment().isCustomized)
    }

    @Test
    fun `any one override is enough to make an output customized`() {
        assertTrue(ScreenAssignment(songOverride = JsonObject(emptyMap())).isCustomized)
        assertTrue(ScreenAssignment(bibleOverride = JsonObject(emptyMap())).isCustomized)
        assertTrue(ScreenAssignment(dictionaryOverride = JsonObject(emptyMap())).isCustomized)
        assertTrue(ScreenAssignment(backgroundOverride = JsonObject(emptyMap())).isCustomized)
        assertTrue(ScreenAssignment(stageMonitorOverride = JsonObject(emptyMap())).isCustomized)
    }

    @Test
    fun `a screen with its own styles and no differences yet draws the document`() {
        val global = AppSettings(songSettings = SongSettings(lyricsColor = "#ABCDEF"))
        val on = ScreenAssignment(songOverride = JsonObject(emptyMap()))
        assertTrue(on.isCustomized, "the switch is on")
        assertEquals("#ABCDEF", global.resolvedFor(on).songSettings.lyricsColor)
    }

    // ── What an override stores ─────────────────────────────────────────────────────────────────

    @Test
    fun `only what the screen changed is stored`() {
        val global = SongSettings(lyricsColor = "#FFFFFF", lyricsFontSize = 70, marginTop = 54)
        val edited = global.copy(lyricsColor = "#FF0000")
        val stored = assertNotNullOverride(songOverrideOf(global, edited))

        assertEquals(setOf("lyricsColor"), stored.keys, "one change stores one key")
    }

    @Test
    fun `a screen that changed nothing stores nothing`() {
        val global = SongSettings(lyricsColor = "#FFFFFF")
        assertNull(songOverrideOf(global, global))
    }

    @Test
    fun `a nested record stores the field that changed, not the record`() {
        val global = SongSettings()
        val edited = global.copy(outlines = global.outlines.copy(lyrics = global.outlines.lyrics.copy(enabled = true)))
        val stored = assertNotNullOverride(songOverrideOf(global, edited))

        assertEquals(setOf("outlines"), stored.keys)
        val outlines = stored["outlines"] as JsonObject
        assertEquals(setOf("lyrics"), outlines.keys, "the other nine profiles are not this screen's business")
        assertEquals(setOf("enabled"), (outlines["lyrics"] as JsonObject).keys)
    }

    @Test
    fun `a setting the screen never mentioned follows the document, however late it was added`() {
        // What a screen customized before a setting existed carries: no key for it at all.
        val override = JsonObject(emptyMap())
        val global = SongSettings(
            translations = listOf(
                SongTranslationSettings(overrideStyle = true, lyrics = SongTextStyle(color = "#2B14CC")),
            ),
        )
        val resolved = withSparseOverride(global, override, SongSettings.serializer())

        assertTrue(resolved.translationSettings(0).overrideStyle)
        assertEquals(
            "#2B14CC",
            resolved.translationSettings(0).lyrics.color,
            "this is the whole point: a snapshot pinned it to the class default and the global did nothing",
        )
    }

    @Test
    fun `what the screen did say still wins`() {
        val global = SongSettings(lyricsColor = "#FFFFFF")
        val edited = global.copy(lyricsColor = "#FF0000")
        val stored = songOverrideOf(global, edited)
        // The document moves on; the screen's own choice does not move with it.
        val later = global.copy(lyricsColor = "#00FF00", lyricsFontSize = 90)
        val resolved = withSparseOverride(later, stored, SongSettings.serializer())

        assertEquals("#FF0000", resolved.lyricsColor, "the screen said this")
        assertEquals(90, resolved.lyricsFontSize, "and said nothing about this")
    }

    // ── What a screen may not say ───────────────────────────────────────────────────────────────

    @Test
    fun `a song override never carries the library or the list columns`() {
        val global = SongSettings(storageDirectory = "/church/songs", colWidthTitle = 321)
        val edited = global.copy(storageDirectory = "/somewhere/stale", colWidthTitle = 10, lyricsColor = "#00FF00")
        val stored = assertNotNullOverride(songOverrideOf(global, edited))

        assertEquals(setOf("lyricsColor"), stored.keys, "the folder and the columns are one per install")
    }

    @Test
    fun `a bible override never carries the library selection or the panels`() {
        val global = BibleSettings(storageDirectory = "/church/bibles", splitBrowseMode = true)
        val edited = global.copy(storageDirectory = "/stale", splitBrowseMode = false, verticalAlignment = "Middle")
        val stored = assertNotNullOverride(bibleOverrideOf(global, edited))

        assertEquals(setOf("verticalAlignment"), stored.keys)
    }

    // ── The translation stack: styled per screen, chosen per install ────────────────────────────

    @Test
    fun `the resolved stack is the global one, in the global order`() {
        val global = AppSettings(bibleSettings = stack("KJV.spb", "SYN.spb", "LUT.spb"))
        val override = bibleOverrideOf(global.bibleSettings, stack("LUT.spb", "KJV.spb"))
        val resolved = global.resolvedFor(ScreenAssignment(bibleOverride = override))

        assertEquals(
            listOf("KJV.spb", "SYN.spb", "LUT.spb"),
            resolved.bibleSettings.translationList().map { it.fileName },
            "which translations present, and in what order, is the global document's decision",
        )
    }

    @Test
    fun `each translation takes its styling from the override, matched by file name`() {
        val global = AppSettings(bibleSettings = stack("KJV.spb", "SYN.spb"))
        val customized = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "SYN.spb", textFontSize = 44),
                BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 22),
            ),
        )
        val resolved = global.resolvedFor(
            ScreenAssignment(bibleOverride = bibleOverrideOf(global.bibleSettings, customized)),
        )

        assertEquals(22, resolved.bibleSettings.translationList()[0].textFontSize, "KJV takes KJV's size")
        assertEquals(44, resolved.bibleSettings.translationList()[1].textFontSize)
    }

    @Test
    fun `a translation added after the override was made keeps the global styling`() {
        val before = stack("KJV.spb")
        val override = bibleOverrideOf(
            before,
            BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "KJV.spb", textFontSize = 22))),
        )
        val global = AppSettings(
            bibleSettings = BibleSettings(
                translations = listOf(
                    BibleTranslationSettings(fileName = "KJV.spb"),
                    BibleTranslationSettings(fileName = "NEW.spb", textFontSize = 55),
                ),
            ),
        )
        val resolved = global.resolvedFor(ScreenAssignment(bibleOverride = override))

        assertEquals(22, resolved.bibleSettings.translationList()[0].textFontSize)
        assertEquals(
            55,
            resolved.bibleSettings.translationList()[1].textFontSize,
            "a translation the override has never heard of is not left unstyled",
        )
    }

    @Test
    fun `a translation's rename comes from the global entry`() {
        val global = AppSettings(
            bibleSettings = BibleSettings(
                translations = listOf(BibleTranslationSettings(fileName = "KJV.spb", customName = "King James")),
            ),
        )
        val customized = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "KJV.spb", customName = "stale", textFontSize = 22),
            ),
        )
        val resolved = global.resolvedFor(
            ScreenAssignment(bibleOverride = bibleOverrideOf(global.bibleSettings, customized)),
        )

        assertEquals("King James", resolved.bibleSettings.translationList()[0].customName)
        assertEquals(22, resolved.bibleSettings.translationList()[0].textFontSize)
    }

    // ── The other three categories ──────────────────────────────────────────────────────────────

    @Test
    fun `a stage monitor override changes the stage monitor and nothing else`() {
        val global = AppSettings(
            stageMonitorSettings = StageMonitorSettings(),
            songSettings = SongSettings(lyricsColor = "#ABCDEF"),
        )
        val customized = global.stageMonitorSettings.copy(layout = StageMonitorLayout.LEFT_RIGHT)
        val resolved = global.resolvedFor(
            ScreenAssignment(
                stageMonitorOverride = stageMonitorOverrideOf(global.stageMonitorSettings, customized),
            ),
        )

        assertEquals(StageMonitorLayout.LEFT_RIGHT, resolved.stageMonitorSettings.layout)
        assertEquals("#ABCDEF", resolved.songSettings.lyricsColor, "one category at a time")
    }

    @Test
    fun `two outputs resolve independently from one global document`() {
        val global = AppSettings(songSettings = SongSettings(lyricsColor = "#FFFFFF"))
        val first = ScreenAssignment(
            songOverride = songOverrideOf(global.songSettings, global.songSettings.copy(lyricsColor = "#FF0000")),
        )
        val second = ScreenAssignment(
            songOverride = songOverrideOf(global.songSettings, global.songSettings.copy(lyricsColor = "#0000FF")),
        )

        assertEquals("#FF0000", global.resolvedFor(first).songSettings.lyricsColor)
        assertEquals("#0000FF", global.resolvedFor(second).songSettings.lyricsColor)
        assertEquals("#FFFFFF", global.songSettings.lyricsColor, "and the document is untouched")
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an assignment written before the overrides existed loads with none of them`() {
        val assignment = Json { ignoreUnknownKeys = true }
            .decodeFromString<ScreenAssignment>("""{"targetDisplay":1}""")
        assertNull(assignment.songOverride)
        assertFalse(assignment.isCustomized)
    }

    @Test
    fun `an override round-trips through json`() {
        val global = SongSettings(lyricsColor = "#FFFFFF")
        val assignment = ScreenAssignment(
            songOverride = songOverrideOf(global, global.copy(lyricsColor = "#FF0000")),
        )
        val json = Json { ignoreUnknownKeys = true }
        val read = json.decodeFromString<ScreenAssignment>(json.encodeToString(assignment))

        assertEquals(assignment.songOverride, read.songOverride)
        assertEquals(
            "#FF0000",
            withSparseOverride(global, read.songOverride, SongSettings.serializer()).lyricsColor,
        )
    }

    private fun assertNotNullOverride(tree: JsonObject?): JsonObject =
        tree ?: error("expected the screen to have stored something")
}
