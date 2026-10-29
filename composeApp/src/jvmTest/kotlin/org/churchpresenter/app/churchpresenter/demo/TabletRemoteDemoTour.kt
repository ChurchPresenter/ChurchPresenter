@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.RemoteControlFlows
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.app.churchpresenter.screenshot.library
import org.churchpresenter.dialogs.PresentationRemoteDialog
import org.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.dialogs.resolveRemoteEventPresentation
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.server.broadcastSlideChange
import org.churchpresenter.server.updatePresentation
import org.churchpresenter.server.updatePresentationLiveStatus
import org.churchpresenter.server.updatePresentationRemoteSettings
import org.churchpresenter.slides.data.RecentPresentationFiles
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.awt.Dimension
import java.awt.Color as AwtColor
import java.awt.Rectangle
import kotlin.test.Test

/**
 * Changing the slides from a tablet: the deck loaded and Remote Control switched on at the church
 * computer, the remote's own web page opened on the tablet and allowed, then the slides moved from it
 * while the big screen follows. The tablet's screen is the real page, served by the tour's own
 * companion server and drawn by headless Chrome at a tablet's size; each tap sends what the page's
 * button sends.
 */
class TabletRemoteDemoTour {
    private val companion = CompanionServer(shutdownGraceMs = 0)
    private val watcher = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val http = HttpClient.newHttpClient()

    @Test
    fun tabletRemote() {
        library()
        val sermon = File(LIBRARY, "Decks/$DECK")
        easter.parentFile.deleteRecursively()
        writeDeck(sermon, DECK_SLIDES)
        writeDeck(easter, EASTER_SLIDES)
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
        RecentPresentationFiles.files.add(sermon.absolutePath)
        profile.deleteRecursively()
        companion.start(PORT)
        // The computer's question, put to the tour's panel; a tablet already allowed goes straight through.
        watcher.launch {
            companion.onPresentationRemoteConnect.collect { request ->
                if (request.clientId in allowed) request.decision.complete(true) else asking.value = request.decision
                lastClient = request.clientId
            }
        }
        try {
            recordTour(
                "tablet-remote",
                tab = "Slides from a Tablet",
                liveLabel = "Changed from the tablet",
                steps = 9,
                heading = "" to "",
                settings = {
                    it.copy(
                        serverSettings = it.serverSettings.copy(port = PORT, serverHost = SHOWN_HOST),
                        presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = false),
                    )
                },
                // What the app's own window publishes and listens to, so the page sees the deck and moves it.
                publish = {
                    it.copy(
                        onPresentationSlidesLoaded = { id, path, name, type, slides, notes ->
                            if (shown?.id != id) shown = Shown(id, 0, slides.size, false)
                            companion.updatePresentation(id, path, name, type, slides, notes)
                        },
                        onSlideChanged = { id, index, total, playing ->
                            shown = Shown(id, index, total, playing)
                            companion.broadcastSlideChange(id, index, total, playing)
                        },
                    )
                },
                flows = RemoteControlFlows(
                    remotePresentationGotoFlow = companion.onPresentationGoto,
                    uploadPresentationFlow = companion.onPresentationUploaded,
                ),
                panel = { settings, change ->
                    LaunchedEffect(settings.presentationRemoteSettings) {
                        companion.updatePresentationRemoteSettings(settings.presentationRemoteSettings, apiKey = "")
                    }
                    val (w, h) = when (view.value) {
                        DIALOG -> DIALOG_W to DIALOG_H
                        ASK -> ASK_W to ASK_H
                        else -> PANEL_W to PANEL_H
                    }
                    Box(Modifier.size(w.dp, h.dp), contentAlignment = Alignment.Center) {
                        when (view.value) {
                            DIALOG -> Box(Modifier.size(DIALOG_W.dp, DIALOG_H.dp)) {
                                PresentationRemoteDialog(
                                    settings = settings,
                                    onSettingsChange = change,
                                    serverUrl = "http://$SHOWN_HOST:$PORT",
                                    apiKeyEnabled = false,
                                    apiKey = "",
                                    tunnelStatus = TunnelStatus.Idle,
                                    tunnelUrl = "",
                                    presentationDisplayUrl = "",
                                    onPresentationDisplayUrlChanged = {},
                                    onStartTunnel = {},
                                    onStopTunnel = {},
                                    onDismiss = {},
                                    frame = { _, content -> content() },
                                )
                            }
                            ASK -> Row(
                                horizontalArrangement = Arrangement.spacedBy(PAIR_GAP_DP.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val prompt = Modifier.size(PROMPT_W.dp, PROMPT_H.dp)
                                    .semantics { contentDescription = PROMPT_LABEL }
                                Box(prompt) {
                                    ConnectRequest()
                                }
                                Tablet(TABLET_SMALL_W)
                            }
                            else -> Tablet(TABLET_W)
                        }
                    }
                },
            ) { remoteTour() }
        } finally {
            watcher.cancel()
            companion.stop()
        }
    }

