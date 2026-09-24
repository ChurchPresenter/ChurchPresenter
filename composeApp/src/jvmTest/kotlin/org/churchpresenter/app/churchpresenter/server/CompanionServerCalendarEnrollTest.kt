package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.testPort
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `POST /api/calendar/enroll`: the one LAN call a phone makes to join the calendar relay. */
class CompanionServerCalendarEnrollTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val enrollUrl get() = "http://127.0.0.1:$port${Constants.ENDPOINT_CALENDAR_ENROLL}"

    @BeforeTest
    fun setUp() {
        server = CompanionServer()
        server.start(port = testPort(39_900))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) delay(25)
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO)
    }

    @AfterTest
    fun tearDown() {
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    private suspend fun enroll(deviceId: String?, body: String) = client.post(enrollUrl) {
        if (deviceId != null) header(Constants.HEADER_DEVICE_ID, deviceId)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `an approved phone is handed its whole enrollment, so there is nothing left to scan`() = runBlocking<Unit> {
        val pending = async { withTimeout(5_000) { server.onCalendarEnroll.first() } }
        val reply = async { enroll("phone-1", """{"deviceName":"Anna's ‮iPhone","code":"48-29 13"}""") }

        val request = pending.await()
        assertEquals("phone-1", request.clientId)
        assertEquals("Anna's iPhone", request.deviceName)
        assertEquals("482913", request.code)
        val reply1 = CalendarEnrollment(
            relayUrl = "https://relay.example/",
            instanceId = "inst-1",
            deviceId = "phone-1",
            deviceToken = "devicetoken-1",
            instanceKey = "instancekey-1",
        ).asReply()
        request.decision.complete(CalendarEnrollDecision.Approved(reply1))

        val response = reply.await()
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue("\"relayUrl\":\"https://relay.example\"" in text, text)
        assertTrue("inst-1" in text && "phone-1" in text, text)
        assertTrue("devicetoken-1" in text && "instancekey-1" in text, text)
    }

    @Test
    fun `a refused phone gets 403`() = runBlocking<Unit> {
        val pending = async { withTimeout(5_000) { server.onCalendarEnroll.first() } }
        val reply = async { enroll("phone-2", """{"deviceName":"x","code":"123456"}""") }

        pending.await().decision.complete(CalendarEnrollDecision.Denied)

        assertEquals(HttpStatusCode.Forbidden, reply.await().status)
    }

    @Test
    fun `a desktop with sync switched off says so, distinctly from a refusal`() = runBlocking<Unit> {
        val pending = async { withTimeout(5_000) { server.onCalendarEnroll.first() } }
        val reply = async { enroll("phone-3", """{"deviceName":"x","code":"123456"}""") }

        pending.await().decision.complete(CalendarEnrollDecision.SyncOff)

        val response = reply.await()
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(ENROLL_ERROR_SYNC_OFF in response.bodyAsText())
    }

    @Test
    fun `a relay the desktop could not reach is a gateway error, not a refusal`() = runBlocking<Unit> {
        val pending = async { withTimeout(5_000) { server.onCalendarEnroll.first() } }
        val reply = async { enroll("phone-4", """{"deviceName":"x","code":"123456"}""") }

        pending.await().decision.complete(CalendarEnrollDecision.RelayFailed)

        assertEquals(HttpStatusCode.BadGateway, reply.await().status)
    }

    @Test
    fun `one open request per device, so a phone cannot pile up prompts`() = runBlocking<Unit> {
        val pending = async { withTimeout(5_000) { server.onCalendarEnroll.first() } }
        val first = async { enroll("phone-5", """{"deviceName":"x","code":"123456"}""") }
        val request = pending.await()

        assertEquals(HttpStatusCode.TooManyRequests, enroll("phone-5", """{"deviceName":"x","code":"123456"}""").status)

        request.decision.complete(CalendarEnrollDecision.Denied)
        assertEquals(HttpStatusCode.Forbidden, first.await().status)
    }

    @Test
    fun `a missing or malformed device id never reaches the operator`() = runBlocking<Unit> {
        assertEquals(HttpStatusCode.BadRequest, enroll(null, """{"deviceName":"x","code":"123456"}""").status)
        assertEquals(HttpStatusCode.BadRequest, enroll("x' OR 1=1", """{"deviceName":"x","code":"123456"}""").status)
        assertEquals(HttpStatusCode.BadRequest, enroll("a".repeat(65), """{"deviceName":"x","code":"123456"}""").status)
    }

    @Test
    fun `the code must be six digits`() = runBlocking<Unit> {
        assertEquals(HttpStatusCode.BadRequest, enroll("phone-3", """{"deviceName":"x","code":"12345"}""").status)
        assertEquals(HttpStatusCode.BadRequest, enroll("phone-3", """{"deviceName":"x"}""").status)
        assertEquals(HttpStatusCode.BadRequest, enroll("phone-3", "not json").status)
    }
}
