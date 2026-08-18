package companionsatellite

import java.util.Base64
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CompanionSatelliteClient] driven end to end against [FakeCompanion] over loopback TCP.
 *
 * This module had **no test source set at all** before this suite, so all 399 lines were measured
 * by nothing: the app's JaCoCo report restricts itself to the `org.churchpresenter` package root (sub-builds are
 * "measured by their own builds"), and this build had no suite to measure them with.
 *
 * The fake's wire format comes from a capture of a real Companion 4.3.3 / API 1.10.1 rather than
 * from this client — see [FakeCompanion]. Assertions about field names, encodings and message
 * order are therefore statements about Companion, not restatements of the client.
 *
 * **Not covered, deliberately:** the 2s ping cadence and the 10s read timeout with its
 * three-strikes rule. Both are hard-coded durations, so any test of them would assert on a clock
 * and cost that clock; `AGENT.md` rules both out.
 */
class CompanionSatelliteClientTest {

    private val DEVICE = "test-device"
    private var client: CompanionSatelliteClient? = null

    @AfterTest
    fun tearDown() {
        client?.dispose()
        client = null
    }

    private class Events {
        val statuses = Collections.synchronizedList(mutableListOf<Pair<CompanionConnectionStatus, String?>>())
        val buttons = Collections.synchronizedList(mutableListOf<CompanionButtonUpdate>())
        val resets = Collections.synchronizedList(mutableListOf<Int>())
        val brightness = Collections.synchronizedList(mutableListOf<Int>())
        val status: CompanionConnectionStatus? get() = statuses.lastOrNull()?.first
    }

    private fun newClient(events: Events) = CompanionSatelliteClient(
        onStatusChanged = { s, e -> events.statuses.add(s to e) },
        onButtonUpdated = { events.buttons.add(it) },
        onButtonsReset = { events.resets.add(it) },
        onBrightnessChanged = { events.brightness.add(it) },
    ).also { client = it }

    /** Bounded poll on observable state. Ends on the condition; the timeout only fails the test. */
    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun connected(
        fake: FakeCompanion,
        events: Events,
        rows: Int = 2,
        columns: Int = 4,
        startRow: Int = 0,
        startColumn: Int = 0,
        deviceId: String = DEVICE,
    ): CompanionSatelliteClient {
        val c = newClient(events)
        c.connect(
            host = "127.0.0.1", port = fake.port, deviceId = deviceId,
            rows = rows, columns = columns, bitmapSize = 72,
            startRow = startRow, startColumn = startColumn, reconnectDelayMs = 100,
        )
        waitFor("CONNECTED") { events.status == CompanionConnectionStatus.CONNECTED }
        return c
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    fun `a BEGIN greeting is answered with ADD-DEVICE and registration reaches CONNECTED`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)

