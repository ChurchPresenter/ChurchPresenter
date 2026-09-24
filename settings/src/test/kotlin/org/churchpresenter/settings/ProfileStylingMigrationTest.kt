package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Versions 14 and 15: captions, the dictionary card, Q&A, video subtitles and Fit/Fill/Stretch
 * moving from one copy for the install onto every profile.
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
        assertEquals(emptyList(), decode("""{"settingsVersion":13,"projectionSettings":{"outputProfiles":[]}}""")
            .projectionSettings.outputProfiles)
        val bare = decode("""{"settingsVersion":13,"projectionSettings":{"outputProfiles":[{"id":"a"}]}}""")
            .projectionSettings.outputProfiles.single()
        assertEquals(STTSettings(), bare.sttSettings)
        assertEquals(OutputScaleMode.FIT, bare.pictureScaleMode)
    }
}
