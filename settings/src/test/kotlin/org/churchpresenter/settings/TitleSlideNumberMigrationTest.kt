package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.settings.utils.Constants
import kotlin.test.assertEquals

/**
 * Version 16: the title slide's song number getting a style of its own, where it shared the lyric
 * slides' one.
 *
 * The split is what #609 asked for -- the number small in a corner of every lyric slide and large at
 * the bottom of the title slide -- but the new record's defaults are the *stock* number's, not
 * whatever a church had configured. Without the seed, everyone who had ever restyled their number
 * would open this build to a title slide drawing stock styling, with nothing in settings to blame.
 *
 * The seed has three things worth pinning, each of which would be silent if wrong: it has to reach
 * inside **every profile** as well as the document, because a profile carries a whole `SongSettings`;
 * it has to pick the outline out of `outlines` rather than from beside the flat fields; and it has to
 * leave a document that already has the record alone.
 */
class TitleSlideNumberMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-title-slide-number-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    /** A version-15 document whose song number is styled, on the document and on one profile. */
    private fun v15(titleSlideNumberJson: String = "") = """
        {"settingsVersion":15,
         "songSettings":{
           "songNumberColor":"#FF0000","songNumberFontSize":48,"songNumberBold":true,
           "songNumberHorizontalAlignment":"Left",
           "songNumberLowerThirdColor":"#00FF00","songNumberLowerThirdFontSize":22,
           "outlines":{"songNumber":{"width":4,"color":"#0000FF"}}
           ${if (titleSlideNumberJson.isBlank()) "" else ",\"layoutExtras\":$titleSlideNumberJson"}
         },
         "projectionSettings":{"outputProfiles":[
           {"id":"default","name":"Default"},
           {"id":"lobby","name":"Lobby","songSettings":{
              "songNumberColor":"#123456","songNumberFontSize":90,
              "outlines":{"songNumber":{"width":2,"color":"#ABCDEF"}}
           }}
         ]}}
    """.trimIndent()

    @Test
    fun `the document's title slide number is seeded from the number it was drawing`() {
        val number = decode(v15()).songSettings.layoutExtras.titleSlideNumber

        assertEquals("#FF0000", number.fullScreen.color)
        assertEquals(48, number.fullScreen.fontSize)
        assertEquals(true, number.fullScreen.bold)
        assertEquals(Constants.LEFT, number.fullScreen.horizontalAlignment)
    }

    @Test
    fun `the lower third half is seeded from the lower third's own fields`() {
        val number = decode(v15()).songSettings.layoutExtras.titleSlideNumber

        assertEquals("#00FF00", number.lowerThird.color)
        assertEquals(22, number.lowerThird.fontSize)
    }

    @Test
    fun `the stroke comes from outlines rather than from beside the flat fields`() {
        val number = decode(v15()).songSettings.layoutExtras.titleSlideNumber

        // The one field of the seventeen that is not a `songNumber*` sibling. Missing it would lose
        // every upgraded install's number stroke, and nothing else would look wrong.
        assertEquals(4, number.fullScreen.outline.width)
        assertEquals("#0000FF", number.fullScreen.outline.color)
    }

    @Test
    fun `every profile is seeded from its own copy, not from the document`() {
        val profiles = decode(v15()).projectionSettings.outputProfiles
        val lobby = profiles.first { it.id == "lobby" }.songSettings.layoutExtras.titleSlideNumber

        assertEquals("#123456", lobby.fullScreen.color, "the profile's own number, not the document's")
        assertEquals(90, lobby.fullScreen.fontSize)
        assertEquals("#ABCDEF", lobby.fullScreen.outline.color)
    }

    @Test
    fun `a profile that carried no song settings of its own is left with the defaults`() {
        val profiles = decode(v15()).projectionSettings.outputProfiles
        val default = profiles.first { it.id == "default" }.songSettings.layoutExtras.titleSlideNumber

        // Nothing to seed from, so the record's own defaults stand -- which are the stock number's.
        assertEquals(SongTitleSlideNumber().fullScreen.color, default.fullScreen.color)
    }

    @Test
    fun `the number stays in the flow, because that is where the title slide always drew it`() {
        val number = decode(v15()).songSettings.layoutExtras.titleSlideNumber

        assertEquals(Constants.NONE, number.corner)
        assertEquals(Constants.NONE, number.lowerThirdCorner)
        assertEquals(SongNumberOffset(), number.offset)
    }

    @Test
    fun `a document that already carries the record keeps its own`() {
        val existing = """{"titleSlideNumber":{"fullScreen":{"color":"#AAAAAA","fontSize":12}}}"""

        val number = decode(v15(existing)).songSettings.layoutExtras.titleSlideNumber

        // Written by a newer build, opened by an older one, rolled forward: the seed must not
        // overwrite what the newer build stored.
        assertEquals("#AAAAAA", number.fullScreen.color)
        assertEquals(12, number.fullScreen.fontSize)
    }

    @Test
    fun `a fresh install draws the title slide number exactly as the lyric slides draw theirs`() {
        val stock = SongSettings()
        val titleSlide = stock.layoutExtras.titleSlideNumber

        // The two are separate settings now, so nothing keeps their defaults in step but this.
        assertEquals(stock.songNumberFontSize, titleSlide.fullScreen.fontSize)
        assertEquals(stock.songNumberLowerThirdFontSize, titleSlide.lowerThird.fontSize)
        assertEquals(stock.songNumberHorizontalAlignment, titleSlide.fullScreen.horizontalAlignment)
    }
}
