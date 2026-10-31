@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import org.churchpresenter.stt.transcribe
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test

/**
 * Scripture follow-along: the Bible tab's detection strip, fed by a stand-in for the Bible Lookup
 * Engine — the app's own client connects to it as to a remote engine, and the tour sends what the
 * engine would have heard.
 */
class FollowAlongDemoTour {
    @Test
    fun followAlong() {
        val engine = DemoBibleEngine().apply { start() }
        // Connected before the first capture, so the opening shot already shows it.
        demoStt.applyConnected()
        heard.clear()
        hear("Good morning, church.")
        try {
            recordTour(
                "follow-along",
                tab = "Follow-Along",
                liveLabel = "Follow the sermon",
                steps = 6,
                heading = "Scripture " to "",
                startTab = "STT",
                settings = { s ->
                    s.copy(
                        hiddenTabs = setOf("QA", "SIGNAGE"),
                        sttSettings = s.sttSettings.copy(serverUrl = SERVER_URL),
                        bibleEngineSettings = s.bibleEngineSettings.copy(
                            enabled = true, runLocal = false, host = LOOPBACK, port = engine.port,
                        ),
                    )
                },
            ) { followTour(engine) }
        } finally {
            engine.stop()
        }
    }

    private fun DemoTour.followTour(engine: DemoBibleEngine) {
        say("intro")
        intro("The Bible tab follows the sermon by itself.")
        say("what")
        caption("Scripture detection", "Listens to the live captions")

        segment("setup")
        say("connect")
        openTab("STT", "Connected to your speech-to-text server")
        say("detect")
        note("Detect scripture", "In STT Display Settings — on from the start",
            find(hasContentDescription("STT Display Settings")))
        say("open-tab")
        openTab("Bible", "A detection strip under the search box")
        waitFor("the engine link") { engine.connected() }
        say("listening")
        note("Listening…", "The engine is running", find(hasText("Listening…")))

        segment("detect")
        say("spoken")
        captionWhile("The preacher says", "“Turn with me to John chapter fifteen, verse one.”") {
            hear("Turn with me to John chapter fifteen, verse one.")
            engine.detect(verse = 1, matchType = "explicit")
            engine.send("""{"type":"version_detected","version":"KJV"}""")
            waitFor("John 15:1 detected") { showing("John 15:1") }
            pause(1.0)
        }
        say("item")
        note("John 15:1", "The verse, as it was heard", row())
        say("badges")
        note("Spoken reference", "Heard in the transcription", find(hasContentDescription("Spoken reference")))
        say("version")
        note("KJV", "The translation being read", find(hasText("KJV")) { it.top > row().bounds.top - 1f })
        say("click")
        step("Click it", "Opens the verse, ready to go", row())
        say("double")
        step("Double-click it", "Straight to the screen", row(), double = true, live = true)
        settle()

        segment("follow")
        say("auto")
        step("Auto-follow", "Keeps up by itself", find(hasText("Auto-follow")))
        say("follow")
        captionWhile("Following along", "Each verse goes up as it's read", projector = true) {
            for (verse in 2..4) {
                hear(JOHN_15[verse - 1])
                engine.detect(verse, matchType = "continuation")
                waitFor("John 15:$verse detected") { showing("John 15:$verse") }
                settle()
                pause(1.6)
            }
        }
        say("speed")
        note("Next verse speed", "Balanced, or Fast", find(hasText("Next verse speed", substring = true)))

        segment("quotes")
        say("text-match")
        step("Text match", "Conservative, Balanced or Aggressive", find(hasText("Text match", substring = true)))
        say("clear-screen")
        step("Clear Display", "The screen goes blank", find(hasContentDescription("Clear Display")), live = true)
        giveKeyboardBack()
        say("quote")
        captionWhile("The preacher says", "“For God so loved the world…”") {
            hear("For God so loved the world, that he gave his only begotten Son.")
            engine.detect(verse = 16, chapter = 3, matchType = "reverse")
            waitFor("John 3:16 detected") { showing("John 3:16") }
            pause(1.0)
        }
        say("staged")
        captionWhile("Matched by its words", "Opened in the Bible tab — not on the screen", projector = true) {
            pause(1.0)
        }
        say("tiers")
        caption("Straight to the screen", "Spoken, read in order, or found in this chapter")
        say("send")
        step("Double-click it", "Now it's on the screen", row(), double = true, live = true)
        settle()

        segment("elsewhere")
        say("clear-list")
        step("Clear", "Empties the list", find(hasContentDescription("Clear detected references")), numbered = false)
        say("other-tab")
        openTab("Songs", "Auto-follow keeps working")
        say("other-verse")
        captionWhile("The preacher says", "“Verse five: I am the vine, ye are the branches.”", projector = true) {
            hear(JOHN_15[4])
            engine.detect(verse = 5, matchType = "explicit")
            waitFor("John 15:5 on the screen") { showing("ye are the branches") }
            settle()
            pause(1.6)
        }
        say("tips")
        caption("Tips", "A close mic, and the translation your preacher reads")
        say("outro")
        outro()
    }

    /** The newest detection: the list puts each new one on top. */
    private fun DemoTour.row() = findAll(hasTestTag("bible_detection_row")).minBy { it.bounds.top }

    /** What the speech-to-text server has transcribed so far, the preacher's latest sentence last. */
    private val heard = mutableListOf<String>()

    private fun hear(sentence: String) {
        heard += sentence
        demoStt.transcribe(*heard.toTypedArray())
    }
}

/**
 * A stand-in for the Bible Lookup Engine's `/bible-engine` endpoint on loopback: the app connects to
 * it as to a remote engine, and [detect] sends what the real one would after hearing a verse.
 */
private class DemoBibleEngine {
    private val sessions = CopyOnWriteArrayList<DefaultWebSocketServerSession>()

    var port: Int = 0
        private set

    private val server = embeddedServer(Netty, port = 0, host = LOOPBACK) {
        install(WebSockets)
        routing {
            webSocket("/bible-engine") {
                sessions.add(this)
                try {
                    // An engine fed straight over the socket, with no STT link of its own to report on.
                    send(Frame.Text("""{"type":"engine_status","sttConfigured":false}"""))
                    // The client's tuning messages: read and dropped.
                    incoming.consumeEach { }
                } finally {
                    sessions.remove(this)
                }
            }
        }
    }

    fun start() {
        server.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    fun stop() = server.stop(0, 0)

    fun connected() = sessions.isNotEmpty()

    fun detect(verse: Int, matchType: String, chapter: Int = 15) = send(
        """{"type":"scripture.detected","reference":{"bookId":$JOHN,"chapter":$chapter,"verseStart":$verse},""" +
            """"matchType":"$matchType","tracks":["transcription"]}""",
    )

    fun send(json: String) = runBlocking { sessions.forEach { it.send(Frame.Text(json)) } }
}

private const val LOOPBACK = "127.0.0.1"
private const val SERVER_URL = "http://192.168.1.20:8000"
private const val JOHN = 43

private val JOHN_15 = listOf(
    "I am the true vine, and my Father is the husbandman.",
    "Every branch in me that beareth not fruit he taketh away.",
    "Now ye are clean through the word which I have spoken unto you.",
    "Abide in me, and I in you.",
    "Verse five. I am the vine, ye are the branches.",
)
