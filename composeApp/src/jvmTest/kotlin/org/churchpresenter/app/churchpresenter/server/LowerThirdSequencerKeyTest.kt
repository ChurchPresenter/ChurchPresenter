package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.atem.FakeAtemSwitcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LowerThirdSequencerKeyTest {

    @AfterTest
    fun reset() = runBlocking {
        LowerThirdSequencer.stop()
        AtemConnectionManager.invalidate()
    }

    private fun atem(port: Int) = AtemSettings(
        host = "127.0.0.1",
        port = port,
        keyPreRollMs = 0,
        keyPostRollMs = 0,
    )

    private fun dskCommands(fake: FakeAtemSwitcher) =
        fake.commandsNamed("CDsL") + fake.commandsNamed("DDsA")

    private fun awaitDsk(fake: FakeAtemSwitcher): List<ByteArray> = runBlocking {
        withTimeout(5_000) {
            while (dskCommands(fake).isEmpty()) yield()
            dskCommands(fake)
        }
    }

    /** Runs a sequence and returns once the lower third itself has reached the UI layer. */
    private fun runAndAwaitShow(
        fake: FakeAtemSwitcher,
        mixEffect: Int? = 0,
        keyer: Int? = 0,
        useDownstreamKey: Boolean = false,
    ): String? = runBlocking {
        val shown = Channel<LowerThirdSequencer.ShowRequest>(capacity = 1)
        val collector = launch { LowerThirdSequencer.onShow.collect { shown.trySend(it) } }
        LowerThirdSequencer.onShow.subscriptionCount.first { it > 0 }
        val keyError = LowerThirdSequencer.run(
            name = "Welcome",
            json = "{}",
            durationMs = 0L,
            pauseAtFrame = false,
            pauseDurationMs = 0L,
            mixEffect = mixEffect,
            keyer = keyer,
            atem = atem(fake.port),
            useDownstreamKey = useDownstreamKey,
            autoEnd = false,
        )
        withTimeout(5_000) { shown.receive() }
        collector.cancel()
        keyError
    }

    @Test
    fun `going live cuts the upstream key on air`() {
        FakeAtemSwitcher().use { fake ->
            val keyError = runAndAwaitShow(fake, mixEffect = 1, keyer = 2)

            assertNull(keyError, "the switcher answered, so there is nothing to report")
            val cmd = fake.awaitCommandsNamed("CKOn", 1).first()
            assertEquals(1, cmd[0].toInt())
            assertEquals(2, cmd[1].toInt())
            assertEquals(1, cmd[2].toInt(), "the key goes ON as the lower third starts")
        }
    }

    @Test
    fun `stopping cuts the same key back off`() {
        FakeAtemSwitcher().use { fake ->
            runAndAwaitShow(fake, mixEffect = 1, keyer = 2)
            fake.awaitCommandsNamed("CKOn", 1)

            runBlocking { LowerThirdSequencer.stop() }

            val cuts = fake.awaitCommandsNamed("CKOn", 2)
            assertEquals(0, cuts.last()[2].toInt(), "the key must come off the air again")
            assertEquals(1, cuts.last()[0].toInt(), "and off the M/E it went on")
            assertEquals(2, cuts.last()[1].toInt())
        }
    }

    @Test
    fun `a downstream key is driven as a DSK rather than an upstream keyer`() {
        FakeAtemSwitcher().use { fake ->
            runAndAwaitShow(fake, mixEffect = 0, keyer = 1, useDownstreamKey = true)

            assertEquals(1, awaitDsk(fake).first()[0].toInt(), "byte 0 is the DSK index")
            assertTrue(fake.commandsNamed("CKOn").isEmpty(), "a DSK lower third must not touch an upstream keyer")
        }
    }

    @Test
    fun `a downstream key is taken off the same way`() {
        FakeAtemSwitcher().use { fake ->
            runAndAwaitShow(fake, mixEffect = 0, keyer = 1, useDownstreamKey = true)
            awaitDsk(fake)

            runBlocking { LowerThirdSequencer.stop() }

            runBlocking { withTimeout(5_000) { while (dskCommands(fake).size < 2) yield() } }
            assertTrue(fake.commandsNamed("CKOn").isEmpty())
        }
    }

    @Test
    fun `a sequence with no keyer configured never opens a connection`() {
        FakeAtemSwitcher().use { fake ->
            val keyError = runAndAwaitShow(fake, mixEffect = null, keyer = null)

            assertNull(keyError)
            assertTrue(fake.received.isEmpty(), "nothing was asked of the switcher at all")
        }
    }

    @Test
    fun `a keyer with no mix effect is not driven either`() {
        FakeAtemSwitcher().use { fake ->
            val keyError = runAndAwaitShow(fake, mixEffect = null, keyer = 2)

            assertNull(keyError)
            assertTrue(fake.received.isEmpty())
        }
    }

    @Test
    fun `a preempting sequence takes the previous key off before its own goes on`() {
        FakeAtemSwitcher().use { fake ->
            runAndAwaitShow(fake, mixEffect = 1, keyer = 2)
            fake.awaitCommandsNamed("CKOn", 1)

            runAndAwaitShow(fake, mixEffect = 3, keyer = 0)

            val cuts = fake.awaitCommandsNamed("CKOn", 3)
            assertEquals(0, cuts[1][2].toInt(), "the first key comes off")
            assertEquals(1, cuts[1][0].toInt())
            assertEquals(1, cuts[2][2].toInt(), "before the second goes on")
            assertEquals(3, cuts[2][0].toInt())
        }
    }

    @Test
    fun `a sequence that ends on its own takes the key off with it`() {
        FakeAtemSwitcher().use { fake ->
            val cleared = Channel<Unit>(capacity = 1)
            runBlocking {
                val collector = launch { LowerThirdSequencer.onClear.collect { cleared.trySend(Unit) } }
                LowerThirdSequencer.onClear.subscriptionCount.first { it > 0 }
                LowerThirdSequencer.run(
                    name = "AutoEnd", json = "{}", durationMs = 0L, pauseAtFrame = false, pauseDurationMs = 0L,
                    mixEffect = 1, keyer = 2, atem = atem(fake.port), autoEnd = true,
                )
                withTimeout(5_000) { cleared.receive() }
                collector.cancel()
            }

            val cuts = fake.awaitCommandsNamed("CKOn", 2)
            assertEquals(0, cuts.last()[2].toInt())
            assertEquals("idle", LowerThirdSequencer.status.value)
        }
    }
}
