package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.settings.utils.Constants
import kotlin.test.assertEquals

/**
 * Version 18: the section label becoming a song element, with a whole style per output where it had a
 * few flat fields shared by both, and a place on the lyrics where it had an X/Y offset (#656).
 *
 * What is worth pinning is what would be silent if wrong: the look a church had set has to survive
 * the move on both outputs, on the document and inside every profile; a field the old file left out
 * has to keep the old default rather than pick up the credits' face and size; and a document already
 * in the new shape is left alone.
 */
class SectionLabelMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-section-label-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    /**
     * Where the document's song settings end up: on the profile that carried none of its own, which
     * version 22 gives the document's copy before dropping it.
     */
    private fun AppSettings.documentSongs(): SongSettings =
        projectionSettings.outputProfiles.first { it.id == "default" }.songSettings

    /** A version-17 document with a styled, positioned label on the document and on one profile. */
    private fun v17(documentLabel: String) = """
        {"settingsVersion":17,
         "songSettings":{"layoutExtras":{"sectionLabel":$documentLabel}},
         "projectionSettings":{"outputProfiles":[
           {"id":"default","name":"Default"},
           {"id":"lobby","name":"Lobby","songSettings":{"layoutExtras":{"sectionLabel":
              {"enabled":true,"fontSize":60,"color":"#123456"}}}}
         ]}}
    """.trimIndent()

    private val styled = """
        {"enabled":true,"fontSize":44,"color":"#FFD54F","bold":true,"italic":true,"underline":true,
         "shadow":true,"fontType":"Georgia","horizontalAlignment":"Left",
         "outline":{"enabled":true,"width":4,"color":"#101820"},
         "offset":{"xPercent":10,"yPercent":20}}
    """.trimIndent()

    @Test
    fun `the label keeps the look it had, on full screen`() {
        val label = decode(v17(styled)).documentSongs().layoutExtras.sectionLabel

        assertEquals(true, label.enabled)
        with(label.fullScreen) {
            assertEquals(44, fontSize)
            assertEquals("#FFD54F", color)
            assertEquals(true, bold)
            assertEquals(true, italic)
            assertEquals(true, underline)
            assertEquals(true, shadow)
            assertEquals("Georgia", fontType)
            assertEquals(Constants.LEFT, horizontalAlignment)
            assertEquals(4, outline.width)
            assertEquals("#101820", outline.color)
        }
    }

    @Test
    fun `the lower third draws with the same look it always shared`() {
        val label = decode(v17(styled)).documentSongs().layoutExtras.sectionLabel

        assertEquals(label.fullScreen, label.lowerThird)
    }

    @Test
    fun `the offset gives way to the label sitting on the lyrics`() {
        val label = decode(v17(styled)).documentSongs().layoutExtras.sectionLabel

        assertEquals(Constants.ABOVE_LYRICS, label.position)
        assertEquals(Constants.ABOVE_LYRICS, label.lowerThirdPosition)
    }

    @Test
    fun `a field the old file left out keeps the old default, not the credits'`() {
        val label = decode(v17("""{"enabled":true}""")).documentSongs().layoutExtras.sectionLabel

        // Blank follows the title's face, which is what the label always drew in; 32 is its old size.
        assertEquals("", label.fullScreen.fontType)
        assertEquals(SongSectionLabel.DEFAULT_FONT_SIZE, label.fullScreen.fontSize)
        assertEquals("", label.lowerThird.fontType)
    }

    @Test
    fun `every profile is migrated from its own copy`() {
        val lobby = decode(v17(styled)).projectionSettings.outputProfiles.first { it.id == "lobby" }
        val label = lobby.songSettings.layoutExtras.sectionLabel

        assertEquals(true, label.enabled)
        assertEquals(60, label.fullScreen.fontSize, "the profile's own size, not the document's")
        assertEquals("#123456", label.lowerThird.color)
    }

    @Test
    fun `a document already in the new shape keeps its own`() {
        val current = """{"enabled":true,"fullScreen":{"fontSize":12},"lowerThird":{"fontSize":9},
            "position":"BelowVerse"}"""

        val label = decode(v17(current)).documentSongs().layoutExtras.sectionLabel

        assertEquals(12, label.fullScreen.fontSize)
        assertEquals(9, label.lowerThird.fontSize)
        assertEquals(Constants.BELOW_VERSE, label.position)
    }

    @Test
    fun `every position survives the load-time repair`() {
        // The repair runs on every load and used to put anything but the two edges back above the
        // verse, switched off -- which would have undone the two new places on every restart.
        SONG_ELEMENT_POSITIONS.forEach { position ->
            val song = SongSettings(titlePosition = position, songNumberPosition = position).migrateElementPositions()
            assertEquals(position, song.titlePosition)
            assertEquals(position, song.songNumberPosition)
            assertEquals(SongSettings().titleDisplay, song.titleDisplay)
        }
    }
}