    private fun DemoTour.remoteTour() {
        say("intro")
        intro("The speaker runs the slides from the pulpit.")
        segment("deck")
        say("tab")
        openTab("Presentation", "On the church computer")
        say("recent")
        val deck = find(hasText(DECK)) { it.left > dp(TAB_AREA_LEFT_DP) }
        step("A recent file", "Double-click it", deck, double = true, numbered = true) {
            deck.node.performTouchInput { doubleClick() }
            waitFor("the deck is rendered", DECK_WAIT_MS) { hasNode(hasText("Slide 5")) }
        }
        say("remote-open")
        val remote = find(hasContentDescription("Open Remote Control"))
        step("Remote Control", "At the top of the slides", remote, numbered = true) {}
        view.value = DIALOG
        openPanel("Remote Control", DIALOG_W, DIALOG_H)
        say("enable")
        val enable = panelItem(hasText("Enable Remote Control"))
        panelNote("Enable Remote Control", "Switch it on", enable, click = true, numbered = true)
        say("qr")
        panelNote("The QR code", "Scan it with the tablet", qrCode())
        closePanel()
        segment("connect")
        snap()
        view.value = ASK
        openPanel("Computer and tablet", ASK_W, ASK_H)
        say("opens")
        panelNote("The remote, in the browser", "Waiting for the computer", tablet())
        say("approve")
        panelNote("Allow this tablet?", "On the church computer", panelItem(hasContentDescription(PROMPT_LABEL)))
        say("allow")
        val session = panelItem(hasContentDescription("Allow for Session"))
        panelNote("Allow for Session", "Until ChurchPresenter closes", session, click = true, numbered = true)
        snap()
        closePanel()
        view.value = TABLET
        openPanel("Tablet", PANEL_W, PANEL_H)
        say("page")
        panelNote("The slides, on the tablet", "Now, next, and every slide", tablet())
        closePanel()
        segment("present")
        say("go-live")
        goLive(sub = "The slides on the big screen")
        companion.updatePresentationLiveStatus(true)
        tap("forward", "forward-live", "Forward", FORWARD) { slide + 1 }
        tap("jump", "jump-live", "Slide five", SLIDE_FIVE) { FIVE }
        tap("backward", "backward-live", "Backward", BACKWARD) { slide - 1 }
        segment("upload")
        upload()
        segment("end")
        say("outro")
        outro()
    }

    /**
     * One tap on the tablet: the button called out on the page, what it sends sent, the page drawn
     * again, and the big screen it changed.
     */
    private fun DemoTour.tap(line: String, liveLine: String, heading: String, box: Rect, to: () -> Int) {
        snap()
        view.value = TABLET
        openPanel("Tablet", PANEL_W, PANEL_H)
        say(line)
        val target = to()
        panelNote(heading, "Tap it", tabletBox(box), numbered = true)
        post("/api/presentation-remote/goto/$target")
        slide = target
        waitFor("slide ${target + 1} is shown", SLIDE_WAIT_MS) { hasNode(hasText("Slide ${target + 1}")) }
        settle()
        snap()
        pause(0.3)
        if (line == "forward") {
            say("notes")
            panelNote("Speaker notes", "From PowerPoint or Keynote", tabletBox(NOTES))
        }
        closePanel()
        say(liveLine)
        showLive(sub = "Slide ${target + 1}, from the tablet")
    }

