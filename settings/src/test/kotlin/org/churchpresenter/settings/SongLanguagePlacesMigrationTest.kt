package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
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
 * Version 26: a profile's song languages became places in the display order (#1029). An install
 * that had already reordered its languages must draw exactly what it drew before -- the same
 * languages, each in its own look.
 */
class SongLanguagePlacesMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-song-language-places-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun settings(order: List<Int>, vararg profiles: OutputProfile) = AppSettings(
        songSettings = SongSettings(languageOrder = order),
        projectionSettings = ProjectionSettings(outputProfiles = profiles.toList()),
    )

    private fun AppSettings.profile(id: String) = projectionSettings.outputProfiles.first { it.id == id }

    @Test
    fun `an order never moved changes nothing`() {
        val before = settings(emptyList(), OutputProfile(id = "screen", songTranslations = listOf(1)))

        assertSame(before, migrateSongLanguagesToPlaces(before))
    }

    @Test
    fun `each profile's languages become the places they hold now`() {
        val before = settings(
            listOf(1, 0, 2, 3),
            OutputProfile(id = "list", songTranslations = listOf(0, 2)),
            OutputProfile(id = "mode", songMode = Constants.SONG_LANG_PRIMARY),
        )

        val after = migrateSongLanguagesToPlaces(before)

        assertEquals(listOf(1, 2), after.profile("list").songTranslations)
        assertEquals(listOf(1), after.profile("mode").songTranslations)
        assertEquals(Constants.SONG_LANG_BOTH, after.profile("mode").songMode)
        listOf("list", "mode").forEach { id ->
            val profile = after.profile(id)
            assertEquals(
                profile.songTranslations.map { place -> before.songSettings.languageDisplayOrder()[place] },
                if (id == "list") listOf(0, 2) else listOf(0),
                "$id draws the languages it drew",
            )
            assertFalse(profile.songSettings.layoutExtras.languageLooksFollowPlace, "$id keeps each language's look")
        }
    }

    @Test
    fun `a profile showing every language, or none, keeps its selection`() {
        val all = OutputProfile(id = "all", songMode = Constants.SONG_LANG_BOTH)
        val off = OutputProfile(id = "off", songMode = Constants.SONG_LANG_OFF, songTranslations = listOf(0))

        val after = migrateSongLanguagesToPlaces(settings(listOf(1, 0, 2, 3), all, off))

        assertEquals(emptyList(), after.profile("all").songTranslations)
        assertEquals(Constants.SONG_LANG_BOTH, after.profile("all").songMode)
        assertEquals(listOf(0), after.profile("off").songTranslations)
        assertEquals(Constants.SONG_LANG_OFF, after.profile("off").songMode)
    }

    @Test
    fun `a version-25 file is migrated on load`() {
        val raw = """
            {"settingsVersion":25,
             "songSettings":{"languageOrder":[1,0]},
             "projectionSettings":{"outputProfiles":[
               {"id":"screen","name":"Screen","songTranslations":[0]}
             ]}}
        """.trimIndent()

        val loaded = SettingsManager().migrateAndDecode(raw)

        val screen = loaded.profile("screen")
        assertEquals(listOf(1), screen.songTranslations)
        assertEquals(listOf(0), screen.songLanguages(2, loaded.songSettings.languageDisplayOrder()), "still English")
        assertFalse(screen.songSettings.layoutExtras.languageLooksFollowPlace)
    }

    @Test
    fun `a new profile's looks follow the place`() {
        assertTrue(SongLayoutExtras().languageLooksFollowPlace)
    }
}
