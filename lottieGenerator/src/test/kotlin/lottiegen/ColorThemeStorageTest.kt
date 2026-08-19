package lottiegen

import lottiegen.model.ColorTheme
import lottiegen.model.ColorThemeColors
import lottiegen.model.defaultColorThemes
import lottiegen.persistence.ColorThemeStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorThemeStorageTest {

    private lateinit var temp: File
    private lateinit var savedHome: String

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("lottiegen-themes-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun themesFile() =
        File(temp, ".churchpresenter/churchpresenter-lottiegen/color-themes.json")

    private fun theme(name: String) =
        ColorTheme(name, ColorThemeColors("#111111", "#222222", "#333333", "#444444", "#555555"))

    @Test
    fun `a first run seeds the defaults and writes them to disk`() {
        val loaded = ColorThemeStorage.load()

        assertEquals(defaultColorThemes(), loaded)
        assertTrue(themesFile().isFile, "the defaults should be persisted, not just returned")
    }

    @Test
    fun `saved themes load back identical`() {
        val mine = listOf(theme("Mine"), theme("Yours"))
        ColorThemeStorage.save(mine)

        assertEquals(mine, ColorThemeStorage.load())
    }

    @Test
    fun `an empty theme list falls back to the defaults`() {
        ColorThemeStorage.save(emptyList())

        assertEquals(defaultColorThemes(), ColorThemeStorage.load())
    }

    @Test
    fun `a corrupt themes file falls back to the defaults rather than throwing`() {
        ColorThemeStorage.save(listOf(theme("Mine")))
        themesFile().writeText("{ not json")

        assertEquals(defaultColorThemes(), ColorThemeStorage.load())
    }

    @Test
    fun `an unknown field in a stored theme is ignored rather than failing the load`() {
        ColorThemeStorage.save(listOf(theme("Mine")))
        themesFile().writeText(themesFile().readText().replace("\"name\"", "\"futureField\": 1, \"name\""))

        assertEquals(listOf("Mine"), ColorThemeStorage.load().map { it.name })
    }

    @Test
    fun `saving into an unwritable location reports the failure instead of throwing`() {
        val dir = themesFile().parentFile
        dir.mkdirs()
        themesFile().mkdirs() // a directory where the file should be — writeText will fail

        ColorThemeStorage.save(listOf(theme("Mine")))

        assertTrue(themesFile().isDirectory, "save must not throw out of a UI callback")
    }
}