    /**
     * The speaker's own deck, sent from the tablet: Upload, the file picked, the file sent as the
     * page sends it, then the slides on the tablet and the deck open on the computer.
     */
    private fun DemoTour.upload() {
        snap()
        view.value = TABLET
        openPanel("Tablet", PANEL_W, PANEL_H)
        say("upload")
        panelNote("Upload", "Tap it", tabletBox(UPLOAD), numbered = true)
        clearNote()
        picker.value = true
        pause(0.3)
        say("pick")
        panelNote(EASTER, "Tap the file", panelItem(hasText(EASTER)), numbered = true)
        clearNote()
        picker.value = false
        pause(0.3)
        val data = Base64.getEncoder().encodeToString(easter.readBytes())
        post("/api/presentation-remote/upload", """{"name":"$EASTER","data":"data:$PPTX_TYPE;base64,$data"}""")
        waitFor("the uploaded deck is rendered", DECK_WAIT_MS) {
            hasNode(hasText(EASTER)) && hasNode(hasText("Slide ${EASTER_SLIDES.size}")) &&
                !hasNode(hasText("Slide ${DECK_SLIDES.size}"))
        }
        settle()
        snap()
        pause(0.3)
        say("uploaded")
        panelNote("The new slides", "Sent from the tablet", tablet())
        closePanel()
        say("upload-desk")
        note(EASTER, "Open on the church computer", find(hasText(EASTER)) { it.left > dp(TAB_AREA_LEFT_DP) })
    }

    /** The remote's page as the tablet's browser shows it now, drawn by headless Chrome. */
    private fun DemoTour.snap() {
        val out = File(profile.parentFile, "tablet-remote.png")
        out.delete()
        // Until the computer says yes the page waits on a request, which never lets virtual time run out.
        val wait = if (allowed.isEmpty()) "--timeout=$PAGE_WAIT_MS" else "--virtual-time-budget=$PAGE_SETTLE_MS"
        val chrome = ProcessBuilder(
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            // Without it the shot can be a frame older than the page, from before the notes came in.
            "--run-all-compositor-stages-before-draw",
            "--window-size=$PAGE_W,$PAGE_H", "--user-data-dir=${profile.path}", wait,
            "--screenshot=${out.path}", "http://127.0.0.1:$PORT/presentation-remote",
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        // The page's socket says hello with the slide but not its notes, which clears them until the
        // next status poll; saying the slide again, notes and all, keeps them on the tablet's screen.
        val resend = watcher.launch {
            while (true) {
                delay(RESEND_MS)
                shown?.let { companion.broadcastSlideChange(it.id, it.index, it.total, it.playing) }
            }
        }
        if (!chrome.waitFor(CHROME_WAIT_S, TimeUnit.SECONDS)) chrome.destroyForcibly().waitFor()
        resend.cancel()
        if (out.isFile) page.value = ImageIO.read(out).toComposeImageBitmap()
        ui.waitForIdle()
    }

    private fun post(path: String, body: String = "") {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$PORT$path"))
            .header("Content-Type", "application/json")
            .header("X-Device-Id", lastClient)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        http.send(request, HttpResponse.BodyHandlers.discarding())
    }

    /** The QR image itself, which has no name of its own: the square above its caption. */
    private fun DemoTour.qrCode(): Target {
        val label = panelItem(hasText("QR Code shows:"))
        val bottom = label.bounds.top - dp(QR_GAP_DP)
        val half = dp(QR_DP) / 2
        val x = label.bounds.center.x
        return Target(label.node, Rect(x - half, bottom - 2 * half, x + half, bottom))
    }

    private fun DemoTour.tablet(): Target = panelItems(hasContentDescription(TABLET_LABEL)).first()

    private fun DemoTour.tabletBox(box: Rect): Target = screenBox(box, tablet())
}

/** A tablet in landscape, showing the remote's page as the browser last drew it. */
@Composable
private fun Tablet(width: Float) {
    val shot = page.value
    val inner = width - 2 * BEZEL_DP
    val height = inner * PAGE_H / PAGE_W + 2 * BEZEL_DP
    Box(
        Modifier.size(width.dp, height.dp)
            .clip(RoundedCornerShape(RADIUS_DP.dp))
            .background(Color(BODY))
            .border(2.dp, Color(EDGE), RoundedCornerShape(RADIUS_DP.dp))
            .padding(BEZEL_DP.dp),
    ) {
        if (shot != null) {
            Image(
                shot,
                contentDescription = TABLET_LABEL,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((RADIUS_DP - BEZEL_DP).dp)),
            )
        }
        if (picker.value) FilePicker(Modifier.align(Alignment.Center))
    }
}

