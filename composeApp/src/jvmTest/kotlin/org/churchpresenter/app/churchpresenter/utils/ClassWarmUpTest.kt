package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassWarmUpTest {

    @Test
    fun `every class on the list loads, so a rename cannot silently drop one`() {
        val missing = WARM_CLASSES.filterNot { name ->
            runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
        }

        assertTrue(missing.isEmpty(), "not found, renamed or moved: $missing")
        assertEquals(WARM_CLASSES.size, WARM_CLASSES.toSet().size, "a name listed twice")
    }

    @Test
    fun `every name is loaded, and one that fails is counted while the rest go on`() {
        val loaded = mutableListOf<String>()
        val names = listOf("a", "b", "c", "d")

        val warmed = warmClasses(names) { name ->
            loaded += name
            when (name) {
                "b" -> throw ClassNotFoundException(name)
                "c" -> throw NoClassDefFoundError(name)
            }
        }

        assertEquals(names, loaded)
        assertEquals(2, warmed)
    }

    @Test
    fun `the default loader loads a real class without failing`() {
        assertEquals(1, warmClasses(listOf(ClassWarmUpTest::class.java.name)))
        assertEquals(0, warmClasses(listOf("org.churchpresenter.NoSuchClass")))
    }

    @Test
    fun `every warmed package is on the class path, and all its classes load`() {
        WARM_PACKAGES.forEach { prefix ->
            val names = classesUnder(listOf(prefix))
            assertTrue(names.isNotEmpty(), "nothing under $prefix -- renamed or moved?")
            assertEquals(names.size, warmClasses(names), "a class under $prefix failed to load")
        }
    }

    @Test
    fun `a jar lists the classes under the prefix and below it, and nothing else`() {
        val dir = Files.createTempDirectory("class-warm-up").toFile()
        try {
            val jar = File(dir, "a.jar")
            JarOutputStream(jar.outputStream()).use { out ->
                listOf(
                    "p/q/A.class", "p/q/A\$1.class", "p/q/r/B.class", "p/Other.class", "p/q/data.txt",
                    "p/q/module-info.class",
                )
                    .forEach { out.putNextEntry(JarEntry(it)); out.closeEntry() }
            }

            assertEquals(setOf("p.q.A", "p.q.A\$1", "p.q.r.B"), classNamesUnder(jar, "p/q/").toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a class folder lists the same way, and an unreadable location lists nothing`() {
        val dir = Files.createTempDirectory("class-warm-up").toFile()
        try {
            File(dir, "p/q/r").mkdirs()
            listOf("p/q/A.class", "p/q/r/B.class", "p/C.class").forEach { File(dir, it).writeText("") }

            assertEquals(setOf("p.q.A", "p.q.r.B"), classNamesUnder(dir, "p/q/").toSet())
            assertEquals(emptyList(), classNamesUnder(File(dir, "missing.jar"), "p/"))
            val classPath = listOf(dir.path, File(dir, "missing.jar").path, "").joinToString(File.pathSeparator)
            assertEquals(setOf("p.q.A", "p.q.r.B"), classesUnder(listOf("p/q/"), classPath).toSet())
        } finally {
            dir.deleteRecursively()
        }
    }
}
