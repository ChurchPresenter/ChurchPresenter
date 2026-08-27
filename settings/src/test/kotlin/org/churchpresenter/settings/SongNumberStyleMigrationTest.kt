package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The song number is drawn from its own stored style now; it used to borrow the title's.
 *
 * Every settings file therefore has the number's fields sitting at their defaults, and a file whose
 * title was styled would have started showing a plain white number. [migrateSongNumberStyle] carries
 * the title across so nothing changes appearance.
 */
class SongNumberStyleMigrationTest {

    private val styledTitle = SongSettings(
        titleFontType = "Georgia",
        titleColor = "#FFD54F",
        titleBold = true,
        titleItalic = true,
        titleUnderline = true,
        titleShadow = true,
        titleShadowColor = "#101010",
        titleShadowSize = 70,
        titleShadowOpacity = 55,
    )

    @Test
    fun `a styled title is carried across to an untouched number`() {
        val migrated = styledTitle.migrateSongNumberStyle()

        assertEquals(
            "",
            migrated.songNumberFontType,
            "blank already means 'follow the title', so pinning a face here would stop it following",
        )
        assertEquals("#FFD54F", migrated.songNumberColor)
        assertEquals(true, migrated.songNumberBold)
        assertEquals(true, migrated.songNumberItalic)
        assertEquals(true, migrated.songNumberUnderline)
        assertEquals(true, migrated.songNumberShadow)
        assertEquals("#101010", migrated.songNumberShadowColor)
        assertEquals(70, migrated.songNumberShadowSize)
        assertEquals(55, migrated.songNumberShadowOpacity)
    }

    @Test
    fun `the lower third's title is carried across too`() {
        val migrated = SongSettings(
            titleLowerThirdColor = "#90CAF9",
            titleLowerThirdBold = true,
        ).migrateSongNumberStyle()

        assertEquals("#90CAF9", migrated.songNumberLowerThirdColor)
        assertEquals(true, migrated.songNumberLowerThirdBold)
    }

    @Test
    fun `a number already styled is left alone`() {
        val deliberate = styledTitle.copy(songNumberColor = "#00FF00")

        assertEquals("#00FF00", deliberate.migrateSongNumberStyle().songNumberColor)
    }

    @Test
    fun `it is idempotent`() {
        val once = styledTitle.migrateSongNumberStyle()

        assertEquals(once, once.migrateSongNumberStyle())
    }

    @Test
    fun `an untouched file stays at its defaults`() {
        assertEquals(SongSettings(), SongSettings().migrateSongNumberStyle())
    }

    @Test
    fun `the new typography fields survive a settings file`() {
        val styled = SongSettings(
            lyricsStrikethrough = true,
            lyricsLetterSpacing = 4,
            lyricsWordSpacing = 9,
            lyricsTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
            lowerThirdLookAheadNextHorizontalAlignment = Constants.LEFT,
        )

        val restored = Json { ignoreUnknownKeys = true }.decodeFromString(
            SongSettings.serializer(),
            Json { encodeDefaults = true }.encodeToString(SongSettings.serializer(), styled),
        )

        assertEquals(styled, restored)
    }

    @Test
    fun `a settings file written before the new fields existed still reads`() {
        val older = """{"lyricsFontSize":88}"""

        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(SongSettings.serializer(), older)

        assertEquals(88, decoded.lyricsFontSize)
        assertEquals(Constants.TEXT_TRANSFORM_NONE, decoded.lyricsTransform)
        assertEquals(0, decoded.lyricsLetterSpacing)
        assertEquals("", decoded.songNumberFontType)
    }
}
