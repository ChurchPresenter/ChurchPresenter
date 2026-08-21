@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the Companion Server card: port, host override, API key protection and its key, file
 * uploads and the upload size cap.
 *
 * Everything here works against a **stopped** server, which is the state an operator configures in —
 * the port and host cannot be changed while it is running anyway. Each test asserts the value written
 * into [ServerSettings], which is what reaches `settings.json`, and the change it makes on screen.
 *
 * The port, host and API key fields each keep a `remember(...)`-keyed local copy of their setting, so
 * a field showing the typed text proves nothing on its own; the round trips below go through a fresh
 * render of the saved settings instead.
 */
class ServerSettingsTabTest {

    // ── Port ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the port field starts on the stored port`() = serverTab { get, _ ->
        assertEquals(Constants.SERVER_DEFAULT_PORT, get().serverSettings.port, "the default port is stored")
        assertFieldShows(Constants.SERVER_DEFAULT_PORT.toString(), "the port field")
    }

    @Test
    fun `a typed port is stored`() = serverTab { get, _ ->
        retypeField(Constants.SERVER_DEFAULT_PORT.toString(), "9123")
        assertEquals(9123, get().serverSettings.port, "the typed port must be stored")
    }

    @Test
    fun `a typed port is what a fresh render of the saved settings shows`() {
        var saved = 0
        serverTab { get, _ ->
            retypeField(Constants.SERVER_DEFAULT_PORT.toString(), "9124")
            saved = get().serverSettings.port
        }
        assertEquals(9124, saved, "the port must have been stored to be re-rendered")
        serverTab(initial = serverSettings { copy(port = saved) }) { _, _ ->
            assertFieldShows("9124", "the port field on a fresh render")
        }
    }

    /**
     * The field refuses non-digits at the keystroke rather than accepting and then discarding them:
     * `onValueChange` only takes the text when it is all digits, so nonsense never reaches the box
     * at all. Asserted on the box as well as on the setting, because those are two different claims.
     */
    @Test
    fun `the port field refuses anything that is not digits`() = serverTab { get, _ ->
        retypeField(Constants.SERVER_DEFAULT_PORT.toString(), "not-a-port")
        assertFieldShows(
            Constants.SERVER_DEFAULT_PORT.toString(),
            "the port field after nonsense was typed into it",
        )
        assertEquals(Constants.SERVER_DEFAULT_PORT, get().serverSettings.port, "and the stored port is untouched")

        // The accepted value proves the field was live for the rejected one too.
        retypeField(Constants.SERVER_DEFAULT_PORT.toString(), "9125")
        assertEquals(9125, get().serverSettings.port, "a real port must still land")
    }

    /** Five digits is the cap, so a six-digit port is refused the same way. */
    @Test
    fun `the port field refuses more than five digits`() = serverTab { get, _ ->
        retypeField(Constants.SERVER_DEFAULT_PORT.toString(), "123456")
        assertEquals(Constants.SERVER_DEFAULT_PORT, get().serverSettings.port, "six digits must be refused")

        retypeField(Constants.SERVER_DEFAULT_PORT.toString(), "65535")
        assertEquals(65535, get().serverSettings.port, "five digits must be accepted")
    }

    // ── Host override ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the host override starts blank and stores what is typed`() = serverTab { get, _ ->
        assertEquals("", get().serverSettings.serverHost, "no host override out of the box")

        fieldShowing("").performScrollTo()
        retypeField("", "192.168.1.50")
        assertEquals("192.168.1.50", get().serverSettings.serverHost, "the typed host must be stored")
    }

    @Test
    fun `a typed host is what a fresh render of the saved settings shows`() {
        var saved = ""
        serverTab { get, _ ->
            fieldShowing("").performScrollTo()
            retypeField("", "church-mac.local")
            saved = get().serverSettings.serverHost
        }
        assertEquals("church-mac.local", saved, "the host must have been stored to be re-rendered")
        serverTab(initial = serverSettings { copy(serverHost = saved) }) { _, _ ->
            assertFieldShows("church-mac.local", "the host field on a fresh render")
        }
    }

    // ── API key protection ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the API key switch turns protection on and off`() = serverTab { get, _ ->
        assertEquals(false, get().serverSettings.apiKeyEnabled, "protection is off out of the box")
        serverSwitch(Switch.API_KEY).assertIsOff()

        serverSwitch(Switch.API_KEY).performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().serverSettings.apiKeyEnabled, "switching on must be stored")
        serverSwitch(Switch.API_KEY).assertIsOn()

        serverSwitch(Switch.API_KEY).performClick()
        waitForIdle()
        assertEquals(false, get().serverSettings.apiKeyEnabled, "switching off must be stored too")
        serverSwitch(Switch.API_KEY).assertIsOff()
    }

    /** The key field and its two buttons only exist once protection is switched on. */
    @Test
    fun `switching protection on reveals the key field and its buttons`() = serverTab { _, _ ->
        assertEquals(0, countOf(ServerLabel.API_KEY), "no key row while protection is off")

        serverSwitch(Switch.API_KEY).performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, countOf(ServerLabel.API_KEY), "the key row must appear")
        assertEquals(1, countOf("Generate"), "with a Generate button")
        assertEquals(1, countOf("Copy"), "and a Copy button")
    }

    /** The host override is filled in by the fixture so the key field is the only blank one. */
    @Test
    fun `a typed API key is stored`() {
        val fx = serverSettings { copy(apiKeyEnabled = true, serverHost = "host.local") }
        serverTab(initial = fx) { get, _ ->
            retypeField("", "hunter2-secret")
            assertEquals("hunter2-secret", get().serverSettings.apiKey, "the typed key must be stored")
            assertEquals("host.local", get().serverSettings.serverHost, "the host must be untouched")
        }
    }

    @Test
    fun `Generate puts a new key in the settings and on screen`() {
        serverTab(initial = serverSettings { copy(apiKeyEnabled = true) }) { get, _ ->
            assertEquals("", get().serverSettings.apiKey, "no key out of the box")

            serverButton("Generate").performScrollTo().performClick()
            waitForIdle()

            val generated = get().serverSettings.apiKey
            assertTrue(generated.isNotBlank(), "Generate must store a key, was \"$generated\"")
            assertFieldShows(generated, "the key field after generating")
        }
    }

    @Test
    fun `Generate produces a different key each time`() {
        serverTab(initial = serverSettings { copy(apiKeyEnabled = true) }) { get, _ ->
            serverButton("Generate").performScrollTo().performClick()
            waitForIdle()
            val first = get().serverSettings.apiKey

            serverButton("Generate").performClick()
            waitForIdle()
            val second = get().serverSettings.apiKey

            assertTrue(second.isNotBlank(), "the second key must exist")
            assertTrue(first != second, "regenerating must not hand back the same key")
        }
    }

    /**
     * Copy writes to the machine's clipboard, so its effect is deliberately not asserted — a test
     * that read the clipboard back would be asserting on the developer's desktop. That it is present
     * and clickable is what can honestly be checked.
     */
    @Test
    fun `the Copy button is offered beside a generated key`() {
        serverTab(initial = serverSettings { copy(apiKeyEnabled = true, apiKey = "abc123") }) { _, _ ->
            serverButton("Copy").performScrollTo().assertExists("Copy must be offered")
            assertFieldShows("abc123", "the key field")
        }
    }

    // ── File upload ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the file upload switch turns uploads on and off`() = serverTab { get, _ ->
        assertEquals(false, get().serverSettings.fileUploadEnabled, "uploads are off out of the box")
        serverSwitch(Switch.FILE_UPLOAD).assertIsOff()

        serverSwitch(Switch.FILE_UPLOAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().serverSettings.fileUploadEnabled, "switching on must be stored")
        serverSwitch(Switch.FILE_UPLOAD).assertIsOn()

        serverSwitch(Switch.FILE_UPLOAD).performClick()
        waitForIdle()
        assertEquals(false, get().serverSettings.fileUploadEnabled, "switching off must be stored too")
    }

    /** The size cap only exists once uploads are allowed. */
    @Test
    fun `switching uploads on reveals the size cap`() = serverTab { get, _ ->
        assertEquals(0, countOf(ServerLabel.MAX_UPLOAD), "no cap while uploads are off")

        serverSwitch(Switch.FILE_UPLOAD).performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, countOf(ServerLabel.MAX_UPLOAD), "the cap must appear")
        assertFieldShows(
            Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(),
            "the cap on its default",
        )
        assertEquals(
            Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB,
            get().serverSettings.maxMediaUploadMb,
            "and the stored default must be unchanged by revealing it",
        )
    }

    @Test
    fun `a typed upload cap is stored`() {
        serverTab(initial = serverSettings { copy(fileUploadEnabled = true) }) { get, _ ->
            retypeField(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(), "512")
            assertEquals(512, get().serverSettings.maxMediaUploadMb, "the typed cap must be stored")
        }
    }

    @Test
    fun `the upload cap refuses anything that is not digits`() {
        serverTab(initial = serverSettings { copy(fileUploadEnabled = true) }) { get, _ ->
            retypeField(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(), "lots")
            assertFieldShows(
                Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(),
                "the cap field after nonsense was typed into it",
            )
            assertEquals(
                Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB,
                get().serverSettings.maxMediaUploadMb,
                "and the stored cap is untouched",
            )

            retypeField(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(), "256")
            assertEquals(256, get().serverSettings.maxMediaUploadMb, "a real number must still land")
        }
    }

    /** The same five-digit ceiling the port has. */
    @Test
    fun `the upload cap refuses more than five digits`() {
        serverTab(initial = serverSettings { copy(fileUploadEnabled = true) }) { get, _ ->
            retypeField(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(), "123456")
            assertEquals(
                Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB,
                get().serverSettings.maxMediaUploadMb,
                "six digits must be refused",
            )

            retypeField(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(), "99999")
            assertEquals(99999, get().serverSettings.maxMediaUploadMb, "five digits must be accepted")
        }
    }

    /**
     * Zero is a digit but not a size — the callback takes only values above zero.
     *
     * The accepted value afterwards is what makes this mean anything: "the cap did not change" holds
     * just as well against a field wired to nothing, so on its own it would pass either way.
     */
    @Test
    fun `an upload cap of zero is not stored`() {
        serverTab(initial = serverSettings { copy(fileUploadEnabled = true) }) { get, _ ->
            retypeField(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString(), "0")
            assertEquals(
                Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB,
                get().serverSettings.maxMediaUploadMb,
                "a cap of zero must be refused",
            )

            retypeField("0", "1")
            assertEquals(1, get().serverSettings.maxMediaUploadMb, "but one megabyte is a size, and lands")
        }
    }

    // ── Independence ────────────────────────────────────────────────────────────────────────────

    /**
     * The three switches write three different flags through the same `s.copy(serverSettings = ...)`
     * shape, which is the arrangement a mis-typed field name hides in.
     */
    @Test
    fun `each switch writes only its own flag`() = serverTab { get, _ ->
        serverSwitch(Switch.API_KEY).performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().serverSettings.apiKeyEnabled)
        assertEquals(false, get().serverSettings.fileUploadEnabled, "uploads must be untouched")
        assertEquals(false, get().serverSettings.enabled, "the server flag must be untouched")

        serverSwitch(Switch.FILE_UPLOAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().serverSettings.fileUploadEnabled)
        assertEquals(true, get().serverSettings.apiKeyEnabled, "protection must survive")
    }
}
