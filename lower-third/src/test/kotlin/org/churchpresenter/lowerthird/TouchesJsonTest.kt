package org.churchpresenter.lowerthird

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which folder events make the preset list rescan: a `.json` added, removed or changed. */
class TouchesJsonTest {

    private fun event(kind: WatchEvent.Kind<*>, name: String?): WatchEvent<*> = object : WatchEvent<Any?> {
        override fun kind(): WatchEvent.Kind<Any?> {
            @Suppress("UNCHECKED_CAST")
            return kind as WatchEvent.Kind<Any?>
        }
        override fun count() = 1
        override fun context(): Any? = name?.let { Path.of(it) }
    }

    @Test
    fun `a json file added, removed or changed counts, whatever its case`() {
        assertTrue(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_CREATE, "Welcome.json"))))
        assertTrue(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_DELETE, "Notices.JSON"))))
        assertTrue(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_MODIFY, "a.json"))))
    }

    @Test
    fun `other files and overflows do not`() {
        assertFalse(touchesJson(listOf(event(StandardWatchEventKinds.ENTRY_CREATE, "notes.txt"))))
        assertFalse(touchesJson(listOf(event(StandardWatchEventKinds.OVERFLOW, null))))
        assertFalse(touchesJson(emptyList()))
    }

    @Test
    fun `one json among other changes is enough`() {
        assertTrue(
            touchesJson(
                listOf(
                    event(StandardWatchEventKinds.ENTRY_CREATE, "notes.txt"),
                    event(StandardWatchEventKinds.ENTRY_MODIFY, "Welcome.json"),
                ),
            ),
        )
    }
}
