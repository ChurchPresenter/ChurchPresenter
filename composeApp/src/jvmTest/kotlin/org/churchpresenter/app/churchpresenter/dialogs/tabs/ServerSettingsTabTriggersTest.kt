@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the Lower third triggers card: the row of copy buttons the operator hands to a Stream Deck.
 *
 * The card lists the Lottie files in the configured lower-third folder, so every test here points the
 * settings at a temporary folder of its own — reusing `withLottieFolder` from the LowerThird tab's
 * support, since both cards read the same folder the same way. It also needs a **running** server:
 * with none, the whole list is replaced by a single line telling the operator to start one. As in
 * `ServerSettingsTabRunningTest`, one server is started for the class rather than per test.
 *
 * **The copy buttons cannot be clicked here.** Each one calls
 * `Toolkit.getDefaultToolkit().systemClipboard`, which throws `HeadlessException` under this suite's
 * headless JVM — verified, not assumed. That is also the only place the trigger URLs appear: they are
 * copied, never rendered. So the URL builders (`apiQuery`, `triggerUrl`, `stillUrl`, `clipUrl`) are
 * unreachable from a test, and what is asserted instead is which buttons each configuration offers —
 * which is what decides which URLs *can* be built.
 */
class ServerSettingsTabTriggersTest {

    companion object {
        private lateinit var server: CompanionServer

        @BeforeClass
        @JvmStatic
        fun startServer() {
            server = CompanionServer()
            server.start(freeServerPort(), "127.0.0.1")
            val deadline = System.currentTimeMillis() + 10_000
            while (!server.isRunning.value && System.currentTimeMillis() < deadline) Thread.sleep(5)
            check(server.isRunning.value) { "the shared test server did not start within 10s" }
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            if (::server.isInitialized) server.stop()
        }
    }

    private fun settingsFor(folder: File, atemHost: String = ""): AppSettings = AppSettings().let {
        it.copy(
            streamingSettings = it.streamingSettings.copy(lowerThirdFolder = folder.absolutePath),
            atemSettings = it.atemSettings.copy(host = atemHost),
        )
    }

    // ── The three states of the list ────────────────────────────────────────────────────────────

