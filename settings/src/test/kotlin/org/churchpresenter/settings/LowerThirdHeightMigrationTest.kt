package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The band height moving off [ProjectionSettings] and onto the two content types that draw a band.
 *
 * The field was *removed*, not renamed away from a default, so without this step an existing file
 * decodes cleanly (`ignoreUnknownKeys`) and silently loses the operator's number — and the next save
 * writes it back without it. That is the failure this whole test class exists to make impossible: a
 * church that set 45 opening the new build to find a 33% band and nothing in settings to blame.
 */
class LowerThirdHeightMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-lower-third-height-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    /** A document from before the move: the height on the projection settings, nowhere else. */
    private fun legacy(height: Int) =
        """{"settingsVersion":7,"projectionSettings":{"lowerThirdHeightPercent":$height}}"""

    @Test
    fun `the old global lands in both new homes`() {
        val settings = decode(legacy(45))

        assertEquals(45, settings.bibleSettings.lowerThirdHeightPercent)
        assertEquals(45, settings.songSettings.lowerThirdHeightPercent)
    }

    @Test
    fun `a file that never set one gets the default in both`() {
        val settings = decode("""{"settingsVersion":7,"projectionSettings":{"windowTop":40}}""")

        assertEquals(33, settings.bibleSettings.lowerThirdHeightPercent)
        assertEquals(33, settings.songSettings.lowerThirdHeightPercent)
    }

    @Test
    fun `a file with no projection settings at all is left alone`() {
        val settings = decode("""{"settingsVersion":7}""")

        assertEquals(33, settings.bibleSettings.lowerThirdHeightPercent)
        assertEquals(33, settings.songSettings.lowerThirdHeightPercent)
    }

    @Test
    fun `a value already in its new home is not overwritten`() {
        // Written by a newer build, opened by an older one, rolled forward again. Flattening the two
        // back into the one they replaced would undo exactly the change this migration is for.
        val settings = decode(
            """
            {"settingsVersion":7,
             "projectionSettings":{"lowerThirdHeightPercent":45},
             "bibleSettings":{"lowerThirdHeightPercent":25}}
            """.trimIndent(),
        )

        assertEquals(25, settings.bibleSettings.lowerThirdHeightPercent, "the Bible keeps its own")
        assertEquals(45, settings.songSettings.lowerThirdHeightPercent, "and songs still inherit")
    }

    @Test
    fun `the rest of the section survives the rewrite`() {
        // The step rebuilds bibleSettings and songSettings, so it has to carry their other fields.
        val settings = decode(
            """
            {"settingsVersion":7,
             "projectionSettings":{"lowerThirdHeightPercent":45,"windowTop":40},
             "songSettings":{"marginTop":12}}
            """.trimIndent(),
        )

        assertEquals(12, settings.songSettings.marginTop)
        assertEquals(45, settings.songSettings.lowerThirdHeightPercent)
        assertEquals(40, settings.projectionSettings.windowTop)
    }

    @Test
    fun `a current-version document is left exactly as it is`() {
        val settings = decode(
            """
            {"settingsVersion":${AppSettings.CURRENT_SETTINGS_VERSION},
             "bibleSettings":{"lowerThirdHeightPercent":25},
             "songSettings":{"lowerThirdHeightPercent":40}}
            """.trimIndent(),
        )

        assertEquals(25, settings.bibleSettings.lowerThirdHeightPercent)
        assertEquals(40, settings.songSettings.lowerThirdHeightPercent)
    }

    @Test
    fun `replaying the migration changes nothing the second time`() {
        val once = decode(legacy(45))
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val twice = decode(json.encodeToString(AppSettings.serializer(), once))

        assertEquals(once.bibleSettings.lowerThirdHeightPercent, twice.bibleSettings.lowerThirdHeightPercent)
        assertEquals(once.songSettings.lowerThirdHeightPercent, twice.songSettings.lowerThirdHeightPercent)
    }
}
