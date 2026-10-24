@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.dialogs.QARemoteDialog
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import kotlin.test.Test

/** The Q&A tour: the server, sharing with phones, moderating questions, going live and sessions. */
class QADemoTour {

    private val companion = CompanionServer(shutdownGraceMs = 0)

    @Test
    fun qa() {
        TestSingletons.latchToTestHome()
        writeQuestions()
        recordTour(
            "qa",
            tab = "Q&A",
            liveLabel = "Answer a question",
            steps = 16,
            settings = {
                it.copy(serverSettings = it.serverSettings.copy(port = SERVER_PORT, serverHost = "192.168.1.42"))
            },
            panel = { settings, change ->
                if (dialog.value == "server") {
                    Box(Modifier.size(600.dp, 440.dp)) {
                        ServerSettingsTab(settings, change, companion, RemoteClientManager())
                    }
                } else {
                    Box(Modifier.size(760.dp, 640.dp)) {
                        QARemoteDialog(
                            serverUrl = "http://192.168.1.42:8080",
                            qaDisplayUrl = "",
                            onQaDisplayUrlChanged = {},
                            apiKeyEnabled = true,
                            apiKey = "sunday-7f3a",
                            tunnelStatus = TunnelStatus.Idle,
                            tunnelUrl = "",
                            onStartTunnel = {},
                            onStopTunnel = {},
                            qaSettings = settings.qaSettings,
                            onSettingsChange = change,
                            onDismiss = {},
                            frame = { _, content -> content() },
                        )
                    }
                }
            },
        ) { qaTour() }
        companion.stop()
    }

    private fun DemoTour.qaTour() {
        say("intro")
        intro("Let the congregation send in questions from their phones.")
        say("open-tab")
        openTab("Q&A", "Questions from the congregation")
        server()
        sharing()
        say("show-qr")
        note(
            "Show the QR code on screen",
            "Everyone can scan it from their seat",
            inTab(hasContentDescription("Show QR on Display")),
        )
        say("voting")
        step(
            "Voting",
            "Let people vote for the questions they want answered",
            inTab(hasContentDescription("Voting", substring = true)),
        )
        moderate()
        live()
        say("history")
        step("History", "Questions from earlier sessions", inTab(hasText("History", substring = true)))
        pause(1.5)
        say("new-session")
        step("New Session", "Start fresh for this service", inTab(hasText("New Session")))
        say("add-own")
        val field = find(hasSetTextAction()) { it.left > dp(380f) }
        type("Add your own question", "Type it in and click Add", field, OWN_QUESTION)
        say("add-click")
        step("Add", "It joins the list like any other", inTab(hasText("Add")))
        say("outro")
        outro()
    }

    /** Settings → Server: Q&A needs the companion server running. */
    private fun DemoTour.server() {
        say("server")
        step(
            "Open Settings",
            "Q&A needs the companion server",
            find(hasContentDescription("Settings")) { it.top < dp(60f) },
        ) {}
        dialog.value = "server"
        openPanel("Settings — Server", 600f, 440f)
        say("server-enable")
        panelNote(
            "Turn on the Companion Server",
            "Phones connect to the app through it",
            panelItem(isToggleable()),
            click = true,
            numbered = true,
        )
        closePanel()
    }

    /** Sharing & Remote Access: the QR codes, church WiFi or public access, and the cooldown. */
    private fun DemoTour.sharing() {
        say("sharing")
        step(
            "Sharing & Remote Access",
            "How phones reach your Q&A",
            inTab(hasContentDescription("Sharing & Remote Access")),
        ) {}
        dialog.value = "qa"
        openPanel("Sharing & Remote Access", 760f, 640f)
        say("qr-scan")
        panelNote(
            "Scan to ask a question",
            "Phones open the question page in their browser",
            panelItem(hasText("Submit Questions")),
        )
        say("admin")
        panelNote("Admin Panel", "Moderate the questions from a phone or tablet", panelReveal(hasText("Admin Panel")))
        say("wifi")
        val local = panelReveal(hasText("http://192.168.1.42:8080/qa"))
        panelNote("Local or public", "Local works for phones on the church WiFi", local)
        say("public")
        panelNote(
            "Public Access",
            "For phones that aren't on the church network",
            panelReveal(hasText("Enable Public Access")),
        )
        say("cooldown")
        panelNote(
            "Cooldown between questions",
            "Stops one phone from sending too many",
            panelReveal(hasText("Cooldown", substring = true, ignoreCase = true)),
        )
        say("qr-message")
        panelNote("QR code message", "The words shown beside the code", panelReveal(hasText("QR Code Message")))
        closePanel()
    }

    /** Approving, denying, editing; filtering and sorting. */
    private fun DemoTour.moderate() {
        say("incoming")
        note("Incoming questions", "With the asker's name and their votes", text("Why does God allow suffering?"))
        say("approve")
        step("Approve", "Ready to be answered", rowButton("Is it wrong to doubt sometimes?", "Approve"))
        say("deny")
        step("Deny", "Hidden from the list", rowButton("Why does God allow suffering?", "Deny"))
        say("edit")
        note("Edit", "Fix a typo or tidy up the wording", rowButton("How should we pray", "Edit"))
        say("delete")
        note("Delete", "Remove it for good", rowButton("How should we pray", "Delete"))
        say("filter")
        note(
            "Filter and sort",
            "Approved, denied, most votes, newest",
            inTab(hasText("SORT", substring = true, ignoreCase = true)),
        )
    }

    /** A question put on screen, then cleared and marked done. */
    private fun DemoTour.live() {
        val row = "Is it wrong to doubt sometimes?"
        say("go-live")
        val live = rowButton(row, "Go Live")
        step("Go Live", "Approved questions go straight up", live, live = true) {
            live.node.performClick()
            ui.waitForIdle()
            settle()
        }
        say("live")
        showLive(sub = "The question is on the big screen")
        say("clear")
        note("Clear Display", "Take it down when it's answered", inTab(hasText("Clear Display")))
    }

    /** The button described [desc] on the row showing [rowText]. */
    private fun DemoTour.rowButton(rowText: String, desc: String): Target {
        val row = text(rowText).bounds
        if (!hasNode(hasContentDescription(desc)) { it.left > dp(380f) && it.center.y in row.top..row.bottom }) {
            val seen = ui.onAllNodes(hasContentDescription("Live", substring = true)).fetchSemanticsNodes()
                .joinToString(" | ") { "${it.config.getOrNull(SemanticsProperties.ContentDescription)}@${it.boundsInWindow}" }
            error("no $desc on row $row; live buttons: $seen")
        }
        return find(hasContentDescription(desc)) { it.left > dp(380f) && it.center.y in row.top..row.bottom }
    }

    private fun DemoTour.inTab(m: SemanticsMatcher) = find(m) { it.left > dp(380f) }
}

private const val SERVER_PORT = 48_767
private const val OWN_QUESTION = "What should we pray for this week?"

/** Which dialog the panel shows: "server" or "qa". */
private val dialog = mutableStateOf("server")
