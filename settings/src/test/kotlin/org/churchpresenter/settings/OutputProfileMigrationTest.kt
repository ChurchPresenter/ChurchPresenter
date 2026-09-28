package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.churchpresenter.settings.utils.Constants

/**
 * Version 12: every output's own settings becoming a profile of its own.
 *
 * The promise is that no output changes on upgrade, so each test here states it the one way that
 * means anything: what the output renders with after the migration equals what it rendered with
 * before -- the repaired document with that output's sparse override written over it, which is what
 * the old per-output resolution did.
 */
/** How many profiles to create while checking none of them is given Blank's reserved id. */
private const val BLANK_ID_PROBE = 12

class OutputProfileMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-output-profile-migration-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    private fun AppSettings.rendered(output: Int): AppSettings {
        val proj = projectionSettings
        return resolvedFor(requireNotNull(proj.profileFor(proj.screenAssignments[output])))
    }

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    /** A version-11 document: sparse overrides on the screens, no profiles yet. */
    private fun v11(vararg screens: String, song: String = "{}", background: String = "{}") = """
        {"settingsVersion":11,
         "songSettings":$song,
         "backgroundSettings":$background,
         "dictionarySettings":{"wordColor":"#111111"},
         "projectionSettings":{"screenAssignments":[${screens.joinToString(",")}]}}
    """.trimIndent()

    @Test
    fun `a screen's own song background survives, and only that surface stops following the document`() {
        val override = """{"songBackground":{"backgroundColor":"#FF0000"}}"""
        val settings = decode(
            v11(
                """{"backgroundOverride":$override}""",
                background = """{"bibleBackground":{"backgroundColor":"#00FF00"}}""",
            ),
        )
        val profile = settings.projectionSettings.outputProfiles.single()

        assertEquals(setOf(BackgroundSurface.SONG.name), profile.backgroundOverrides)
        assertEquals(
            withSparseOverride(settings.backgroundSettings, obj(override), BackgroundSettings.serializer()),
            settings.rendered(0).backgroundSettings,
        )
        val edited = settings.copy(
            backgroundSettings = settings.backgroundSettings.copy(
                bibleBackground = BackgroundConfig(backgroundColor = "#0000FF"),
            ),
        )
        assertEquals("#0000FF", edited.rendered(0).backgroundSettings.bibleBackground.backgroundColor)
    }

    @Test
    fun `a screen's own default backgrounds mark the two default surfaces`() {
        val settings = decode(
            v11(
                """{"backgroundOverride":""" +
                    """{"defaultBackgroundColor":"#123456","defaultLowerThirdAboveBandType":"Color"}}""",
            ),
        )
        val profile = settings.projectionSettings.outputProfiles.single()

        assertEquals(
            setOf(BackgroundSurface.DEFAULT.name, BackgroundSurface.DEFAULT_LOWER_THIRD.name),
            profile.backgroundOverrides,
        )
        assertEquals("#123456", settings.rendered(0).backgroundSettings.defaultBackgroundColor)
        assertEquals("Color", settings.rendered(0).backgroundSettings.defaultLowerThirdAboveBandType)
    }

    @Test
    fun `every stored background field belongs to exactly one surface`() {
        val json = Json { encodeDefaults = true }
        val stored = obj(json.encodeToString(BackgroundSettings.serializer(), BackgroundSettings()))
        for (key in stored.keys) {
            assertEquals(1, BackgroundSurface.entries.count { key in it.fieldKeys }, key)
        }
    }

    @Test
    fun `a screen's own dictionary look survives and the document's is left alone`() {
        val settings = decode(v11("""{"dictionaryOverride":{"wordColor":"#ABCDEF"}}""", "{}"))

        assertEquals("#ABCDEF", settings.rendered(0).dictionarySettings.wordColor)
        assertEquals("#111111", settings.rendered(1).dictionarySettings.wordColor)
        assertEquals("#111111", settings.dictionarySettings.wordColor)
    }

    @Test
    fun `a title stored at the old middle position stays off every screen, as it drew`() {
        val settings = decode(v11("{}", song = """{"titlePosition":"Middle","titleLowerThirdPosition":"Middle"}"""))
        val song = settings.rendered(0).songSettings

        assertEquals(Constants.ABOVE_VERSE, song.titlePosition)
        assertEquals(Constants.NONE, song.titleDisplay)
        assertEquals(Constants.NONE, song.titleLowerThirdDisplay)
    }

    @Test
    fun `a number that inherited a styled title keeps that look on the screen and on the title slide`() {
        val settings = decode(v11("{}", song = """{"titleColor":"#FFD700","titleBold":true}"""))
        val song = settings.rendered(0).songSettings

        assertEquals("#FFD700", song.songNumberColor)
        assertTrue(song.songNumberBold)
        assertEquals("#FFD700", song.layoutExtras.titleSlideNumber.fullScreen.color)
    }

    @Test
    fun `a screen's own song styling is written over the repaired document`() {
        val override = """{"lyricsColor":"#00FF00"}"""
        val settings = decode(v11("""{"songOverride":$override}""", song = """{"titlePosition":"Middle"}"""))

        assertEquals(
            styleTreeOf(
                withSparseOverride(settings.songSettings, obj(override), SongSettings.serializer()),
                SongSettings.serializer(),
                SONG_GLOBAL_KEYS,
            ),
            styleTreeOf(settings.rendered(0).songSettings, SongSettings.serializer(), SONG_GLOBAL_KEYS),
        )
    }

    @Test
    fun `an untouched screen draws exactly the document`() {
        val settings = decode(v11("{}", song = """{"titleColor":"#FFD700","titlePosition":"Middle"}"""))
        val rendered = settings.rendered(0)

        assertEquals(settings.songSettings, rendered.songSettings)
        assertEquals(settings.bibleSettings, rendered.bibleSettings)
        assertEquals(settings.backgroundSettings, rendered.backgroundSettings)
        assertEquals(settings.stageMonitorSettings, rendered.stageMonitorSettings)
        assertEquals(settings.dictionarySettings, rendered.dictionarySettings)
    }

    @Test
    fun `the old vertical band mode keeps stacking`() {
        val settings = decode(v11("""{"displayMode":"${Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL}"}"""))

        assertTrue(settings.projectionSettings.outputProfiles.single().isLowerThirdVertical)
    }

    @Test
    fun `a released version-9 document with a whole snapshot override draws what it drew`() {
        val raw = """
            {"settingsVersion":9,
             "backgroundSettings":{"songBackground":{"backgroundColor":"#000000"},"defaultBackgroundColor":"#000000"},
             "projectionSettings":{"screenAssignments":[
                {"backgroundOverride":{"songBackground":{"backgroundColor":"#FF0000"},"defaultBackgroundColor":"#000000"}}
             ]}}
        """.trimIndent()
        val settings = decode(raw)

        val profile = settings.projectionSettings.outputProfiles.single()
        assertEquals(setOf(BackgroundSurface.SONG.name), profile.backgroundOverrides)
        assertEquals("#FF0000", settings.rendered(0).backgroundSettings.songBackground.backgroundColor)
    }

    /** A version-11 document with [count] translations stacked and the given arrangements. */
    private fun bibleDoc(
        vararg screens: String,
        count: Int = 4,
        fullScreen: String = Constants.BILINGUAL_TOP_BOTTOM,
        band: String = Constants.BILINGUAL_SIDE_BY_SIDE,
    ): String {
        val stack = (1..count).joinToString(",") { """{"fileName":"b$it.spb"}""" }
        return """
            {"settingsVersion":11,
             "bibleSettings":{"translations":[$stack],"bilingualLayout":"$fullScreen","bilingualLayoutLowerThird":"$band"},
             "projectionSettings":{"screenAssignments":[${screens.joinToString(",")}]}}
        """.trimIndent()
    }

    private val lowerThird = """{"displayMode":"${Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL}"}"""

    private fun AppSettings.profileAt(output: Int) =
        requireNotNull(projectionSettings.profileFor(projectionSettings.screenAssignments[output]))

    @Test
    fun `a full-screen grid becomes the stack it drew, on the document and every profile`() {
        val settings = decode(
            bibleDoc("{}", fullScreen = Constants.BILINGUAL_GRID_2X2, band = Constants.BILINGUAL_GRID_2X2),
        )

        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, settings.bibleSettings.bilingualLayout)
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, settings.rendered(0).bibleSettings.bilingualLayout)
        assertEquals(Constants.BILINGUAL_GRID_2X2, settings.rendered(0).bibleSettings.bilingualLayoutLowerThird)
    }

    @Test
    fun `side by side on the full screen is left as it is`() {
        val settings = decode(bibleDoc("{}", fullScreen = Constants.BILINGUAL_SIDE_BY_SIDE))

        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, settings.rendered(0).bibleSettings.bilingualLayout)
    }

    @Test
    fun `a two-cell band keeps showing only the two translations it drew`() {
        val settings = decode(bibleDoc(lowerThird))

        assertEquals(listOf(0, 1), settings.profileAt(0).bibleTranslations)
    }

    @Test
    fun `a band grid keeps showing as many translations as it has cells`() {
        val settings = decode(bibleDoc(lowerThird, band = Constants.BILINGUAL_GRID_1X3))

        assertEquals(listOf(0, 1, 2), settings.profileAt(0).bibleTranslations)
    }

    @Test
    fun `a band with room for every translation keeps following the stack`() {
        val settings = decode(bibleDoc(lowerThird, band = Constants.BILINGUAL_GRID_2X2))

        assertEquals(emptyList(), settings.profileAt(0).bibleTranslations)
    }

    @Test
    fun `a band narrowed to some translations keeps the first ones of those`() {
        val narrowed = """{"displayMode":"${Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL}",""" +
            """"bibleTranslations":[1,2,3]}"""
        val settings = decode(bibleDoc(narrowed))

        assertEquals(listOf(1, 2), settings.profileAt(0).bibleTranslations)
    }

    @Test
    fun `full screens and two-translation stacks are not narrowed`() {
        assertEquals(emptyList(), decode(bibleDoc("{}")).profileAt(0).bibleTranslations)
        assertEquals(emptyList(), decode(bibleDoc(lowerThird, count = 2)).profileAt(0).bibleTranslations)
    }

    // ── Every output follows a profile ──────────────────────────────────────────

    @Test
    fun `a document with no outputs is given the factory profile rather than none`() {
        val proj = decode(v11()).projectionSettings

        assertEquals(listOf(DEFAULT_OUTPUT_PROFILE_ID), proj.outputProfiles.map { it.id })
        assertEquals(DEFAULT_OUTPUT_PROFILE_ID, proj.fallbackProfileId)
    }

    @Test
    fun `an output saved with no profile or a missing one is repointed at the first profile on load`() {
        val raw = """
            {"settingsVersion":${AppSettings.CURRENT_SETTINGS_VERSION},
             "projectionSettings":{
               "outputProfiles":[{"id":"profile1","name":"A"},{"id":"profile2","name":"B"}],
               "screenAssignments":[{"activeProfileId":"profile2"},{"activeProfileId":null},{}],
               "browserSourceOutputs":[{"activeProfileId":"gone"}],
               "ndiOutputs":[{}]}}
        """.trimIndent()

        val proj = decode(raw).projectionSettings

        assertEquals(
            listOf("profile2", "profile1", "profile1"), proj.screenAssignments.map { it.activeProfileId },
            "a valid reference is left alone; a null one follows the first profile",
        )
        assertEquals(listOf("profile1"), proj.browserSourceOutputs.map { it.activeProfileId })
        assertEquals(listOf("profile1"), proj.ndiOutputs.map { it.activeProfileId })
    }

    @Test
    fun `a current document with its profiles emptied is given the factory one and its outputs follow it`() {
        val raw = """
            {"settingsVersion":${AppSettings.CURRENT_SETTINGS_VERSION},
             "projectionSettings":{"outputProfiles":[],"screenAssignments":[{"activeProfileId":"profile1"}]}}
        """.trimIndent()

        val proj = decode(raw).projectionSettings

        assertEquals(listOf(DEFAULT_OUTPUT_PROFILE_ID), proj.outputProfiles.map { it.id })
        assertEquals(DEFAULT_OUTPUT_PROFILE_ID, proj.screenAssignments.single().activeProfileId)
    }

    @Test
    fun `an output deliberately on Blank keeps it through the load repair`() {
        val raw = """
            {"settingsVersion":${AppSettings.CURRENT_SETTINGS_VERSION},
             "projectionSettings":{
               "outputProfiles":[{"id":"profile1","name":"A"}],
               "screenAssignments":[{"activeProfileId":"$BLANK_OUTPUT_PROFILE_ID"}],
               "browserSourceOutputs":[{"activeProfileId":"$BLANK_OUTPUT_PROFILE_ID"}],
               "ndiOutputs":[{"activeProfileId":"$BLANK_OUTPUT_PROFILE_ID"}]}}
        """.trimIndent()

        val proj = decode(raw).projectionSettings

        assertEquals(BLANK_OUTPUT_PROFILE_ID, proj.screenAssignments.single().activeProfileId)
        assertEquals(BLANK_OUTPUT_PROFILE_ID, proj.browserSourceOutputs.single().activeProfileId)
        assertEquals(BLANK_OUTPUT_PROFILE_ID, proj.ndiOutputs.single().activeProfileId)
        assertEquals(listOf("profile1"), proj.outputProfiles.map { it.id }, "Blank is never stored as a profile")
    }

    @Test
    fun `Blank resolves to the built-in profile that shows nothing, backgrounds included`() {
        val proj = ProjectionSettings()
        val profile = requireNotNull(proj.profileFor(ScreenAssignment(activeProfileId = BLANK_OUTPUT_PROFILE_ID)))

        assertSame(BLANK_OUTPUT_PROFILE, profile)
        assertFalse(profile.showBible)
        assertFalse(profile.showSongs)
        assertFalse(profile.showSubtitles)
        assertFalse(profile.showFullscreenBackground)
        assertFalse(profile.showLowerThirdBackground)
        assertFalse(profile.showBibleBackground)
        assertFalse(profile.showSongsBackground)
    }

    @Test
    fun `a new profile never takes the id Blank has reserved`() {
        val profiles = (1..BLANK_ID_PROBE).fold(listOf<OutputProfile>()) { acc, _ -> acc + newOutputProfile(acc) }
        assertTrue(profiles.none { it.id == BLANK_OUTPUT_PROFILE_ID })
    }
}
