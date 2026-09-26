package org.churchpresenter.atem

import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hello, when the network is not perfect: it is one UDP datagram, so a connect has to survive
 * it being lost, and has to survive the switcher answering it more than once.
 *
 * Neither is something [FakeAtemSwitcher] should be taught -- it replays what the captures showed
 * (see its doc comment) -- so the imperfection lives in [LossyLink], a relay between the client and
 * an ordinary fake that drops or repeats packets on the way through.
 */
class AtemClientHelloTest {

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val FLAG_HELLO = 0x02
        const val FLAGS_SHIFT = 3

        fun isHello(packet: ByteArray) =
            packet.isNotEmpty() && ((packet[0].toInt() and 0xFF) shr FLAGS_SHIFT) and FLAG_HELLO != 0
    }

    /**
     * A relay on its own port: packets from the client go on to [switcherPort], replies come back.
     * [dropHellos] client hellos are swallowed on the way out; with [repeatHelloReply] the
     * switcher's hello reply is delivered twice.
     */
    private class LossyLink(
        switcherPort: Int,
        dropHellos: Int = 0,
        private val repeatHelloReply: Boolean = false,
    ) : AutoCloseable {
        private val front = DatagramSocket(0, InetAddress.getByName(LOOPBACK))
        private val back = DatagramSocket(0, InetAddress.getByName(LOOPBACK))
        private val switcher = InetSocketAddress(LOOPBACK, switcherPort)
        private val client = AtomicReference<InetSocketAddress?>(null)
        private val hellosToDrop = AtomicInteger(dropHellos)

        /** Client hellos that reached this relay, dropped or not. */
        val hellosSeen = AtomicInteger(0)

        val port: Int get() = front.localPort

        private val outbound = thread(isDaemon = true, name = "lossy-link-out") {
            val buf = ByteArray(65536)
            while (true) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { front.receive(p) }.getOrElse { return@thread }
                client.set(p.socketAddress as InetSocketAddress)
                val bytes = p.data.copyOf(p.length)
                if (isHello(bytes)) {
                    hellosSeen.incrementAndGet()
                    if (hellosToDrop.getAndDecrement() > 0) continue
                }
                runCatching { back.send(DatagramPacket(bytes, bytes.size, switcher)) }
            }
        }

        private val inbound = thread(isDaemon = true, name = "lossy-link-in") {
            val buf = ByteArray(65536)
            while (true) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { back.receive(p) }.getOrElse { return@thread }
                val to = client.get() ?: continue
                val bytes = p.data.copyOf(p.length)
                val copies = if (repeatHelloReply && isHello(bytes)) 2 else 1
                repeat(copies) { runCatching { front.send(DatagramPacket(bytes, bytes.size, to)) } }
            }
        }

        override fun close() {
            front.close()
            back.close()
            outbound.join()
            inbound.join()
        }
    }

    @Test
    fun `a lost hello is sent again, and the connect goes through`() {
        FakeAtemSwitcher().use { fake ->
            LossyLink(fake.port, dropHellos = 1).use { link ->
                val client = AtemClient(LOOPBACK, link.port)
                try {
                    runBlocking { client.connect(collectState = false) }

                    assertTrue(client.isAlive(), "connected through the second hello")
                    assertEquals(2, link.hellosSeen.get(), "the first was lost, so it was sent again")
                } finally {
                    client.disconnect()
                }
            }
        }
    }

    @Test
    fun `a hello answered twice does not reset the session`() {
        FakeAtemSwitcher().use { fake ->
            LossyLink(fake.port, repeatHelloReply = true).use { link ->
                val client = AtemClient(LOOPBACK, link.port)
                try {
                    runBlocking { client.connect(collectState = true) }
                    // A command the switcher only records if it arrives on the session it assigned.
                    runBlocking { client.setKeyOnAir(AtemKey(useDsk = true, mixEffect = 0, keyer = 0), true) }

                    assertTrue(fake.commandsNamed("CDsL").isNotEmpty(), "the command reached the switcher")
                } finally {
                    client.disconnect()
                }
            }
        }
    }
}
