package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Version 23: a profile's flat `show*` switches become its look, grouped by layer, and a linked
 * profile's overrides of them follow them to their new paths.
 */
class OutputLookMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-output-look-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun profiles(json: String): List<OutputProfile> = SettingsManager().migrateAndDecode(
        """{"settingsVersion":22,"projectionSettings":{"outputProfiles":[$json]}}""",
    ).projectionSettings.outputProfiles

    @Test
    fun `every switch moves to its place in the look`() {
        val old = LEGACY_LOOK_PATHS.keys.joinToString(",") { "\"$it\":false" }
        val profile = profiles("""{"id":"p",$old}""").single()
        // Messages and props came after the switches, so no old file turns them off.
        assertEquals(OutputLook.NOTHING.copy(messages = true, props = true), profile.look)
    }

    @Test
    fun `a switch never stored stays drawn, and one stored on stays on`() {
        val profile = profiles("""{"id":"p","showQA":false,"showMedia":true}""").single()
        assertFalse(profile.look.slide.qa)
        assertTrue(profile.look.media.video)
        assertEquals(OutputLook(slide = SlideLook(qa = false)), profile.look)
    }

    @Test
    fun `a follower keeps its own switches, under their new paths`() {
        val (main, youth) = profiles(
            """
            {"id":"main","showPictures":false,"showSTT":false},
            {"id":"youth","parentId":"main","overrides":["showSTT","bibleSettings.fadeIn"],"showSTT":true}
            """,
        )
        assertEquals(setOf("look.captions", "bibleSettings.fadeIn"), youth.overrides)
        assertTrue(youth.look.captions, "its own")
        assertFalse(youth.look.media.pictures, "its master's")
        assertFalse(main.look.captions)
    }

    @Test
    fun `a profile with nothing to move is handed back as it is`() {
        val overrides = JsonArray(listOf(JsonPrimitive("x")))
        val profile = JsonObject(mapOf("id" to JsonPrimitive("p"), "overrides" to overrides))
        assertSame(profile, moveShowSwitchesIntoLook(profile))
        val bare = JsonObject(mapOf("id" to JsonPrimitive("p")))
        assertSame(bare, moveShowSwitchesIntoLook(bare))
    }

    @Test
    fun `an override alone is renamed even with no switch stored`() {
        val profile = JsonObject(mapOf("overrides" to JsonArray(listOf(JsonPrimitive("showCanvas"), JsonPrimitive(1)))))
        val moved = moveShowSwitchesIntoLook(profile)
        assertEquals(JsonArray(listOf(JsonPrimitive("look.slide.canvas"), JsonPrimitive(1))), moved["overrides"])
    }

    @Test
    fun `a look already stored keeps what the switches do not name`() {
        val profile = profiles("""{"id":"p","look":{"captions":false},"showWebsite":false}""").single()
        assertFalse(profile.look.captions)
        assertFalse(profile.look.slide.web)
    }

    @Test
    fun `the blank profile draws nothing`() {
        assertEquals(OutputLook.NOTHING, BLANK_OUTPUT_PROFILE.look)
    }

    @Test
    fun `each layer is edited on its own`() {
        val look = OutputProfile().withLook {
            withBackground { copy(songs = false) }
                .withMedia { copy(subtitles = false) }
                .withSlide { copy(dictionary = false) }
        }.look
        assertEquals(
            OutputLook(
                background = BackgroundLook(songs = false),
                media = MediaLook(subtitles = false),
                slide = SlideLook(dictionary = false),
            ),
            look,
        )
    }
}
