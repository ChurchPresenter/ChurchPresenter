package org.churchpresenter.app.churchpresenter.dialogs

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.planningcenter.PlanningCenterAuthServer
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanningCenterConnectFlowTest {

    @BeforeTest
    fun stubClients() {
        mockkObject(PlanningCenterClient)
        mockkObject(PlanningCenterAuthServer)
    }

    @AfterTest
    fun cleanUp() {
        unmockkObject(PlanningCenterClient)
        unmockkObject(PlanningCenterAuthServer)
    }

    private class Recorder {
        var browsedUri: URI? = null
        var connected: List<Any?>? = null
        var error: String? = null
    }

    private fun run(recorder: Recorder = Recorder()): Recorder {
        runBlocking {
            connectToPlanningCenter(
                browse = { recorder.browsedUri = it },
                onConnected = { accessToken, refreshToken, expiresAtEpochMs, personName ->
                    recorder.connected = listOf(accessToken, refreshToken, expiresAtEpochMs, personName)
                },
                onError = { recorder.error = it }
            )
        }
        return recorder
    }

    @Test
    fun `a successful flow browses to the authorization url and connects with the person's name`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Success("auth-code")
        coEvery { PlanningCenterClient.exchangeCodeForToken(any(), any(), "auth-code", any()) } returns
            PlanningCenterClient.TokenOutcome.Success(
                PlanningCenterClient.TokenSet("access-1", "refresh-1", 123456789L)
            )
        coEvery { PlanningCenterClient.getCurrentPerson(any(), any()) } returns
            PlanningCenterClient.PersonOutcome.Success(PlanningCenterClient.ConnectedPerson("Jane Doe"))

        val recorder = run()

        assertTrue(recorder.browsedUri.toString().startsWith("https://"))
        assertEquals(listOf("access-1", "refresh-1", 123456789L, "Jane Doe"), recorder.connected)
        assertNull(recorder.error)
    }

    @Test
    fun `a person lookup failure still connects, with a blank name`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Success("auth-code")
        coEvery { PlanningCenterClient.exchangeCodeForToken(any(), any(), any(), any()) } returns
            PlanningCenterClient.TokenOutcome.Success(
                PlanningCenterClient.TokenSet("access-1", "refresh-1", 123456789L)
            )
        coEvery { PlanningCenterClient.getCurrentPerson(any(), any()) } returns
            PlanningCenterClient.PersonOutcome.NetworkError

        val recorder = run()

        assertEquals(listOf("access-1", "refresh-1", 123456789L, ""), recorder.connected)
    }

    @Test
    fun `invalid client credentials surface their own message`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Success("auth-code")
        coEvery { PlanningCenterClient.exchangeCodeForToken(any(), any(), any(), any()) } returns
            PlanningCenterClient.TokenOutcome.InvalidCredentials

        val recorder = run()

        assertEquals("Invalid client ID or secret", recorder.error)
        assertNull(recorder.connected)
    }

    @Test
    fun `a network error during token exchange surfaces its own message`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Success("auth-code")
        coEvery { PlanningCenterClient.exchangeCodeForToken(any(), any(), any(), any()) } returns
            PlanningCenterClient.TokenOutcome.NetworkError

        val recorder = run()

        assertEquals("Network error — check your connection", recorder.error)
    }

    @Test
    fun `an unspecified token failure surfaces a generic message`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Success("auth-code")
        coEvery { PlanningCenterClient.exchangeCodeForToken(any(), any(), any(), any()) } returns
            PlanningCenterClient.TokenOutcome.Failure

        val recorder = run()

        assertEquals("Connection failed", recorder.error)
    }

    @Test
    fun `a callback error surfaces the server's own message`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Error("access_denied")

        val recorder = run()

        assertEquals("access_denied", recorder.error)
        assertNull(recorder.connected)
    }

    @Test
    fun `a callback timeout surfaces a timeout message`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Timeout

        val recorder = run()

        assertEquals("Timed out waiting for browser sign-in", recorder.error)
    }
}
