package org.churchpresenter.calendar.sync

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FollowerShadowStoreTest {

    private val root: File = Files.createTempDirectory("follower-shadow").toFile()
    private val folder = File(root, "data")
    private val store = FollowerShadowStore(folder)

    private val copies = mapOf(
        "s1" to RemoteService(
            id = "s1",
            date = "2026-09-27",
            startTime = "10:00",
            name = "Sunday",
            rows = listOf(
                RemoteRow.Ref("m", "Intro video", RemoteKind.MEDIA),
                RemoteRow.Preset("p", "Countdown", "p1"),
            ),
            version = 3L,
            rev = 7L,
        ),
        "gone" to RemoteService(id = "gone", date = "2026-09-20", startTime = "", name = "", deleted = true, rev = 9L),
    )

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `the copies come back as they were saved, folder created on the way`() {
        store.save("inst", copies)

        assertTrue(store.file.isFile)
        assertEquals(copies, FollowerShadowStore(folder).load("inst"))
    }

    @Test
    fun `nothing is remembered before the first save`() {
        assertEquals(emptyMap(), store.load("inst"))
    }

    @Test
    fun `copies saved for another instance are not this one's`() {
        store.save("other", copies)

        assertEquals(emptyMap(), store.load("inst"))
    }

    @Test
    fun `an unreadable file is taken as no copies`() {
        folder.mkdirs()
        store.file.writeText("{ not json")

        assertEquals(emptyMap(), store.load("inst"))
    }
}