    @Test
    fun `a stopped server is explained instead of showing dead URLs`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder)) { _, _ ->
                onNodeWithText(ServerLabel.NO_TRIGGERS_SERVER_OFF).assertExists()
                onNodeWithText("welcome").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a running server with no lower thirds says the folder is empty`() {
        withLottieFolder { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onNodeWithText(ServerLabel.NO_LOWER_THIRDS).assertExists()
                onNodeWithText(ServerLabel.NO_TRIGGERS_SERVER_OFF).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a folder whose json is not a Lottie animation counts as empty`() {
        withLottieFolder("notes.json" to NOT_LOTTIE_JSON) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onNodeWithText(ServerLabel.NO_LOWER_THIRDS)
                    .assertExists("a json that is not an animation must not become a trigger")
                onNodeWithText("notes").assertDoesNotExist()
            }
        }
    }

    /**
     * The two ways the folder itself can be unusable. `lowerThirdFolder.isNotEmpty() && it.isDirectory`
     * guards the listing, and both halves matter: a fresh install has no folder configured at all, and
     * a path can survive in settings after the folder it named has been replaced by a file.
     */
    @Test
    fun `no configured folder counts as empty`() {
        serverTab(initial = AppSettings(), server = server) { _, _ ->
            onNodeWithText(ServerLabel.NO_LOWER_THIRDS)
                .assertExists("an unconfigured folder must read as empty, not crash")
        }
    }

    @Test
    fun `a folder path that is really a file counts as empty`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            val notADirectory = File(folder, "welcome.json")
            serverTab(initial = settingsFor(notADirectory), server = server) { _, _ ->
                onNodeWithText(ServerLabel.NO_LOWER_THIRDS)
                    .assertExists("a file where a folder should be must read as empty")
                onNodeWithText("welcome").assertDoesNotExist()
            }
        }
    }

    // ── The rows ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each lower third gets a row named after its file`() {
        withLottieFolder(
            "welcome.json" to lottieJson("welcome"),
            "offering.json" to lottieJson("offering"),
        ) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onNodeWithText("welcome").assertExists("the row is named by the file, without .json")
                onNodeWithText("offering").assertExists()
                onNodeWithText(ServerLabel.NO_LOWER_THIRDS).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `rows are listed in case-insensitive name order`() {
        withLottieFolder(
            "Zulu.json" to lottieJson("Zulu"),
            "alpha.json" to lottieJson("alpha"),
            "Mike.json" to lottieJson("Mike"),
        ) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                val tops = listOf("alpha", "Mike", "Zulu").map {
                    onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
                }
                assertEquals(tops.sorted(), tops, "rows must be sorted regardless of case")
            }
        }
    }

    /** Without an ATEM configured a row offers exactly the two go-live buttons. */
    @Test
    fun `a row offers a keyed and an unkeyed trigger`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onAllNodesWithText("Go Live + Key").assertCountEquals(1)
                onAllNodesWithText("Go Live").assertCountEquals(1)
            }
        }
    }

    @Test
    fun `every row gets its own pair of buttons`() {
        withLottieFolder(
            "one.json" to lottieJson("one"),
            "two.json" to lottieJson("two"),
            "three.json" to lottieJson("three"),
        ) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onAllNodesWithText("Go Live + Key").assertCountEquals(3)
                onAllNodesWithText("Go Live").assertCountEquals(3)
            }
        }
    }

    // ── The ATEM branch ─────────────────────────────────────────────────────────────────────────

    /**
     * With a switcher configured each row grows the still and clip upload buttons, because those
     * URLs only mean anything when there is an ATEM to upload into.
     */
    @Test
    fun `configuring an ATEM adds the still and clip buttons to every row`() {
        withLottieFolder(
            "welcome.json" to lottieJson("welcome"),
            "offering.json" to lottieJson("offering"),
        ) { folder ->
            serverTab(initial = settingsFor(folder, atemHost = "10.0.0.5"), server = server) { _, _ ->
                onAllNodesWithText("Go Live + Key").assertCountEquals(2)
                onAllNodesWithText("Go Live").assertCountEquals(2)
                // Two more buttons per row once a switcher is configured.
                assertEquals(2, countOf("Still + Key"), "each row must offer a keyed still upload")
                assertEquals(2, countOf("Still only"), "and an unkeyed one")
                assertEquals(2, countOf("Clip + Key"), "plus a keyed clip upload")
                assertEquals(2, countOf("Clip only"), "and an unkeyed one")
            }
        }
    }

    @Test
    fun `no ATEM means no still or clip buttons`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                for (label in listOf("Still + Key", "Still only", "Clip + Key", "Clip only")) {
                    assertEquals(0, countOf(label), "no switcher, so no $label button")
                }
            }
        }
    }

    /**
     * The long explanation of what Clip + Key does is only worth showing once there is a switcher to
     * do it with, so it appears with one configured and not without.
     */
    @Test
    fun `the Clip plus Key explanation appears only with a switcher configured`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder, atemHost = "10.0.0.5"), server = server) { _, _ ->
                assertEquals(1, countOf(CLIP_KEY_NOTE), "the note must appear with a switcher")
            }
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                assertEquals(0, countOf(CLIP_KEY_NOTE), "and not without one")
            }
        }
    }

    private val CLIP_KEY_NOTE =
        "Clip + Key uploads the clip, waits for the ATEM to finish processing it, then turns the key " +
            "on automatically — no Companion delay needed. (Clip only + a separate key still needs a " +
            "manual delay.)"

    // ── The global action row ───────────────────────────────────────────────────────────────────

    /**
     * Below the per-file rows sits a row of actions that belong to no particular lower third: take
     * the current one down, clear every output, and — with a switcher — turn its key on or off.
     * These were missed on a first pass because the per-file rows are what the card is *about*.
     */
    @Test
    fun `the takedown actions are offered whenever the server is running`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onNodeWithText("Hide Lower Third").assertExists("hide takes down only the lower third")
                onNodeWithText("Clear Display").assertExists("clear takes down every output")
            }
        }
    }

    /** They are part of the triggers card, so a stopped server takes them away with the rest. */
    @Test
    fun `the takedown actions are gone while the server is stopped`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder)) { _, _ ->
                onNodeWithText("Hide Lower Third").assertDoesNotExist()
                onNodeWithText("Clear Display").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `the key on and off actions appear only with a switcher configured`() {
        withLottieFolder("welcome.json" to lottieJson("welcome")) { folder ->
            serverTab(initial = settingsFor(folder, atemHost = "10.0.0.5"), server = server) { _, _ ->
                onNodeWithText("Copy Key On").assertExists()
                onNodeWithText("Copy Key Off").assertExists()
            }
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onNodeWithText("Copy Key On").assertDoesNotExist()
                onNodeWithText("Copy Key Off").assertDoesNotExist()
            }
        }
    }

    /** With no lower thirds at all the takedown actions still make sense, and are still offered. */
    @Test
    fun `the takedown actions survive an empty folder`() {
        withLottieFolder { folder ->
            serverTab(initial = settingsFor(folder), server = server) { _, _ ->
                onNodeWithText(ServerLabel.NO_LOWER_THIRDS).assertExists()
                onNodeWithText("Hide Lower Third").assertExists()
                onNodeWithText("Clear Display").assertExists()
            }
        }
    }
}
