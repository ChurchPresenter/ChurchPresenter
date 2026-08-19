package lottiegen

import lottiegen.editor.BuildRegistrar
import lottiegen.spec.RegistryEntry
import lottiegen.spec.SpecJson
import lottiegen.spec.StyleRegistry
import lottiegen.spec.StyleSpec
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildRegistrarTest {

    private lateinit var temp: File
    private lateinit var savedDir: String

    @BeforeTest
    fun isolateWorkingDir() {
        temp = Files.createTempDirectory("lottiegen-registrar-test").toFile()
        savedDir = System.getProperty("user.dir")
    }

    @AfterTest
    fun restoreWorkingDir() {
        System.setProperty("user.dir", savedDir)
        temp.deleteRecursively()
    }

    /** A styles dir at [relative] under [temp], with a registry holding [entries]. */
    private fun stylesDir(relative: String, entries: List<RegistryEntry> = emptyList()): File {
        val dir = File(temp, relative).apply { mkdirs() }
        File(dir, "registry.json").writeText(StyleRegistry.encode(StyleRegistry(entries)), Charsets.UTF_8)
        return dir
    }

    private fun cwd(dir: File) = System.setProperty("user.dir", dir.absolutePath)

    // ── locateStylesDir ───────────────────────────────────────────────────────

    @Test
    fun `finds the styles dir when run from the module root`() {
        val styles = stylesDir("src/main/resources/styles")
        cwd(temp)
        assertEquals(styles.canonicalFile, BuildRegistrar.locateStylesDir()?.canonicalFile)
    }

    @Test
    fun `finds the styles dir when run from the repo root`() {
        val styles = stylesDir("lottieGenerator/src/main/resources/styles")
        cwd(temp)
        assertEquals(styles.canonicalFile, BuildRegistrar.locateStylesDir()?.canonicalFile)
    }

    @Test
    fun `finds the styles dir from a sibling module's working directory`() {
        // gradle sets cwd = composeApp/ for an embedded dev run; the ancestor walk climbs out.
        val styles = stylesDir("lottieGenerator/src/main/resources/styles")
        val composeApp = File(temp, "composeApp").apply { mkdirs() }
        cwd(composeApp)
        assertEquals(styles.canonicalFile, BuildRegistrar.locateStylesDir()?.canonicalFile)
    }

    @Test
    fun `a styles dir without a registry is not mistaken for the checkout`() {
        File(temp, "src/main/resources/styles").mkdirs()
        cwd(temp)
        assertNull(BuildRegistrar.locateStylesDir())
    }

    @Test
    fun `returns null when not running from a source checkout`() {
        cwd(temp)
        assertNull(BuildRegistrar.locateStylesDir())
    }

    @Test
    fun `does not climb past four ancestors`() {
        val styles = stylesDir("src/main/resources/styles")
        val deep = File(temp, "a/b/c/d/e").apply { mkdirs() }
        cwd(deep)
        assertNull(BuildRegistrar.locateStylesDir())
        // proving the same layout IS found from within range
        cwd(File(temp, "a/b"))
        assertEquals(styles.canonicalFile, BuildRegistrar.locateStylesDir()?.canonicalFile)
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun `registering writes the spec under an id and slug derived name`() {
        val dir = stylesDir("styles")
        val result = BuildRegistrar.register(StyleSpec(id = "42", name = "Ribbon Fold"), dir)

        assertEquals("style42_ribbon-fold.json", result.specFile.name)
        assertTrue(result.specFile.isFile)
        assertEquals("42", SpecJson.decode(result.specFile.readText(Charsets.UTF_8)).id)
    }

    @Test
    fun `registering adds a registry entry pointing at the spec resource`() {
        val dir = stylesDir("styles")
        val result = BuildRegistrar.register(StyleSpec(id = "42", name = "Ribbon Fold"), dir)

        val entry = StyleRegistry.decode(result.registryFile.readText(Charsets.UTF_8))
            .entries.single { it.id == "42" }
        assertEquals("Ribbon Fold", entry.name)
        assertEquals("/styles/style42_ribbon-fold.json", entry.resource)
    }

    @Test
    fun `re-registering the same id replaces its entry rather than duplicating it`() {
        val dir = stylesDir("styles", listOf(RegistryEntry("42", "Old Name", "/styles/style42_old-name.json")))
        File(dir, "style42_old-name.json").writeText("{}", Charsets.UTF_8)

        BuildRegistrar.register(StyleSpec(id = "42", name = "New Name"), dir)

        val entries = StyleRegistry.decode(File(dir, "registry.json").readText(Charsets.UTF_8)).entries
        assertEquals(1, entries.count { it.id == "42" })
        assertEquals("New Name", entries.single { it.id == "42" }.name)
    }

    @Test
    fun `renaming a style deletes the spec file the old name left behind`() {
        val dir = stylesDir("styles", listOf(RegistryEntry("42", "Old Name", "/styles/style42_old-name.json")))
        val stale = File(dir, "style42_old-name.json").apply { writeText("{}", Charsets.UTF_8) }

        BuildRegistrar.register(StyleSpec(id = "42", name = "New Name"), dir)

        assertFalse(stale.exists(), "the previous spec file should not be left orphaned")
        assertTrue(File(dir, "style42_new-name.json").isFile)
    }

    @Test
    fun `re-registering under an unchanged name keeps the spec file`() {
        val dir = stylesDir("styles")
        val first = BuildRegistrar.register(StyleSpec(id = "42", name = "Same"), dir)
        val second = BuildRegistrar.register(StyleSpec(id = "42", name = "Same"), dir)

        assertEquals(first.specFile, second.specFile)
        assertTrue(second.specFile.isFile, "rewriting the same name must not delete the file it just wrote")
    }

    @Test
    fun `registering a new id leaves other styles untouched`() {
        val dir = stylesDir("styles", listOf(RegistryEntry("7", "Existing", "/styles/style7_existing.json")))
        val other = File(dir, "style7_existing.json").apply { writeText("{}", Charsets.UTF_8) }

        BuildRegistrar.register(StyleSpec(id = "42", name = "Fresh"), dir)

        val entries = StyleRegistry.decode(File(dir, "registry.json").readText(Charsets.UTF_8)).entries
        assertNotNull(entries.singleOrNull { it.id == "7" })
        assertTrue(other.exists())
        assertEquals(2, entries.size)
    }
}
