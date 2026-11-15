package org.churchpresenter.settings

import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Version 27: a look-ahead output's current lines are styled by Lyrics. A profile with look-ahead on
 * must draw what it drew before -- its look-ahead style, now held as its lyrics -- and one with it
 * off must not change at all.
 */
class LookAheadIntoLyricsMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-look-ahead-into-lyrics-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val box = TextBox(enabled = true, xPercent = 10f)
    private val moved = SongElementShift(x = 30, y = -20)

    /** Song settings whose look-ahead differs from their lyrics in every field the migration copies. */
    private val styled = SongSettings(
        lyricsColor = "#FFFFFF",
        lookAheadColor = "#FF0000",
        lookAheadFontType = "Verdana",
        lookAheadFontSize = 98,
        lookAheadFontSizeAutoFit = false,
        lookAheadBold = true,
        lookAheadItalic = true,
        lookAheadUnderline = true,
        lookAheadStrikethrough = true,
        lookAheadShadow = true,
        lookAheadShadowColor = "#112233",
        lookAheadShadowSize = 150,
        lookAheadShadowOpacity = 40,
        lookAheadHorizontalAlignment = "Left",
        lookAheadLetterSpacing = 4,
        lookAheadWordSpacing = 6,
        lookAheadTransform = "Uppercase",
        lookAheadBackdrop = TextBackdrop(lineBackground = true),
        lowerThirdLookAheadColor = "#00FF00",
        lowerThirdLookAheadFontType = "Georgia",
        lowerThirdLookAheadFontSize = 33,
        lowerThirdLookAheadBackdrop = TextBackdrop(border = true),
        outlines = SongOutlines(
            lookAhead = TextOutline(enabled = true, width = 7),
            lookAheadLowerThird = TextOutline(enabled = true, width = 3),
        ),
        translations = listOf(
            SongTranslationSettings(
                overrideStyle = true,
                lyrics = SongTextStyle(color = "#AAAAAA", chordColor = "#123456"),
                lookAhead = SongTextStyle(color = "#BBBBBB"),
                lookAheadLowerThird = SongTextStyle(color = "#CCCCCC"),
            ),
        ),
        layoutExtras = SongLayoutExtras(
            allLanguages = SongAllLanguages(
                style = SongTranslationSettings(lookAhead = SongTextStyle(color = "#DDDDDD")),
                firstLanguageOwnKeys = setOf("lookAhead.color", "lyrics.fontSize", "title.color"),
            ),
            textBoxes = mapOf("LOOK_AHEAD#1@LT" to box, "LYRICS" to TextBox(), "NEXT_SECTION" to box),
            elementShifts = mapOf("LOOK_AHEAD" to moved, "LYRICS@LT" to SongElementShift(x = 5)),
        ),
    )

    private fun settings(vararg profiles: OutputProfile) =
        AppSettings(projectionSettings = ProjectionSettings(outputProfiles = profiles.toList()))

    private fun AppSettings.profile(id: String) = projectionSettings.outputProfiles.first { it.id == id }

    @Test
    fun `a profile with look-ahead on draws its look-ahead style as its lyrics`() {
        val after = migrateLookAheadIntoLyrics(
            settings(OutputProfile(id = "screen", songLookAhead = true, songSettings = styled)),
        ).profile("screen").songSettings

        assertEquals("#FF0000", after.lyricsColor)
        assertEquals("Verdana", after.lyricsFontType)
        assertEquals(98, after.lyricsFontSize)
        assertFalse(after.lyricsFontSizeAutoFit)
        assertTrue(after.lyricsBold && after.lyricsItalic && after.lyricsUnderline && after.lyricsStrikethrough)
        assertTrue(after.lyricsShadow)
        assertEquals("#112233", after.lyricsShadowColor)
        assertEquals(150, after.lyricsShadowSize)
        assertEquals(40, after.lyricsShadowOpacity)
        assertEquals("Left", after.lyricsHorizontalAlignment)
        assertEquals(4, after.lyricsLetterSpacing)
        assertEquals(6, after.lyricsWordSpacing)
        assertEquals("Uppercase", after.lyricsTransform)
        assertEquals(TextBackdrop(lineBackground = true), after.lyricsBackdrop)
        assertEquals(TextOutline(enabled = true, width = 7), after.outlines.lyrics)

        assertEquals("#00FF00", after.lyricsLowerThirdColor)
        assertEquals("Georgia", after.lyricsLowerThirdFontType)
        assertEquals(33, after.lyricsLowerThirdFontSize)
        assertEquals(TextBackdrop(border = true), after.lyricsLowerThirdBackdrop)
        assertEquals(TextOutline(enabled = true, width = 3), after.outlines.lyricsLowerThird)
    }

    @Test
    fun `each language's look and the All layer move with it`() {
        val after = migrateLookAheadIntoLyrics(
            settings(OutputProfile(id = "screen", songLookAhead = true, songSettings = styled)),
        ).profile("screen").songSettings

        val language = after.translations.single()
        assertEquals("#BBBBBB", language.lyrics.color)
        assertEquals("#123456", language.lyrics.chordColor, "the chord colour is the lyrics' own")
        assertEquals("#CCCCCC", language.lyricsLowerThird.color)
        assertEquals("#DDDDDD", after.layoutExtras.allLanguages.style.lyrics.color)
        assertEquals(setOf("lyrics.color", "title.color"), after.layoutExtras.allLanguages.firstLanguageOwnKeys)
    }

    @Test
    fun `the look-ahead's boxes and moves become the lyrics'`() {
        val after = migrateLookAheadIntoLyrics(
            settings(OutputProfile(id = "screen", songLookAhead = true, songSettings = styled)),
        ).profile("screen").songSettings.layoutExtras

        assertEquals(mapOf("LYRICS#1@LT" to box, "NEXT_SECTION" to box), after.textBoxes)
        assertEquals(mapOf("LYRICS" to moved), after.elementShifts)
    }

    @Test
    fun `a profile with look-ahead off keeps its lyrics`() {
        val before = settings(OutputProfile(id = "screen", songSettings = styled))

        assertSame(before, migrateLookAheadIntoLyrics(before))
    }

    @Test
    fun `a linked profile keeps what changed on it and on its master as its own`() {
        val master = OutputProfile(id = "master", songLookAhead = true, songSettings = styled)
        val onFollower =
            OutputProfile(id = "follower", parentId = "master", songLookAhead = true, songSettings = styled)
        val offFollower = OutputProfile(id = "quiet", parentId = "master", songSettings = styled)

        val after = migrateLookAheadIntoLyrics(settings(master, onFollower, offFollower))

        assertTrue("songSettings.lyricsColor" in after.profile("follower").overrides)
        assertTrue(
            "songSettings.lyricsColor" in after.profile("quiet").overrides,
            "a follower with look-ahead off keeps its own lyrics instead of taking the master's new ones",
        )
        assertEquals("#FFFFFF", after.profile("quiet").songSettings.lyricsColor)
        assertTrue(after.profile("master").overrides.isEmpty())
    }

    @Test
    fun `a version-26 file is migrated on load`() {
        val raw = """
            {"settingsVersion":26,
             "projectionSettings":{"outputProfiles":[
               {"id":"screen","songLookAhead":true,"songSettings":{"lookAheadColor":"#FF0000"}}
             ]}}
        """.trimIndent()

        val after = SettingsManager().migrateAndDecode(raw)

        assertEquals(AppSettings.CURRENT_SETTINGS_VERSION, after.settingsVersion)
        assertEquals("#FF0000", after.profile("screen").songSettings.lyricsColor)
    }
}
