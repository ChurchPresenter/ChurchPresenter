package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.app_name
import org.churchpresenter.strings.generated.resources.key_name_tab
import org.churchpresenter.strings.generated.resources.tab_focus_lost
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.StringResource
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StringWarmUpTest {

    private val strings = listOf(Res.string.app_name, Res.string.key_name_tab, Res.string.tab_focus_lost)

    @Test
    fun `every string is read, and one that fails is counted while the rest go on`() {
        val read = mutableListOf<StringResource>()
        val failures = listOf(MissingResourceException("x"), IOException("x"), IllegalStateException("x"))

        val warmed = runBlocking {
            warmStrings(strings + strings) { resource ->
                read += resource
                if (read.size > strings.size) throw failures[read.size - strings.size - 1]
            }
        }

        assertEquals(strings + strings, read)
        assertEquals(strings.size, warmed)
    }

    @Test
    fun `a cancelled warm-up stops rather than counting the cancellation as a failure`() {
        var reads = 0

        assertFailsWith<CancellationException> {
            runBlocking {
                warmStrings(strings) {
                    reads++
                    throw CancellationException("window closed")
                }
            }
        }

        assertEquals(1, reads)
    }

    /**
     * The point of the warm-up: what `getString` reads is what `stringResource` looks for, Compose
     * Resources' one string cache. That cache is private to the library, so its keys are read by
     * reflection -- "file/offset-size" -- and checked for each string read here. A library upgrade
     * that splits the two paths fails this rather than leaving a warm-up that warms nothing.
     */
    @Test
    fun `reading ahead fills the cache stringResource reads`() {
        assertEquals(strings.size, runBlocking { warmStrings(strings) })

        val cached = cachedStringKeys()
        strings.forEach { resource ->
            assertTrue(itemKeys(resource).any { it in cached }, "${resource.key} is not in the string cache")
        }
    }

    private fun cachedStringKeys(): Set<Any?> {
        val utils = Class.forName("org.jetbrains.compose.resources.StringResourcesUtilsKt")
        val cache = utils.getDeclaredField("stringItemsCache").apply { isAccessible = true }.get(null)
        val map = cache.javaClass.getDeclaredField("cache").apply { isAccessible = true }.get(cache) as Map<*, *>
        return synchronized(map) { map.keys.toSet() }
    }

    private fun itemKeys(resource: StringResource): List<String> {
        val items = field(resource, resource.javaClass.superclass, "items") as Set<*>
        return items.map { item ->
            val type = item!!.javaClass
            "${field(item, type, "path")}/${field(item, type, "offset")}-${field(item, type, "size")}"
        }
    }

    private fun field(target: Any, type: Class<*>, name: String): Any? =
        type.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
