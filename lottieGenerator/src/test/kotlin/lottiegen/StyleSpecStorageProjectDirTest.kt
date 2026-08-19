package lottiegen

import lottiegen.persistence.StyleSpecStorage
import lottiegen.spec.StyleSpec
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The parts of [StyleSpecStorage] that resolve the project dir out of user.home. */
class StyleSpecStorageProjectDirTest {

    private lateinit var temp: File
    private lateinit var savedHome: String

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("lottiegen-specdir-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun save(name: String) =
        StyleSpecStorage.save(StyleSpec(name = name), StyleSpecStorage.fileForName(name))

    @Test
    fun `an untouched project folder lists nothing`() {
        assertEquals(emptyList(), StyleSpecStorage.list())
    }

    @Test
    fun `saved projects are listed case-insensitively by name`() {
        listOf("Zebra", "apple", "Mango").forEach { save(it) }

        assertEquals(listOf("apple.json", "mango.json", "zebra.json"), StyleSpecStorage.list().map { it.name })
    }

    @Test
    fun `only json files are listed`() {
        save("Keeper")
        File(StyleSpecStorage.fileForName("x").parentFile, "notes.txt").writeText("ignore me")

        assertEquals(listOf("keeper.json"), StyleSpecStorage.list().map { it.name })
    }

    @Test
    fun `a name becomes a slugged file in the project dir`() {
        val file = StyleSpecStorage.fileForName("My Lower Third")

        assertEquals("my-lower-third.json", file.name)
        assertTrue(file.parentFile.absolutePath.startsWith(temp.absolutePath))
    }

    @Test
    fun `a taken slug is uniquified rather than overwritten`() {
        save("Banner")
        save("Banner")
        save("Banner")

        assertEquals(
            listOf("banner-1.json", "banner-2.json", "banner.json"),
            StyleSpecStorage.list().map { it.name }.sorted()
        )
    }

    @Test
    fun `a project round trips through the name-derived file`() {
        val file = StyleSpecStorage.fileForName("Round Trip")
        StyleSpecStorage.save(StyleSpec(name = "Round Trip"), file)

        assertEquals("Round Trip", StyleSpecStorage.load(file)?.name)
    }
}