/** The tablet's own file picker, opened by Upload: the files on the tablet it may send. */
@Composable
private fun FilePicker(modifier: Modifier) {
    Column(
        modifier.width(PICKER_W.dp)
            .clip(RoundedCornerShape(PICKER_RADIUS_DP.dp))
            .background(Color(PICKER_BG))
            .padding(PICKER_PAD_DP.dp),
        verticalArrangement = Arrangement.spacedBy(PICKER_GAP_DP.dp),
    ) {
        Text("Choose a file", color = Color.White, fontSize = PICKER_TITLE_SP.sp, fontWeight = FontWeight.Bold)
        PICKER_FILES.forEach { (name, kind) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(PICKER_ROW_RADIUS_DP.dp))
                    .background(Color(PICKER_ROW)).padding(PICKER_ROW_PAD_DP.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, color = Color.White, fontSize = PICKER_ROW_SP.sp)
                Text(kind, color = Color(PICKER_KIND), fontSize = PICKER_ROW_SP.sp)
            }
        }
    }
}

/** What the church computer asks the first time a tablet opens the remote. */
@Composable
private fun ConnectRequest() {
    val event = RemoteEvent(
        type = RemoteEventType.PRESENTATION_CONNECT,
        title = "",
        clientId = lastClient,
        clientLabel = "Pastor's iPad",
    )
    val shown = resolveRemoteEventPresentation(event, 1, isClientKnownAllowed = false, isClientKnownBlocked = false)
    val answer: (Boolean) -> Unit = { yes ->
        if (yes) allowed += lastClient
        asking.value?.complete(yes)
        asking.value = null
    }
    RemoteEventDialogContent(
        event = event,
        actionLabel = shown.actionLabel,
        typeIcon = shown.typeIcon,
        typeAccent = shown.typeAccent,
        bodyTitle = shown.bodyTitle,
        remaining = 0,
        showAllowPermanently = true,
        isClientKnownAllowed = false,
        isClientKnownBlocked = false,
        isInstanceLinkFollower = false,
        onAllow = { answer(true) },
        onAllowForSession = { answer(true) },
        onAllowPermanently = { answer(true) },
        onBlockForSession = { answer(false) },
        onBlockPermanently = { answer(false) },
        onDeny = { answer(false) },
    )
}

private val view = mutableStateOf("")
private val picker = mutableStateOf(false)
private val page = mutableStateOf<ImageBitmap?>(null)
private val asking = mutableStateOf<CompletableDeferred<Boolean>?>(null)
private val allowed = mutableSetOf<String>()

@Volatile private var lastClient = ""
private var slide = 0

private class Shown(val id: String, val index: Int, val total: Int, val playing: Boolean)

@Volatile private var shown: Shown? = null

/** The tablet's own browser profile, so it is one device across every page it draws. */
private val profile = File(System.getenv("CP_DEMO_OUT") ?: "build/demo", "tablet-remote-chrome")

/** The speaker's own deck, as it sits on the tablet before it is sent. */
private val easter = File(profile.parentFile, "tablet-remote-files/$EASTER")

private const val DIALOG = "dialog"
private const val ASK = "ask"
private const val TABLET = "tablet"
private const val TABLET_LABEL = "Tablet screen"
private const val PROMPT_LABEL = "Connect request"
private const val QR_DP = 180f
private const val QR_GAP_DP = 8f
private const val CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
private const val PORT = 48_766
private const val SHOWN_HOST = "192.168.1.42"
private const val FIVE = 4
private const val DECK = "Psalm 23.pptx"
private const val EASTER = "Easter Sunday.pptx"
private const val PPTX_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

// Where the page's controls sit, as fractions of the tablet's screen; measured from the page.
private val FORWARD = Rect(0.503f, 0.93f, 0.992f, 0.99f)
private val BACKWARD = Rect(0.008f, 0.93f, 0.497f, 0.99f)
private val UPLOAD = Rect(0.926f, 0.009f, 0.993f, 0.056f)
private val NOTES = Rect(0.005f, 0.066f, 0.995f, 0.116f)
private val SLIDE_FIVE = Rect(0.432f, 0.825f, 0.534f, 0.907f)

private const val PAGE_W = 1180
private const val PAGE_H = 820
private const val PAGE_WAIT_MS = 3_000

