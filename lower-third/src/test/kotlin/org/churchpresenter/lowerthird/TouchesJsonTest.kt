package org.churchpresenter.lowerthird

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which folder events make the preset list rescan: a `.json` added, removed or changed. */
class TouchesJsonTest {

    private fun <T : Any> event(kind: WatchEvent.Kind<T>, context: T?): WatchEvent<T> = object : WatchEvent<T> {
        override fun kind(): WatchEvent.Kind<T> = kind
        override fun count() = 1
        override fun context(): T? = context
    }

    @Test
    fun `a json file added, removed or changed counts, whatever its case`() {
        assertTrue(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("Welcome.json")))))
        assertTrue(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_DELETE, Path.of("Notices.JSON")))))
        assertTrue(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("a.json")))))
    }

    @Test
    fun `other files and overflows do not`() {
        assertFalse(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("notes.txt")))))
        assertFalse(touchesJson(listOf(event(StandardWatchEventKinds.OVERFLOW, null))))
        assertFalse(touchesJson(emptyList()))
    }

    @Test
    fun `one json among other changes is enough`() {
        assertTrue(
            touchesJson(
                listOf(
                    event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("notes.txt")),
                    event(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("Welcome.json")),
                ),
            ),
        )
    }
}
