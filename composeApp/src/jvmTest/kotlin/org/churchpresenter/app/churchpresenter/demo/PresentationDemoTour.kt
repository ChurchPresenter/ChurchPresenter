@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.xmlbeans.XmlObject
import org.churchpresenter.dialogs.PresentationRemoteDialog
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.slides.data.RecentPresentationFiles
import org.openxmlformats.schemas.presentationml.x2006.main.STTransitionSideDirectionType
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.io.File
import kotlin.test.Test

/** The Presentation tour: an animated deck opened from the recents, presented, blanked and run from a phone. */
class PresentationDemoTour {

    @Test
    fun presentation() {
        seedRecentDecks()
        recordTour(
            "presentation",
            tab = "Presentation",
            liveLabel = "Present your slides",
            steps = 17,
            // A port of its own, so the server switched on in the video never meets a running copy of the app.
            settings = {
                it.copy(serverSettings = it.serverSettings.copy(port = 48_765, serverHost = "192.168.1.42"))
            },
            panel = { settings, change ->
                if (shownPanel.value == "server") {
                    Box(Modifier.size(600.dp, 440.dp)) {
                        ServerSettingsTab(settings, change, companion, RemoteClientManager())
                    }
                    return@recordTour
                }
                Box(Modifier.size(400.dp, 720.dp)) { PresentationRemoteDialog(
                    settings = settings,
                    onSettingsChange = change,
                    serverUrl = "http://192.168.1.42:8080",
                    apiKeyEnabled = true,
                    apiKey = "sunday-7f3a",
                    tunnelStatus = TunnelStatus.Idle,
                    tunnelUrl = "",
                    presentationDisplayUrl = "",
                    onPresentationDisplayUrlChanged = {},
                    onStartTunnel = {},
                    onStopTunnel = {},
                    onDismiss = {},
                    frame = { _, content -> content() },
                ) }
            },
        ) { presentationTour() }
        companion.stop()
    }

    /** A server of the tour's own, on a port nothing else uses; switched on in the video. */
    private val companion = CompanionServer(shutdownGraceMs = 0)

    private fun DemoTour.presentationTour() {
        say("intro")
        intro("Show PowerPoint, Keynote and PDF slides on the big screen.")
        say("open-tab")
        openTab("Presentation", "Your slides, ready to present")
        say("select-file")
        note("Select a file", "PowerPoint, Keynote or PDF", find(hasText("Select File")))
        say("recents")
        val deck = find(hasText(DECK_NAME)) { it.left > dp(380f) }
        step("Open a recent file", "Double-click it to load the slides", deck, double = true) {
            deck.node.performTouchInput { doubleClick() }
            waitFor("the deck is rendered", 30_000L) { hasNode(hasText("Slide 5")) }
        }
        say("click-slide")
        step("Click a slide", "It shows in the preview first", find(hasText("Slide 2")))
        say("go-live")
        goLive(sub = "Sends the slide to the projector")
        say("live-1")
        showLive(sub = "Only the title so far — the points are animated")
        animations()
        say("double-click")
        val four = find(hasText("Slide 4"))
        step("Double-click a slide", "Goes live in one move", four, double = true, live = true) {
            four.node.performTouchInput { doubleClick() }
            ui.waitForIdle()
            settle()
        }
        say("live-3")
        showLive(sub = "Slide four, live")
        visibility()
        slideshow()
        blankScreen()
        remote()
        finish()
    }

    /** The deck's own animations: three points appearing one per press, then a push to the next slide. */
    private fun DemoTour.animations() {
        val area = find(hasText("Slide 2"))
        listOf("first", "second", "third").forEachIndexed { i, which ->
            say("build-$which")
            press(
                if (i == 0) "Play the next animation" else "And the next",
                "Each press of → plays one step",
                area, Key.DirectionRight, "→", live = true,
            ) { settle() }
            say("built-$which")
            showLive(
                if (i == 2) "Every point is in" else "Point ${i + 1} appears",
                "Animated, just as in PowerPoint",
            )
        }
        say("transition-slide")
        press("On to the next slide", "Its slide transition plays", area, Key.DirectionRight, "→", live = true) {
            settle()
        }
        say("live-2")
        showLive("A push transition", "Straight from the original file")
    }

