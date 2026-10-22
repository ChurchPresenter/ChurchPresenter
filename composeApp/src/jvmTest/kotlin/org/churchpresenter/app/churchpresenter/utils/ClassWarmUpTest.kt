package org.churchpresenter.app.churchpresenter.utils

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
}
