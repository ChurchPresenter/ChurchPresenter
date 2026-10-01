package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Versions 14 and 15: captions, the dictionary card, Q&A, video subtitles and Fit/Fill/Stretch
 * moving from one copy for the install onto every profile. Version 21: the document's copies of the
 * first four going, and staying gone on every save.
 *
 * The profile fields are new, so without these steps an existing file decodes cleanly and every
 * profile silently takes the class defaults -- a church with yellow captions and a filled picture
 * opening the new build to white captions and a letterboxed one, with nothing in settings to blame.
 */
class ProfileStylingMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-profile-styling-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    /** A version-13 document: the looks on the document, two profiles carrying none of them. */
    private fun v13(extraProfileJson: String = "") = """
        {"settingsVersion":13,
         "sttSettings":{"serverUrl":"http://stt.local","textColor":"#FFFF00","maxSegments":8},
         "qaSettings":{"textColor":"#00FF00","rateLimitCooldownSeconds":45},
         "dictionarySettings":{"wordColor":"#FF00FF"},
         "mediaSettings":{"textColor":"#00FFFF","maxLines":4},
         "pictureSettings":{"scaleMode":"FILL"},
         "mediaScaleMode":"STRETCH",
         "projectionSettings":{"outputProfiles":[
            {"id":"a","name":"A"},
            {"id":"b","name":"B"$extraProfileJson}
         ]}}
    """.trimIndent()

    @Test
    fun `every profile is given the document's caption, Q&A, dictionary and subtitle look`() {
        val settings = decode(v13())

        for (profile in settings.projectionSettings.outputProfiles) {
            assertEquals("#FFFF00", profile.sttSettings.textColor, "${profile.id}: captions")
            assertEquals(8, profile.sttSettings.maxSegments, "${profile.id}: caption segments")
            assertEquals("#00FF00", profile.qaSettings.textColor, "${profile.id}: Q&A")
            assertEquals("#FF00FF", profile.dictionarySettings.wordColor, "${profile.id}: dictionary")
            assertEquals("#00FFFF", profile.mediaSettings.textColor, "${profile.id}: subtitles")
            assertEquals(4, profile.mediaSettings.maxLines, "${profile.id}: subtitle lines")
        }
    }

    @Test
    fun `every profile is given the document's picture and media scaling`() {
        val settings = decode(v13())

        for (profile in settings.projectionSettings.outputProfiles) {
            assertEquals(OutputScaleMode.FILL, profile.pictureScaleMode, "${profile.id}: pictures")
            assertEquals(OutputScaleMode.STRETCH, profile.mediaScaleMode, "${profile.id}: media")
        }
    }

    @Test
    fun `an output draws exactly what it drew before the move`() {
        val settings = decode(v13())
        val rendered = settings.resolvedFor(settings.projectionSettings.outputProfiles.first())

        assertEquals("#FFFF00", rendered.sttSettings.textColor)
        assertEquals(OutputScaleMode.FILL, rendered.pictureSettings.scaleMode)
        assertEquals(OutputScaleMode.STRETCH, rendered.mediaScaleMode)
    }

    @Test
    fun `a profile that already carries its own look keeps it`() {
        // Written by a build that had this, opened by one that briefly did not, rolled forward again.
        val settings = decode(v13(""","sttSettings":{"textColor":"#123456"},"mediaScaleMode":"FIT""""))
        val b = settings.projectionSettings.outputProfiles.first { it.id == "b" }

        assertEquals("#123456", b.sttSettings.textColor, "its own captions")
        assertEquals(OutputScaleMode.FIT, b.mediaScaleMode, "its own media scaling")
        assertEquals("#00FF00", b.qaSettings.textColor, "and the document's for what it lacked")
    }

    @Test
    fun `the install-wide parts stay on the document`() {
        val settings = decode(v13())

        assertEquals("http://stt.local", settings.sttSettings.serverUrl)
        assertEquals(45, settings.qaSettings.rateLimitCooldownSeconds)
    }

    @Test
    fun `a version-14 file only gains the scaling`() {
        val raw = """
            {"settingsVersion":14,
             "sttSettings":{"textColor":"#FFFF00"},
             "pictureSettings":{"scaleMode":"STRETCH"},
             "projectionSettings":{"outputProfiles":[{"id":"a","sttSettings":{"textColor":"#ABCDEF"}}]}}
        """.trimIndent()
        val a = decode(raw).projectionSettings.outputProfiles.single()

        assertEquals("#ABCDEF", a.sttSettings.textColor, "version 14 already ran; its captions stand")
        assertEquals(OutputScaleMode.STRETCH, a.pictureScaleMode)
    }

    @Test
    fun `a document with no profiles or no looks is left alone`() {
        // Nothing to seed; the load's own repair then gives the install its factory profile.
        assertEquals(
            listOf(DEFAULT_OUTPUT_PROFILE_ID),
            decode("""{"settingsVersion":13,"projectionSettings":{"outputProfiles":[]}}""")
                .projectionSettings.outputProfiles.map { it.id },
        )
        val bare = decode("""{"settingsVersion":13,"projectionSettings":{"outputProfiles":[{"id":"a"}]}}""")
            .projectionSettings.outputProfiles.single()
        assertEquals(STTSettings(), bare.sttSettings)
        assertEquals(OutputScaleMode.FIT, bare.pictureScaleMode)
    }

    /** A version-20 document: the profile carries its own looks, the document a drifted copy. */
    private val v20 = """
        {"settingsVersion":20,
         "sttSettings":{"serverUrl":"http://stt.local","lastConnectedUrl":"http://stt.local","fontSize":80},
         "qaSettings":{"textColor":"#00FF00","rateLimitCooldownSeconds":45,"votingEnabled":false,"qrCodeMessage":"Hi"},
         "dictionarySettings":{"wordColor":"#FF00FF"},
         "mediaSettings":{"textColor":"#00FFFF"},
         "projectionSettings":{"outputProfiles":[
            {"id":"a","sttSettings":{"fontSize":42},"qaSettings":{"textColor":"#0000FF"},
             "dictionarySettings":{"wordColor":"#111111"},"mediaSettings":{"textColor":"#222222"}},
            {"id":"b"}
         ]}}
    """.trimIndent()

    private fun root(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `version 21 leaves the document only its install-wide keys`() {
        val stripped = root(SettingsManager().stripProfileOwnedStyling(v20))

        assertEquals(STT_GLOBAL_KEYS, stripped.getValue("sttSettings").jsonObject.keys)
        assertEquals(QA_GLOBAL_KEYS, stripped.getValue("qaSettings").jsonObject.keys)
        assertFalse("dictionarySettings" in stripped)
        assertFalse("mediaSettings" in stripped)
        assertEquals(root(v20).getValue("projectionSettings"), stripped.getValue("projectionSettings"))
    }

    @Test
    fun `version 21 gives a profile without a look the document's before dropping it`() {
        val settings = decode(v20)
        val (a, b) = settings.projectionSettings.outputProfiles

        assertEquals(42, a.sttSettings.fontSize, "a keeps its own")
        assertEquals("#111111", a.dictionarySettings.wordColor)
        assertEquals(80, b.sttSettings.fontSize, "b had none, so it takes the document's")
        assertEquals("#FF00FF", b.dictionarySettings.wordColor)
        assertEquals("#00FFFF", b.mediaSettings.textColor)
        assertEquals("#00FF00", b.qaSettings.textColor)
    }

    @Test
    fun `version 21 keeps the install-wide keys and what the outputs draw`() {
        val settings = decode(v20)
        val rendered = settings.resolvedFor(settings.projectionSettings.outputProfiles.first())

        assertEquals("http://stt.local", settings.sttSettings.serverUrl)
        assertEquals("http://stt.local", settings.sttSettings.lastConnectedUrl)
        assertEquals(45, settings.qaSettings.rateLimitCooldownSeconds)
        assertEquals("Hi", settings.qaSettings.qrCodeMessage)
        assertEquals(42, rendered.sttSettings.fontSize)
        assertEquals("http://stt.local", rendered.sttSettings.serverUrl)
        assertEquals("#0000FF", rendered.qaSettings.textColor)
    }

    @Test
    fun `a save never writes the document-level looks back`() {
        val manager = SettingsManager()
        val settings = manager.migrateAndDecode(v20)
        manager.saveSettings(settings)

        val saved = root(File(home, ".churchpresenter/settings.json").readText())
        assertEquals(STT_GLOBAL_KEYS, saved.getValue("sttSettings").jsonObject.keys)
        assertEquals(QA_GLOBAL_KEYS, saved.getValue("qaSettings").jsonObject.keys)
        assertFalse("dictionarySettings" in saved)
        assertFalse("mediaSettings" in saved)
        val reloaded = SettingsManager().loadSettings()
        val profile = reloaded.projectionSettings.outputProfiles.first()
        assertEquals(settings.resolvedFor(profile).sttSettings, reloaded.resolvedFor(profile).sttSettings)
        assertEquals(settings.projectionSettings.outputProfiles, reloaded.projectionSettings.outputProfiles)
    }
}