    /** Hiding a slide, and blanking only the output (the eye in the top bar). */
    private fun DemoTour.visibility() {
        say("hide")
        val five = find(hasText("Slide 5")).bounds
        val eye = find(hasContentDescription("Hide — skipped by Next and the slideshow")) {
            it.center.y in five.top - dp(10f)..five.bottom + dp(10f) && it.left > five.left
        }
        step("Hide a slide", "Next and the slideshow skip it", eye)
    }

    private fun DemoTour.slideshow() {
        say("play")
        note("Play the slides", "They advance on their own", find(hasContentDescription("Play")))
        say("interval")
        note("How long each slide stays", "Seconds before the next one", text("AUTO-SCROLL INTERVAL"))
        say("transition")
        note("Transition duration", "How long the change between slides takes", text("TRANSITION DURATION"))
        say("loop")
        val loop = find(hasContentDescription("Loop", substring = true))
        note("Loop the deck", "It starts again after the last slide", loop)
        say("animation")
        step("Choose the transition", "Crossfade, Fade, Slide and more", text("Crossfade"))
        say("fade")
        step("Fade", "A gentle fade between slides", menuItem("Fade"))
    }

    private fun DemoTour.blankScreen() {
        say("blank")
        val blank = find(hasContentDescription("Blank Output"))
        step("Blank the screen", "The audience sees nothing while you get ready", blank, live = true) {
            blank.node.performClick()
            ui.waitForIdle()
        }
        say("blanked")
        showLive("Blanked", "The slide is still waiting underneath")
        say("unblank")
        val unblank = find(hasContentDescription("Unblank Output"))
        step("Bring it back", "One click and the slide returns", unblank, live = true) {
            unblank.node.performClick()
            ui.waitForIdle()
        }
        say("keys")
        note("Keyboard shortcuts", "Space plays and pauses; B or . blanks the screen", text("blank screen"))
    }

    /** The Remote Control dialog, opened as its own window, walked through control by control. */
    private fun DemoTour.remote() {
        server()
        say("remote")
        val button = find(hasContentDescription("Open Remote Control"))
        // The real button opens a separate window; the tour shows the same dialog beside the app instead.
        step("Open Remote Control", "Run the slides from a phone or tablet", button) {}
        shownPanel.value = "remote"
        openPanel("Remote Control", 400f, 720f)
        say("remote-enable")
        panelNote(
            "Enable Remote Control",
            "Lets phones and tablets change the slides",
            panelItem(hasText("Enable Remote Control")),
            click = true,
            numbered = true,
        )
        say("remote-qr")
        panelNote("Scan the QR code", "The phone opens the remote in its browser", panelItem(hasText("QR Code shows:")))
        say("remote-copy")
        panelNote("Or copy the address", "Send it to whoever is presenting", panelItem(hasText("Copy URL")))
        say("remote-key")
        panelNote(
            "Protected by your API key",
            "Only phones with the link can control the slides",
            panelItem(hasText("Remote access uses", substring = true)),
        )
        say("remote-public")
        panelNote(
            "Public Access",
            "For devices that are not on the church WiFi",
            panelItem(hasText("Public Access")),
        )
        closePanel()
    }

    /** Settings → Server: the companion server Remote Control needs, switched on. */
    private fun DemoTour.server() {
        say("server")
        val gear = find(hasContentDescription("Settings")) { it.top < dp(60f) }
        step("Open Settings", "Remote Control needs the companion server", gear) {}
        shownPanel.value = "server"
        openPanel("Settings — Server", 600f, 440f)
        say("server-enable")
        panelNote(
            "Turn on the Companion Server",
            "Phones and tablets connect to the app through it",
            panelItem(isToggleable()),
            click = true,
            numbered = true,
        )
        waitFor("the server is running", 15_000L) { companion.isRunning.value }
        say("server-key")
        panelNote(
            "API Key Protection",
            "Only devices with the key can connect",
            panelItem(hasText("API Key Protection")),
        )
        closePanel()
    }

