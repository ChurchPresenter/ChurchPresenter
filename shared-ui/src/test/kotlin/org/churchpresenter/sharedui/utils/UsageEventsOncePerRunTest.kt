package org.churchpresenter.sharedui.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageEventsOncePerRunTest {

    private val dir: File = Files.createTempDirectory("cp-usage-once").toFile()
    private val file = File(dir, "usage-events.json")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `an event recorded once per run counts once however often it happens`() {
        val store = UsageEventStore { file }
        repeat(3) { store.recordOncePerRun(UsageEvent.SONG_DUAL_LANGUAGE) }
        assertEquals(mapOf(UsageEvent.SONG_DUAL_LANGUAGE to 1), store.unreported())
    }

    @Test
    fun `the next run counts it again`() {
        UsageEventStore { file }.recordOncePerRun(UsageEvent.SONG_DUAL_LANGUAGE)
        UsageEventStore { file }.recordOncePerRun(UsageEvent.SONG_DUAL_LANGUAGE)
        assertEquals(mapOf(UsageEvent.SONG_DUAL_LANGUAGE to 2), UsageEventStore { file }.unreported())
    }

    @Test
    fun `recording no times records nothing`() {
        val store = UsageEventStore { file }
        store.record(UsageEvent.SONG_DUAL_LANGUAGE, times = 0)
        assertEquals(emptyMap(), store.unreported())
    }
}
