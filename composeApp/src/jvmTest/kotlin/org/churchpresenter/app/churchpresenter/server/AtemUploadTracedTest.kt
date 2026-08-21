package org.churchpresenter.app.churchpresenter.server

import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtemUploadTracedTest {

    @BeforeTest
    fun enableSentry() {
        Sentry.init { options ->
            options.dsn = "https://key@localhost/1"
            options.setTransportFactory(NoOpTransportFactory.getInstance())
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableAutoSessionTracking = false
            options.tracesSampleRate = 1.0
            // Sentry.close() blocks for this long draining its queue, and the default is 2000ms —
            // paid by every test in this class, which is the whole of its runtime. There is nothing
            // to drain: the transport above is NoOp, so the wait is pure teardown cost.
            options.shutdownTimeoutMillis = 0
        }
    }

    @AfterTest
    fun disableSentry() {
        Sentry.close()
    }

    private fun frame(bytes: Int) = EncodedFrame(ByteArray(bytes) { (it and 0x7F).toByte() }, rawLen = bytes * 4)

    private fun connected(fake: FakeAtemSwitcher): AtemClient =
        AtemClient("127.0.0.1", fake.port).also { runBlocking { it.connect(collectState = true) } }

    @Test
    fun `a traced still upload still transfers every byte`() {
        FakeAtemSwitcher().use { fake ->
            val payload = frame(2_000)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(slot = 2, frame = payload, name = "traced") }
            } finally {
                client.disconnect()
            }

            val sent = fake.commandsNamed("FTDa").sumOf {
                ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
            }
            assertEquals(payload.data.size, sent)
        }
    }

    @Test
    fun `a traced upload locks and unlocks the media store`() {
        FakeAtemSwitcher().use { fake ->
            val payload = frame(1_200)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(slot = 0, frame = payload, name = "traced") }
            } finally {
                client.disconnect()
            }

            val locks = fake.awaitCommandsNamed("LOCK", 2)
            assertEquals(1, locks.first()[2].toInt())
            assertEquals(0, locks.last()[2].toInt())
        }
    }

    @Test
    fun `a traced upload of an empty frame is refused before anything is sent`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                assertFailsWith<Exception> {
                    runBlocking { client.uploadStillEncoded(slot = 0, frame = frame(0), name = "empty") }
                }
                assertTrue(fake.commandsNamed("FTSD").isEmpty(), "nothing may be started for an empty frame")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a traced upload to a slot the switcher does not have is refused`() {
        FakeAtemSwitcher(stillSlotCount = 4).use { fake ->
            val client = connected(fake)
            try {
                assertFailsWith<Exception> {
                    runBlocking { client.uploadStillEncoded(slot = 99, frame = frame(500), name = "far") }
                }
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a traced upload spanning several grants still completes`() {
        FakeAtemSwitcher(grantChunkSize = 400, chunksPerGrant = 4).use { fake ->
            val payload = frame(5_000)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(slot = 1, frame = payload, name = "big") }
            } finally {
                client.disconnect()
            }

            val sent = fake.commandsNamed("FTDa").sumOf {
                ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
            }
            assertEquals(payload.data.size, sent)
        }
    }
}