    private fun DemoTour.finish() {
        say("preset")
        note(
            "Save a preset",
            "A named shortcut to this deck for any future service",
            find(hasContentDescription("Save preset")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule")
        step(
            "Add the deck to the schedule",
            "Bring it back during the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule-row")
        val row = reveal { findAll(hasText("Good Shepherd", substring = true)) { it.right < dp(380f) }.first() }
        note("Added to your schedule", "Run the whole service from this list", row)
        say("clear")
        val clear = find(hasContentDescription("Clear Presentation"))
        note("Clear the presentation", "Closes the deck when you're done", clear)
        say("outro")
        outro()
    }
}

private const val DECK_NAME = "Good Shepherd.pptx"

/** Which dialog the panel shows: "server" or "remote". */
private val shownPanel = mutableStateOf("server")

/** The animated sermon deck, then the recents that name it first. */
private fun seedRecentDecks() {
    library()
    val deck = File(LIBRARY, "Decks/$DECK_NAME")
    writeAnimatedDeck(deck)
    RecentPresentationFiles.files.clear()
    RecentPresentationFiles.pinned.clear()
    RecentPresentationFiles.files.addAll(listOf(deck.absolutePath, File(LIBRARY, "Decks/Sermon.pdf").absolutePath))
}

private val NAVY = Color(0x14, 0x22, 0x3A)
private val GOLD = Color(0xF2, 0xC9, 0x4C)

/**
 * A five-slide sermon deck with real PowerPoint animations: slide 2's three points build in one per
 * click (fade, by paragraph), slide 3 arrives with a push transition and fades its reference in,
 * slide 4 with a fade transition.
 */
private fun writeAnimatedDeck(file: File) {
    XMLSlideShow().use { ppt ->
        ppt.pageSize = Dimension(960, 540)
        ppt.slide { s ->
            s.text("The Good Shepherd", Rectangle(80, 170, 800, 110), 66.0, Color.WHITE, bold = true)
            s.text("Psalm 23 · Sunday Morning", Rectangle(80, 290, 800, 60), 28.0, GOLD)
        }
        ppt.slide { s ->
            s.text("What the Shepherd does", Rectangle(80, 60, 800, 80), 44.0, GOLD, bold = true)
            val points = s.text("He leads me beside still waters", Rectangle(110, 180, 760, 280), 34.0, Color.WHITE)
            points.addNewTextParagraph().addNewTextRun().apply {
                setText("He restores my soul")
                fontSize = 34.0
                setFontColor(Color.WHITE)
            }
            points.addNewTextParagraph().addNewTextRun().apply {
                setText("He walks with me through the valley")
                fontSize = 34.0
                setFontColor(Color.WHITE)
            }
            points.textParagraphs.forEach { it.spaceAfter = 24.0 }
            addBuilds(s, points.shapeId.toLong(), listOf(0, 1, 2))
        }
        ppt.slide { s ->
            val verse = "The LORD is my shepherd;\nI shall not want."
            s.text(verse, Rectangle(80, 150, 800, 200), 48.0, Color.WHITE, bold = true)
            val ref = s.text("Psalm 23:1", Rectangle(80, 360, 800, 60), 30.0, GOLD)
            s.xmlObject.addNewTransition().addNewPush().dir = STTransitionSideDirectionType.L
            addBuilds(s, ref.shapeId.toLong(), emptyList())
        }
        ppt.slide { s ->
            s.text("Response", Rectangle(80, 170, 800, 100), 60.0, Color.WHITE, bold = true)
            s.text("Take a moment to pray", Rectangle(80, 280, 800, 60), 30.0, GOLD)
            s.xmlObject.addNewTransition().addNewFade()
        }
        ppt.slide { s ->
            s.text("Thank you for joining us", Rectangle(80, 190, 800, 100), 54.0, Color.WHITE, bold = true)
            s.text("See you next Sunday", Rectangle(80, 290, 800, 60), 30.0, GOLD)
            s.xmlObject.addNewTransition().addNewFade()
        }
        file.parentFile.mkdirs()
        file.outputStream().use { ppt.write(it) }
    }
}

private fun XMLSlideShow.slide(build: (XSLFSlide) -> Unit) {
    val slide = createSlide()
    slide.background.fillColor = NAVY
    build(slide)
}

private fun XSLFSlide.text(
    text: String,
    at: Rectangle,
    size: Double,
    color: Color,
    bold: Boolean = false,
): XSLFTextShape {
    val box = createTextBox()
    box.anchor = at
    box.clearText()
    text.split("\n").forEachIndexed { i, line ->
        val first = box.textParagraphs.firstOrNull()
        val paragraph = if (i == 0 && first != null) first else box.addNewTextParagraph()
        paragraph.addNewTextRun().apply {
            setText(line)
            fontSize = size
            setFontColor(color)
            isBold = bold
        }
    }
    return box
}

/**
 * Click-triggered fade builds on [shapeId] — one per paragraph in [paragraphs], or the whole shape
 * when it is empty — written as the `<p:timing>` PowerPoint itself saves.
 */
private fun addBuilds(slide: XSLFSlide, shapeId: Long, paragraphs: List<Int>) {
    val targets = if (paragraphs.isEmpty()) {
        listOf("""<p:spTgt spid="$shapeId"/>""")
    } else {
        paragraphs.map { """<p:spTgt spid="$shapeId"><p:txEl><p:pRg st="$it" end="$it"/></p:txEl></p:spTgt>""" }
    }
    var id = 10
    val steps = targets.joinToString("") { tgt ->
        val ids = List(5) { id++ }
        """<p:par><p:cTn id="${ids[0]}" fill="hold"><p:stCondLst><p:cond delay="indefinite"/></p:stCondLst>""" +
            """<p:childTnLst><p:par><p:cTn id="${ids[1]}" fill="hold">""" +
            """<p:stCondLst><p:cond delay="0"/></p:stCondLst>""" +
            """<p:childTnLst><p:par><p:cTn id="${ids[2]}" presetID="10" presetClass="entr" presetSubtype="0" """ +
            """fill="hold" grpId="0" nodeType="clickEffect"><p:stCondLst><p:cond delay="0"/></p:stCondLst>""" +
            """<p:childTnLst><p:set><p:cBhvr><p:cTn id="${ids[3]}" dur="1" fill="hold"><p:stCondLst>""" +
            """<p:cond delay="0"/></p:stCondLst></p:cTn><p:tgtEl>$tgt</p:tgtEl><p:attrNameLst>""" +
            """<p:attrName>style.visibility</p:attrName></p:attrNameLst></p:cBhvr><p:to><p:strVal val="visible"/>""" +
            """</p:to></p:set><p:animEffect transition="in" filter="fade">""" +
            """<p:cBhvr><p:cTn id="${ids[4]}" dur="500"/>""" +
            """<p:tgtEl>$tgt</p:tgtEl></p:cBhvr></p:animEffect></p:childTnLst></p:cTn></p:par></p:childTnLst>""" +
            """</p:cTn></p:par></p:childTnLst></p:cTn></p:par>"""
    }
    val timing = """<p:timing xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" """ +
        """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:tnLst><p:par>""" +
        """<p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"><p:childTnLst>""" +
        """<p:seq concurrent="1" nextAc="seek"><p:cTn id="2" dur="indefinite" nodeType="mainSeq">""" +
        """<p:childTnLst>$steps</p:childTnLst></p:cTn></p:seq></p:childTnLst></p:cTn></p:par></p:tnLst></p:timing>"""
    val source = XmlObject.Factory.parse(timing).newCursor()
    source.toFirstChild()
    val target = slide.xmlObject.newCursor()
    target.toEndToken()
    source.copyXml(target)
    source.close()
    target.close()
}