// Past the page's 2.5-second status poll, which is what brings a slide's notes back after connecting.
private const val PAGE_SETTLE_MS = 8_000
private const val RESEND_MS = 300L
private const val CHROME_WAIT_S = 30L
private const val DECK_WAIT_MS = 30_000L
private const val SLIDE_WAIT_MS = 10_000L
private const val TAB_AREA_LEFT_DP = 380f
private const val PANEL_W = 1340f
private const val PANEL_H = 940f
private const val ASK_W = 1100f
private const val ASK_H = 460f
private const val DIALOG_W = 400f
private const val DIALOG_H = 720f
private const val PROMPT_W = 480f
private const val PROMPT_H = 340f
private const val PAIR_GAP_DP = 40
private const val TABLET_W = 1300f
private const val TABLET_SMALL_W = 560f
private const val BEZEL_DP = 14
private const val RADIUS_DP = 30
private const val BODY = 0xFF2C2E33
private const val PICKER_BG = 0xF21C1D21
private const val PICKER_ROW = 0xFF2A2C31
private const val PICKER_KIND = 0xFF9CA3AF
private const val PICKER_W = 760
private const val PICKER_RADIUS_DP = 18
private const val PICKER_PAD_DP = 30
private const val PICKER_GAP_DP = 16
private const val PICKER_ROW_RADIUS_DP = 10
private const val PICKER_ROW_PAD_DP = 22
private const val PICKER_TITLE_SP = 34
private const val PICKER_ROW_SP = 28
private const val NAVY = 0x14223A
private const val GOLD = 0xF2C94C
private const val DECK_W = 960
private const val DECK_H = 540
private const val TITLE_PT = 60.0
private const val SUB_PT = 30.0
private const val EDGE = 0xFF45474D

/** A sermon in PowerPoint, every slide with speaker notes for the remote to show. */
private fun writeDeck(file: File, slides: List<Triple<String, String, String>>) {
    XMLSlideShow().use { ppt ->
        ppt.pageSize = Dimension(DECK_W, DECK_H)
        slides.forEach { (title, sub, notes) ->
            val slide = ppt.createSlide()
            slide.background.fillColor = AwtColor(NAVY)
            slide.line(title, Rectangle(80, 180, 800, 100), TITLE_PT, AwtColor.WHITE, bold = true)
            slide.line(sub, Rectangle(80, 290, 800, 60), SUB_PT, AwtColor(GOLD))
            val sheet = ppt.getNotesSlide(slide)
            val body = sheet.placeholders.filterIsInstance<XSLFTextShape>()
                .firstOrNull { it.textType?.name?.contains("BODY") == true } ?: sheet.placeholders.getOrNull(1)
            body?.text = notes
        }
        file.parentFile.mkdirs()
        file.outputStream().use { ppt.write(it) }
    }
}

private fun XSLFSlide.line(
    text: String,
    at: Rectangle,
    size: Double,
    color: AwtColor,
    bold: Boolean = false,
) {
    val box = createTextBox()
    box.anchor = at
    box.clearText()
    box.addNewTextParagraph().addNewTextRun().apply {
        setText(text)
        fontSize = size
        setFontColor(color)
        isBold = bold
    }
}

private val EASTER_SLIDES = listOf(
    Triple("He Is Risen", "Easter Sunday", "Big welcome. Mention the egg hunt after the service."),
    Triple("Luke 24:1-6", "The empty tomb", "Read it slowly, and let the last line land."),
    Triple("Response", "Come and see", "Invite the band up. Close in prayer."),
)

private val PICKER_FILES = listOf(
    "Easter Sunday.pptx" to "PowerPoint",
    "Youth Night.key" to "Keynote",
    "Bulletin.pdf" to "PDF",
)

private val DECK_SLIDES = listOf(
    Triple("The Lord Is My Shepherd", "Psalm 23", "Welcome everyone. Ask them to open to Psalm 23."),
    Triple("Psalm 23:1-6", "Read it together", "Read the whole psalm aloud, slowly."),
    Triple("1. He Provides", "I shall not want", "Story: the food bank volunteers this spring."),
    Triple("2. He Restores", "He restores my soul", "Pause here. Rest is not laziness."),
    Triple("3. He Walks With Us", "Through the valley", "Mention the Johnsons' family, with their permission."),
    Triple("Response", "Take a moment to pray", "Invite the band up. Close in prayer."),
)
