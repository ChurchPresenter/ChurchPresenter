package org.churchpresenter.telemetry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.settings.InstanceLinkSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryTallyTest {

    @Test
    fun `songbooks are counted once each, and a song with none is in the root one`() {
        assertEquals(3 to 5, songCountsOf(listOf("Hymns", "Hymns", "", " ", "Kids/AM")))
        assertEquals(0 to 0, songCountsOf(emptyList()))
    }

    @Test
    fun `of two counts the one with more songs wins`() {
        assertEquals(1 to 9, mostSongs(3 to 5, 1 to 9))
        assertEquals(3 to 5, mostSongs(3 to 5, 1 to 2))
        assertEquals(2 to 5, mostSongs(3 to 5, 2 to 5))
        assertEquals(3 to 5, mostSongs(3 to 5, null))
        assertEquals(1 to 2, mostSongs(null, 1 to 2))
        assertNull(mostSongs(null, null))
    }

    @Test
    fun `the ping waits for the first list and keeps the largest it saw`() = runTest {
        val tally = LibraryTally()
        val loaded = async { tally.await(first = 60.seconds) }
        runCurrent()
        assertFalse(loaded.isCompleted)
        tally.biblesLoaded(2)
        tally.biblesLoaded(1)
        tally.songsLoaded(listOf("Hymns", "Hymns", "Kids"))
        tally.songsLoaded(listOf("Hymns"))
        assertEquals(LibraryTally.Loaded(2 to 3, 2), loaded.await())
    }

    @Test
    fun `a follower's later catalog is counted when it arrives inside the settle`() = runTest {
        val tally = LibraryTally()
        tally.songsLoaded(listOf("Hymns"))
        val loaded = async { tally.await(first = 60.seconds, settle = 30.seconds) }
        advanceTimeBy(10.seconds)
        tally.songsLoaded(listOf("Primary", "Primary", "Kids"))
        assertEquals(LibraryTally.Loaded(2 to 3, 0), loaded.await())
    }

    @Test
    fun `with nothing loaded in time the songs are left out`() = runTest {
        assertEquals(LibraryTally.Loaded(null, 0), LibraryTally().await(first = 60.seconds))
    }

    @Test
    fun `only a follower that connects at launch waits for the primary`() {
        fun settle(link: InstanceLinkSettings) = LibraryTally.settleFor(AppSettings(instanceLink = link))
        val follower = InstanceLinkSettings(enabled = true, autoConnect = true, role = InstanceLinkRole.CONTROLLED)
        assertEquals(LibraryTally.FOLLOWER_SETTLE, settle(follower))
        assertEquals(Duration.ZERO, settle(InstanceLinkSettings()))
        assertEquals(Duration.ZERO, settle(follower.copy(enabled = false)))
        assertEquals(Duration.ZERO, settle(follower.copy(autoConnect = false)))
        assertEquals(Duration.ZERO, settle(follower.copy(role = InstanceLinkRole.CONTROLLER)))
    }
}