            assertTrue(
                events.statuses.map { it.first }.containsAll(
                    listOf(CompanionConnectionStatus.CONNECTING, CompanionConnectionStatus.CONNECTED)
                ),
                "status passes through CONNECTING, got ${events.statuses.map { it.first }}"
            )
            val add = fake.linesStartingWith("ADD-DEVICE").single()
            assertContains(add, "DEVICEID=\"$DEVICE\"")
            assertContains(add, "PRODUCT_NAME=\"ChurchPresenter\"")
            assertContains(add, "CAN_CHANGE_PAGE=\"Change page\"")
            assertContains(add, "BRIGHTNESS=0", ignoreCase = false)
        }
    }

    @Test
    fun `connect resets the button grid to its full size before anything is on the wire`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events, rows = 3, columns = 5)
            assertEquals(15, events.resets.first(), "the grid is cleared to rows x columns")
        }
    }

    @Test
    fun `a rejected registration surfaces Companion's own message`() {
        FakeCompanion(acceptRegistration = false, registrationError = "Invalid LAYOUT_MANIFEST").use { fake ->
            val events = Events()
            val c = newClient(events)
            c.connect("127.0.0.1", fake.port, DEVICE, rows = 2, columns = 2, bitmapSize = 72, reconnectDelayMs = 60_000)
            waitFor("ERROR status") { events.status == CompanionConnectionStatus.ERROR }
            assertEquals("Invalid LAYOUT_MANIFEST", events.statuses.last().second)
        }
    }

    // ── LAYOUT_MANIFEST ───────────────────────────────────────────────────────

    private fun manifestOf(addDeviceLine: String): String {
        val encoded = Regex("LAYOUT_MANIFEST=\"([^\"]*)\"").find(addDeviceLine)!!.groupValues[1]
        return String(Base64.getDecoder().decode(encoded))
    }

    @Test
    fun `the layout manifest declares one control per button at page coordinates`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events, rows = 2, columns = 3)

            val manifest = manifestOf(fake.linesStartingWith("ADD-DEVICE").single())
            // The real instance rejects the whole ADD-DEVICE with "Invalid LAYOUT_MANIFEST" if the
            // bitmap size keys are `width`/`height` rather than `w`/`h`.
            assertContains(manifest, "\"bitmap\":{\"w\":72,\"h\":72}")
            assertContains(manifest, "\"colors\":\"hex\"")
            // Control i sits at (i / columns, i % columns) with no offset.
            assertContains(manifest, "\"0\":{\"row\":0,\"column\":0}")
            assertContains(manifest, "\"2\":{\"row\":0,\"column\":2}")
            assertContains(manifest, "\"3\":{\"row\":1,\"column\":0}")
            assertContains(manifest, "\"5\":{\"row\":1,\"column\":2}")
        }
    }

    @Test
    fun `a start offset shifts every control onto a sub-rectangle of the page`() {
        // The whole reason registration uses LAYOUT_MANIFEST rather than the legacy
        // KEYS_TOTAL/KEYS_PER_ROW form, which always anchors at row 0 / column 0.
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events, rows = 2, columns = 2, startRow = 1, startColumn = 3)

            val manifest = manifestOf(fake.linesStartingWith("ADD-DEVICE").single())
            assertContains(manifest, "\"0\":{\"row\":1,\"column\":3}")
            assertContains(manifest, "\"1\":{\"row\":1,\"column\":4}")
            assertContains(manifest, "\"2\":{\"row\":2,\"column\":3}")
            assertContains(manifest, "\"3\":{\"row\":2,\"column\":4}")
        }
    }

    // ── Button state ──────────────────────────────────────────────────────────

    @Test
    fun `a KEY-STATE is reported with its decoded text, colours, bitmap and page`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)

            // Field set, encodings and the "page/row/column" LOCATION form are what the real
            // Companion sent; "Prog\nCAM1" is a real label from the captured instance.
            fake.sendKeyState(DEVICE, controlId = 1, text = "Prog\nCAM1", color = "#ff0000", page = 1, row = 0, column = 1)
            waitFor("button update") { events.buttons.isNotEmpty() }

            val update = events.buttons.single()
            assertEquals(1, update.index, "CONTROLID is the button index")
            assertEquals("Prog\nCAM1", update.text, "TEXT is base64-encoded on the wire")
            assertEquals("#ff0000", update.color)
            assertEquals("#ffffff", update.textColor)
            assertEquals(1, update.page, "page is the first segment of LOCATION")
            assertEquals(72, update.bitmapSize)
            assertEquals(72 * 72 * 3, update.bitmapRgb?.size, "a 72px RGB bitmap")
            assertTrue(!update.pressed)
        }
    }

    @Test
    fun `a pressed button is reported as pressed`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)
            fake.sendKeyState(DEVICE, controlId = 3, pressed = true)
            waitFor("button update") { events.buttons.isNotEmpty() }
            assertTrue(events.buttons.single().pressed, "PRESSED=1 means pressed")
        }
    }

    @Test
    fun `a KEY-STATE without a CONTROLID is ignored rather than reported as button zero`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)

            fake.sendRaw("KEY-STATE DEVICEID=\"$DEVICE\" PRESSED=0 TYPE=\"BUTTON\" ")
            // Positive signal that the handler ran past the malformed line: a good one follows it
            // through the same single-threaded read loop, so its arrival proves the first was handled.
            fake.sendKeyState(DEVICE, controlId = 7)
            waitFor("the following update") { events.buttons.isNotEmpty() }

            assertEquals(listOf(7), events.buttons.map { it.index }, "only the well-formed line is reported")
        }
    }

    @Test
    fun `a KEY-STATE with no LOCATION reports a null page rather than guessing one`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)
            fake.sendRaw("KEY-STATE DEVICEID=\"$DEVICE\" CONTROLID=\"2\" PRESSED=0 TYPE=\"BUTTON\" ")
            waitFor("button update") { events.buttons.isNotEmpty() }
            assertNull(events.buttons.single().page, "LOCATION is absent on older API versions")
        }
    }

    @Test
    fun `base64 padding inside a value survives parsing`() {
        // Values are split on the FIRST '=' only; splitting on every '=' would corrupt any
        // base64 payload whose length needs padding, which is most of them.
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)
            fake.sendKeyState(DEVICE, controlId = 0, text = "CAM1")   // encodes to "Q0FNMQ=="
            waitFor("button update") { events.buttons.isNotEmpty() }
            assertEquals("CAM1", events.buttons.single().text)
        }
    }

    @Test
    fun `KEYS-CLEAR resets the whole grid`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events, rows = 2, columns = 4)
            events.resets.clear()

            fake.sendKeysClear(DEVICE)
            waitFor("reset") { events.resets.isNotEmpty() }
            assertEquals(8, events.resets.single())
        }
    }

    @Test
    fun `a BRIGHTNESS message is reported as a percentage`() {
        FakeCompanion(brightness = 0).use { fake ->
            val events = Events()
            connected(fake, events)
            fake.sendBrightness(DEVICE, 42)
            waitFor("brightness") { events.brightness.isNotEmpty() }
            assertEquals(42, events.brightness.single())
        }
    }

    @Test
    fun `registration is followed by Companion's initial brightness`() {
        FakeCompanion(brightness = 100).use { fake ->
            val events = Events()
            connected(fake, events)
            waitFor("initial brightness") { events.brightness.isNotEmpty() }
            assertEquals(100, events.brightness.first())
        }
    }

    // ── Outbound commands ─────────────────────────────────────────────────────

    @Test
    fun `pressing a button sends a down then an up for the same control`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = connected(fake, events)

            c.pressButton(5)
            waitFor("both press edges") { fake.linesStartingWith("KEY-PRESS").size >= 2 }

            val presses = fake.linesStartingWith("KEY-PRESS")
            assertEquals(2, presses.size)
            assertContains(presses[0], "CONTROLID=\"5\"")
            assertContains(presses[0], "PRESSED=1", ignoreCase = false)
            assertContains(presses[1], "CONTROLID=\"5\"")
            assertContains(presses[1], "PRESSED=0", ignoreCase = false)
        }
    }

    @Test
    fun `pressing a button before the connection is up sends nothing`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = newClient(events)
            c.pressButton(0)
            // Now connect for real: reaching CONNECTED proves the client got that far, and the
            // press above still must not appear ahead of the registration.
            c.connect("127.0.0.1", fake.port, DEVICE, rows = 1, columns = 1, bitmapSize = 72, reconnectDelayMs = 100)
            waitFor("CONNECTED") { events.status == CompanionConnectionStatus.CONNECTED }
            assertTrue(fake.linesStartingWith("KEY-PRESS").isEmpty(), "a press with no session is dropped")
        }
    }

    @Test
    fun `changing page sends one relative step per requested time`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = connected(fake, events)

            c.changePage(forward = true, times = 3)
            waitFor("three page steps") { fake.linesStartingWith("CHANGE-PAGE").size >= 3 }

            val steps = fake.linesStartingWith("CHANGE-PAGE")
            assertEquals(3, steps.size, "the protocol has no go-to-page, only repeated steps")
            assertTrue(steps.all { it.contains("DIRECTION=1") }, "forward is DIRECTION=1")
        }
    }

    @Test
    fun `changing page backwards sends DIRECTION zero`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = connected(fake, events)
            c.changePage(forward = false)
            waitFor("one page step") { fake.linesStartingWith("CHANGE-PAGE").isNotEmpty() }
            assertContains(fake.linesStartingWith("CHANGE-PAGE").single(), "DIRECTION=0")
        }
    }

    @Test
    fun `a non-positive page step count sends nothing`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = connected(fake, events)

            c.changePage(forward = true, times = 0)
            // Positive signal that the client processed a later request: a valid step arrives.
            c.changePage(forward = true, times = 1)
            waitFor("the valid step") { fake.linesStartingWith("CHANGE-PAGE").isNotEmpty() }
            assertEquals(1, fake.linesStartingWith("CHANGE-PAGE").size, "only the valid request is sent")
        }
    }

    @Test
    fun `a server ping is answered with a pong carrying the same body`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)
            fake.sendRaw("PING 12345")
            waitFor("pong") { fake.linesStartingWith("PONG").isNotEmpty() }
            assertEquals("PONG 12345", fake.linesStartingWith("PONG").single().trim())
        }
    }

    @Test
    fun `a device id containing a quote cannot break out of its quoted slot`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val evil = "we\"ird\\id"                       // on the wire: we"ird\id
            connected(fake, events, deviceId = evil)

            val add = fake.linesStartingWith("ADD-DEVICE").single()
            // Escaped form: DEVICEID="we\"ird\\id"
            assertContains(add, "DEVICEID=\"we\\\"ird\\\\id\"", message = "both metacharacters are escaped")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    fun `disconnect reports DISCONNECTED and stops the session`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = connected(fake, events)
            c.disconnect()
            waitFor("DISCONNECTED") { events.status == CompanionConnectionStatus.DISCONNECTED }

            events.buttons.clear()
            fake.sendKeyState(DEVICE, controlId = 1)
            // Prove the negative against a positive signal rather than a pause: reconnecting a
            // fresh client and seeing ITS update means the fake had flushed by then.
            val second = Events()
            connected(fake, second)
            fake.sendKeyState(DEVICE, controlId = 2)
            waitFor("the new client's update") { second.buttons.isNotEmpty() }
            assertTrue(events.buttons.isEmpty(), "a disconnected client receives nothing further")
        }
    }

    @Test
    fun `a dropped connection is retried and registration happens again`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)
            assertEquals(1, fake.connectionCount)

            fake.dropConnection()
            waitFor("a second connection") { fake.connectionCount >= 2 }
            waitFor("re-registration") { fake.linesStartingWith("ADD-DEVICE").size >= 2 }
            waitFor("CONNECTED again") { events.status == CompanionConnectionStatus.CONNECTED }
        }
    }

    @Test
    fun `connecting again supersedes the previous session`() {
        FakeCompanion().use { fake ->
            val events = Events()
            val c = connected(fake, events)
            c.connect("127.0.0.1", fake.port, "second-device", rows = 1, columns = 1, bitmapSize = 72, reconnectDelayMs = 100)
            waitFor("the second registration") {
                fake.linesStartingWith("ADD-DEVICE").any { it.contains("second-device") }
            }
            waitFor("CONNECTED on the new session") { events.status == CompanionConnectionStatus.CONNECTED }

            fake.sendKeyState("second-device", controlId = 0)
            waitFor("an update on the new session") { events.buttons.isNotEmpty() }
            assertNotNull(events.buttons.first())
        }
    }

    @Test
    fun `an unknown command is ignored rather than breaking the session`() {
        FakeCompanion().use { fake ->
            val events = Events()
            connected(fake, events)

            // REMOVE-DEVICE / DEVICE-CONFIG / CAPS all arrive in practice and need no action.
            fake.sendRaw("DEVICE-CONFIG DEVICEID=\"$DEVICE\" SOMETHING=1 ")
            fake.sendKeyState(DEVICE, controlId = 4)
            waitFor("the following update") { events.buttons.isNotEmpty() }
            assertEquals(4, events.buttons.single().index, "the session survives an unknown command")
        }
    }
}
