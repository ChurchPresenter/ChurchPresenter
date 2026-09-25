package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Version 17: a saved quick-tray tile's lower-third half going back to inheriting the band.
 *
 * The tray's old constructor seeded **both** halves opaque black, and `resolveBackground` ranks a
 * quick pick above the configured band — so pressing any tile painted every lower third solid black,
 * a transparent keyed one included. The constructor was fixed and the fix said so plainly: *"tiles
 * already saved keep whatever they hold."* They did, which is why the report came back saying the
 * override was **still** happening after the update. A fix to a constructor cannot reach data that
 * is already on disk; only a migration can.
 *
 * What is worth pinning here is the *narrowness* of it. The seeded value is reset and nothing else
 * is, because the editor of the day offered no Inherit switch on that half: a tile holding exactly
 * what the constructor produced is one nobody chose, and anything else is a choice. A test that only
 * checked "the black one became inherit" would pass just as well against a migration that wiped
 * every tile's band, which is the more damaging bug of the two.
 */
class QuickBackgroundInheritMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-quick-background-migration-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    /** A pre-17 document holding [tiles] in the tray, written as the old build wrote them. */
    private fun documentWith(vararg tiles: String) = """
        {
          "settingsVersion": 16,
          "quickBackgrounds": [${tiles.joinToString(",")}]
        }
    """.trimIndent()

    /** What the old constructor produced for both halves. */
    private val seededBlack = """{"type":"color","color":"#000000"}"""

    @Test
    fun `a tile seeded black on both halves has its band put back to inherit`() {
        val settings = decode(
            documentWith("""{"id":"a","background":$seededBlack,"lowerThirdBackground":$seededBlack}"""),
        )
        val tile = settings.quickBackgrounds.single()
        assertFalse(
            tile.lowerThirdBackground.isCustom,
            "the band must inherit again, not stay black: ${tile.lowerThirdBackground}",
        )
        // And only that half: the tile still overrides the full screen, which is what it is for.
        assertTrue(tile.background.isCustom, "the full-screen half must keep overriding")
        assertEquals("#000000", tile.background.color)
    }

    @Test
    fun `a band the operator actually chose is left alone`() {
        // Anything that is not the seeded value is a choice. Four shapes of choice, one document.
        val chosen = listOf(
            """{"type":"color","color":"#ff0000"}""" to "another colour",
            """{"type":"image","image":"/backgrounds/stage.png"}""" to "a picture",
            """{"type":"color","color":"#000000","dim":40}""" to "a dimmed black",
            """{"type":"gradient","color":"#000000","colorEnd":"#203040"}""" to "a gradient",
        )
        val settings = decode(
            documentWith(
                *chosen.mapIndexed { index, (json, _) ->
                    """{"id":"t$index","background":$seededBlack,"lowerThirdBackground":$json}"""
                }.toTypedArray(),
            ),
        )
        assertEquals(chosen.size, settings.quickBackgrounds.size)
        settings.quickBackgrounds.forEachIndexed { index, tile ->
            assertTrue(
                tile.lowerThirdBackground.isCustom,
                "${chosen[index].second} must survive the migration: ${tile.lowerThirdBackground}",
            )
        }
    }

    @Test
    fun `a tile already inheriting is untouched`() {
        val settings = decode(
            documentWith("""{"id":"a","background":$seededBlack,"lowerThirdBackground":{}}"""),
        )
        assertFalse(settings.quickBackgrounds.single().lowerThirdBackground.isCustom)
    }

    @Test
    fun `an empty tray migrates without complaint`() {
        assertTrue(decode(documentWith()).quickBackgrounds.isEmpty())
    }

    @Test
    fun `a document already at the current version is not touched again`() {
        // The gate matters: without it, a band an operator sets to plain black *after* upgrading
        // would be reset to inherit on the next load, for ever.
        val raw = """
            {
              "settingsVersion": ${AppSettings.CURRENT_SETTINGS_VERSION},
              "quickBackgrounds": [
                {"id":"a","background":$seededBlack,"lowerThirdBackground":$seededBlack}
              ]
            }
        """.trimIndent()
        assertTrue(
            decode(raw).quickBackgrounds.single().lowerThirdBackground.isCustom,
            "a black band chosen after the upgrade must survive every later load",
        )
    }
}
